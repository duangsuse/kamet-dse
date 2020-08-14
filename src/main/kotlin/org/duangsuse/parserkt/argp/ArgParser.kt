package org.duangsuse.parserkt.argp

import org.duangsuse.parserkt.Tuple4

// Argument patters: flag(no-arg)/action, argument:optional/repeatable

/** Argument with name, help, optional param+defaultValue(repeated:initial)+convert? name/param is separated with " ", better put shorthands second */
data class Arg<R>(
  val name: String, val help: String, val param: String?, val defaultValue: R?,
  val repeatable: Boolean, val convert: Convert<R>) {
  inline val isFlag get() = (param == null)
  val names = name.split()
  val firstName = names.first()
  val secondName = names.run { if (size < 2) first() else this[1] }
  override fun toString() =  param?.let { "[$firstName $it]" + (if (repeatable) "*" else "") } ?: firstName
}

private typealias DynArg = Arg<out Any>
private typealias OM<E> = OneOrMore<E>
private typealias DynOM = OM<Any>
open class ParseResult<A, B, C, D>(
  val tup: Tuple4<OM<A>, OM<B>, OM<C>, OM<D>> = Tuple4(OM(), OM(), OM(), OM()),
  var flags: String = "", val items: MutableList<String> = mutableListOf(),
  val named: NamedMap? = null) {
  val isEarlyStopped get() = ('h' in flags)
}

/** Order constraint for positional(no-prefix) args (comparing to prefix args) */
enum class PositionalMode { MustBefore, MustAfter, Disordered }

private typealias AL = List<DynArg>/*< too long :) */
private val eAL: AL = emptyList()

val noArg: Arg<Unit> = arg<Unit>("\u0000", "") { error("noArg parsed") }
val helpArg = arg("h help", "print this help") { SwitchParser.stop() }
/** Simple parser helper for no-multi (prefixed) param and up to 4 param storage(parse with items) [moreArgs],
 *  subset of [SwitchParser]; add [Arg.convert] to [itemArgs] to store arg into [ParseResult.named].
 *  Arg order: `(param) (flags) (items) (more)` and [autoSplit]/[itemMode] */
open class ArgParser4<A,B,C,D>(
  val p1: Arg<A>, val p2: Arg<B>,
  val p3: Arg<C>, val p4: Arg<D>,
  vararg val flags: Arg<*>, val itemArgs: AL = eAL, val moreArgs: AL? = null,
  val autoSplit: List<String> = emptyList(),
  val itemMode: PositionalMode = PositionalMode.Disordered) {
  protected open val prefixes = listOf("--", "-")
  protected open val deftPrefix = "-"

  private val typedArgs = listOf(p1, p2, p3, p4)
  private val allArgs = (typedArgs + flags + (moreArgs ?: emptyList())).filter { it != noArg }
  init {
    for (p in allArgs) if (p.isFlag && p !in flags) error("flag $p should be putted in ArgParser(flags = ...)")
    for (p in flags) when { !p.isFlag -> error("$p in flags should not have param") ; p.defaultValue != null -> error("use convert to give default for flag $p") }
    for (p in itemArgs) when {
      p.defaultValue != null || p.repeatable -> error("named item $p should not repeatable or have default")
      (' ' in p.name) -> error("item arg $p should not have aliases") //^ note, arg with no param is flag
    }
  }
  private var lastDriver: Driver? = null
  /** Parse input [args], could throw [SwitchParser.ParseError] */
  fun run(args: ArgArray) = Driver(args).also { lastDriver = it }.run()
  /** Reconstruct input back from [result], note that dynamic/converted args are not always handled */
  fun backRun(result: ParseResult<A, B, C, D>, use_shorthand: Boolean = false) = (lastDriver ?: error("run once first")).backRun(result, use_shorthand)

  inner class Driver(args: ArgArray): SwitchParser<ParseResult<A, B, C, D>>(args, prefixes) {
    private val dynamicNameMap = moreArgs?.run { associateByAll { it.names } }
    private val dynamicResult: MutableMap<String, DynOM>? = if (moreArgs != null) mutableMapOf() else null
    private fun ensureDynamicResult() = dynamicResult ?: error("add moreArgs for rescue")
    private fun DynOM.addResult(p: Arg<*>, res: Any, name: String) {
      if (p.repeatable) add(res)
      else value = if (value == null) res else argDuplicateError(name)
    }

    override val res = ParseResult<A, B, C, D>(named = dynamicResult?.let(::NamedMap))
    private inline val tup get() = res.tup
    @Suppress("UNCHECKED_CAST") // dynamic type
    private val ps = listOf(p1 to tup.e1, p2 to tup.e2, p3 to tup.e3, p4 to tup.e4).filter { it.first != noArg } as List<Pair<Arg<*>, DynOM>>
    private val nameMap = ps.associateByAll { it.first.names }.toMutableMap()
    private val flagMap = flags.asIterable().associateByAll { it.names } //v split prefix try order always defined by hand
    private val autoSplits by lazy { autoSplit.mapNotNull { nameMap[it]?.first ?: dynamicNameMap?.get(it) } }

    private var lastPosit = 0 //v all about positional args
    private val itemArgZ = itemArgs.iterator()
    private val caseName = if (itemMode == PositionalMode.MustBefore) "all-before" else "all-after"
    private val isVarItem = itemArgs.lastOrNull()?.name == "..."
    private inline val isItemLess get() = !isVarItem && itemArgZ.hasNext()
    private fun positFail(): Nothing {
      val (capPre, capMsg) = prefixMessageCaps()
      throw ParseError(capPre("reading ${res.items}: ")+capMsg("should $caseName options"))
    }
    private fun itemFail(case_name: String): Nothing = parseError("too $case_name items (all $itemArgs)")
    private fun argDuplicateError(name: String): Nothing = parseError("argument $name repeated")

    override fun onItem(text: String) {
      fun addItem() = res.items.plusAssign(text)
      if (itemMode != PositionalMode.Disordered && pos != lastPosit+1) { // check continuous (ClosedRange)
        if (itemMode == PositionalMode.MustAfter && lastPosit == 0) lastPosit = pos-1 // one chance, late-initial posit. n-1 to keep in ++ later
        else { addItem() ; positFail() } // zero initiated posit, fail.
      }
      val arg = when {
        itemArgZ.hasNext() -> itemArgZ.next()
        isVarItem -> itemArgs.last()
        else -> itemFail("many")
      }
      arg.run { currentArg += " (" + param.showIfPresent {"$it of "} + "$firstName)"
        convert?.invoke(text)?.let { ensureDynamicResult().getOrPutOM(name).addResult(this, it, firstName) }
      } ?: addItem()
      lastPosit++
    }
    private fun checkPosition(): Unit = when {
      (itemMode == PositionalMode.MustBefore && isItemLess) -> itemFail("less heading")
      (itemMode == PositionalMode.MustAfter && res.items.isNotEmpty()) -> positFail()
      else -> {} //^ appeared-first is ok when items only, so no appear after param.
    }
    private fun readFlag(name: String): Boolean = flagMap[name]?.let {
      if (it == helpArg) printHelp().also { res.flags += 'h' } // suppress post chk. in [Driver.run]
      res.flags += it.convert?.invoke(name) ?: it.secondName ; true
    } ?: false
    private fun readDynamicArg(name: String): Boolean {
      dynamicNameMap?.get(name)?.let { p -> dynamicResult!!.getOrPutOM(p.firstName).addResult(p, read(p), name) ; return true }
      return false
    }
    private var autoSplitRes: Any? = null
    private fun read(p: Arg<*>): Any { // support multi-param
      autoSplitRes?.let { autoSplitRes = null ; return it }
      val params = p.param?.split() ?: return p.defaultValue ?: error("dynamic arg $p w/o param and default is invalid")
      val converter = p.convert ?: { it }
      val argFull = currentArg //< full name --arg
      return params.singleOrNull()?.let { arg(it, converter)!! } //v errors looks like missing --duck's name / 's age, or bad argument in
        ?: params.joinToString("\u0000") { currentArg = argFull ; arg(it) }.also { currentArg = "in $argFull" }.let(converter)!!
    }
    override fun onPrefix(name: String) {
      checkPosition()
      if (readFlag(name) || readDynamicArg(name)) return
      fun autoSplit(): Pair<Arg<*>, DynOM>? {
        for (sp in autoSplits) for (prefix in sp.names) extractArg(prefix, name)?.let {
          checkAutoSplitForName(prefix, it) ; autoSplitRes = sp.convert?.invoke(it) ?: it
          val sto = sp.firstName.let { k -> nameMap[k]?.second ?: dynamicResult!!.getOrPutOM(k) }
          return sp to sto
        } //^ support arg name aliases & dynRes
        return null
      } //^ prefix ordered scan & dynRes  //v null elvis chain
      val (p, sto) = nameMap[name] ?: autoSplit() ?: dynamicInterpret(name)
      sto.addResult(p, read(p), name)
    }
    private fun dynamicInterpret(name: String) = rescuePrefix(name).let {
      val key = it.firstName
      val dynSto = ensureDynamicResult().getOrPutOM(key)
      (it to dynSto).also { pair -> nameMap[key] = pair }
    } //^ dynamic interpret for prefix (also cached)

    private fun MutableMap<String, DynOM>.getOrPutOM(key: String) = getOrPut(key) { DynOM() }
    private fun parseError(message: String): Nothing = throw ParseError(prefixMessageCaps().first(message))
    private fun missing(p: Arg<*>) = if (!p.repeatable) parseError("missing argument $deftPrefix${p.firstName} ${p.param}: ${p.help}") else null
    private fun assignDefaultOrFail(p: Arg<*>, sto: DynOM) {
      if (!p.repeatable) { sto.value = sto.value ?: p.defaultValue ?: missing(p) }
      else { if (sto.size == 0) p.defaultValue?.let(sto::add) ?: missing(p) }
    }
    override fun run() = super.run().also {
      if (it.isEarlyStopped) return@also
      ps.forEach { (p, sto) -> assignDefaultOrFail(p, sto) } //<v check & init default
      moreArgs?.forEach { p -> assignDefaultOrFail(p, dynamicResult!!.getOrPutOM(p.firstName)) }
      if (itemMode == PositionalMode.MustAfter) {
        if (isItemLess) itemFail("less")
      } //^[if] less item parsed?
      checkResult(it)
    }
    fun backRun(result: ParseResult<A, B, C, D>, use_shorthand: Boolean): ArgArray {
      val argLine: MutableList<String> = mutableListOf()
      val itemArgNames = itemArgs.mapTo(mutableSetOf()) { it.name }
      fun addItems() {
        result.items.forEach { argLine.add(it) }
        result.named?.map?.forEach { (k, v) -> if (k in itemArgNames) argLine.add("${v.get()}") }
      } // order branched
      fun addArg(p: Arg<*>, res: Any) {
        if (res == p.defaultValue) { return }
        argLine.add("$deftPrefix${if (use_shorthand) p.secondName else p.firstName}")
        if (p.param?.split()?.size?:0 > 1) when (res) { // multi-param
          is Pair<*, *> -> res.run { argLine.add("$first"); argLine.add("$second") }
          is Iterable<*> -> res.mapTo(argLine, Any?::toString)
        } else argLine.add("$res") //< single param
      }
      fun addArg(p: Arg<*>, sto: DynOM) {
        fun add(res: Any) = addArg(p, res)
        sto.value?.let(::add) ?:/*repeated*/ sto.forEach(::add)
      }
      if (itemMode == PositionalMode.MustBefore) addItems()
      result.flags.forEach { argLine.add("$deftPrefix${it}") }
      for ((p, sto) in ps.map { it.first }.zip(result.tup.run { listOf(e1, e2, e3, e4) })) {
        @Suppress("unchecked_cast") addArg(p, sto as DynOM)
      } //^ append for ps
      result.named?.map?.forEach { (k, v) ->
        if (k in itemArgNames) return@forEach
        val p = dynamicNameMap?.get(k) ?: error("nonexistent arg named $k")
        addArg(p, v)
      } //^ and for dynRes
      if (itemMode == PositionalMode.MustAfter || itemMode == PositionalMode.Disordered) addItems()
      return argLine.toTypedArray()
    }

    override fun checkPrefixForName(name: String, prefix: String) = this@ArgParser4.checkPrefixForName(name, prefix)
    override fun prefixMessageCaps(): Pair<TextCaps, TextCaps> = this@ArgParser4.prefixMessageCaps() //<^ delegates for outer class
  }

  /** [caps]: (param to help) ; [row_max]: in summary, max line len ; [groups]: "*" to "Options", "a b c" to "SomeGroup" */
  fun toString(
    caps: Pair<TextCaps, TextCaps> = (TextCaps.None to TextCaps.None), row_max: Int = 70,
    prog: String = "", head: String = "Usage: ", epilogue: String = "",
    indent: String = "  ", space: String = " ", colon: String = ": ", comma: String = ", ", newline: String = "\n",
    groups: Map<String, String>? = null, transform_summary: ((String) -> String)? = {it}): String {
    val sb = StringBuilder(head) ; prog.takeUnlessEmpty()?.let { sb.append(it).append(space) }
    val pre = deftPrefix
    val (capParam, capHelp) = caps
    fun appendItemNames() = itemArgs.joinTo(sb, space) { "<${it.firstName}>" }
    fun appendSummary(sb: StringBuilder) = allArgs.joinToBreakLines(sb, space, row_max, newline+' '.repeats(head.length)) {
      val surround: String = if (it.repeatable) "{}" else if (it.defaultValue != null) "()" else "[]"
      sb.append(surround[0]).append(pre).append(it.joinedName())
      it.param?.split()?.joinTo(sb, comma, prefix = space) { param -> capParam(param) }
      sb.append(surround[1])
      return@joinToBreakLines ""
    } //v begin building.
    if (itemMode == PositionalMode.MustBefore) appendItemNames().append(space)
    if (transform_summary == null) appendSummary(sb) else sb.append(appendSummary(StringBuilder()).toString().let(transform_summary))
    if (itemMode == PositionalMode.MustAfter) { sb.append(space) ; appendItemNames() }
    sb.append(newline) // detailed desc.
    fun appendDesc(p: Arg<*>, groupIndent: String) {
      if (p.help.isEmpty()) { return }
      sb.append(groupIndent).append(indent).append(pre)
        .append(p.joinedName()).append(colon).append(capHelp(p.help))
      p.defaultValue?.let { sb.append(" (default ${it.toString().takeNotEmptyOr("none")})") }
      sb.append(newline)
    }
    if (groups == null) { for (p in allArgs) appendDesc(p, "") }
    else { //v append groups if.
      val grouping = allArgs.groupBy { p -> groups.entries.firstOrNull { p.name in it.key.split() }?.value ?: groups["*"] ?: error("* required for default name") }
      grouping.forEach { (g, ps) -> sb.append(g).append(colon).append(newline) ; ps.forEach { appendDesc(it, indent) } }
    }
    if (itemMode == PositionalMode.Disordered && itemArgs.isNotEmpty()) { sb.append("options can be mixed with items: ".let(capHelp::invoke)); appendItemNames() }
    return sb.append(epilogue).toString()
  }
  private fun Arg<*>.joinedName() = name.split().joinToString(" $deftPrefix")

  override fun toString() = toString(epilogue = "")
  protected open fun printHelp() = println(toString())
  protected open fun prefixMessageCaps() = TextCaps.nonePair
  /** Return-super if [name] still unknown, please note that [Arg.help] / (backRun) is never used, and one of [Arg.param] / [Arg.defaultValue] must be provided */
  protected open fun rescuePrefix(name: String): Arg<*> { throw SwitchParser.ParseError("$name unknown") }
  protected open fun checkAutoSplitForName(name: String, param: String) {}
  /** Call-super first if and args like "--c" are rejected, note no [autoSplit] done on it's [name] */
  protected open fun checkPrefixForName(name: String, prefix: String) {
    val (capPre, _) = prefixMessageCaps()
    if (prefix == "--" && name.length == 1) throw SwitchParser.ParseError(capPre("single-char shorthand should like: -$name"))
  }
  protected open fun checkResult(result: ParseResult<A, B, C, D>) {}
}

open class ArgParser3<A,B,C>(p1: Arg<A>, p2: Arg<B>, p3: Arg<C>, vararg flags: Arg<*>, items: AL=eAL, more: AL=eAL): ArgParser4<A,B,C,Unit>(p1, p2, p3, noArg, *flags, itemArgs=items, moreArgs=more)
open class ArgParser2<A,B>(p1: Arg<A>, p2: Arg<B>, vararg flags: Arg<*>, items: AL=eAL, more: AL=eAL): ArgParser3<A,B,Unit>(p1, p2, noArg, *flags, items=items, more=more)
open class ArgParser1<A>(p1: Arg<A>, vararg flags: Arg<*>, items: AL=eAL, more: AL=eAL): ArgParser2<A,Unit>(p1, noArg, *flags, items=items, more=more)
