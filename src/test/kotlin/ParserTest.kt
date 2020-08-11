import org.duangsuse.parserkt.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ParserTest {
  val letter = elementIn('a'..'z', 'A'..'Z', *singleCharRanges('$','_'))
  val digit = elementIn('0'..'9').convert { it-'0' }
  val word = Repeat(::asString, letter)
  val number = Repeat({concatAsNumber()}, digit)
  @Test fun classifier() {
    val b23 = "bili 233"
    assertEquals("bili", word(b23))
    assertEquals(2, number("2.33bit)"))
    val rb23 = Seq(word, item(' '), number) { e1 to e3 }(b23)!!
    assertEquals("bili" to 233L, rb23)
    assertEquals("", word.toDefault("")("123"))
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
  @Test fun pipedP() {
    val p1 = Piped(number) { it+1+sourceLoc.line }
    assertEquals(125, p1("123"))
    val p2 = Piped.concat(word, number, { it }, { first.map { it+second.toInt() } })
    assertEquals("bcd".toList(), p2("abc1"))
    val p3 = Piped.concat(Repeat({concatAsNumber()}, digit, at_most = 4), Seq(itemLowCase('x'), number) {e2}) { first*second }
    assertEquals(1234L, p3("12345"))
    assertEquals(0L, p3("322X0"))
    assertEquals(66660L, p3("3333x20 "))
  }
  @Test fun pipedDecideGreedy() {
  }
  private inline fun <T: Any> assertParses(p: Parser<T>, inputs: Iterable<String>) = inputs.forEach { assertNotNull(p(it.input)) }
  private fun rec(it: Input): String? = Decide(item('.').convert(Char::toString), Seq(item('a'), ::rec) { e1.plus(e2) })(it)
}
