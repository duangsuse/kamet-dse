import org.duangsuse.kamet.CascadeMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CascadeMapTest {
  @Test fun cascadeGet() {
    val m = CascadeMap(mutableMapOf("a" to 1, "b" to 2))
    assertEquals(1, m["a"])
    val m1 = m.subMap()
    assertEquals(1 to 2, m1["a"] to m1["b"])
    m1["a"] = 233
    assertEquals(233, m1["a"])
    assertEquals(1, m["a"])
  }
  @Test fun cascadeContains() {
    val m = CascadeMap(mutableMapOf('x' to true))
    val m1 = m.subMap()
    assertTrue('x' in m1 && 'x' in m)
  }
}