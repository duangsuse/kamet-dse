package org.duangsuse.parserkt

import java.util.*
import kotlin.NoSuchElementException

interface Feed { // In last project that's a generic stream, and CharInput should be separated
  val peek: Char; fun consume(): Char
  object End: NoSuchElementException("no more")
}
interface SourceLocated {
  val sourceLoc: SourceLocator
  interface SourceLocator { val file: String; val line: Int; val column: Int }
  data class CurrentLocation(override val file: String, override var line: Int, override var column: Int, val eol: Char): SourceLocator {
    constructor(file: String, eol: Char = System.lineSeparator().singleOrNull() ?: '\n'): this(file, 1, 1, eol)
    override fun toString() = "$file:$line:$column"
    fun onAccept(c: Char) {
      val isEol = (c == eol).also { if(it) line++ }
      column = if (isEol) 1 else column+1
    }
  }
}
interface FeedControl {
  fun peekMany(n: Int): String
  val isEnd: Boolean
}

typealias ErrorHandler = Feed.(String) -> Unit
interface FeedError { var errorHandler: ErrorHandler }
private val raiseError: ErrorHandler = { msg -> throw InputMismatchException(this.sourceLoc?.let { "$it: $msg" } ?: msg) }

// Inputs, for CharIterator and String.
interface Input: SourceLocated,Feed, FeedControl, FeedError

abstract class BaseInput(file: String): Input { // not implemented: Feed,FeedControl
  protected val currentLoc = SourceLocated.CurrentLocation(file)
  override val sourceLoc: SourceLocated.SourceLocator = currentLoc
  override var errorHandler = raiseError
}

class CharFIFO(val string: StringBuilder = StringBuilder()) {
  fun add(c: Char) { string.append(c) }
  fun pop(): Char = string[0].also { string.deleteCharAt(0) }
}

open class IteratorInput(private val iterator: CharIterator, file: String = "<anon>"): BaseInput(file) {
  private var lastItem = try { iterator.nextChar() } catch (e: Exception) { throw Error("initial peek failed for $iterator", e) }
  private var tailConsumed = false // goes true last-1st time consume(), last-2nd: !iterator.hasNext() but peek unconsumed

  private val peekBuffer = CharFIFO()
  override val peek: Char get() = lastItem
  override val isEnd get() = tailConsumed
  override fun consume(): Char {
    val oldItem = lastItem
    if (peekBuffer.string.isNotEmpty()) lastItem = peekBuffer.pop()
    else updateOnConsume()
    if (!isEnd) currentLoc.onAccept(oldItem)
    return oldItem
  }
  private fun updateOnConsume() = if (iterator.hasNext()) lastItem = iterator.nextChar()
    else if (!tailConsumed) tailConsumed = true
    else throw Feed.End

  override fun peekMany(n: Int): String {
    require(n > 1) {"$n must >1"}
    val sb = StringBuilder()
    if (!isEnd) { sb.append(lastItem) } else {  return "" } //[0]
    try {
      for (_t in 1 until n) { sb.append(iterator.nextChar()) }
    } catch (_: StringIndexOutOfBoundsException) {}
    if (sb.isNotEmpty()) sb.substring(1).forEach(peekBuffer::add)
    return sb.toString()
  }
}

open class StringInput(val string: String, file: String = "<string>"): BaseInput(file) {
  init { require(string.isNotEmpty()) {"empty input"} }
  private var pos = 0
  final override var isEnd = false ; private set
  override val peek: Char get() = string[pos]
  override fun consume(): Char {
    val res = string[pos++]
    if (pos == string.length) {
      if (!isEnd) { pos--; isEnd = true }
      else throw Feed.End
    }
    if (!isEnd) currentLoc.onAccept(res); return res
  }

  override fun peekMany(n: Int): String {
    require(n > 1) {"$n must >1"}
    return string.substring(pos, (pos + n).coerceAtMost(string.length))
  }
}

typealias CharPredicate = (Char) -> Boolean
typealias CharReducer<R> = Reducer<Char, R>

val Feed.sourceLoc get() = (this as? SourceLocated)?.sourceLoc
fun <R> Feed.takeWhile(reducer: CharReducer<R>, p: CharPredicate): R {
  while (p(peek)) reducer.accept(consume())
  return reducer.finish()
}
fun Feed.takeWhile(p: CharPredicate) = takeWhile(asString(), p)
fun Feed.error(message: String): Unit = (this as? FeedError)?.errorHandler?.invoke(this, message) ?: kotlin.error(message)
fun <R> Feed.catchErrorNull(op: Feed.() -> R): R? = try { op() } catch (_: Error) { null }
