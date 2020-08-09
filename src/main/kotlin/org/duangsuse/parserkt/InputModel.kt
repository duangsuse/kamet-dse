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
interface Input: SourceLocated, Feed, FeedControl, FeedError

class CharFIFO(val string: StringBuilder = StringBuilder()) {
  fun add(c: Char) { string.append(c) }
  fun pop(): Char = string[0].also { string.deleteCharAt(0) }
}

abstract class BaseInput(file: String): Input {
  protected val currentLoc = SourceLocated.CurrentLocation(file)
  override val sourceLoc: SourceLocated.SourceLocator = currentLoc
  override var errorHandler = raiseError
}

open class IteratorInput(private val iterator: CharIterator, file: String = "<anon>"): BaseInput(file) {
  private var lastItem = try { iterator.nextChar() } catch (e: Exception) { throw Error("initial peek failed in $iterator") }
  private var tailConsumed = false // goes true last-1st time consume(), last-2nd: !iterator.hasNext() but peek unconsumed

  private val peekBuffer = CharFIFO()
  override val isEnd get() = tailConsumed
  override val peek: Char get() = lastItem
  override fun consume(): Char {
    if (peekBuffer.string.isNotEmpty()) return lastItem.also { lastItem = peekBuffer.pop() }
    val res = lastItem
    updateOnConsume(); currentLoc.onAccept(res)
    return res
  }
  private fun updateOnConsume() = if (iterator.hasNext()) lastItem = iterator.nextChar()
    else if (!tailConsumed) tailConsumed = true
    else throw Feed.End

  override fun peekMany(n: Int): String {
    require(n > 1) {"$n must >1"}
    val sb = StringBuilder()
    if (!isEnd) { sb.append(lastItem); currentLoc.column++ } //[0]
    try {
      for (_t in 1 until n) { sb.append(iterator.nextChar()) }
    } catch (_: StringIndexOutOfBoundsException) {}
    if (sb.isNotEmpty()) sb.substring(1).forEach(peekBuffer::add)
    return sb.toString()
  }
}

open class StringInput(val string: String, file: String = "<string>"): BaseInput(file) {
  private var pos = 0
  override val isEnd get() = (pos == string.length)
  override val peek: Char get() = string[pos]
  override fun consume(): Char {
    try { val res = string[pos++]; if(!isEnd) currentLoc.onAccept(res); return res }
    catch (_: StringIndexOutOfBoundsException) { pos = string.length; throw Feed.End }
  }

  override fun peekMany(n: Int): String {
    require(n > 1) {"$n must >1"}
    return string.substring(pos, (pos + n).coerceAtMost(string.length))
  }
}

typealias CharPredicate = (Char) -> Boolean
typealias CharFold<R> = Fold<Char, R>

val Feed.sourceLoc get() = (this as? SourceLocated)?.sourceLoc
fun <R> Feed.takeWhile(fold: CharFold<R>, p: CharPredicate): R {
  while (p(peek)) fold.accept(consume())
  return fold.finish()
}
fun Feed.takeWhile(p: CharPredicate) = takeWhile(asString(), p)
fun Feed.error(message: String): Unit = (this as? FeedError)?.errorHandler?.invoke(this, message) ?: kotlin.error(message)
