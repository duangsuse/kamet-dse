package org.duangsuse.parserkt

import org.duangsuse.kamet.Twice

// Parser Piped(concat, decide, greedy), SurroundBy, JoinBy, InfixParser, TrieParser
// Complex parsers(join, infix) cannot be inlined

object Piped {
  inline operator fun <T> invoke(crossinline p: Parser<T>, crossinline op: Input.(T) -> T? = {it}): Parser<T> = { s ->
    p(s)?.let { s.op(it) }
  }
  inline fun <R, T> concat(crossinline p: Parser<T>, crossinline p1: Parser<T>, crossinline op_both: Twice<T>.() -> R)
    = concat(p, p1, { it }, op_both)
  inline fun <R, A, B> concat
    (crossinline p: Parser<A>, crossinline p1: Parser<B>,
     crossinline op: (A) -> R, crossinline op_both: Pair<A, B>.() -> R): Parser<R> = parse@ {
    val res = p(it) ?: return@parse notPas
    val res1 = p1(it) ?: return@parse op(res)
    return@parse (res to res1).op_both()
  }
  inline fun <T> decide(crossinline pp: Parser<Parser<T>>): Parser<T> = parse@ {
    val p = pp(it) ?: return@parse notPas
    p(it)
  }
  inline fun <T> greedy(crossinline p: Parser<T>): Parser<List<T>> = parse@ { s ->
    val results = mutableListOf<T>()
    while (!s.isEnd) p(s)?.let { results.add(it) } ?: s.consume()
    results
  }
}

typealias SurroundPair<T> = Pair<Parser<T>?, Parser<T>?>
inline fun <SUR, T> SurroundBy(crossinline left: Parser<SUR>, crossinline right: Parser<SUR>, crossinline item: Parser<T>): Parser<T> = parse@ { s ->
  left(s) ?: return@parse notPas
  val parsed = item(s)
  right(s) ?: return@parse notPas
  parsed
}
inline fun <SUR, T> SurroundBy(surround: SurroundPair<SUR>, crossinline item: Parser<T>): Parser<T> = parse@ { s ->
  val (left, right) = surround
  if (left != null) left(s) ?: return@parse notPas
  val parsed = item(s)
  if (right != null) right(s) ?: return@parse notPas
  parsed
}

fun <T> SurroundBy(surround: PairedChar, item: Parser<T>): Parser<T> = SurroundBy(item(surround.start), item(surround.close), item)
data class PairedChar(val start: Char, val close: Char)
infix fun Char.paired(close: Char) = PairedChar(this, close)

infix fun <SUR, T> Parser<T>.prefix(item: Parser<SUR>) = SurroundBy(item to null, this)
infix fun <SUR, T> Parser<T>.suffix(item: Parser<SUR>) = SurroundBy(null to item, this)


/**
 * Represent text pattern like `a, b, c, ..., last`. `JoinBy(sep, f_sep, item, f_item, rescue)`
 * - join by [sep] and includes [item], specify their fold in arg before them
 * - [rescue] is called to give default item(or error) when separator is missing its item
 * - [sep_default] is provided for [toDefault] always parsed value, when case^, the missing item is ignored
 */
fun <SEP, SEP_S, T, T_S> JoinBy
  (sep: Parser<SEP>, fold_sep: Fold<SEP, SEP_S>, item: Parser<T>, fold_item: Fold<T, T_S>,
   rescue: Input.(Pair<T_S, SEP_S>) -> T? = { notPas.also { error("expecting item for last separator") } },
   sep_default: SEP? = null): Parser<Pair<T_S, SEP_S>> = parse@ { s ->
  val items = fold_item() ; val separators = fold_sep()
  fun readItem() = item(s)?.also { items.accept(it) }
  fun readSep() = sep(s)?.also { separators.accept(it) }
  fun finish() = items.finish() to separators.finish()

  readItem() ?: return@parse notPas
  var separator = readSep()
  while (separator != notPas) {
    readItem() ?: if (sep_default?.let { separator == it } == true) return@parse finish()
      else rescue(s, finish()) ?: return@parse finish()
    separator = readSep()
  }
  return@parse finish()
}

fun <T, T_S> JoinBy(sep: Parser<Char>, item: Parser<T>, fold_item: Fold<T, T_S>): Parser<T_S>
  = JoinBy(sep, asIgnored(' '), item, fold_item).convert { it.first }

// TrieParser and InfixOp/InfixParser
typealias CharTrie<V> = Trie<Char, V>
fun <T> TrieParser(trie: CharTrie<T>): Parser<T> = parse@ { s ->
  var point = trie
  while (true)
    try { point = point.routes[s.peek]?.also { s.consume() } ?: break }
    catch (_: Feed.End) { break }
  point.value
}

inline fun <T> MapParser(map: Map<Char, T>, crossinline no_key: Input.(Char) -> T? = {notPas}): Parser<T> = parse@ { s ->
  val key = s.peek; val value = map[key]
  return@parse when {
    value == null -> s.no_key(key)
    s.isEnd -> notPas
    else -> { s.consume(); value }
  }
}

typealias InfixJoin<T> = T.(T) -> T // (+) joins a b
data class Precedence(val ordinal: Int, val isRAssoc: Boolean)
infix fun String.infixl(prec: Int) = this to Precedence(prec, false)
infix fun String.infixr(prec: Int) = this to Precedence(prec, true)

class InfixOp<T>(val name: String, val assoc: Precedence, val join: InfixJoin<T>): Comparable<InfixOp<T>> {
  override fun compareTo(other: InfixOp<T>) = assoc.ordinal.compareTo(other.assoc.ordinal)
  override fun toString() = name
}
infix fun <T> Pair<String, Precedence>.join(op: InfixJoin<T>) = InfixOp(first, second, op)
fun <T> CharTrie<InfixOp<T>>.register(op: InfixOp<T>) { this[op.name] = op }

typealias InfixRescue<EXP> = Input.(EXP, InfixOp<EXP>) -> EXP?
fun <EXP> InfixParser(atom: Parser<EXP>, op: Parser<InfixOp<EXP>>, rescue: InfixRescue<EXP> = { _, _ -> notPas }): Parser<EXP> = parse@ { s ->
  /** Reads expr like `1 + (2*3)` recursively, viewport: two operators base(+rhs1)+ */
  fun infixChain(base: EXP, op_left: InfixOp<EXP>? = null): EXP? {
    val op1 = op_left ?: op(s) ?: return base  //'+' in 1+(2*3)... || return atom "1"
    val rhs1 = atom(s) ?: rescue(s, base, op1) ?: return notPas //"2"
    val op2 = op(s) ?: return op1.join(base, rhs1) //'*' //(a⦁b) END: terminated

    fun associateLeft() = infixChain(op1.join(base, rhs1), op2) //(a ⦁ b) ⦁ ...
    fun associateRight() = infixChain(rhs1, op2)?.let { op1.join(base, it) } //a ⦁ (b ⦁ ...)
    return when { // lessThan b => first
      op1 < op2 -> associateLeft()
      op1 > op2 -> associateRight()
      else -> if (op1.assoc.isRAssoc) associateRight() else associateLeft()
    }
  }

  val base = atom(s) ?: return@parse notPas
  infixChain(base)
}
