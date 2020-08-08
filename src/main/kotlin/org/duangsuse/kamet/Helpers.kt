package org.duangsuse.kamet

import java.lang.IllegalStateException

open class CascadeMap<K, V>(private val map: MutableMap<K, V> = mutableMapOf(), val parent: CascadeMap<K, V>? = null): MutableMap<K, V> by map {
  override fun get(key: K): V? = chainSearch(key, this)
  fun subMap() = CascadeMap(parent = this)

  private tailrec fun chainSearch(key: K, sub: CascadeMap<K, V>): V? {
    return sub.map[key] ?: chainSearch(key, sub.parent ?: return null)
  }
  override fun containsKey(key: K): Boolean {
    var cur: CascadeMap<K, V>? = this
    while (cur != null) {
      if (key in cur.map) return true
      cur = cur.parent
    }
    return false
  }
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

fun Iterable<*>.joinToCommaString() = joinToString(", ")
inline fun impossible(): Nothing = throw IllegalStateException("impossible")
