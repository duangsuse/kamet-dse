package org.duangsuse.parserkt.argp

private class HelpSubCommand(private val parent: DynArgParser, private val prog: String): DynArgParserUnit(noArg, noArg, noArg, noArg,
  itemArgs = listOf(arg("...", "sub-command path"))) {
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
