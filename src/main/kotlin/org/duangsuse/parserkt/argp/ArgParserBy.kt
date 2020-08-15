package org.duangsuse.parserkt.argp

/** [ArgParser4] class constructed with val by delegate support, using [flags] / [arg] ordered call side-effect(not direct value)s */
class ArgParserBy<ITEM>(private val prog: String, vararg val items: Arg<ITEM>) {
  private val flags: MutableList<Arg<*>> = mutableListOf()
  private val args: MutableList<Arg<*>> = mutableListOf() //v inner classes composed with by delegates
  private var autoSplit: List<String> = emptyList()
  private var itemMode: PositionalMode = PositionalMode.MustAfter

  private val ap by lazy { flags.add(helpArg)
    DynArgParserUnit(noArg, noArg, noArg, noArg, *flags.toTypedArray(), itemArgs=items.toList(), moreArgs=this.args, autoSplit=this.autoSplit, itemMode=this.itemMode) }
  private lateinit var res: DynParseResult //^ created later, flags/moreArgs is not decidable in <init>

  inner class ByFlag(private val p: Arg<*>) { init { flags.add(p) }
    operator fun <M> getValue(_m:M, _p:KP): Boolean = p.secondName in res.flags
    operator fun <M> setValue(_m:M, _p:KP, v: Boolean) { res.flags = res.flags.replace(p.secondName, "") }
  }
  abstract inner class ByArgParse<T>(protected val p: Arg<T>) {
    init { args.add(p) }
    protected var sto get() = res.named!!.map.getValue(p.firstName)
      set(v) { res.named!!.getMutable()!![p.firstName] = v }
    protected fun remove() = args.remove(p)
    fun <R> wrap(transform: (Arg<T>) -> R): R { remove() ; return transform(p) }
  }
  inner class ByArg<T>(p: Arg<T>): ByArgParse<T>(p) {
    init { require(!p.repeatable) {"use (ap+p).multiply() instead"} }
    operator fun <M> getValue(_m:M, _p:KP): T = @Suppress("unchecked_cast") (sto.get() as T)
    operator fun <M> setValue(_m:M, _p:KP, v: T) { sto = OneOrMore<Any>(v) }
    fun <R> multiply(transform: (OneOrMore<T>) -> R): ByArgConvert<T, R> = //<v convert ops
      wrap { ByArgConvert(it.repeatable(), transform) }
    fun multiply(): ByArgRepeat<T> = wrap { ByArgRepeat(it.repeatable()) }
    private fun <T> Arg<T>.repeatable() = Arg(name, help, param, null, true, convert)
  }
  inner class ByArgConvert<T, R>(p: Arg<T>, private val transform: (OneOrMore<T>) -> R): ByArgParse<T>(p) {
    operator fun <M> getValue(_m:M, _p: KP): R = @Suppress("unchecked_cast") (sto as OneOrMore<T>).let(transform)
  }
  inner class ByArgRepeat<T>(p: Arg<T>): ByArgParse<T>(p) {
    operator fun <M> getValue(_m:M, _p:KP): List<T> = @Suppress("unchecked_cast") (sto.toList() as List<T>)
    operator fun <M> setValue(_m:M, _p:KP, v: List<T>) { sto = OneOrMore<Any>().apply { v.forEach { add(it as Any) } } }
  }
  inner class ByNamed<T>(name: String): ByArgParse<String>(arg(name, "")) {
    init { remove() } //< so arg() above just placeholder
    operator fun <M> getValue(_m:M, _p:KP): T = @Suppress("unchecked_cast") (sto.get() as T)
    operator fun <M> setValue(_m:M, _p:KP, v: T) { sto = OneOrMore<Any>(v) }
  }

  fun flag(name: String, help: String, flag: String? = null) = ByFlag(if (flag != null) arg(name, help) {flag} else arg(name, help))
  fun <T> named(name: String) = ByNamed<T>(name)
  operator fun <T> plus(p: Arg<T>) = ByArg(p)
  fun autoSplit(rules: String = "") { autoSplit = rules.split() }
  fun itemMode(mode: PositionalMode) { itemMode = mode }

  fun run(args: ArgArray): List<String> { res = ap.run(args) ; return res.items }
  fun backRun(): ArgArray = @Suppress("unchecked_cast") ap.backRun(res as ParseResult<Unit,Unit,Unit,Unit>)
  fun asParser(): DynArgParserUnit = ap
  override fun toString() = asParser().toString(prog=this.prog)
}
