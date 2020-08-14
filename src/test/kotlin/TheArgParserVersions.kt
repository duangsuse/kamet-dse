import org.duangsuse.parserkt.argp.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

// https://github.com/jshmrsn/karg#example-usage
/**
 * +Features: types other than [String]; `--help` override; custom terminating: [SwitchParser.stop]
 */
object KArgExample: ArgParser2<String, String>(
  arg("text-to-print text t", "Print this text."),
  arg("text-to-print-after", "If provided, print this text after the primary text."),
  arg("shout", "Print in all uppercase.") {"s"},
  items = listOf(arg("...", "rest to print"))
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
    assertFails { run("--foo 42".splitArgv()) }
  }
}

object KArgParserExample2 {
  object Parser: ArgParser1<Unit>(
    noArg, arg("ignoreCase", "Ignore case when comparing file contents") {"C"},
    items = listOf(arg("sourceFile", ""), arg("targetFile", ""))
  ) {
  }
  fun compareFiles(file1: File, file2: File, ignoreCase: Boolean) {}
  fun findDuplicates(directories: List<File>, ignoreCase: Boolean) {}
}
