package org.duangsuse.parserkt

import org.duangsuse.kamet.impossible
import java.lang.StringBuilder

typealias Fold<T, R> = () -> Reducer<T, R>

/** Reducer, or acceptor objects extracted from [Iterable.fold] */
interface Reducer<in T, out R> {
  fun accept(x: T)
  fun finish(): R
  fun fold(initial: T, xs: Iterable<T>): R {
    xs.forEach(::accept)
    return finish()
  }
}

open class ConvertReducer<A, T, R>(initial: A, val accept: A.(T) -> A, val finish: A.() -> R): Reducer<T, R> {
  private var acc = initial
  override fun accept(x: T) { acc = accept(acc, x) }
  override fun finish(): R = acc.finish()
}
class ModifyReducer<T, R>(initial: R, accept: R.(T) -> R, finish: R.() -> R): ConvertReducer<R, T, R>(initial, accept, finish) {
  constructor(initial: R, accept: R.(T) -> R): this(initial, accept, { this })
}

fun <T> asList(): Reducer<T, List<T>> = ModifyReducer(mutableListOf()) { add(it); this }
fun asString(): Reducer<Char, String> = ConvertReducer(StringBuilder(), { append(it) }, { toString() })
fun concatAsNumber(base: Int = 10): Reducer<Int, Long> = ModifyReducer(0L) { this*base + it }

// Mutable tuple 2~4
data class Tuple2<A, B>(var e1: A, var e2: B)
data class Tuple3<A, B, C>(var e1: A, var e2: B, var e3: C)
data class Tuple4<A, B, C, D>(var e1: A, var e2: B, var e3: C, var e4: D)

/** Extensible sequence indexing tree */
open class Trie<K, V>(var value: V?) {
  constructor() : this(null)
  val routes: MutableMap<K, Trie<K, V>> by lazy(::mutableMapOf)

  operator fun get(key: Iterable<K>): V? = getPath(key).value
  open operator fun set(key: Iterable<K>, value: V) { getOrCreatePath(key).value = value }

  fun getPath(key: Iterable<K>) = key.fold(initial = this) { point, k -> point.routes[k] ?: errorNoPath(key, k) }
  fun getOrCreatePath(key: Iterable<K>) = key.fold(initial = this) { point, k -> point.routes.getOrPut(k, ::Trie) }

  operator fun contains(key: Iterable<K>) = try { this[key] != null }
    catch (_: NoSuchElementException) { false }

  private fun errorNoPath(key: Iterable<K>, k: K): Nothing = throw NoSuchElementException("${key.joinToString("/")} @$k")

  override fun equals(other: Any?) = (other is Trie<*,*>) && other.routes == routes && other.value == value
  override fun hashCode() = hash(routes, value)
  override fun toString(): String = when {
    value == null -> "Path$routes"
    value != null && routes.isNotEmpty() -> "Bin[$value]$routes"
    value != null && routes.isEmpty() -> "Term($value)"
    else -> impossible()
  }
}

operator fun <V> Trie<Char, V>.get(key: CharSequence) = get(key.asIterable())
operator fun <V> Trie<Char, V>.set(key: CharSequence, value: V) = set(key.asIterable(), value)

fun <V> Trie<Char, V>.mergeStrings(vararg pairs: Pair<String, V>) {
  for ((k, v) in pairs) set(k, v)
}