package org.duangsuse.parserkt.argp

/** [ArgParser4] class constructed with val by delegate support, using [flags]/[params]/[items] ordered call side-effect(not direct value)s */
class ArgParserBy(private val prog: String) {
  private val flags: MutableList<Arg<*>> = mutableListOf()
  private val params: MutableList<Arg<*>> = mutableListOf() //v inner classes composed with by delegates
  private val items: MutableList<Arg<*>> = mutableListOf()
  private var autoSplit: List<String> = emptyList()
  private var itemMode: PositionalMode = PositionalMode.MustAfter

  private val reversed by lazy { items.firstOrNull()?.name == SPREAD } //< item [...] a b reversed
  private fun mayReversed(ap: DynArgParserUnit) = if (reversed) ap.reversed() else ap
  private val ap by lazy { flags.add(helpArg)
    DynArgParserUnit(noArg, noArg, noArg, noArg, *flags.toTypedArray(),
      itemArgs=items.toList(), moreArgs=this.params, autoSplit=this.autoSplit, itemMode=this.itemMode).let(::mayReversed) }
  private lateinit var res: DynParseResult //^ created later, flags/moreArgs is not decidable in <init>

  class By(private val self: ArgParserBy) {
    private inline val res get() = self.res
    inner class Flag(private val p: Arg<*>) { init { self.flags.add(p) }
      operator fun <M> getValue(_m:M, _p:KP): Boolean = p.secondName in res.flags
      operator fun <M> setValue(_m:M, _p:KP, v: Boolean) { res.flags = res.flags.replace(p.secondName, "") }
    }
    abstract inner class ArgParse<T>(protected val p: Arg<T>, private val isItem: Boolean = false) {
      init { (if (isItem) self.items else self.params).add(p) }
      protected var sto get() = res.named!!.map.getValue(p.firstName)
        set(v) { res.named!!.getMutable()!![p.firstName] = v }
      private fun remove() = (if (isItem) self.items else self.params).remove(p)
      fun <R> wrap(transform: (Arg<T>) -> R): R { remove() ; return transform(p) }
    }
    open inner class ByArg<T>(p: Arg<T>, isItem: Boolean): ArgParse<T>(p, isItem) {
      operator fun <M> getValue(_m:M, _p:KP): T = @Suppress("unchecked_cast") (sto.get() as T)
      operator fun <M> setValue(_m:M, _p:KP, v: T) { sto = OneOrMore<Any>(v) }
    }
    inner class ArgRepeat<T>(p: Arg<T>, isItem: Boolean): ArgParse<T>(p, isItem) {
      operator fun <M> getValue(_m:M, _p:KP): List<T> = @Suppress("unchecked_cast") (sto.toList() as List<T>)
      operator fun <M> setValue(_m:M, _p:KP, v: List<T>) { sto = createOneOrMore(v) }
    }

    inner class Param<T>(p: Arg<T>): ByArg<T>(p, isItem = false) {
      init { require(!p.repeatable) {"use (ap+p).multiply() instead"} }
      fun <R> multiply(transform: (OneOrMore<T>) -> R): ArgConvert<T, R> = wrap { ArgConvert(it.repeatable(), transform) }
      fun multiply(): ArgRepeat<T> = wrap { ArgRepeat(it.repeatable(), isItem = false) } //<^ convert ops
      private fun <T> Arg<T>.repeatable() = Arg(name, help, param, null, true, convert)
    }
    inner class ArgConvert<T, R>(p: Arg<T>, private val transform: (OneOrMore<T>) -> R): ArgParse<T>(p) {
      operator fun <M> getValue(_m:M, _p: KP): R = @Suppress("unchecked_cast") (sto as OneOrMore<T>).let(transform)
    }
  }

  fun flag(name: String, help: String, flag: String? = null) = By(this).Flag(if (flag != null) arg(name, help) { flag } else arg(name, help))
  operator fun <T> plus(p: Arg<T>) = By(this).Param(p)
  fun <T> item(p: Arg<T>) = By(this).ByArg(p, true)
  fun <T> items(p: Arg<T>) = By(this).ArgRepeat(p, true)
  fun autoSplit(rules: String = "") { autoSplit = rules.split() }
  fun itemMode(mode: PositionalMode) { itemMode = mode }

  fun run(args: ArgArray): List<String> {
    res =  ap.run(if (reversed) ap.reversedArgArray(args) else args)
    if (reversed) { res.items.reverse() ; res.named?.getMutable()?.mapKey(SPREAD) { createOneOrMore(it.reversed()) } }
    return res.items
  }
  fun backRun(): ArgArray = @Suppress("unchecked_cast") ap.backRun(res as ParseResult<Unit,Unit,Unit,Unit>)
  fun asParser(): DynArgParserUnit = ap
  override fun toString() = asParser().toString(prog=this.prog)
}
