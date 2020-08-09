import org.duangsuse.parserkt.*
import kotlin.test.*

class FeedTest {
  fun itWorks(a: Input) {
    assertEquals("ni2", a.peekMany(3))
    assertEquals("ni", a.takeWhile { !it.isDigit() })
    assertEquals(a.peek..a.consume(), '2'..'2')
    assertFalse(a.isEnd)
    assertEquals('3', a.consume())
    assertTrue(a.isEnd); assertFailsWith<Feed.End> { a.consume() }
    assertEquals("", a.peekMany(3))
    assertEquals(4, a.sourceLoc.column)
  }
  @Test fun iterator() = itWorks(IteratorInput("ni23".iterator()))
  @Test fun string() = itWorks(StringInput("ni23"))
}