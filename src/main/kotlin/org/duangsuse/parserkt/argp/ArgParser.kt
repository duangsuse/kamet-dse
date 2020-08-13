package org.duangsuse.parserkt.argp

import org.duangsuse.parserkt.Tuple4

// Argument patters: flag(no-arg)/action, argument:optional/repeatable

/** Argument with name, help, optional param+defaultValue(repeated:initial)+convert? name/param is separated with " ", better put shorthands second */
data class Arg<R>(
  val name: String, val help: String, val param: String?, val defaultValue: R?,
  val repeatable: Boolean, val convert: Convert<R>) {
  val isFlag get() = (param == null)
  val firstName get() = name.split().first()
  val secondName get() = name.split().run { if (size < 2) first() else this[1] }
}

private typealias OM<E> = OneOrMore<E>
private typealias DynOM = OM<Any>
open class ParseResult<A, B, C, D>(
  val tup: Tuple4<OM<A>, OM<B>, OM<C>, OM<D>> = Tuple4(OM(), OM(), OM(), OM()),
  var flags: String = "", val items: MutableList<String> = mutableListOf(),
  val named: NamedMap? = null)

/** Order constraint for positional(no-prefix) args (comparing to prefix args) */
enum class PositionalMode { MustBefore, MustAfter, Disordered }

val noArg: Arg<Unit> = arg<Unit>("\u0000", "") { error("noArg parsed") }
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
    private val dynamicResult: MutableMap<String, DynOM>? = if (moreArgs != null) mutableMapOf() else null

    override val res = ParseResult<A, B, C, D>(named = dynamicResult?.let(::NamedMap))
    private inline val tup get() = res.tup
    @Suppress("UNCHECKED_CAST") // dynamic type
    private val ps = listOf(p1 to tup.e1, p2 to tup.e2, p3 to tup.e3, p4 to tup.e4).filter { it.first != noArg } as List<Pair<Arg<*>, DynOM>>
    private val nameMap = ps.associateBySplit { it.first.name.split() }.toMutableMap()
    private val flagMap = flags.asIterable().associateBySplit { it.name.split() } //v split prefix try order always defined by hand
    private val autoSplits by lazy { autoSplit.mapNotNull { nameMap[it]?.first ?: dynamicNameMap?.get(it) } }
    init {
      ps.forEach { val p = it.first ; if (p.isFlag) error("flag ${p.firstName} should be putted in ArgParser(flag = ...)") }
    }

    private var lastPosit = 0 //v all about positional args
    private val caseName = if (itemMode == PositionalMode.MustBefore) "all-before" else "all-after"
    private val isVarItem = (itemNames == listOf("..."))
    private fun positFail(): Nothing {
      val (capPre, capMsg) = prefixMessageCaps()
      parseError(capPre("reading ${res.items}: ")+capMsg("should $caseName options"))
    }
    private fun itemFail(case_name: String): Nothing = parseError(prefixMessageCaps().first("too $case_name items (all $itemNames)"))
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
        res.flags += it.convert?.invoke(name) ?: it.secondName ; return
      } //^flag  //v read, auto-split
      var split: Any? = null
      fun read(p: Arg<*>): Any { // support multi-param
        split?.let { return it }
        val params = p.param?.split() ?: return p.defaultValue ?: error("dynamic arg w/o param or default is invalid")
        val converter = p.convert ?: { it }
        val argFull = currentArg //< full name --arg
        return params.singleOrNull()?.let { arg(it, converter)!! } //v errors looks like missing --duck's name / 's age, or bad argument in
          ?: params.joinToString("\u0000") { currentArg = argFull ; arg(it) }.also { currentArg = "in $argFull" }.let(converter)!!
      }
      //v dynamic args and split helperFn
      dynamicNameMap?.get(name)?.let { dynamicResult!![it.firstName] = OneOrMore(read(it)) ; return }
      fun autoSplit(): Pair<Arg<*>, DynOM>? {
        for (sp in autoSplits) for (bang in sp.name.split()) // aliases
          extractArg(bang, name)?.let {
            checkAutoSplitForName(bang, it) ; split = sp.convert?.invoke(it) ?: it
            val sto = sp.firstName.let { k -> nameMap[k]?.second ?: dynamicResult?.getOrPutOM(k) }
            return sp to sto!!
          }
        return null
      } //v null elvis chain
      val (p, sto) = nameMap[name] ?: autoSplit() ?: rescuePrefix(name).let {
        val key = it.firstName
        val dynSto = (dynamicResult ?: error("add moreArgs for rescue")).getOrPutOM(key)
        (it to dynSto).also { pair -> nameMap[key] = pair }
      } //^ dynamic interpret for prefix (also cached)
      if (p.repeatable) sto.add(read(p))
      else sto.value =
        if (sto.value == null) read(p)
        else parseError(prefixMessageCaps().first("argument $name repeated"))
    }

    private fun MutableMap<String, DynOM>.getOrPutOM(key: String) = getOrPut(key) { DynOM() }
    private fun parseError(message: String): Nothing = throw ParseError(message)
    override fun run() = super.run().also {
      ps.forEach { (p, sto) ->
        if (p.defaultValue != null) if (p.repeatable) sto.add(p.defaultValue) else sto.value = sto.value ?: p.defaultValue
      }
      moreArgs?.forEach { p -> val k = p.firstName ; if (p.defaultValue != null) dynamicResult!![k] = (dynamicResult[k] ?: OM(p.defaultValue)) }
      //^ fill default values
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
      fun addArg(p: Arg<*>, res: Any) {
        if (res == p.defaultValue) { return }
        argLine.add("$deftPrefix${if (use_shorthand) p.secondName else p.firstName}")
        if (p.param?.split()?.size?:0 > 1)
          when (res) { // multi-param
            is Pair<*, *> -> res.run { argLine.add("$first"); argLine.add("$second") }
            is Iterable<*> -> res.mapTo(argLine, Any?::toString)
          }
        else argLine.add("$res")
      }
      fun addArg(p: Arg<*>, sto: DynOM) {
        fun add(res: Any) = addArg(p, res)
        sto.value?.let(::add)
          ?: sto.forEach(::add)
      }
      for ((p, sto) in ps.map { it.first }.zip(result.tup.run { listOf(e1, e2, e3, e4) })) {
        @Suppress("unchecked_cast") addArg(p, sto as DynOM)
      } //^ append for ps
      result.named?.map?.forEach { k, v ->
        val p = dynamicNameMap?.get(k) ?: error("nonexistent arg named $k")
        addArg(p, v)
      }
      if (itemMode == PositionalMode.MustAfter || itemMode == PositionalMode.Disordered) addItems()
      return argLine.toTypedArray()
    }

    override fun checkPrefixForName(name: String, prefix: String) = this@ArgParser4.checkPrefixForName(name, prefix)
    override fun prefixMessageCaps(): Pair<TextCaps, TextCaps> = this@ArgParser4.prefixMessageCaps() //<^ delegates for outer class
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
    indent: String = "  ", space: String = " ", colon: String = ": ", comma: String = ", ", newline: String = "\n",
    groups: Map<String, String>? = null, transform_summary: ((String) -> String)? = {it}): String {
    val sb = StringBuilder(head)
    val pre = deftPrefix
    val (capParam, capHelp) = caps
    fun appendItemNames() = itemNames.joinTo(sb, space) { "<$it>" }
    if (itemMode == PositionalMode.MustBefore) appendItemNames().append(space)
    fun appendSummary(sb: StringBuilder) = allParams.joinToBreakLines(sb, space, row_max, newline+' '.repeats(head.length)) {
      val surround: String = if (it.repeatable) "{}" else if (it.defaultValue != null) "()" else "[]"
      sb.append(surround[0]).append(pre).append(it.joinedName())
      it.param?.split()?.joinTo(sb, comma, prefix = space) { param -> capParam(param) }
      sb.append(surround[1])
      return@joinToBreakLines ""
    }
    if (transform_summary == null) appendSummary(sb) else sb.append(appendSummary(StringBuilder()).toString().let(transform_summary))
    if (itemMode == PositionalMode.MustAfter) { sb.append(space) ; appendItemNames() }
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
    if (itemNames.isNotEmpty() && itemMode == PositionalMode.Disordered) sb.append("options can be mixed with items: ".let(capHelp::invoke)).append(itemNames)
    return sb.append(epilogue).toString()
  }
  private inline val deftPrefix get() = prefixes.last()
  private fun Arg<*>.joinedName() = name.split().joinToString(" $deftPrefix")

  override fun toString() = toString(epilogue = "")
  protected open fun printHelp() = println(toString())
  protected open fun prefixMessageCaps() = TextCaps.nonePair
  /** Return-super if [name] still unknown, please note that [Arg.help] / (backRun) is never used and one of [Arg.param], [Arg.defaultValue] must be provided */
  protected open fun rescuePrefix(name: String): Arg<*> { throw SwitchParser.ParseError("$name unknown") }
  protected open fun checkAutoSplitForName(name: String, param: String) {}
  /** Call-super first if and args like "--c" are rejected */
  protected open fun checkPrefixForName(name: String, prefix: String) {
    val (capPre, _) = prefixMessageCaps()
    if (prefix == "--" && name.length == 1) throw SwitchParser.ParseError(capPre("single-char shorthand should like: -$name"))
  }
  protected open fun checkResult(result: ParseResult<A, B, C, D>) {}
}

open class ArgParser3<A,B,C>(p1: Arg<A>, p2: Arg<B>, p3: Arg<C>, vararg flags: Arg<*>): ArgParser4<A,B,C,Unit>(p1, p2, p3, noArg, flags=*flags)
open class ArgParser2<A,B>(p1: Arg<A>, p2: Arg<B>, vararg flags: Arg<*>): ArgParser3<A,B,Unit>(p1, p2, noArg, flags=*flags)
open class ArgParser1<A>(p1: Arg<A>, vararg flags: Arg<*>): ArgParser2<A,Unit>(p1, noArg, flags=*flags)
