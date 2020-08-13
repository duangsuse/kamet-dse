package org.duangsuse.parserkt.argp

/**
 * Simple argument parser driver, use [arg] for arguments when [onPrefix], use [extractArg] for args like `-Ts`/`-T s`;
 *  throw [ParseStop] when stop is requested. [prefixes] __should be__ sorted descending by [String.length]
 */
abstract class SwitchParser<R>(private val args: ArgArray, private val prefixes: Iterable<String> = listOf("--", "-")): Iterator<String> {
  constructor(args: ArgArray, vararg prefixes: String): this(args, prefixes.sortedByDescending { it.length })
  protected var pos = 0 ; private set
  override fun hasNext() = pos < args.size
  override fun next(): String = args[pos++]
  protected var currentArg = "?"

  protected abstract fun onPrefix(name: String)
  protected abstract fun onItem(text: String)
  protected abstract val res: R

  protected fun <R> arg(name: String, convert: (String) -> R): R
    = if (!hasNext()) throw ParseError(prefixMessageCaps().first("expecting $name for $currentArg"))
      else next().also { currentArg += "'s $name" }.let(convert)
  protected fun arg(name: String) = arg(name) { it }
  protected open fun checkPrefixForName(name: String, prefix: String) {}
  protected open fun prefixMessageCaps(): Pair<TextCaps, TextCaps> = TextCaps.nonePair
  class ParseError(message: String, exception: Throwable? = null): Error(message, exception)
  object ParseStop: Exception()

  protected fun onArg(text: String) {
    prefixes.firstOrNull { text.startsWith(it) }?.let {
      val name = text.substring(it.length)
      checkPrefixForName(name, it)
      onPrefix(name) /*or*/} ?: onItem(text)
  }
  open fun run(): R {
    val (capPre, capMsg) = prefixMessageCaps()
    fun msgFmt(ex: Throwable) = capMsg(": ${ex.message}")
    var argCount = 1
    while (hasNext()) try {
      val arg = next().also { currentArg = it }
      onArg(arg) ; argCount++
    } catch (_: ParseStop) { break }
      catch (e: IllegalArgumentException) { throw IllegalArgumentException(capPre("bad argument $argCount, $currentArg")+msgFmt(e), e) }
      catch (e: Throwable) { throw ParseError(capPre("parse fail near $currentArg (#$pos, arg $argCount)")+msgFmt(e), e) }
    return res
  }
  companion object {
    fun extractArg(prefix_name: String, text: String): String?
      = if (text.startsWith(prefix_name)) text.substring(prefix_name.length) else null
    fun stop(): Nothing = throw ParseStop
  }
}
