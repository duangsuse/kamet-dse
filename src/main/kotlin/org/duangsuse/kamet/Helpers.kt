package org.duangsuse.kamet

import java.lang.IllegalStateException

typealias Twice<T> = Pair<T, T>

class CascadeMap<K, V>(private val map: MutableMap<K, V> = mutableMapOf(), private val parent: CascadeMap<K, V>? = null): MutableMap<K, V> by map {
  override fun get(key: K): V? = chainSearch(key, this)?.also { map[key] = it }
  private tailrec fun chainSearch(key: K, sub: CascadeMap<K, V>): V? {
    return sub.map[key] ?: chainSearch(key, sub.parent ?: return null)
  }

  fun subMap() = CascadeMap(parent = this)
  override fun containsKey(key: K) = chainSearch(key, this) != null
}

fun <T, A, B> Iterable<T>.splitPairs(selector: (T) -> Pair<A, B>): Pair<List<A>, List<B>> {
  val xs: MutableList<A> = mutableListOf()
  val ys: MutableList<B> = mutableListOf()
  for ((x, y) in map(selector)) { xs.add(x); ys.add(y) }
  return xs to ys
}

inline fun <T, reified R> List<T>.mapToArray(transform: (T) -> R): Array<R> {
  val array = arrayOfNulls<R>(size)
  for ((i, x) in withIndex()) array[i] = transform(x)
  @Suppress("unchecked_cast") return array as Array<R>
}

internal fun Iterable<*>.joinToCommaString() = joinToString(", ")
internal fun impossible(): Nothing = throw IllegalStateException("impossible")

internal fun Char.describe() = "'$this' (0x${toShort().toString(16)})" //'A' (0x41)

private val escapeMap = mapOf(
  '\\' to '\\',
  '"' to '"',
  'n' to '\n',
  'r' to '\r',
  't' to '\t',
  'b' to '\b',
  'f' to '\u000c',
  'v' to '\u000b',
  '0' to '\u0000'
)
private val reversedEscapeMap = escapeMap.entries.associate { it.value to it.key }
internal fun Char.unescape(): Char = escapeMap[this] ?: throw IllegalEscapeException(this)
internal fun String.escape(): String = fold(StringBuilder()) { sb, c ->
  reversedEscapeMap[c]?.let { sb.append('\\').append(it) } ?: sb.append(c)
}.toString()

internal fun String.showIf(p: Boolean) = if (p) this else ""
internal fun Any?.showIfNotNull(prefix: String) = this?.let { prefix+it } ?: ""

fun String.toLongOverflow(base: Int = 10): Long =
  substring(if (this[0] == '-') 1 else 0).fold(0L) { acc, c -> acc*base + (c-'0') }

class IllegalCastException(type: Type, dest: Type): IllegalArgumentException("Illegal cast from $type to $dest")
class IllegalEscapeException(char: Char): NoSuchElementException("Illegal escape char: ${char.describe()}")
