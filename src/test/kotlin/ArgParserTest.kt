import org.duangsuse.parserkt.argp.ArgParser3
import org.duangsuse.parserkt.argp.SwitchParser
import org.duangsuse.parserkt.argp.arg
import kotlin.test.Test
import kotlin.test.assertEquals

private var printed = ""
val luaP = ArgParser3(
  arg("l", "require library 'name' into global 'name'", "name", repeatable = true),
  arg("e", "execute string 'stat'", "stat"),
  arg("hex", "just an option added for test", "n") { it.toInt(16).toString() },
  arg("i", "enter interactive mode after executing 'script'"),
  arg("v", "show version information") { printed += "Lua 5.3" ; SwitchParser.stop() },
  arg("E", "ignore environment variables"),
  arg("", "stop handling options and execute stdin") { SwitchParser.stop() },
  arg("-", "stop handling options") { SwitchParser.stop() }
)

class ArgParserTest {
  @Test fun itWorks() {
    assertEquals(listOf("a", "b"), p("-l a -l b").tup.e1.toList())
    p("-e hello -l a -i -E --").run {
      val (e1, e2, _, _) = tup
      assertEquals("hello", e2.get())
      assertEquals("a", e1[0])
      assertEquals(listOf("a"), e1.toList())
      assertEquals("iE", flags)
    }
    p("-hex ff -v - -v").run {
      assertEquals("255", tup.e3.get())
      assertEquals("Lua 5.3", printed)
    }
    p("-- a").run { assertEquals(emptyList<String>(), items) }
  }
  @Test fun itFormats() {
    assertEquals("""
      usage: {-l name} (-e stat) (-hex n) (-i) (-v) (-E) (-) (--)
        -l: require library 'name' into global 'name'
        -e: execute string 'stat'
        -hex: just an option added for test
        -i: enter interactive mode after executing 'script'
        -v: show version information
        -E: ignore environment variables
        -: stop handling options and execute stdin
        --: stop handling options

    """.trimIndent(), luaP.toString())
  }
  fun p(args: String) = luaP.run(args.split(" ").toTypedArray())
}
