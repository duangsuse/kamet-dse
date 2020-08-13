import org.duangsuse.parserkt.argp.*
import java.io.File
import kotlin.test.*

private var printed = ""
val luaP = ArgParser3(
  arg("l", "require library 'name' into global 'name'", "name", repeatable = true),
  arg("e", "execute string 'stat'", "stat mode", "a" to "b", convert = multiParam { it[0] to it[1].also { m -> require(m != "fail") } }),
  arg("hex", "just an option added for test", "n", "FA") { it.toInt(16).toString() },
  arg("i", "enter interactive mode after executing 'script'"), //^ errors should not looks like -e's stat's mode, that's error.
  arg("v", "show version information") { printed += "Lua 5.3" ; SwitchParser.stop() },
  arg("E", "ignore environment variables"),
  arg("", "stop handling options and execute stdin") { SwitchParser.stop() },
  arg("-", "stop handling options") { SwitchParser.stop() }
)

abstract class BaseArgParserTest<A,B,C,D>(val p: ArgParser4<A,B,C,D>) {
  fun assertFailMessage(expected: String, args: String) = assertEquals(expected, assertFailsWith<Throwable> { p.run(args.splitArgv()) }.message)
  fun p(args: String) = p.run(args.splitArgv())
  fun backP(args: String) = p(args).let { p.run(p.backRun(it)) }
}

class ArgParserTest: BaseArgParserTest<String, Pair<String, String>, String,Unit>(luaP) {
  @Test fun itWorks() {
    assertEquals(listOf("a", "b"), p("-l a -l b").tup.e1.toList())
    p("-e hello stmt -l a -i -E --").run {
      val (e1, e2, e3, _) = tup
      assertEquals("hello" to "stmt", e2.get())
      assertEquals("a", e1[0])
      assertEquals(listOf("a"), e1.toList())
      assertEquals("FA", e3.get())
      assertEquals("iE", flags)
    }
    p("-hex ff -v - -v").run {
      assertEquals("255", tup.e3.get())
      assertEquals("Lua 5.3", printed)
    }
    backP("-l a -e fault expr -l b -i -E -v").run {
      assertEquals("iE", flags)
      assertEquals(listOf("a", "b"), tup.e1.toList())
      assertEquals("fault" to "expr", tup.e2.get())
    }
    backP("-- a").run { assertEquals(emptyList<String>(), items) }
  }
  @Test fun itFails() {
    assertFailMessage("parse fail near --E (#3, arg 2): single-char shorthand should like: -E", "--hex af --E")
    assertFailMessage("bad argument 1, --hex's n: For input string: \".23\"", "--hex .23")
    assertFailMessage("parse fail near -e (#5, arg 3): argument e repeated", "-e wtf mode x -e twice")
    assertEquals("flag wtf should be putted in ArgParser(flag = ...)",
      assertFailsWith<IllegalStateException> { ArgParser1(arg("wtf", "e mmm", param = null)).run("".splitArgv()) }.message)
    assertFailMessage("parse fail near -e (#1, arg 1): expecting stat for -e", "-e")
    assertFailMessage("parse fail near -e (#4, arg 2): expecting mode for -e", "-hex 23 -e wtf")
    assertFailMessage("bad argument 1, in -e: Failed requirement.", "-e code fail")
  }
  @Test fun itFormats() {
    assertEquals("""
      Usage: {-l name} (-e stat, mode) (-hex n) [-i] [-v] [-E] [-] [--]
        -l: require library 'name' into global 'name'
        -e: execute string 'stat' (default (a, b))
        -hex: just an option added for test (default FA)
        -i: enter interactive mode after executing 'script'
        -v: show version information
        -E: ignore environment variables
        -: stop handling options and execute stdin
        --: stop handling options

    """.trimIndent(), luaP.toString())
    assertEquals("""
      用法： {-l NAME} (-e STAT, MODE) (-hex N) [-i] [-v] [-E] [-] [--]哈。
      | 参数-l呢，是Require library 'name' into global 'name'哈。
      | 参数-e呢，是Execute string 'stat' (default (a, b))哈。
      | 参数-hex呢，是Just an option added for test (default FA)哈。
      | 参数-i呢，是Enter interactive mode after executing 'script'哈。
      | 参数-v呢，是Show version information哈。
      | 参数-E呢，是Ignore environment variables哈。
      | 参数-呢，是Stop handling options and execute stdin哈。
      | 参数--呢，是Stop handling options哈。
      就是这样，喵。
    """.trimIndent(), luaP.toString(TextCaps.AllUpper to TextCaps.Capitalized, head="用法： ", epilogue="就是这样，喵。", indent="| 参数", colon="呢，是", newline="哈。\n"))
    assertEquals("""
      Usage: {-l name} (-e stat, mode) (-hex n) [-i] [-v] [-E] [-] [--]
      Options: 
          -l: require library 'name' into global 'name'
          -e: execute string 'stat' (default (a, b))
          -hex: just an option added for test (default FA)
      Flags: 
          -i: enter interactive mode after executing 'script'
          -E: ignore environment variables
      Help: 
          -v: show version information
          -: stop handling options and execute stdin
          --: stop handling options

    """.trimIndent(), luaP.toString(groups = mapOf("l e hex" to "Options", "i E" to "Flags", "*" to "Help")))
  }
  @Test fun unorderedFormats() {
    val pas = ArgParser4(arg("donkey", "donkey you rides", "name"), noArg, noArg, noArg, listOf("papa", "mama"))
    assertEquals("""
      Usage: [-donkey name]
        -donkey: donkey you rides
      options can be mixed with items: [papa, mama]
    """.trimIndent(), pas.toString())
    assertEquals(listOf("A", "B"), pas.run(arrayOf("A", "-donkey", "ED2K", "B")).items)
  }
}

val myP = object: ArgParser4<String,Int,Unit,Unit>(
  arg("name", "name of the user", "", "Alice"),
  arg<Int>("count C", "number of the widgets", "", 1) { it.toInt() },
  noArg, noArg,
  moreArgs = listOf(
    arg("xxx", "added for rescue", "", ""),
    arg("map", "build map", "k v", repeatable = true, convert = multiParam { it[0] to it[1] })),
  itemNames=listOf("ah"), itemMode=PositionalMode.MustBefore,
  autoSplit=listOf("name", "count"),
  flags=*arrayOf(arg("v", "enable verbose mode"))
) {
  override val prefixes: List<String> = listOf("--", "/", "-")

  override fun checkAutoSplitForName(name: String, param: String) {
    if (name == "name" && param == "Jerky") throw SwitchParser.ParseError("$param doesn't like been broken into parts")
  }
  override fun checkPrefixForName(name: String, prefix: String) {
    super.checkPrefixForName(name, prefix)
    if (name.startsWith("C") && prefix == "/") throw SwitchParser.ParseError("/C is good emotion, not parameter")
  }
  override fun rescuePrefix(name: String): Arg<*> = when {
    name == "add" -> arg("add", "that list", "value", repeatable = true)
    name.endsWith("=") -> {
      val dest = name.substring(0, name.length-1)
      arg(dest, "woc", "value")
    }
    '=' in name -> arg(name.substringBefore('='), "emm", default_value = name.substringAfter('='))
    else -> super.rescuePrefix(name)
  }
}

class ExtendArgParserTest: BaseArgParserTest<String, Int, Unit,Unit>(myP) {
  @Test fun readsPositional() {
    backP("hello --nameMike -v -count233").run {
      val (e1, e2) = tup
      assertEquals("hello", items[0])
      assertEquals("Mike", e1.get())
      assertEquals(233, e2.get())
      assertTrue('v' in flags)
    }
    backP("emm -C80").run { assertEquals(80, tup.e2.get()) }
    assertFailMessage("parse fail near -xx (#1, arg 1): too less heading items (all [ah])", "-xx")
    assertFailMessage("parse fail near s (#2, arg 2): too many items (all [ah])", "a s -nameJack")
    assertFailMessage("parse fail near loo (#4, arg 4): reading [hel, loo]: should all-before options", "hel -count666 -v loo")
    assertFailMessage("parse fail near x (#3, arg 3): reading [exx, x]: should all-before options", "exx -C61 x --name wtf y")
  }
  @Test fun fourCasesForMustBefore() {
    assertFailMessage("parse fail near --nameJerky (#2, arg 2): Jerky doesn't like been broken into parts", "ya --nameJerky")
    assertFailMessage("parse fail near /C19 (#7, arg 5): /C is good emotion, not parameter", "eh -name Black -v /xxx a /C19")
    backP("wtf") // item only
    assertFailMessage("parse fail near -name (#1, arg 1): too less heading items (all [ah])", "-name Jessie emm")
    assertFailMessage("parse fail near -name (#1, arg 1): too less heading items (all [ah])", "-name Jake ehh -v")
  }
  @Test fun dynamicInterpret() {
    p("as --add 1 -add 2 -add 3").run { assertEquals("1 2 3".split(), named!!.getAsList("add")) }
    p("ds -ax= 232 -bx=333").run { val m=named!! ; assertEquals("232", m["ax"]) ; assertEquals("333", m["bx"]) }
    backP("mapping -map apple 苹果 -C80 -map pear 梨子 -map a b").apply {
      assertEquals(mapOf("apple" to "苹果", "pear" to "梨子", "a" to "b"), named!!.getAsList<Pair<String,String>>("map").toMap())
      assertEquals(80, tup.e2.get())
      assertEquals("mapping", items[0])
    }
  }
  @Test fun format() {
    assertEquals("""
      Usage: <ah> (-name name) (-count -C count) [-v] (-xxx xxx) {-map k, v}
        -name: name of the user (default Alice)
        -count -C: number of the widgets (default 1)
        -v: enable verbose mode
        -xxx: added for rescue (default none)
        -map: build map

    """.trimIndent(), myP.toString())
  }
}

val yourP = ArgParser4(
  arg("name N", "name of the user", "", "Duckling"),
  arg<Int>("count c", "number of widgets", "") { it.toInt() },
  arg<File>("I", "directory to search for header files", "file", repeatable = true) { File(it) },
  arg("mode", "mode of operation", "", "small").checkOptions("fast", "small", "quite"),
  itemNames = listOf("source", "dest"), itemMode = PositionalMode.MustAfter,
  flags = *arrayOf(helpArg)
)

class ExtendArgParserTest1: BaseArgParserTest<String, Int, File, String>(yourP) {
  @Test fun readsPositional() {
    backP("-N mike --count 233 -I ArgParserTest.kt -I ParserTest.kt -mode fast fg hs").run {
      assertEquals(2, tup.e3.size)
      assertEquals("fast", tup.e4.get())
    }
    backP("-count 233 -mode quite sd bd").run {
      assertEquals("Duckling", tup.e1.get())
      assertEquals(listOf("sd", "bd"), items)
    }
    assertFailMessage("parse fail near -N (#2, arg 2): reading [a]: should all-after options", "a -N make")
    assertFailMessage("parse fail near -c (#4, arg 3): reading [k]: should all-after options", "-N make k -c 88 c d")
    assertFailMessage("too less items (all [source, dest])", "-c 233 sf")
    assertFailMessage("parse fail near 996 (#5, arg 4): too many items (all [source, dest])", "--name doge 233 666 996")
    p("-h --help")
  }
  @Test fun format() {
    assertEquals("""
      Usage: (-name -N name) [-count -c count] {-I file} (-mode mode) [-h -help] <source> <dest>
        -name -N: name of the user (default Duckling)
        -count -c: number of widgets
        -I: directory to search for header files
        -mode: mode of operation in fast, small, quite (default small)
        -h -help: print this help

    """.trimIndent(), yourP.toString())
  }
}

fun argFileD(name: String, help: String, param: String = "path", default_value: File = noFile, repeatable: Boolean = false)
  = argFile(name, help, param, default_value, repeatable, "")

object AWKArgParser: ArgParser4<File, String, String, String>(
  argFile("file= f exec= E", "execute script file", "path", flags = ""),
  arg("field-separator= F", "set field separator", "fs", " \t"),
  arg("assign= v", "assign variable", "var=val", repeatable = true),
  arg("load= l", "load library", "lib", repeatable = true),
  flags = *defineFlags(
    "characters-as-bytes" to "b",
    "traditional" to "c",
    "copyright" to "C",
    "gen-pot" to "g",
    "bignum" to "M",
    "use-lc-numeric" to "N",
    "non-decimal-data" to "n",
    "optimize" to "O",
    "posix" to "P",
    "re-interval" to "r",
    "no-optimize" to "s",
    "sandbox" to "S",
    "lint-old" to "t"
  ) +arrayOf(helpArg, arg("version V", "print version") { println("GNU Awk 5.0.1, API: 3.0"); SwitchParser.stop() }),
  autoSplit = "F E v d D L l o p".split(),
  itemNames = listOf("..."), itemMode = PositionalMode.MustAfter,
  moreArgs = listOf(
    argFileD("dump-variables= d", "dump vars to file", "file"),
    argFileD("debug= D", "debug", "file"),
    arg("source= e", "execute source", "code", "emm"),
    argFileD("include= i", "include file", "file"),
    arg("lint= L", "lint level", "", "none").checkOptions("fatal", "invalid", "no-ext"),
    argFileD("pretty-print= o", "pretty print to", "file"),
    argFileD("profile= p", "use profile", "file")
  )
) {

  override fun checkAutoSplitForName(name: String, param: String)
    = if (name.length == 1 && name[0] !in "vfdDlLop") throw SwitchParser.ParseError("auto-split $param used in shorthands") else Unit

  override fun checkResult(result: ParseResult<File, String, String, String>) {
    if (result.tup.e1.get() == noFile && result.items.isEmpty()) throw SwitchParser.ParseError("no executable provided")
  }
}

class AWKArgParserTests: BaseArgParserTest<File, String, String, String>(AWKArgParser) {
  @Test fun fourCaseForMustAfter() {
    assertFailMessage("no executable provided", "-g")
    assertFailMessage("parse fail near -g (#2, arg 2): reading [a.awk]: should all-after options", "a.awk -g")
    assertFailMessage("parse fail near -g (#2, arg 2): reading [x.awk]: should all-after options", "x.awk -g a.awk")
    backP("a.awk")
  }
  @Test fun itWorks() {
    backP("-field-separator=: print a.awk").run {
      assertEquals(":", tup.e2.get()) } // if f=noFile, items[0] = code.
    p("-p233 -lint=no-ext --pretty-print=f --posix -S a.awk").run {
      val named = this.named!!
      assertEquals("emm", named["source="])
      assertEquals("233", named.getAs<File>("profile=").name)
      assertEquals("no-ext", named.getAs<String>("lint="))
      assertEquals("PS", flags)
    }
    backP("-la -lb -va=2 -vb=2 -fa")
    assertFailMessage("parse fail near -F: (#1, arg 1): auto-split : used in shorthands", "-F:")
  }
  @Test fun format() {
    assertEquals("""
    Usage: (-file= -f -exec= -E path) (-field-separator= -F fs) {-assign= -v var=val}
            {-load= -l lib} [-characters-as-bytes -b] [-traditional -c] [-copyright -C]
            [-gen-pot -g] [-bignum -M] [-use-lc-numeric -N] [-non-decimal-data -n]
            [-optimize -O] [-posix -P] [-re-interval -r] [-no-optimize -s]
            [-sandbox -S] [-lint-old -t] [-h -help] [-version -V] (-dump-variables= -d file)
            (-debug= -D file) (-source= -e code) (-include= -i file) (-lint= -L lint=)
            (-pretty-print= -o file) (-profile= -p file) <...>
      -file= -f -exec= -E: execute script file (default none)
      -field-separator= -F: set field separator (default  	)
      -assign= -v: assign variable
      -load= -l: load library
      -characters-as-bytes -b: characters as bytes
      -traditional -c: traditional
      -copyright -C: copyright
      -gen-pot -g: gen pot
      -bignum -M: bignum
      -use-lc-numeric -N: use lc numeric
      -non-decimal-data -n: non decimal data
      -optimize -O: optimize
      -posix -P: posix
      -re-interval -r: re interval
      -no-optimize -s: no optimize
      -sandbox -S: sandbox
      -lint-old -t: lint old
      -h -help: print this help
      -version -V: print version
      -dump-variables= -d: dump vars to file (default none)
      -debug= -D: debug (default none)
      -source= -e: execute source (default emm)
      -include= -i: include file (default none)
      -lint= -L: lint level in fatal, invalid, no-ext (default none)
      -pretty-print= -o: pretty print to (default none)
      -profile= -p: use profile (default none)

    """.trimIndent(), p.toString())
  }
}
