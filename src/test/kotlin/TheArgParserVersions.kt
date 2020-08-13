import org.duangsuse.parserkt.argp.ArgParser2
import org.duangsuse.parserkt.argp.SwitchParser
import org.duangsuse.parserkt.argp.arg
import org.duangsuse.parserkt.argp.splitArgv
import kotlin.test.Test
import kotlin.test.assertEquals

// https://github.com/jshmrsn/karg#example-usage
/**
 * +Features: types other than [String]; `--help` override; custom terminating: [SwitchParser.stop]
 */
object KArgExample: ArgParser2<String, String>(
  arg("text-to-print text t", "Print this text."),
  arg("text-to-print-after", "If provided, print this text after the primary text."),
  arg("shout", "Print in all uppercase.") {"s"},
  items = listOf("...")
) {
  fun main(vararg args: String) {
    val res = run(args)
    var output = res.tup.e1.get()
    res.items.forEach { output += " $it" }
    res.tup.e2.forEach { output += "\n$it" }
    println(output.let { if ('s' in res.flags) it.toUpperCase() else it })
  }
}

// https://github.com/DanielScholzde/KArgParser#kargparser
class KArgParserExample1: ArgParser2<Int, String>(
  arg<Int>("foo", "Description for foo", "n") { it.toInt() },
  arg("bar", "Description for bar", "str")
) {
  @Test fun main() {
    run("--bar Penny --foo 42".splitArgv()).run {
      assertEquals(42, tup.e1.get())
      assertEquals("Penny", tup.e2.get())
    }
  }
}
