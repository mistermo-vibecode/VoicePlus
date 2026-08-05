package voice.core.scanner.mp4

import android.net.Uri
import androidx.media3.datasource.ByteArrayDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import voice.core.data.MarkData

@RunWith(AndroidJUnit4::class)
internal class ChapterTrackProcessorTest {

  @Test
  fun `one stts entry can span several chunks`() {
    val chapters = invoke(
      names = listOf("one", "two", "three"),
      samplesPerChunk = listOf(2, 3, 1),
      durations = listOf(SttsEntry(sampleCount = 6, sampleDuration = 100)),
    )

    chapters shouldBe listOf(
      MarkData(startMs = 0, name = "one"),
      MarkData(startMs = 200, name = "two"),
      MarkData(startMs = 500, name = "three"),
    )
  }

  @Test
  fun `chunk duration can cross stts entries`() {
    val chapters = invoke(
      names = listOf("one", "two", "three"),
      samplesPerChunk = listOf(2, 3, 1),
      durations = listOf(
        SttsEntry(sampleCount = 3, sampleDuration = 100),
        SttsEntry(sampleCount = 3, sampleDuration = 200),
      ),
    )

    chapters shouldBe listOf(
      MarkData(startMs = 0, name = "one"),
      MarkData(startMs = 200, name = "two"),
      MarkData(startMs = 700, name = "three"),
    )
  }

  @Test
  fun `insufficient duration data still returns chapters`() {
    val chapters = invoke(
      names = listOf("one", "two", "three"),
      samplesPerChunk = listOf(2, 2, 2),
      durations = listOf(SttsEntry(sampleCount = 3, sampleDuration = 100)),
    )

    chapters shouldBe listOf(
      MarkData(startMs = 0, name = "one"),
      MarkData(startMs = 200, name = "two"),
      MarkData(startMs = 300, name = "three"),
    )
  }

  @Test
  fun `zero-count stts entry is skipped`() {
    val chapters = invoke(
      names = listOf("one", "two"),
      samplesPerChunk = listOf(1, 1),
      durations = listOf(
        SttsEntry(sampleCount = 0, sampleDuration = 100),
        SttsEntry(sampleCount = 2, sampleDuration = 100),
      ),
    )

    chapters shouldBe listOf(
      MarkData(startMs = 0, name = "one"),
      MarkData(startMs = 100, name = "two"),
    )
  }

  @Test
  fun `huge chunks consume duration runs without per sample work`() {
    val chapters = invoke(
      names = listOf("one", "two"),
      samplesPerChunk = listOf(Int.MAX_VALUE, 1),
      durations = listOf(
        SttsEntry(sampleCount = 3_000_000_000L, sampleDuration = 4_294_967_295L),
      ),
      timeScale = 4_294_967_295L,
    )

    chapters shouldBe listOf(
      MarkData(startMs = 0, name = "one"),
      MarkData(startMs = 2_147_483_647_000L, name = "two"),
    )
  }

  private fun invoke(
    names: List<String>,
    samplesPerChunk: List<Int>,
    durations: List<SttsEntry>,
    timeScale: Long = 1000L,
  ): List<MarkData> {
    val encodedNames = names.map { it.toByteArray(Charsets.UTF_8) }
    val chunkOffsets = mutableListOf<Long>()
    val data = ByteArray(encodedNames.sumOf { it.size + 2 })
    var offset = 0
    encodedNames.forEach { nameBytes ->
      chunkOffsets.add(offset.toLong())
      data[offset] = (nameBytes.size ushr 8).toByte()
      data[offset + 1] = nameBytes.size.toByte()
      nameBytes.copyInto(data, destinationOffset = offset + 2)
      offset += nameBytes.size + 2
    }

    val stscEntries = samplesPerChunk.mapIndexed { index, samples ->
      StscEntry(firstChunk = index + 1L, samplesPerChunk = samples)
    }
    val output = Mp4ChpaterExtractorOutput(
      chunkOffsets = mutableListOf(chunkOffsets),
      durations = mutableListOf(durations),
      stscEntries = mutableListOf(stscEntries),
      timeScales = mutableListOf(timeScale),
    )

    return ChapterTrackProcessor()(
      uri = Uri.EMPTY,
      dataSource = ByteArrayDataSource(data),
      trackId = 1,
      output = output,
    )
  }
}
