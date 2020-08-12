package org.duangsuse.parserkt.argp

import org.duangsuse.parserkt.Tuple4

// Argument patters: flag(no-arg)/action, argument:optional/repeatable

/** Argument with name, help, optional param+defaultValue(repeated:initial)+convert? name is separated with " ", better put shorthands second */
data class Arg<R>(
  val name: String, val help: String, val param: String?, val defaultValue: R?,
  val repeatable: Boolean, val convert: ((String) -> R)?) {
  val isFlag get() = (param == null)
  val firstName get() = name.split().first()
  val secondName get() = name.split().run { if (size < 2) first() else this[1] }
}

private typealias OM<E> = OneOrMore<E>
open class ParseResult<A, B, C, D>(
  val tup: Tuple4<OM<A>, OM<B>, OM<C>, OM<D>> = Tuple4(OM(), OM(), OM(), OM()),
  var flags: String = "", val items: MutableList<String> = mutableListOf(),
  val named: NamedMap? = null)

/** Order constraint for positional(no-prefix) args (comparing to prefix args) */
enum class PositionalMode { MustBefore, MustAfter, Disordered }

val noArg: Arg<String> = arg("\u0000", "") { error("noArg parsed") }
val helpArg = arg("h help", "print this help") { SwitchParser.stop() }
/** Simple parser helper for no-multi (prefixed) param and up to 4 param storage(parse with items) [moreArgs], subset of [SwitchParser] */
open class ArgParser4<A,B,C,D>(
  val p1: Arg<A>, val p2: Arg<B>,
  val p3: Arg<C>, val p4: Arg<D>,
  val itemNames: List<String> = emptyList(), val itemMode: PositionalMode = PositionalMode.Disordered,
  val autoSplit: List<String> = emptyList(), vararg val flags: Arg<*>,
  val moreArgs: List<Arg<out Any>>? = null) {
  open val prefixes = listOf("--", "-")
  inner class Driver(args: ArgArray): SwitchParser<ParseResult<A, B, C, D>>(args, prefixes) {
    private val dynamicNameMap = moreArgs?.run { associateBySplit { it.name.split() } }
    private val dynamicResult: MutableMap<String, Any>? = if (moreArgs != null) mutableMapOf() else null

    override val res = ParseResult<A, B, C, D>(named = dynamicResult?.let(::NamedMap))
    private inline val tup get() = res.tup
    @Suppress("UNCHECKED_CAST") // dynamic type
    private val ps = listOf(p1 to tup.e1, p2 to tup.e2, p3 to tup.e3, p4 to tup.e4).filter { it.first != noArg } as List<Pair<Arg<*>, OM<Any>>>
    private val nameMap = ps.associateBySplit { it.first.name.split() }
    private val flagMap = flags.asIterable().associateBySplit { it.name.split() }
    private val autoSplits by lazy { autoSplit.mapNotNull(nameMap::get) }
    init {
      ps.forEach { val p = it.first ; if (p.isFlag) error("flag ${p.firstName} should be putted in ArgParser(flag = ...)") }
    }

    private var lastPosit = 0 //v all about positional args
    private val caseName = if (itemMode == PositionalMode.MustBefore) "all-before" else "all-after"
    private val isVarItem = (itemNames == listOf("..."))
    private fun positFail(): Nothing = parseError("reading ${res.items}: should $caseName options")
    private fun itemFail(case_name: String): Nothing = parseError("too $case_name items (all $itemNames)")
    private inline val isItemLess get() = !isVarItem && res.items.size < itemNames.size

    override fun onItem(text: String) = res.items.plusAssign(text).also {
      if (itemMode == PositionalMode.Disordered) return@also
      lastPosit++ // check continuous (ClosedRange)
      if (pos != lastPosit) {
        if (itemMode == PositionalMode.MustAfter && lastPosit == 0 +1) lastPosit = pos // one chance, late-initial posit
        else positFail() // zero initiated posit, fail.
      }
      if (!isVarItem && res.items.size > itemNames.size) itemFail("many")
    }
    override fun onPrefix(name: String) {
      when { // check position
        (itemMode == PositionalMode.MustBefore && isItemLess) -> itemFail("less heading")
        (itemMode == PositionalMode.MustAfter && res.items.isNotEmpty()) -> positFail()
        //^ appeared-first is ok when items only, so no appear after param.
      }
      flagMap[name]?.let {
        if (it == helpArg) printHelp().also { res.flags += 'h' } // suppress post chk. in [Driver.run]
        res.flags += it.convert?.invoke(name) ?: it.firstName ; return
      }
      var split: Any? = null // flag, auto-split
      fun autoSplit(): Pair<Arg<*>, OM<Any>>? {
        for (sp in autoSplits) for (bang in sp.first.name.split()) // aliases
          extractArg(bang, name)?.let { checkAutoSplitForName(name, it) ; split = sp.first.convert?.invoke(it) ; return sp }
        return null
      }
      fun read(p: Arg<*>): Any = split ?: arg(p.param!!, p.convert ?: error("missing converter"))!!
      dynamicNameMap?.get(name)?.let { dynamicResult!![it.firstName] = read(it) } // dynamic args
      val (p, sto) = nameMap[name] ?: autoSplit() ?: parseError("$name unknown")
      p.param!!
      if (p.repeatable) sto.add(read(p))
      else sto.`_ value` =
        if (sto.`_ value` == null) read(p)
        else parseError("argument $name repeated")
    }

    private fun parseError(message: String): Nothing = throw ParseError(message)
    override fun run() = super.run().also {
      for ((p, sto) in ps) {
        if (p.defaultValue != null) if (p.repeatable) sto.add(p.defaultValue) else sto.`_ value` = sto.`_ value` ?: p.defaultValue
      } //^ fill default values
      if (itemMode == PositionalMode.MustAfter && 'h' !in it.flags) {
        if (isItemLess) itemFail("less")
      } //^ less item parsed?
      checkResult(it)
    }
    fun backRun(result: ParseResult<A, B, C, D>, use_shorthand: Boolean): ArgArray {
      val argLine: MutableList<String> = mutableListOf()
      fun addItems() = result.items.forEach { argLine.add(it) } // order branched
      if (itemMode == PositionalMode.MustBefore) addItems()
      result.flags.forEach { argLine.add("$deftPrefix${it}") }
      fun addArg(p: Arg<*>, res: Any) { argLine.add("$deftPrefix${if (use_shorthand) p.secondName else p.firstName}"); argLine.add("$res") }
      for ((p, sto) in ps.map { it.first }.zip(result.tup.run { listOf(e1, e2, e3, e4) })) {
        fun add(res: Any) = addArg(p, res)
        sto.`_ value`?.let(::add)
          ?: sto.toList().filterNotNull().forEach(::add)
      }
      result.named?.`_ map`?.forEach { k, v -> addArg(dynamicNameMap?.get(k) ?: error("nonexistent arg named $k"), v) }
      if (itemMode == PositionalMode.MustAfter || itemMode == PositionalMode.Disordered) addItems()
      return argLine.toTypedArray()
    }
  }
  private var lastDriver: Driver? = null
  fun run(args: ArgArray) = Driver(args).also { lastDriver = it }.run()
  fun backRun(result: ParseResult<A, B, C, D>, use_shorthand: Boolean = false) = (lastDriver ?: error("run once first")).backRun(result, use_shorthand)

  private val params = listOf(p1, p2, p3, p4)
  private val allParams = (params + flags + (moreArgs ?: emptyList())).filter { it != noArg }
  /** [caps]: (param to help) ; [row_max]: in summary, max line len ; [groups]: "*" to "Options", "a b c" to "SomeGroup" */
  fun toString(
    caps: Pair<TextCaps, TextCaps> = (TextCaps.None to TextCaps.None), row_max: Int = 70,
    head: String = "Usage: ", epilogue: String = "",
    indent: String = "  ", colon: String = ": ", newline: String = "\n",
    groups: Map<String, String>? = null): String {
    val sb = StringBuilder(head)
    val pre = deftPrefix
    val (capParam, capHelp) = caps
    if (itemMode == PositionalMode.MustBefore) sb.append(itemNames).append(' ')
    var rowSize = 0 // summary line breaks
    allParams.joinTo(sb, " ") {
      val surround: String = if (it.repeatable) "{}" else if (it.defaultValue != null) "()" else "[]"
      var s = pre+it.joinedName() ; it.param?.run { s += " ${capParam(this)}" }
      val pad = if (rowSize >= row_max) "$newline${' '.repeats(head.length)}".also { rowSize = 0 } else ""
      pad + surround.let { c -> "${c[0]}$s${c[1]}" }.also { summary -> rowSize += summary.length }
    }
    if (itemMode == PositionalMode.MustAfter) sb.append(' ').append(itemNames)
    sb.append(newline) // detailed desc.
    fun appendDesc(p: Arg<*>, groupIndent: String) {
      sb.append(groupIndent).append(indent).append(pre)
        .append(p.joinedName()).append(colon).append(capHelp(p.help))
      p.defaultValue?.let { sb.append(" (default $it)") }
      sb.append(newline)
    }
    if (groups == null) { for (p in allParams) appendDesc(p, "") }
    else { //v append groups if.
      val grouping = allParams.groupBy { p -> groups.entries.firstOrNull { p.name in it.key.split() }?.value ?: groups["*"] ?: error("* required for default name") }
      grouping.forEach { (g, ps) -> sb.append(g).append(colon).append(newline) ; ps.forEach { appendDesc(it, indent) } }
    }
    if (itemNames.isNotEmpty() && itemMode == PositionalMode.Disordered) sb.append("options can be mixed with items: ").append(itemNames)
    return sb.append(epilogue).toString()
  }
  private inline val deftPrefix get() = prefixes.last()
  private fun Arg<*>.joinedName() = name.split().joinToString(" $deftPrefix")

  override fun toString() = toString(epilogue = "")
  protected open fun printHelp() = println(toString())
  protected open fun checkAutoSplitForName(name: String, param: String) {}
  protected open fun checkResult(result: ParseResult<A, B, C, D>) {}
}

open class ArgParser3<A,B,C>(p1: Arg<A>, p2: Arg<B>, p3: Arg<C>, vararg flags: Arg<*>): ArgParser4<A,B,C,String>(p1, p2, p3, noArg, flags=*flags)
open class ArgParser2<A,B>(p1: Arg<A>, p2: Arg<B>, vararg flags: Arg<*>): ArgParser4<A,B,String,String>(p1, p2, noArg, noArg, flags=*flags)
open class ArgParser1<A>(p1: Arg<A>, vararg flags: Arg<*>): ArgParser4<A,String,String,String>(p1, noArg, noArg, noArg, flags=*flags)
