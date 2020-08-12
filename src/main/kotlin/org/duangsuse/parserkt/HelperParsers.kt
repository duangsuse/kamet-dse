package org.duangsuse.parserkt

import org.duangsuse.kamet.Twice

object PConv {
  inline fun int(p: Parser<Long>) = p.convert { it.toInt() }
  inline fun string(p: Parser<Char>) = p.convert { "$it" }
  inline fun <A, B> pair(p: Parser<Tuple2<A, B>>) = p.convert { it.e1 to it.e2 }
}

/** Create multiply route in path to [key], helper for functions like `setLowCase` */
fun <K, V> Trie<K, V>.getOrCreatePaths(key: Iterable<K>, layer: (K) -> Iterable<K>): List<Trie<K, V>>
  = key.fold(listOf(this)) { points, k ->
  points.flatMap { point ->
    layer(k).map { point.routes.getOrPut(it, ::Trie) }
  }
}

fun <V> CharTrie<V>.getOrCreatePathsBothCase(key: CharSequence)
  = getOrCreatePaths(key.asIterable()) { listOf(it.toUpperCase(), it.toLowerCase()) }

fun <V> CharTrie<V>.setBothCase(key: CharSequence, value: V) = getOrCreatePathsBothCase(key).forEach { it.value = value }
fun <V> CharTrie<V>.mergeStringsBothCase(vararg pairs: Pair<CharSequence, V>) { for ((k, v) in pairs) this.setBothCase(k, v) }

/** Template parsers */
open class LexicalBasicsHelper {
  fun digitFor(cs: CharRange, zero: Char = '0', pad: Int = 0): Parser<Int>
    = elementIn(cs).convert { (it - zero) +pad }

  inline fun stringFor(crossinline char: Parser<Char>) = Repeat(::asString, char).toDefault("")

  inline fun prefix1(crossinline head: Parser<Char>, crossinline item: Parser<String>): Parser<String> = Seq(head, item) { e1 + e2 }
  inline fun suffix1(tail: Char, crossinline item: Parser<Char>) = Seq(Repeat(::asString, item), item(tail)) { e1 + e2 }
  inline fun suffix1(vararg tail: CharRange, crossinline item: Parser<Char>) = Seq(Repeat(::asString, item), elementIn(*tail)) { e1 + e2 }

  inline val newlineChar get() = elementIn('\r', '\n')
  inline val singleLine get() = suffix1(item=anyChar, tail=*singleCharRanges('\r', '\n'))
}

abstract class LexicalBasics(protected val white: Parser<Char> = elementIn(' ', '\t', '\n', '\r')) {
  protected inline val ws1 get() = Repeat(asIgnored(Unit), white)
  protected inline val ws get() = ws1.toDefault(Unit)

  /** tokenize */ protected inline fun <T> _t(crossinline p: Parser<T>) = SurroundBy(ws, alwaysParsed(Unit), p)
  /** tokenizeInner */ protected inline fun <T> _t_(crossinline p: Parser<T>) = SurroundBy(ws, ws, p)

  protected inline fun <T> split(crossinline p: Parser<T>) = SurroundBy(ws, ws1, p)
  protected infix fun <T> Parser<Char>.separated(item: Parser<T>): Parser<List<T>>
    = _t(JoinBy(this, item, ::asList).toDefault(emptyList()))

  inline fun calmly(start: Parser<Char>, close: Char, crossinline format: CalmlyFormat) = start.alsoDo {
    sourceLoc.let { stateAs<ExpectClose>()?.add(close, it) }
  } to item(close).calmWhile(notItem(close), close) { format(close to close) }
  /** calm for `() []` surroundings, inner whitespaces should be skipped with [_t_] */
  inline fun calmly(start: Parser<Char>, close: Char) = calmly(start, close, calmlyFormat)

  companion object Helper: LexicalBasicsHelper() {
    inline val digit get() = digitFor('0'..'9')
    /** signed = negative */
    inline val sign get() = elementIn('+', '-').toDefault('+').convert { it == '-' }
    inline val bin get() = digitFor('0'..'1'); val octal = digitFor('0'..'8')
    inline val hex get() = Decide(digit, digitFor('A'..'F', 'A', 10), digitFor('a'..'f', 'a', 10))

    inline val numLong get() = Repeat({concatAsNumber()}, digit)
    inline val numInt get() = PConv.int(numLong)

    val calmlyFormat: CalmlyFormat = { pair ->
      val fromTag = stateAs<ExpectClose>()?.remove(pair)?.let {" (from $it)"} ?: ""
      "expecting ${pair.second}$fromTag"
    }
  }
}

typealias CalmlyFormat = Input.(Twice<*>) -> String

open class ExpectClose {
  private val map: MutableMap<Any, MutableList<SourceLocated.SourceLocation>> = mutableMapOf()
  fun add(id: Any, sourceLoc: SourceLocated.SourceLocation) { map.getOrPut(id, ::mutableListOf).add(sourceLoc) }
  fun remove(id: Any): SourceLocated.SourceLocation = map.getValue(id).removeLast()
}

fun <E> MutableList<E>.removeLast() = removeAt(lastIndex)

inline fun <T> Iterable<T>.allWithNext(predicate: (T, T) -> Boolean): Boolean {
  val xz = iterator()
  var left = xz.next()
  while (xz.hasNext()) {
    val right = xz.next()
    if (!predicate(left, right)) return false
    else left = right
  }
  return true
}
fun <T, I:Comparable<I>> Iterable<T>.isDescendingSortedBy(selector: (T) -> I): Boolean = allWithNext { a, b -> selector(a) >= selector(b) }
