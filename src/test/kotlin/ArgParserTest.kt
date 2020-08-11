import org.duangsuse.parserkt.argp.*
import java.io.File
import kotlin.test.*

private var printed = ""
val luaP = ArgParser3(
  arg("l", "require library 'name' into global 'name'", "name", repeatable = true),
  arg("e", "execute string 'stat'", "stat"),
  arg("hex", "just an option added for test", "n", "FA") { it.toInt(16).toString() },
  arg("i", "enter interactive mode after executing 'script'"),
  arg("v", "show version information") { printed += "Lua 5.3" ; SwitchParser.stop() },
  arg("E", "ignore environment variables"),
  arg("", "stop handling options and execute stdin") { SwitchParser.stop() },
  arg("-", "stop handling options") { SwitchParser.stop() }
)

abstract class BaseArgParserTest<A,B,C,D>(val p: ArgParser4<A,B,C,D>) {
  fun assertFailMessage(expected: String, args: String) = assertEquals(expected, assertFailsWith<SwitchParser.ParseError> { p.run(args.splitArgv()) }.message)
  fun p(args: String) = p.run(args.splitArgv())
  fun String.splitArgv() = split(" ").toTypedArray()
}

class ArgParserTest: BaseArgParserTest<String, String, String, String>(luaP) {
  @Test fun itWorks() {
    assertEquals(listOf("a", "b"), p("-l a -l b").tup.e1.toList())
    p("-e hello -l a -i -E --").run {
      val (e1, e2, e3, _) = tup
      assertEquals("hello", e2.get())
      assertEquals("a", e1[0])
      assertEquals(listOf("a"), e1.toList())
      assertEquals("FA", e3.get())
      assertEquals("iE", flags)
    }
    p("-hex ff -v - -v").run {
      assertEquals("255", tup.e3.get())
      assertEquals("Lua 5.3", printed)
    }
    p("-- a").run { assertEquals(emptyList<String>(), items) }
  }
  @Test fun itFails() {
    assertFailMessage("parse fail near --E (#3, arg 2): single-char shorthand should like: -E", "--hex af --E")
    assertFailMessage("parse fail near --hex's n (#2, arg 1): For input string: \".23\"", "--hex .23")
    assertFailMessage("parse fail near -e (#3, arg 2): argument e repeated", "-e wtf -e twice")
    assertEquals("flag wtf should be putted in ArgParser(flag = ...)",
      assertFailsWith<IllegalStateException> { ArgParser1(arg("wtf", "e mmm", param = null)).run("".splitArgv()) }.message)
  }
  @Test fun itFormats() {
    assertEquals("""
      usage: {-l name} (-e stat) [-hex n] (-i) (-v) (-E) (-) (--)
        -l: require library 'name' into global 'name'
        -e: execute string 'stat'
        -hex: just an option added for test (default FA)
        -i: enter interactive mode after executing 'script'
        -v: show version information
        -E: ignore environment variables
        -: stop handling options and execute stdin
        --: stop handling options

    """.trimIndent(), luaP.toString())
  }
  @Test fun unorderedFormats() {
    val pas = ArgParser4(arg("donkey", "donkey you rides", "name"), noArg, noArg, noArg, listOf("papa", "mama"))
    assertEquals("""
      usage: (-donkey name)
        -donkey: donkey you rides
      options can be mixed with items: [papa, mama]
    """.trimIndent(), pas.toString())
    assertEquals(listOf("A", "B"), pas.run(arrayOf("A", "-donkey", "ED2K", "B")).items)
  }
}

val myP = ArgParser4(
  arg("name", "name of the user", ""),
  arg<Int>("count C", "number of the widgets", "") { it.toInt() },
  noArg, noArg,
  itemNames=listOf("ah"), itemMode=PositionalMode.MustBefore,
  autoSplit=listOf("name", "count"),
  flags=*arrayOf(arg("v", "enable verbose mode"))
)

class ExtendArgParserTest: BaseArgParserTest<String, Int, String, String>(myP) {
  @Test fun readsPositional() {
    p("hello --nameMike -v -count233").run {
      val (e1, e2) = tup
      assertEquals("hello", items[0])
      assertEquals("Mike", e1.get())
      assertEquals(233, e2.get())
      assertTrue('v' in flags)
    }
    p("emm -C80").run { assertEquals(80, tup.e2.get()) }
    assertFailMessage("parse fail near -xx (#1, arg 1): too less items (all [ah])", "-xx")
    assertFailMessage("parse fail near s (#2, arg 2): too many items (all [ah])", "a s -nameJack")
    assertFailMessage("parse fail near loo (#4, arg 4): reading [hel, loo]: should all-before options", "hel -count666 -v loo")
    assertFailMessage("parse fail near x (#3, arg 3): reading [exx, x]: should all-before options", "exx -C61 x --name wtf y")
  }
  @Test fun format() {
    assertEquals("""
      usage: [ah] (-name name) (-count -C count) (-v)
        -name: name of the user
        -count -C: number of the widgets
        -v: enable verbose mode

    """.trimIndent(), myP.toString())
  }
}

val yourP = ArgParser4(
  arg("name N", "name of the user", "", "Duckling"),
  arg<Int>("count c", "number of widgets", "") { it.toInt() },
  arg<File>("I", "directory to search for header files", "file", repeatable = true) { File(it) },
  arg("mode", "mode of operation", "").checkOptions("fast" to "f", "small" to "s", "quite" to "q"),
  itemNames = listOf("source", "dest"), itemMode = PositionalMode.MustAfter,
  flags = *arrayOf(helpArg)
)

class ExtendArgParserTest1: BaseArgParserTest<String, Int, File, String>(yourP) {
  @Test fun readsPositional() {
    p("-N mike --count 233 -I ArgParserTest.kt -I ParserTest.kt -mode fast fg hs").run {
      assertEquals(2, tup.e3.size)
      assertEquals("f", tup.e4.get())
    }
    assertFailMessage("parse fail near a (#1, arg 1): reading [a]: should all-after options", "a -N make")
    assertFailMessage("parse fail near c (#6, arg 4): reading [k, c]: should all-after options", "-N make k -c 88 c d")
    assertFailMessage("too less items (all [source, dest])", "-c 233 sf")
    assertFailMessage("parse fail near 996 (#5, arg 4): too many items (all [source, dest])", "--name doge 233 666 996")
    p("-h --help")
  }
  @Test fun format() {
    assertEquals("""
      usage: [-name -N name] (-count -c count) {-I file} (-mode mode) (-h -help) [source, dest]
        -name -N: name of the user (default Duckling)
        -count -c: number of widgets
        -I: directory to search for header files
        -mode: mode of operation in: fast, small, quite
        -h -help: print this help

    """.trimIndent(), yourP.toString())
  }
}
