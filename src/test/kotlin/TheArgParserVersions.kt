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
  @JvmStatic fun main(vararg args: String) {
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

// https://github.com/st235/ArgsParser#usage-example
object ArgsParser: ArgParser3<Int, String, Int>(
  argInt("id", "ID"),
  arg("name", "Name"),
  argInt("age", "Age")
) {
  @JvmStatic fun main(vararg args: String) {
    val tup = run(args).tup
    println("""
      id: ${tup.e1}
      name: ${tup.e2}
      age: ${tup.e3}
    """.trimIndent())
  }
}

// https://github.com/dustinliu/argparse4k#usage
object ArgParse4KExamples: ArgParser1<String>(
  arg("foo", "fo fo fo"), // meta-var assignment is not supported for typed args
  arg("detached d", "fd sf"),
  arg("v", "help version") { SwitchParser.stop() },
  items = listOf(arg("container", "container name")) // TODO subParser ccc -v
) {
  override fun toString() = "testprog " + super.toString()
}

// https://github.com/tarantelklient/kotlin-argparser
class KotlinArgParserExamples: ArgParser2<String, String>(
  arg("arg1", "simple test argument", ""),
  arg("arg2", "test argument with default", "text", "hello world"),
  arg("flag", "flag argument")
)

// https://github.com/substack/minimist#example
object MiniMistExample: ArgParser4<Unit, Unit, Unit, Unit>(noArg, noArg, noArg, noArg, itemArgs = listOf(arg("...", "rest items")), moreArgs = emptyList()) {
  override fun rescuePrefix(name: String): Arg<*> {
    for (re in autoParam)
      re.find(name)?.let { val (k, v) = it.destructured ; return arg(k, "", null, v) }
    return if (name.matches(nameArg)) arg(name, name, "")
    else arg("flags", "", null, name, repeatable = true)
  }
  override fun checkPrefixForName(name: String, prefix: String) {
    super.checkPrefixForName(name, prefix)
    //if (prefix.length == 1 && name.length != 1 && !autoParam[1].matches(name) && '=' !in name) throw SwitchParser.ParseError("assign names like --name=value")
  }
  private val nameArg = Regex("""\w""")
  private val autoParam = listOf(Regex("""(\w+)=(\S+)"""), Regex("""(\w+)(\d+)"""))
}

class TheArgParserVersions {
  @Test fun miniMist() {
    val res = MiniMistExample.run("-a beep -b bop").named!!
    assertEquals("beep", res["a"])
    assertEquals("bop", res["b"])
    val res1 = MiniMistExample.run("-x 3 -y 4 -n5 -abc -def --beep=bop foo bar baz")
    assertEquals(mapOf("x" to "3", "y" to "4", "n" to "5", "beep" to "bop"), res1.named!!.map.filterValues { it.value != null }.mapValues { it.value.get() })
    assertEquals(listOf("abc", "def"), res1.named!!.getAsList("flags"))
    assertEquals("foo bar baz".split(" "), res1.items)
  }
}

internal fun <A,B,C,D> ArgParser4<A,B,C,D>.run(text: String) = run(text.splitArgv())
