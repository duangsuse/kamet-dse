import org.duangsuse.parserkt.*
import org.junit.Test
import kotlin.test.assertEquals

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
  private val String.input get() = StringInput(this)
}