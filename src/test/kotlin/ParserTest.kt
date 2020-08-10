import org.duangsuse.parserkt.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ParserTest {
  @Test fun classifier() {
    val letter = elementIn('a'..'z', 'A'..'Z', *singleCharRanges('$','_'))
    val digit = elementIn('0'..'9')
    val word = Repeat(::asString, letter)
    val number = Repeat({concatAsNumber()}, digit.convert { it-'0' })
    val b23 = "bili 233"
    assertEquals("bili", word(b23.input))
    assertEquals(2, number("2.33bit)".input))
    val rb23 = Seq(word, item(' '), number) { e1 to e3 }(b23.input)!!
    assertEquals("bili" to 233L, rb23)
  }
  @Test fun recursive() {
    assertEquals("aaa.", rec("aaa.".input))
    assertEquals(notPas, rec("xx".input))
  }
  @Test fun stringP() {
    val str = Decide(string("ab"), string("ac"), string("ad"), Seq(item('a'), elementIn('0'..'9')) { "$e1$e2" })
    assertParses(str, "ab ac ad a2 a4".split(" "))
    val text = "ac ab a7 ad ".input
    assertEquals("acaba7ad", Repeat(::concatAsString, Seq(str, item(' ')) {e1})(text))
  }
  private inline fun <T: Any> assertParses(p: Parser<T>, inputs: Iterable<String>) = inputs.forEach { assertNotNull(p(it.input)) }
  private fun rec(it: Input): String? = Decide(item('.').convert(Char::toString), Seq(item('a'), ::rec) { e1.plus(e2) })(it)
  private val String.input get() = StringInput(this)
}