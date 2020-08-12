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
  = Arg(name, "$help in: ${pairs.joinToString(", ") {it.first}}", param, default_value, repeatable, mapOf(*pairs)::getValue)
fun Arg<String>.checkOptions(vararg pairs: Pair<String, String>) = options(defaultValue, *pairs)

class OneOrMore<E>(internal var value: E? = null): Iterable<E> {
  private val list: MutableList<E> by lazy(::mutableListOf)
  val size get() = list.size
  fun add(item: E) { list.add(item) }
  fun get() = value ?: error("use list[0]")
  operator fun get(index: Int) = if (value == null) list[index] else error("not list")
  override fun iterator(): Iterator<E> = if (value != null) listOf(value!!).iterator() else list.iterator()
  override fun toString() = "${value ?: list}"
}


internal fun <T> Iterable<T>.associateBySplit(keySelector: (T) -> Iterable<String>): Map<String, T> {
  val map: MutableMap<String, T> = mutableMapOf()
  for (item in this) for (k in keySelector(item)) map[k] = item
  return map
}
internal fun String.split() = split(' ')
