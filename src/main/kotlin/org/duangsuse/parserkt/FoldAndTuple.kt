package org.duangsuse.parserkt

import java.lang.StringBuilder

/** Reducer, or acceptor objects extracted from [Iterable.fold] */
interface Fold<in T, out R> {
  fun accept(x: T)
  fun finish(): R
  fun fold(initial: T, xs: Iterable<T>): R {
    xs.forEach(::accept)
    return finish()
  }
}

open class ConvertFold<A, T, R>(initial: A, val accept: A.(T) -> A, val finish: A.() -> R): Fold<T, R> {
  private var acc = initial
  override fun accept(x: T) { acc = accept(acc, x) }
  override fun finish(): R = acc.finish()
}
class ModifyFold<T, R>(initial: R, accept: R.(T) -> R, finish: R.() -> R): ConvertFold<R, T, R>(initial, accept, finish) {
  constructor(initial: R, accept: R.(T) -> R): this(initial, accept, { this })
}

fun <T> asList(): Fold<T, List<T>> = ModifyFold(mutableListOf()) { add(it); this }
fun asString(): Fold<Char, String> = ConvertFold(StringBuilder(), { append(it) }, { toString() })

data class Tuple1<A>(var e1: A)
data class Tuple2<A, B>(var e1: A, var e2: B)
data class Tuple3<A, B, C>(var e1: A, var e2: B, var e3: C)
data class Tuple4<A, B, C, D>(var e1: A, var e2: B, var e3: C, var e4: D)
