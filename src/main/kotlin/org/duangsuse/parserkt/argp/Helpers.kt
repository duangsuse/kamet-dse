package org.duangsuse.parserkt.argp

typealias ArgArray = Array<out String>
typealias Convert<R> = ((String) -> R)?

inline fun <reified R> arg(
  name: String, help: String, param: String? = null,
  default_value: R? = null, repeatable: Boolean = false, noinline convert: Convert<R>): Arg<R>
  = Arg(name, help, if (param == "") name.split(' ').first() else param, default_value, repeatable, convert)
fun arg(name: String, help: String, param: String? = null,
        default_value: String? = null, repeatable: Boolean = false, convert: Convert<String> = null)
  = arg<String>(name, help, param, default_value, repeatable, convert)

fun <R> Arg<String>.options(default_value: R? = null, vararg pairs: Pair<String, R>): Arg<R>
  = Arg(name, "$help in ${pairs.joinToString(", ") {it.first}}", param, default_value, repeatable, mapOf(*pairs)::getValue)
fun Arg<String>.checkOptions(vararg strings: String) = options(defaultValue, *strings.map { it to it }.toTypedArray())
fun <R> multiParam(convert: (List<String>) -> R): Convert<R> = { it.split('\u0000').let(convert) }

fun defineFlags(vararg pairs: Pair<String, String>): Array<Arg<*>>
  = pairs.asIterable().map { arg("${it.first} ${it.second}", it.first.split("-").joinToString(" ")) }.toTypedArray()

class OneOrMore<E>(var value: E? = null): Iterable<E> {
  val list: MutableList<E> by lazy(::mutableListOf)
  val size get() = if (value != null) 1 else list.size
  fun add(item: E) { list.add(item) }
  fun get() = value ?: error("use list[0]")
  operator fun get(index: Int) = if (value != null) error("not list") else list[index]
  override fun iterator(): Iterator<E> = if (value != null) listOf(value!!).iterator() else list.iterator()
  override fun toString() = "${value ?: list}"
}

class NamedMap(val map: Map<String, OneOrMore<Any>>) {
  inline fun <reified R> getAs(key: String) = map[key]?.get() as R
  inline fun <reified R> getAsList(key: String) = @Suppress("unchecked_cast") (map[key]?.list as List<R>)
  operator fun get(key: String) = getAs<String>(key)
}

enum class TextCaps {
  None, AllUpper, AllLower, Capitalized;
  operator fun invoke(text: String) = when (this) {
    None -> text
    AllUpper -> text.toUpperCase()
    AllLower -> text.toLowerCase()
    Capitalized -> text.capitalize()
  }
  companion object Constants {
    val nonePair = None to None
  }
}

internal fun <T> Iterable<T>.associateByAll(keySelector: (T) -> Iterable<String>): Map<String, T> {
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

fun String.splitArgv() = split(" ").toTypedArray()

inline fun <T> Iterable<T>.joinToBreakLines(sb: StringBuilder, separator: String, line_limit: Int, line_separator: String, crossinline transform: (T) -> CharSequence): StringBuilder {
  var lineSize = 0
  var lastLength = sb.length
  return joinTo(sb, separator) {
    val line = transform(it).also { line -> lineSize += line.length + (sb.length - lastLength) ; lastLength = sb.length  }
    if (lineSize < line_limit) line
    else (line_separator + line).also { lineSize = 0 }
  }
}
