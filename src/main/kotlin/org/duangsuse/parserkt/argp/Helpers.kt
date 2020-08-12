package org.duangsuse.parserkt.argp

typealias ArgArray = Array<out String>

inline fun <reified R> arg(
  name: String, help: String, param: String? = null,
  default_value: R? = null, repeatable: Boolean = false, noinline convert: ((String) -> R)?): Arg<R>
  = Arg(name, help, if (param == "") name.split(' ').first() else param, default_value, repeatable, convert)
fun arg(name: String, help: String, param: String? = null,
        default_value: String? = null, repeatable: Boolean = false, convert: ((String) -> String)? = { it })
  = arg<String>(name, help, param, default_value, repeatable, convert)

fun <R> Arg<String>.options(default_value: R? = null, vararg pairs: Pair<String, R>): Arg<R>
  = Arg(name, "$help in ${pairs.joinToString(", ") {it.first}}", param, default_value, repeatable, mapOf(*pairs)::getValue)
fun Arg<String>.checkOptions(vararg strings: String) = options(defaultValue, *strings.map { it to it }.toTypedArray())

fun defineFlags(vararg pairs: Pair<String, String>): Array<Arg<*>>
  = pairs.asIterable().map { arg("${it.first} ${it.second}", it.first.split("-").joinToString(" ")) }.toTypedArray()

class OneOrMore<E>(internal var `_ value`: E? = null): Iterable<E> {
  private val list: MutableList<E> by lazy(::mutableListOf)
  val size get() = if (`_ value` != null) 1 else list.size
  fun add(item: E) { list.add(item) }
  fun get() = `_ value` ?: error("use list[0]")
  operator fun get(index: Int) = if (`_ value` != null) error("not list") else list[index]
  override fun iterator(): Iterator<E> = if (`_ value` != null) listOf(`_ value`!!).iterator() else list.iterator()
  override fun toString() = "${`_ value` ?: list}"
}

class NamedMap(internal val `_ map`: Map<String, Any>) {
  inline fun <reified R> getAs(key: String) = key as R
}

internal fun <T> Iterable<T>.associateBySplit(keySelector: (T) -> Iterable<String>): Map<String, T> {
  val map: MutableMap<String, T> = mutableMapOf()
  for (item in this) for (k in keySelector(item)) map[k] = item
  return map
}
internal fun String.split() = split(' ')
internal fun Char.repeats(n: Int): String {
  val sb = StringBuilder()
  for (_t in 1..n) sb.append(this)
  return sb.toString()
}
