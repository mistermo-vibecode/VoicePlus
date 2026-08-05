package voice.core.scanner

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.repo.ChapterRepoImpl
import voice.core.data.repo.internals.dao.ChapterDao
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.FileBasedDocumentFile
import voice.core.documentfile.nameWithoutExtension
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ChapterParserTest {

  private val testFolder = TemporaryFolder()

  @Rule
  fun testFolder() = testFolder

  @Before
  fun setUp() {
    testFolder.create()
  }

  @Test
  fun parserSorts() = runTest {
    val audiobook = testFolder.newFolder("audiobook")
    testFolder.newFile("audiobook/Chapter 1.mp3")
    testFolder.newFile("audiobook/Chapter 2.mp3")
    testFolder.newFile("audiobook/Chapter 20.mp3")
    testFolder.newFile("audiobook/Chapter 3.mp3")
    testFolder.newFile("audiobook/Chapter 30.mp3")

    val chapterParser = ChapterParser(
      chapterRepo = ChapterRepoImpl(
        mockk {
          coEvery {
            chapter(any())
          } returns null
          coEvery {
            insert(any())
          } just Runs
        },
      ),
      mediaAnalyzer = mockk {
        coEvery {
          analyze(any())
        } answers {
          val file = firstArg<CachedDocumentFile>()
          Metadata(
            duration = 1000,
            fileName = file.nameWithoutExtension(),
            artist = null,
            album = null,
            chapters = emptyList(),
            title = null,
            genre = null,
            narrator = null,
            series = null,
            part = null,
          )
        }
      },
      ignoreFileTagsStore = mockk { every { data } returns MutableStateFlow(false) },
    )
    chapterParser.parse(FileBasedDocumentFile(audiobook))
      .chapters
      .map { it.name }
      .shouldContainExactly(
        "Chapter 1",
        "Chapter 2",
        "Chapter 3",
        "Chapter 20",
        "Chapter 30",
      )
  }

  @Test
  fun sameTimestampAndSizeUsesCachedChapter() = runTest {
    val audioFile = testFolder.newFile("cached.mp3").apply {
      writeBytes(byteArrayOf(1, 2, 3, 4))
    }
    val (chapterParser, mediaAnalyzer) = parserFixture()

    chapterParser.parse(FileBasedDocumentFile(audioFile)).let { }
    chapterParser.parse(FileBasedDocumentFile(audioFile)).let { }

    coVerify(exactly = 1) { mediaAnalyzer.analyze(any()).let { } }
  }

  @Test
  fun changedSizeReparsesWhenTimestampIsUnchanged() = runTest {
    val audioFile = testFolder.newFile("changed-size.mp3").apply {
      writeBytes(byteArrayOf(1, 2, 3, 4))
    }
    val originalLength = audioFile.length()
    val originalLastModified = audioFile.lastModified()
    val (chapterParser, mediaAnalyzer) = parserFixture()

    chapterParser.parse(FileBasedDocumentFile(audioFile)).let { }

    audioFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6))
    assertTrue(audioFile.length() != originalLength)
    assertTrue(audioFile.setLastModified(originalLastModified))
    val restoredLastModified = audioFile.lastModified()
    assertEquals(originalLastModified, restoredLastModified)

    chapterParser.parse(FileBasedDocumentFile(audioFile)).let { }

    coVerify(exactly = 2) { mediaAnalyzer.analyze(any()).let { } }
  }

  @Test
  fun legacyZeroFileSizeRowReparsesOnce() = runTest {
    val audioFile = testFolder.newFile("legacy.mp3").apply {
      writeBytes(byteArrayOf(1, 2, 3, 4))
    }
    val documentFile = FileBasedDocumentFile(audioFile)
    val legacyChapter = Chapter(
      id = ChapterId(documentFile.uri),
      name = "legacy",
      duration = 1000L,
      fileLastModified = Instant.ofEpochMilli(audioFile.lastModified()),
      markData = emptyList(),
      fileSize = 0L,
    )
    val (chapterParser, mediaAnalyzer) = parserFixture(legacyChapter)

    chapterParser.parse(documentFile).let { }
    chapterParser.parse(documentFile).let { }

    coVerify(exactly = 1) { mediaAnalyzer.analyze(any()).let { } }
  }

  private fun parserFixture(initialChapter: Chapter? = null): Pair<ChapterParser, MediaAnalyzer> {
    val mediaAnalyzer = mockk<MediaAnalyzer>()
    coEvery { mediaAnalyzer.analyze(any()) } answers {
      val file = firstArg<CachedDocumentFile>()
      Metadata(
        duration = 1000L,
        fileName = file.nameWithoutExtension(),
        artist = null,
        album = null,
        chapters = emptyList(),
        title = null,
        genre = null,
        narrator = null,
        series = null,
        part = null,
      )
    }
    val chapterDao = mockk<ChapterDao> {
      coEvery { chapter(any()) } returns initialChapter
      coEvery { insert(any()) } just Runs
    }
    return ChapterParser(
      chapterRepo = ChapterRepoImpl(chapterDao),
      mediaAnalyzer = mediaAnalyzer,
      ignoreFileTagsStore = mockk { every { data } returns MutableStateFlow(false) },
    ) to mediaAnalyzer
  }
}
