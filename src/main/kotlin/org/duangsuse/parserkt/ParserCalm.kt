package org.duangsuse.parserkt

typealias LocatedError = Pair<SourceLocated.SourceLocation, String>
typealias ErrorMessager = Input.() -> String

fun Input.addErrorList(): List<LocatedError> {
  val errorList: MutableList<LocatedError> = mutableListOf()
  errorHandler = { message -> errorList.add(this@addErrorList.sourceLoc to message) }
  return errorList
}

fun expectingMessager(expecting: Any): ErrorMessager = {"unexpected $peek, expecting $expecting"}

/** Add error and __skip unacceptable__ [p], yield [defaultValue] */
fun <T> Input.calmWhile(p: Parser<*>, defaultValue: T, message: String): T {
  this.error(message)
  while (true) if (p(this) == notPas) break
  return defaultValue
}
fun <T> Parser<T>.calmWhile(p: Parser<*>, defaultValue: T, messager: ErrorMessager) = Piped(this) {
  this@calmWhile(this) ?: calmWhile(p, defaultValue, messager())
}

/** Try read an item, until input ends */
inline fun calmSatisfy(crossinline p: CharPredicate, crossinline messager: ErrorMessager): Parser<Char> = parse@ { s ->
  if (!p(s.peek)) s.error(s.messager())
  var parsed: Char? = null
  while (parsed == notPas) {
    s.consumeOrNull() ?: break
    parsed = if (p(s.peek)) s.consume() else notPas
  }
  return@parse parsed
}
inline fun calmItem(c: Char, crossinline messager: ErrorMessager) = calmSatisfy({ it == c }, messager)
fun calmItem(c: Char) = calmItem(c, expectingMessager("`$c'"))

// Inputs with state
interface StatedInput<out ST>: Input { val state: ST }

fun <ST> Input.withState(value: ST) = when (this) {
  is StringInput -> StatedStringInput(value, string, sourceLoc.file)
  is IteratorInput -> StatedIteratorInput(value, iterator, sourceLoc.file)
  else -> throw IllegalArgumentException("unsupported type")
}
@Suppress("UNCHECKED_CAST")
inline fun <reified ST> Input.stateAs(): ST? = (this as? StatedInput<ST>)?.state

open class StatedStringInput<ST>(override val state: ST, string: String, file: String): StringInput(string, file), StatedInput<ST>
open class StatedIteratorInput<ST>(override val state: ST, iterator: CharIterator, file: String): IteratorInput(iterator, file), StatedInput<ST>
