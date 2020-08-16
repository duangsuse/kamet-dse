package org.duangsuse.parserkt.argp

import org.duangsuse.parserkt.argp.Env.Constants.sys

private class HelpSubCommand(private val parent: DynArgParser, private val prog: String): DynArgParserUnit(noArg, noArg, noArg, noArg,
  itemArgs = listOf(arg(SPREAD, "sub-command path"))) {
  override fun preCheckResult(result: ParseResult<Unit, Unit, Unit, Unit>) {
    result.flags += "h" //< make parent-parser return early
    if (result.items.isEmpty()) { println(parent.toString()) ; return }
    val helpContainer = parent.getSub(result.items.run { subList(0, size-1) })
    val name = result.items.last()
    val subcmd = try { helpContainer.getSub(listOf(name))
    } catch (_: NoSuchElementException) { println("unknown sub-command $name in ${helpContainer.toString(head="cmd: ")}") ; SwitchParser.stop() }
    val help = helpContainer.getSubHelp(name)
    println("$prog${result.items.joinToString(" ")}: $help")
    println(subcmd.toString())
  }
}

fun DynArgParser.addHelpSubCommand(prog: String = "") {
  addSub("help", "show help for sub-command", HelpSubCommand(this, prog))
}

/** Try loads env with key `SOME_NAME` from [Env.sys] first. see [getEnv] */
fun <T> Arg<T>.env(): Arg<T> = wrapHelpAndDefault { "$help, env ${firstName.envKey}" to (sys.getEnv(firstName)?.let { convert?.invoke(it) } ?: defaultValue) }
fun Arg<String>.getEnv() = wrapHelpAndDefault { "$help, env ${firstName.envKey}" to (sys.getEnv(firstName) ?: defaultValue) }
private val String.envKey get() = replace('-', '_').toUpperCase()

fun <T> Arg<T>.wrapHelpAndDefault(op: Arg<T>.() -> Pair<String, T?>) = op(this).run { Arg(name, first, param, second, repeatable, convert) }
fun <T, R> Arg<T>.wrapConvertAndDefault(default_value: R?, convert: Convert<R>): Arg<R> = Arg(name, help, param, default_value, repeatable, convert)
/** Makes item arg result writes to [ParseResult.named], or [Arg.convert] must not be null */
fun <T> Arg<T>.addNopConvert() = wrapConvertAndDefault(defaultValue) { convert?.invoke(it) ?: @Suppress("unchecked_cast") (it as T) }

/** Support reversed-order item destruct `a b c [...]` to `[...] c b a` */
fun <A,B,C,D> ArgParser4<A,B,C,D>.reversed() = run {
  ArgParser4(p1, p2, p3, p4, flags=*flags, itemArgs=itemArgs.reversed(), moreArgs=moreArgs, autoSplit=autoSplit, itemMode=itemMode.reversed())
}
fun PositionalMode.reversed() = when (this) {
  PositionalMode.MustBefore -> PositionalMode.MustAfter
  PositionalMode.MustAfter -> PositionalMode.MustBefore
  else -> this
}
fun DynArgParser.reversedArgArray(args: ArgArray) = run {
  val ap = ArgParser4(p1, p2, p3, p4, flags=*flags, itemArgs=listOf(arg(SPREAD, "items")), moreArgs=moreArgs, autoSplit=autoSplit, itemMode=itemMode)
  return@run ap.run(args).let { res -> ap.backRun(res) { it.reversed() } }
}

fun <T> createOneOrMore(xz: Iterable<T>) = OneOrMore<Any>().apply { xz.forEach { add(it as Any) } }
