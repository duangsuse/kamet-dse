package org.duangsuse.parserkt.argp

/**
 * Simple argument parser driver, use [arg] for arguments when [onPrefix], use [extractArg] for args like `-Ts`/`-T s`,
 *  throw [ParseStop] when stop is requested. [prefixes] __should be__ sorted descending by [String.length]
 */
abstract class SwitchParser<R>(private val args: Array<out String>, private vararg val prefixes: String = arrayOf("--", "-")): Iterator<String> {
  protected var pos = 0 ; private set
  override fun hasNext() = pos < args.size
  override fun next(): String = args[pos++]
  private var currentArg = "?"

  protected abstract fun onPrefix(name: String)
  protected abstract fun onItem(text: String)
  protected abstract val res: R

  protected open fun <R> arg(name: String, convert: (String) -> R): R
    = if (!hasNext()) throw ParseError("expecting $name for $currentArg")
      else next().also { currentArg += "'s $name" }.let(convert)
  protected fun arg(name: String) = arg(name) { it }
  protected open fun checkPrefixForName(name: String, prefix: String) {
    if (prefix == "--" && name.length == 1) throw ParseError("single-char shorthand should like: -$name")
  }
  class ParseError(message: String, exception: Throwable? = null): Error(message, exception)
  object ParseStop: Exception()

  open fun run(): R {
    var argIndex = 1
    while (hasNext()) try {
      val arg = next().also { currentArg = it }
      prefixes.firstOrNull { arg.startsWith(it) }?.let {
        val name = arg.substring(it.length)
        checkPrefixForName(name, it)
        onPrefix(name) } ?: onItem(arg)
      argIndex++
    } catch (_: ParseStop) { break }
      catch (e: Throwable) { throw ParseError("parse fail near $currentArg (#$pos, arg $argIndex): ${e.message}", e) }
    return res
  }
  companion object {
    fun extractArg(prefix_name: String, text: String): String?
      = if (text.startsWith(prefix_name)) text.substring(prefix_name.length) else null
    fun stop(): Nothing = throw ParseStop
  }
}