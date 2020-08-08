package org.duangsuse.kamet.irbuild

import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.llvm.global.LLVM.LLVMDisposeMessage

typealias Producer<T> = () -> T
typealias Consumer<T> = (T) -> Unit

class CascadeMap<K, V>(private val map: MutableMap<K, V> = mutableMapOf(), val parent: CascadeMap<K, V>? = null): MutableMap<K, V> by map {
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

inline fun withErrorHandling(message: String, op: (BytePointer) -> Int) {
  val szError = BytePointer(null as Pointer?)
  op(szError).also { if (it != 0) error("$message: ${szError.asErrorMessage()}") }
}

fun BytePointer.asErrorMessage(): String = string.also { LLVMDisposeMessage(this) }

fun <T, A, B> Iterable<T>.splitPairs(selector: (T) -> Pair<A, B>): Pair<List<A>, List<B>> {
  val xs: MutableList<A> = mutableListOf()
  val ys: MutableList<B> = mutableListOf()
  for ((x, y) in map(selector)) { xs.add(x); ys.add(y) }
  return xs to ys
}

internal fun Boolean.toInt() = if (this) 1 else 0