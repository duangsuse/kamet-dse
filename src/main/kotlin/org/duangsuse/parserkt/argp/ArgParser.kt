package org.duangsuse.parserkt.argp

import org.duangsuse.parserkt.Tuple4

// Argument patters: flag(no-arg)/action, argument:optional/repeatable

/** Argument with name, help, optional param+defaultValue+convert? name is separated with " " */
data class Arg<R>(
  val name: String, val help: String, val param: String?, var defaultValue: R?,
  val repeatable: Boolean, val convert: ((String) -> R)?) {
  val isFlag get() = (param == null)
  val firstName get() = name.split().first()
}

private typealias OM<E> = OneOrMore<E>
open class ParseResult<A, B, C, D>(
  val tup: Tuple4<OM<A>, OM<B>, OM<C>, OM<D>> = Tuple4(OM(), OM(), OM(), OM()),
  var flags: String = "", val items: MutableList<String> = mutableListOf())

/** Order constraint for positional(no-prefix) args (comparing to prefix args) */
enum class PositionalMode { MustBefore, MustAfter, Disordered }

val noArg: Arg<String> = arg("\u0000", "") { error("noArg parsed") }
val helpArg = arg("h help", "print this help") { SwitchParser.stop() }
/** Simple parser helper for no-multi (prefixed) param and up to 4 param storage(parse with items), subset of [SwitchParser] */
open class ArgParser4<A,B,C,D>(
  val p1: Arg<A>, val p2: Arg<B>,
  val p3: Arg<C>, val p4: Arg<D>,
  val itemNames: List<String> = emptyList(), val itemMode: PositionalMode = PositionalMode.Disordered,
  val autoSplit: List<String> = emptyList(), vararg val flags: Arg<*>) {
  open val prefixes = listOf("--", "-")
  inner class Driver(args: ArgArray): SwitchParser<ParseResult<A, B, C, D>>(args, prefixes) {
    override val res = ParseResult<A, B, C, D>()
    private inline val tup get() = res.tup
    @Suppress("UNCHECKED_CAST") // dynamic type
    private val ps = listOf(p1 to tup.e1, p2 to tup.e2, p3 to tup.e3, p4 to tup.e4) as List<Pair<Arg<*>, OM<Any>>>
    private val nameMap = ps.associateBySplit { it.first.name.split() }
    private val flagMap = flags.asIterable().associateBySplit { it.name.split() }
    private val autoSplits by lazy { autoSplit.mapNotNull(nameMap::get) }
    init {
      for ((p, sto) in ps.filter { it.first != noArg }) {
        if (p.isFlag) error("flag ${p.firstName} should be putted in ArgParser(flag = ...)")
        if (p.defaultValue != null) sto.value = p.defaultValue
      }
    }

    private var lastPosit = 0
    private val caseName = if (itemMode == PositionalMode.MustBefore) "all-before" else "all-after"
    private fun itemFail(case_name: String): Nothing = parseError("too $case_name items (all $itemNames)")
    private fun positFail(): Nothing = parseError("reading ${res.items}: should $caseName options")
    private inline val isItemLess get() = res.items.size < itemNames.size
    override fun onItem(text: String) = res.items.plusAssign(text).also {
      if (itemMode == PositionalMode.Disordered) return@also
      lastPosit++ // check continuous (ClosedRange)
      when {
        itemMode == PositionalMode.MustAfter && pos == 1 -> positFail() // no first-appear.
        (pos != lastPosit) ->
          if (itemMode == PositionalMode.MustAfter && lastPosit == 0 +1) lastPosit = pos // one chance, late-initial posit
          else positFail() // zero initiated posit, fail.
      }
      if (res.items.size > itemNames.size) itemFail("many")
    }
    override fun onPrefix(name: String) {
      if (itemMode == PositionalMode.MustBefore && isItemLess) itemFail("less")
      flagMap[name]?.let {
        if (it == helpArg) printHelp().also { res.flags += 'h' } // suppress post chk. in [run]
        res.flags += it.convert?.invoke(name) ?: it.firstName ; return
      }
      var split: Any? = null // flag, auto-split
      fun autoSplit(): Pair<Arg<*>, OM<Any>>? {
        for (sp in autoSplits) for (bang in sp.first.name.split()) // aliases
          extractArg(bang, name)?.let { split = sp.first.convert?.invoke(it); return sp }
        return null
      }
      val (p, sto) = nameMap[name] ?: autoSplit() ?: parseError("$name unknown")
      p.param!!
      fun read() = split ?: arg(p.param, p.convert ?: error("missing converter"))!!
      if (p.repeatable) sto.add(read())
      else sto.value =
        if (sto.value == null || sto.value == p.defaultValue) read()
        else parseError("argument $name repeated")
    }

    private fun parseError(message: String): Nothing = throw ParseError(message)
    override fun run() = super.run().also {
      if (itemMode == PositionalMode.MustAfter && isItemLess && 'h' !in it.flags) itemFail("less")
    }
  }
  fun run(args: ArgArray) = Driver(args).run()
  private val params = listOf(p1, p2, p3, p4)
  private val allParams = (params + flags).filter { it != noArg }
  fun toString(head: String = "usage: ", epilogue: String = "", indent: String = "  ", colon: String = ": ", newline: Char = '\n'): String {
    val sb = StringBuilder(head)
    val pre = prefixes.last()
    if (itemMode == PositionalMode.MustBefore) sb.append(itemNames).append(' ')
    allParams.joinTo(sb, " ") {
      val surround: String = if (it.repeatable) "{}" else if (it.defaultValue != null) "()" else "[]"
      var s = pre+it.joinedName() ; it.param?.run { s += " $this" }
      surround.let { c -> "${c[0]}$s${c[1]}" }
    }
    if (itemMode == PositionalMode.MustAfter) sb.append(' ').append(itemNames)
    sb.append(newline) // detailed desc.
    for (p in allParams) {
      sb.append(indent).append(pre)
        .append(p.joinedName()).append(colon).append(p.help)
      p.defaultValue?.let { sb.append(" (default $it)") }
      sb.append(newline)
    }
    if (itemNames.isNotEmpty() && itemMode == PositionalMode.Disordered) sb.append("options can be mixed with items: ").append(itemNames)
    return sb.append(epilogue).toString()
  }
  private fun Arg<*>.joinedName() = name.split().joinToString(" ${prefixes.last()}")
  override fun toString() = toString(epilogue = "")
  protected open fun printHelp() = println(toString())
}

open class ArgParser3<A,B,C>(p1: Arg<A>, p2: Arg<B>, p3: Arg<C>, vararg flags: Arg<*>): ArgParser4<A,B,C,String>(p1, p2, p3, noArg, flags=*flags)
open class ArgParser2<A,B>(p1: Arg<A>, p2: Arg<B>, vararg flags: Arg<*>): ArgParser4<A,B,String,String>(p1, p2, noArg, noArg, flags=*flags)
open class ArgParser1<A>(p1: Arg<A>, vararg flags: Arg<*>): ArgParser4<A,String,String,String>(p1, noArg, noArg, noArg, flags=*flags)

