package org.duangsuse.parserkt

import kotlin.reflect.KMutableProperty0

// Pattern Seq, Decide, Repeat and (not)item, (not)elementIn, satisfy and string
// And basic definitions about parsers and toDefault/convert/alsoDo

typealias Parser<T> = (Input) -> T?
typealias AlwaysParser<T> = (Input) -> T
inline val notPas: Nothing? get() = null

fun <T> Parser<T>.toDefault(defaultValue: T): AlwaysParser<T> = { this(it) ?: defaultValue }
inline fun <T> Parser<T>.alsoDo(crossinline op: (T) -> Unit): Parser<T> = { this(it)?.also(op) } // also, apply
inline fun <T, R> Parser<T>.convert(crossinline transform: (T) -> R): Parser<R> = { this(it)?.let(transform) } // let, run

private typealias P<T> = Parser<T>
inline fun <T> Input.run(p: P<T>, assign: KMutableProperty0<T?>): T? = p(this)?.also { assign.set(it) }
inline fun <R, A, B> Seq(crossinline p1: P<A>, crossinline p2: P<B>, crossinline op: Tuple2<A, B>.() -> R): Parser<R> = parse@ {
  val tup = Tuple2<A?, B?>(null, null)
  it.run(p1, tup::e1) ?: return@parse notPas
  it.run(p2, tup::e2) ?: return@parse notPas
  @Suppress("unchecked_cast") (tup as Tuple2<A, B>).op()
}

fun <T> Decide(vararg ps: Parser<out T>): Parser<T> = parse@ {
  for (p in ps) { val res = p(it); if (res != notPas) return@parse res }
  return@parse notPas
}

inline fun <T, R> Repeat(crossinline fold: Fold<T, R>, crossinline p: Parser<T>, at_least: Int = 0, at_most: Int = Int.MAX_VALUE): Parser<R> = parse@ {
  val reducer = fold()
  var count = 1
  while (true) {
    val parsed = p(it) ?: break
    it.catchErrorNull { reducer.accept(parsed) } ?: break
    count++; if (count > at_most) break
  }
  return@parse if (count >= at_least) reducer.finish() else notPas
}

fun item(c: Char) = satisfy { it == c }
fun notItem(c: Char) = satisfy { it != c }
fun elementIn(vararg ranges: CharRange) = satisfy { ranges.any { cs -> it in cs } }
fun elementIn(cs: CharRange) = satisfy { it in cs }
fun notElementIn(cs: CharRange) = satisfy { it !in cs }
fun singleCharRanges(vararg cs: Char) = Array(cs.size) { i -> cs[i]..cs[i] }
inline fun satisfy(crossinline predicate: CharPredicate): Parser<Char> = parse@ {
  if (predicate(it.peek)) it.consumeOrNull() else notPas
}
fun string(string: String): Parser<String> = parse@ {
  return@parse if (it.peekMany(string.length) == string) {
    for (_i in string.indices) it.consume()
    string
  } else null
}
fun Feed.consumeOrNull() = try { consume() } catch (_:Feed.End) { null }

// seq #p = 2~4
inline fun <R, A, B, C> Seq(crossinline p1: P<A>, crossinline p2: P<B>, crossinline p3: P<C>, crossinline op: Tuple3<A, B, C>.() -> R): Parser<R> = parse@ {
  val tup = Tuple3<A?, B?, C?>(null, null, null)
  it.run(p1, tup::e1) ?: return@parse notPas
  it.run(p2, tup::e2) ?: return@parse notPas
  it.run(p3, tup::e3) ?: return@parse notPas
  @Suppress("unchecked_cast") (tup as Tuple3<A, B, C>).op()
}
inline fun <R, A, B, C, D> Seq(crossinline p1: P<A>, crossinline p2: P<B>, crossinline p3: P<C>, crossinline p4: P<D>, crossinline op: Tuple4<A, B, C, D>.() -> R): Parser<R> = parse@ {
  val tup = Tuple4<A?, B?, C?, D?>(null, null, null, null)
  it.run(p1, tup::e1) ?: return@parse notPas
  it.run(p2, tup::e2) ?: return@parse notPas
  it.run(p3, tup::e3) ?: return@parse notPas
  it.run(p4, tup::e4) ?: return@parse notPas
  @Suppress("unchecked_cast") (tup as Tuple4<A, B, C, D>).op()
}
