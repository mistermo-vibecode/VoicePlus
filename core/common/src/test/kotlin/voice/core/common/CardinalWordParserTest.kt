package voice.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardinalWordParserTest {

  @Test fun `parse simple words 1 to 20`() {
    assertEquals(1, CardinalWordParser.parse("one"))
    assertEquals(5, CardinalWordParser.parse("five"))
    assertEquals(12, CardinalWordParser.parse("twelve"))
    assertEquals(20, CardinalWordParser.parse("twenty"))
  }

  @Test fun `parse tens`() {
    assertEquals(30, CardinalWordParser.parse("thirty"))
    assertEquals(50, CardinalWordParser.parse("fifty"))
    assertEquals(90, CardinalWordParser.parse("ninety"))
  }

  @Test fun `parse hyphenated compounds`() {
    assertEquals(21, CardinalWordParser.parse("twenty-one"))
    assertEquals(35, CardinalWordParser.parse("thirty-five"))
    assertEquals(99, CardinalWordParser.parse("ninety-nine"))
  }

  @Test fun `parse one hundred`() {
    assertEquals(100, CardinalWordParser.parse("one hundred"))
  }

  @Test fun `parse is case-insensitive`() {
    assertEquals(3, CardinalWordParser.parse("Three"))
    assertEquals(22, CardinalWordParser.parse("Twenty-Two"))
  }

  @Test fun `returns null for unrecognised word`() {
    assertNull(CardinalWordParser.parse("prologue"))
    assertNull(CardinalWordParser.parse(""))
    assertNull(CardinalWordParser.parse("one-hundred-and-one"))
  }

  @Test fun `toWord 1 to 20`() {
    assertEquals("one", CardinalWordParser.toWord(1))
    assertEquals("twelve", CardinalWordParser.toWord(12))
    assertEquals("twenty", CardinalWordParser.toWord(20))
  }

  @Test fun `toWord tens`() {
    assertEquals("thirty", CardinalWordParser.toWord(30))
    assertEquals("ninety", CardinalWordParser.toWord(90))
  }

  @Test fun `toWord compounds`() {
    assertEquals("twenty-one", CardinalWordParser.toWord(21))
    assertEquals("thirty-five", CardinalWordParser.toWord(35))
    assertEquals("ninety-nine", CardinalWordParser.toWord(99))
  }

  @Test fun `toWord one hundred`() {
    assertEquals("one hundred", CardinalWordParser.toWord(100))
  }

  @Test fun `toWord returns null out of range`() {
    assertNull(CardinalWordParser.toWord(0))
    assertNull(CardinalWordParser.toWord(-1))
    assertNull(CardinalWordParser.toWord(101))
  }
}
