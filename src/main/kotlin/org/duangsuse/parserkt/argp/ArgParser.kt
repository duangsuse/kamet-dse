package org.duangsuse.parserkt.argp

import org.duangsuse.parserkt.Tuple4

// Argument patters: flag(no-arg)/action, argument:optional/repeatable

data class Arg<R>(
  val name: String, val help: String, val param: String?, var defaultValue: R?,
  val repeatable: Boolean, val converter: ((String) -> R)?) {
  val isFlag get() = (param == null)
}
fun arg(
  name: String, help: String, param: String? = null,
  defaultValue: String? = null, repeatable: Boolean = false, converter: ((String) -> String)? = { it })
  = Arg(name, help, param, defaultValue, repeatable, converter)


class OneOrMore<E>(internal var value: E? = null): Iterable<E> {
  val list: MutableList<E> by lazy(::mutableListOf)
  fun add(item: E) { list.add(item) }
  fun get() = value ?: error("use list[0]")
  val size get() = list.size
  operator fun get(index: Int) = if (value == null) list[index] else error("not list")
  override fun iterator(): Iterator<E> = if (value != null) listOf(value!!).iterator() else list.iterator()
  override fun toString() = "${value ?: list}"
}
private typealias OM<E> = OneOrMore<E>

open class ParseResult<A, B, C, D>(
  val tup: Tuple4<OM<A>, OM<B>, OM<C>, OM<D>> = Tuple4(OM(), OM(), OM(), OM()),
  var flags: String = "", val items: MutableList<String> = mutableListOf())

private val noArg = arg("\u0000", "") { error("noArg parsed") }
/** Simple parser helper for no-multi (prefixed) param and up to 4 param storage, subset of [SwitchParser] */
open class ArgParser4<A,B,C,D>(
  val p1: Arg<A>, val p2: Arg<B>,
  val p3: Arg<C>, val p4: Arg<D>, vararg val flags: Arg<*>) {
  open val prefix = arrayOf("--", "-")
  inner class Driver(args: Array<out String>): SwitchParser<ParseResult<A, B, C, D>>(args, *prefix) {
    override val res = ParseResult<A, B, C, D>()
    private inline val tup get() = res.tup
    @Suppress("UNCHECKED_CAST") // dynamic type
    private val ps = listOf(p1 to tup.e1, p2 to tup.e2, p3 to tup.e3, p4 to tup.e4) as List<Pair<Arg<*>, OM<Any?>>>
    private val nameMap = ps.associateBy { it.first.name }
    private val flagMap = flags.associateBy { it.name }
    init {
      ps.filter { it.first.defaultValue != null }.forEach { it.second.value = it.first.defaultValue }
    }
    override fun onItem(text: String) { res.items.add(text) }
    override fun onPrefix(name: String) {
      flagMap[name]?.let { res.flags += it.converter?.invoke(name) ?: it.name ; return }
      val (p, sto) = nameMap[name] ?: error("$name unknown")
      if (p.isFlag) res.flags += p.name.first()
      p.param!!
      fun read() = arg(p.param, p.converter ?: error("missing converter"))
      if (p.repeatable) sto.add(read())
      else sto.value = if (sto.value == null) read() else throw ParseError("argument $name repeated")
    }
  }
  fun run(args: Array<out String>) = Driver(args).run()
  private val ps = listOf(p1, p2, p3, p4)
  fun toString(head: String = "usage: ", epilogue: String = "", indent: String = "  ", comma: String = ": ", newline: Char = '\n'): String {
    val sb = StringBuilder(head)
    val all = (ps + flags).filter { it != noArg }
    val pre = prefix.last()
    all.joinTo(sb, " ") {
      val surround: String = if (it.repeatable) "{}" else if (it.defaultValue != null) "[]" else "()"
      var s = pre+it.name ; it.param?.run { s += " $this" }
      surround.let { c -> "${c[0]}$s${c[1]}" }
    }
    sb.append(newline) // detailed desc.
    for (p in all) {
      sb.append(indent).append(pre)
        .append(p.name).append(comma).append(p.help).append(newline)
      p.defaultValue?.let { sb.append("(default $it") }
    }
    return sb.append(epilogue).toString()
  }
  override fun toString() = toString(epilogue = "")
}
open class ArgParser3<A,B,C>(p1: Arg<A>, p2: Arg<B>, p3: Arg<C>, vararg flags: Arg<*>): ArgParser4<A,B,C,String>(p1, p2, p3, noArg, *flags)
open class ArgParser2<A,B>(p1: Arg<A>, p2: Arg<B>, vararg flags: Arg<*>): ArgParser4<A,B,String,String>(p1, p2, noArg, noArg, *flags)
open class ArgParser1<A>(p1: Arg<A>, vararg flags: Arg<*>): ArgParser4<A,String,String,String>(p1, noArg, noArg, noArg, *flags)
