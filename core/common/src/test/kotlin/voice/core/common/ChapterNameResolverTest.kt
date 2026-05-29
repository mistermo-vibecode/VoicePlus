package voice.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterNameResolverTest {

  @Test fun `override takes precedence over everything`() {
    assertEquals("Intro", resolveChapterName("Chapter 12", offset = 5, override = "Intro"))
  }

  @Test fun `zero offset returns raw name unchanged`() {
    assertEquals("Chapter 12", resolveChapterName("Chapter 12", offset = 0, override = null))
  }

  @Test fun `digit offset positive`() {
    assertEquals("Chapter 14", resolveChapterName("Chapter 12", offset = 2, override = null))
  }

  @Test fun `digit offset negative`() {
    assertEquals("Chapter 10", resolveChapterName("Chapter 12", offset = -2, override = null))
  }

  @Test fun `digit result clamped to 1`() {
    assertEquals("Chapter 1", resolveChapterName("Chapter 1", offset = -5, override = null))
  }

  @Test fun `zero-padded digits lose padding after offset`() {
    assertEquals("Chapter 2", resolveChapterName("Chapter 01", offset = 1, override = null))
  }

  @Test fun `digit in suffix is matched`() {
    assertEquals("Part 3: The Valley", resolveChapterName("Part 2: The Valley", offset = 1, override = null))
  }

  @Test fun `word offset positive`() {
    assertEquals("Chapter Fourteen", resolveChapterName("Chapter Twelve", offset = 2, override = null))
  }

  @Test fun `word offset negative`() {
    assertEquals("Chapter Ten", resolveChapterName("Chapter Twelve", offset = -2, override = null))
  }

  @Test fun `word result clamped to one`() {
    assertEquals("Chapter One", resolveChapterName("Chapter Two", offset = -5, override = null))
  }

  @Test fun `word preserves original capitalisation`() {
    assertEquals("Chapter Twenty-Three", resolveChapterName("Chapter Twenty-One", offset = 2, override = null))
  }

  @Test fun `lowercase word stays lowercase`() {
    assertEquals("chapter ten", resolveChapterName("chapter twelve", offset = -2, override = null))
  }

  @Test fun `compound word offset`() {
    assertEquals("Chapter Twenty-Three", resolveChapterName("Chapter Twenty-One", offset = 2, override = null))
  }

  @Test fun `pure named chapter is no-op`() {
    assertEquals("Prologue", resolveChapterName("Prologue", offset = 3, override = null))
    assertEquals("Epilogue", resolveChapterName("Epilogue", offset = -1, override = null))
  }

  @Test fun `bare number offset`() {
    assertEquals("5", resolveChapterName("3", offset = 2, override = null))
  }

  @Test fun `one hundred via two-token match`() {
    assertEquals("Chapter Ninety-Eight", resolveChapterName("Chapter One Hundred", offset = -2, override = null))
  }
}
