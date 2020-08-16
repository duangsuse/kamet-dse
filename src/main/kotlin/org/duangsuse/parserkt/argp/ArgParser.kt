package org.duangsuse.parserkt.argp

// Argument patters: flag(no-arg)/param(with action?)/item(converted=named?), argument:optional/repeatable
data class Tuple4<A, B, C, D>(var e1: A, var e2: B, var e3: C, var e4: D) //< imported from funcly-ParserKt model

/** Argument with name, help, optional param+defaultValue(repeated:initial)+convert? name/param is separated with " ", better put shorthands second */
data class Arg<out R>(
  val name: String, val help: String, val param: String?, val defaultValue: R?,
  val repeatable: Boolean, val convert: Convert<R>) {
  inline val isFlag get() = (param == null)
  val names = name.split()
  val firstName = names.first()
  val secondName = names.run { if (size < 2) first() else this[1] }
  override fun toString() =  param?.let { "[$firstName $it]" + (if (repeatable) "*" else "") } ?: firstName
}

private typealias DynArg = Arg<Any?>
internal typealias DynArgParser = ArgParser4<*,*,*,*> //< addSub type checking is impossible, only dynamic is used
internal typealias DynArgParserUnit = ArgParser4<Unit,Unit,Unit,Unit>
internal typealias DynParseResult = ParseResult<*,*,*,*>
internal typealias DynParseResultUnit = ParseResult<Unit,Unit,Unit,Unit>
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

fun NamedMap.getMutable() = map as? MutableMap //<^ dynamicRes & subParser MutableMap bind
fun <T> Iterable<T>.filterNotNoArg() = filter { it != noArg }

abstract class ArgParserHandlers {
  protected open fun printHelp() = println(toString())
  protected open fun prefixMessageCaps() = TextCaps.nonePair
  /** Return-super if [name] still unknown, please note that [Arg.help] / (backRun) is never used, and one of [Arg.param] / [Arg.defaultValue] must be provided */
  protected open fun rescuePrefix(name: String): Arg<*> { throw SwitchParser.ParseError("$name unknown") }
  protected open fun checkAutoSplitForName(name: String, param: String) {}
  /** Call-super first if and args like "--c" are rejected, note no [ArgParser4.autoSplit] is done on it's [name] */
  protected open fun checkPrefixForName(name: String, prefix: String) {
    val (capPre, _) = prefixMessageCaps()
    if (prefix == "--" && name.length == 1) throw SwitchParser.ParseError(capPre("single-char shorthand should like: -$name"))
  }
  /** Un-parse method used in [ArgParser4.backRun]. call super ?: (your case) */
  protected open fun showParam(param: Any): String? = param.takeIf { it is Enum<*> }?.let { "$it".toLowerCase() }
}

private typealias AL = List<DynArg>/*< too long :) */
private val eAL: AL = emptyList()
const val SPREAD = "..." //< spread itemArg name

val noArg: Arg<Unit> = arg<Unit>("\u0000\u0000", "") { error("noArg parsed") }
val helpArg = arg("h help", "print this help") { SwitchParser.stop() }
/** Simple parser helper for no-multi (prefixed) param and up to 4 param storage(parse with items) [moreArgs],
 *  subset of [SwitchParser]; add [Arg.convert] to [itemArgs] to store arg into [ParseResult.named].
 *  Arg order: `(param) (flags) (items) (more)` and [autoSplit]/[itemMode]
 *
 *  [addSub] is used to add [ArgParserAsSubDelegate] sub-commands, note that all arguments before sub's name are __just ignored__ by parent */
open class ArgParser4<A,B,C,D>(
  val p1: Arg<A>, val p2: Arg<B>,
  val p3: Arg<C>, val p4: Arg<D>,
  vararg val flags: Arg<*>, val itemArgs: AL = eAL, val moreArgs: AL? = null,
  val autoSplit: List<String> = emptyList(),
  val itemMode: PositionalMode = PositionalMode.Disordered): ArgParserHandlers() {
  protected open val prefixes = listOf("--", "-")
  protected open val deftPrefix = "-"

  open val commandAliases: MutableMap<String, List<String>> = mutableMapOf()
  protected open val subCommands: MutableMap<String, DynArgParser> = mutableMapOf()
  protected open val subCommandHelps: MutableMap<String, String> = mutableMapOf()

  private val typedArgs = listOf(p1, p2, p3, p4)
  private val allArgs = (typedArgs + flags + (moreArgs ?: emptyList())).filterNotNoArg()
  init {
    for (p in allArgs) if (p.isFlag && p !in flags) error("flag $p should be putted in ArgParser(flags = ...)")
    for (p in flags) when { !p.isFlag -> error("$p in flags should not have param") ; p.defaultValue != null -> error("use convert to give default for flag $p") }
    for (p in itemArgs) when {
      p.defaultValue != null || p.repeatable -> error("named item $p should not repeatable or have default")
      (' ' in p.name) -> error("item arg $p should not have aliases") //^ note, arg with no param is flag
    }
  }

  @Suppress("UNCHECKED_CAST") //< this is only used in sub-commands run (onItem)
  private fun runTo(res: DynParseResult?, args: ArgArray) = Driver(args, res as ParseResult<A,B,C,D>?).run()
  /** Parse input [args], could throw [SwitchParser.ParseError]. see also [backRun], [addSub], [toString] */
  fun run(args: ArgArray) = runTo(null/*tree root parser*/, args)

  protected open fun rescueMissing(p: Arg<*>): Any? = null
  /** Check [result]'s flags, params or named map, see also: [NamedMap], [OneOrMore] */
  protected open fun checkResult(result: ParseResult<A,B,C,D>) {}
  /** Pre-check and fill [result]'s storage, if [SwitchParser.ParseStop] is thrown then post-processes are ignored */
  protected open fun preCheckResult(result: ParseResult<A,B,C,D>) {}

  private val dynamicNameMap = moreArgs?.run { associateByAll { it.names } }
  private val flagMap = flags.asIterable().associateByAll { it.names }
  inner class Driver(args: ArgArray, parent_res: ParseResult<A,B,C,D>?): SwitchParser<ParseResult<A,B,C,D>>(args, prefixes) {
    private val dynamicResult: MutableMap<String, DynOM>? = parent_res?.named?.getMutable() ?: if (moreArgs != null) mutableMapOf() else null
    override val res = parent_res ?: ParseResult(named = dynamicResult?.let(::NamedMap)) //^ cast res.named.map as MutableMap.

    private fun MutableMap<String, DynOM>.getOrPutOM(key: String) = getOrPut(key) { DynOM() }
    private fun ensureDynamicResult() = dynamicResult ?: error("add moreArgs for rescue")
    private fun DynOM.addResult(p: Arg<*>, res: Any, name: String) {
      if (p.repeatable || name == SPREAD) add(res)
      else value = if (value == null) res else argDuplicateFail(name)
    }

    @Suppress("UNCHECKED_CAST") // dynamic runtime index-T paired type, still safe
    private val argToOMs = res.tup.run { listOf(p1 to e1, p2 to e2, p3 to e3, p4 to e4) }.filter { it.first != noArg } as List<Pair<Arg<*>, DynOM>>
    private val nameMap = argToOMs.associateByAll { it.first.names }.toMutableMap() //<^ typed (arg,name+storage) s
    private val autoSplitArgs by lazy { autoSplit.mapNotNull { nameMap[it]?.first ?: dynamicNameMap?.get(it) } } //< split prefix try order always defined by hand

    private var lastPosit = 0 //v all about positional args
    private val itemArgZ = itemArgs.iterator()
    private val caseName = if (itemMode == PositionalMode.MustBefore) "all-before" else "all-after"
    private val isVarItem = itemArgs.lastOrNull()?.name == SPREAD //< reversed order spread arg is not directly supported
    private inline val isItemLess get() = !isVarItem && itemArgZ.hasNext()
    private fun positFail(): Nothing {
      val (capPre, capMsg) = prefixMessageCaps()
      throw ParseError(capPre("reading ${res.items}: ")+capMsg("should $caseName options"))
    }
    private fun itemFail(case_name: String): Nothing = parseError("too $case_name items (all $itemArgs)")
    private fun argDuplicateFail(name: String): Nothing = parseError("argument $name repeated")

    override fun onItem(text: String) {
      commandAliases[text]?.forEach(::onArg)?.also { return } //<v recursion unchecked.
      subCommands[text]?.runTo(res, args.sliceArray(pos..args.lastIndex))?.also { stop() }
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
    //vv all about onPrefix handling. var autoSplitRes.
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
        for (sp in autoSplitArgs) for (prefix in sp.names) extractArg(prefix, name)?.let {
          checkAutoSplitForName(prefix, it) ; autoSplitRes = sp.convert?.invoke(it) ?: it
          val sto = sp.firstName.let { k -> nameMap[k]?.second ?: dynamicResult!!/*autoSplit:enabled,!null*/.getOrPutOM(k) }
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

    private fun parseError(message: String): Nothing = throw ParseError(prefixMessageCaps().first(message))
    private fun missing(p: Arg<*>) = if (!p.repeatable) parseError("missing argument $deftPrefix${p.firstName} ${p.param}: ${p.help}") else null
    private fun assignDefaultOrFail(p: Arg<*>, sto: DynOM) {
      if (!p.repeatable) { sto.value = sto.value ?: p.defaultValue ?: rescueMissing(p) ?: missing(p) }
      else { if (sto.size == 0) p.defaultValue?.let(sto::add) ?: rescueMissing(p) ?: missing(p) }
    }
    override fun run() = super.run().also { //<v post check & init default
      if (it.isEarlyStopped) return@also
      try { preCheckResult(it) } catch (_: ParseStop) { return@also } //< for more filling mechanism
      argToOMs.forEach { (p, sto) -> assignDefaultOrFail(p, sto) }
      moreArgs?.forEach { p -> assignDefaultOrFail(p, dynamicResult!!.getOrPutOM(p.firstName)) }
      if (itemMode == PositionalMode.MustAfter) {
        if (isItemLess) itemFail("less")
      } //^[if] less item parsed?
      checkResult(it)
    }

    override fun checkPrefixForName(name: String, prefix: String) = this@ArgParser4.checkPrefixForName(name, prefix)
    override fun prefixMessageCaps(): Pair<TextCaps, TextCaps> = this@ArgParser4.prefixMessageCaps() //<^ delegates for outer class
  }

  /** Reconstruct input back from [result], note that dynamic/converted args are not always handled. sub-commands are not supported */
  fun backRun(result: ParseResult<A,B,C,D>, use_shorthand: Boolean = false, transform_items: (List<String>) -> List<String> = {it}): ArgArray {
    val argLine: MutableList<String> = mutableListOf()
    val itemArgNames = itemArgs.mapTo(mutableSetOf()) { it.name }
    fun addItems() {
      result.items.let(transform_items).forEach { argLine.add(it) }
      result.named?.map?.forEach { (k, v) -> if (k in itemArgNames) argLine.add("${v.get()}") }
    } // order branched
    fun addArg(p: Arg<*>, res: Any) {
      if (res == p.defaultValue) { return }
      argLine.add("$deftPrefix${if (use_shorthand) p.secondName else p.firstName}")
      fun add(param: Any?) { argLine.add(param?.let(::showParam) ?: "$param"/*null*/) }
      if (p.param?.split()?.size?:0 > 1) when (res) { // multi-param
        is Pair<*, *> -> res.run { add(first) ; add(second) }
        is Triple<*, *, *> -> res.run { add(first) ; add(second) ; add(third) }
        is Iterable<*> -> res.forEach(::add)
      } else add(res) //< single param
    }
    fun addArg(p: Arg<*>, sto: DynOM) {
      fun add(res: Any) = addArg(p, res)
      sto.value?.let(::add) ?:/*repeated*/ sto.forEach(::add)
    }
    if (itemMode == PositionalMode.MustBefore) addItems()
    result.flags.forEach { argLine.add("$deftPrefix${it}") }
    for ((p, sto) in typedArgs.zip(result.tup.run { listOf(e1, e2, e3, e4) })) {
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

  /** [caps]: (param to help) ; [row_max]: in summary, max line len ; [groups]: "*" to "Options", "(subcmd)" to "Subcmd", "a b c" to "SomeGroup" */
  fun toString(
    caps: Pair<TextCaps, TextCaps> = (TextCaps.None to TextCaps.None), row_max: Int = 70,
    prog: String = "", head: String = "Usage: ", epilogue: String = "",
    indent: String = "  ", space: String = " ", colon: String = ": ", comma: String = ", ", newline: String = "\n",
    groups: Map<String, String>? = null, transform_summary: ((String) -> String)? = {it}, recursive: Boolean = true): String {
    val sb = StringBuilder(head) ; prog.takeUnlessEmpty()?.let { sb.append(it).append(space) }
    val (capParam, capHelp) = caps
    fun appendItemNames() = itemArgs.joinTo(sb, space) { "<${it.firstName}>" }
    fun appendSummary(sb: StringBuilder) = allArgs.joinToBreakLines(sb, space, row_max, newline+' '.repeats(head.length)) {
      val surround: String = if (it.repeatable) "{}" else if (it.defaultValue != null) "()" else "[]"
      sb.append(surround[0]).append(it.prefixedNames())
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
      sb.append(groupIndent).append(indent)
        .append(p.prefixedNames()).append(colon).append(capHelp(p.help))
      p.defaultValue?.let { sb.append(" (default ${it.toString().takeNotEmptyOr("none")})") }
      sb.append(newline)
    }
    fun appendSubcmd() {
      if (subCommandHelps.isEmpty()) return
      val group = groups?.get("(subcmd)")?.also { sb.append(it).append(colon).append(newline) }
      val groupIndent = if (group != null) indent else ""
      val newIndent = groupIndent+indent
      for ((name, help) in subCommandHelps) {
        sb.append(groupIndent).append(indent).append(name).append(colon).append(help)
        if (recursive) {
          sb.append(newline).append(newIndent)
          .append(subCommands[name]!!.toString(caps, row_max, "", "", "", indent, space, colon, comma, newline+newIndent, groups, transform_summary, recursive))
          sb.deleteLast(newIndent.length+1/*newline*/)
        }
      } //^ this dizzy should be rewrote, truly... ~(w_w)_
    }
    appendSubcmd()
    if (groups == null) { for (p in allArgs) appendDesc(p, "") }
    else { //v append groups if.
      val grouping = allArgs.groupBy { p -> groups.entries.firstOrNull { p.name in it.key.split() }?.value ?: groups["*"] ?: error("* required for default name") }
      grouping.forEach { (g, ps) -> sb.append(g).append(colon).append(newline) ; ps.forEach { appendDesc(it, indent) } }
    }
    if (itemMode == PositionalMode.Disordered && itemArgs.isNotEmpty()) { sb.append("options can be mixed with items: ".let(capHelp::invoke)); appendItemNames() }
    return sb.append(epilogue).toString()
  }
  private fun Arg<*>.prefixedNames() = names.joinToString(prefix = deftPrefix, separator = " $deftPrefix")
  override fun toString() = toString(epilogue = "")

  /** Register another [ArgParser4] as named sub, map their typed tuple args to [ParseResult.named]. see also: [commandAliases], [allArgs] */
  fun addSub(name: String, help: String, argp: DynArgParser) {
    val newP = ArgParserAsSubDelegate(argp) //^ only all-4 typed arg is noArg can be used later in runTo(parent_res, args)
    subCommandHelps[name] = help ; subCommands[name] = newP //^ inherit from argp. split secondary constructor is too complex so just reassign
  }
  fun getSub(path: Iterable<String>) = path.fold(initial = this as DynArgParser) { point, k ->
    point.subCommands[k] ?: throw NoSuchElementException("missing key $k @${point.subCommands}") }
  fun getSubHelp(name: String) = subCommandHelps.getValue(name)

  /** Delegate for [ArgParser4]'s [addSub]. [checkResult] is re-implemented to map [ParseResult.named] back. */
  open class ArgParserAsSubDelegate(private val argp: DynArgParser): DynArgParserUnit(noArg, noArg, noArg, noArg,
    *argp.flags, itemArgs=argp.itemArgs, moreArgs=argp.typedArgs.filterNotNoArg() + (argp.moreArgs?:emptyList()), autoSplit=argp.autoSplit, itemMode=argp.itemMode) {
    override val commandAliases = argp.commandAliases
    override val subCommands = argp.subCommands
    override val subCommandHelps = argp.subCommandHelps
    override val deftPrefix = argp.deftPrefix
    override val prefixes = argp.prefixes
    override fun rescuePrefix(name: String): Arg<*> = argp.rescuePrefix(name)
    override fun rescueMissing(p: Arg<*>): Any? = argp.rescueMissing(p)
    override fun prefixMessageCaps() = argp.prefixMessageCaps()
    override fun printHelp() = argp.printHelp()
    override fun checkAutoSplitForName(name: String, param: String) = argp.checkAutoSplitForName(name, param)
    override fun checkPrefixForName(name: String, prefix: String) = argp.checkPrefixForName(name, prefix)
    override fun checkResult(result: DynParseResultUnit) = runResultCheck(result) { checkResult(it) }
    override fun preCheckResult(result: DynParseResultUnit) = runResultCheck(result) {
      try { preCheckResult(it) } finally { result.flags = it.flags }
    }
    private fun runResultCheck(result: DynParseResultUnit, op: ArgParser4<Any,Any,Any,Any>.(ParseResult<Any,Any,Any,Any>) -> Unit) {
      val dynArgp = @Suppress("unchecked_cast") (argp as ArgParser4<Any,Any,Any,Any>)
      val (a, b, c, d) = argp.typedArgs.map { if (it == noArg) DynOM(Unit) else result.named!!/*more=typedArgs,!null*/.getMutable()!![it.firstName]!!/*run->assignDefault,!null*/ }
      val newRes = result.run { ParseResult(Tuple4(a,b,c,d), flags, items, named) }
      dynArgp.op(newRes)
    }
    override fun showParam(param: Any) = argp.showParam(param)
  } //^ Kotlin by auto-delegate could not be used, since public interface is required.
}

open class ArgParser3<A,B,C>(p1: Arg<A>, p2: Arg<B>, p3: Arg<C>, vararg flags: Arg<*>, items: AL=eAL, more: AL=eAL): ArgParser4<A,B,C,Unit>(p1, p2, p3, noArg, *flags, itemArgs=items, moreArgs=more)
open class ArgParser2<A,B>(p1: Arg<A>, p2: Arg<B>, vararg flags: Arg<*>, items: AL=eAL, more: AL=eAL): ArgParser3<A,B,Unit>(p1, p2, noArg, *flags, items=items, more=more)
open class ArgParser1<A>(p1: Arg<A>, vararg flags: Arg<*>, items: AL=eAL, more: AL=eAL): ArgParser2<A,Unit>(p1, noArg, *flags, items=items, more=more)
