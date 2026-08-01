package voice.core.data

import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
public data class MarkData(
  val startMs: Long,
  val name: String,
) : Comparable<MarkData> {
  override fun compareTo(other: MarkData): Int {
    return startMs.compareTo(other.startMs)
  }
}

@Serializable
public data class ChapterMark(
  val name: String?,
  val startMs: Long,
  val endMs: Long,
) {

  init {
    require(startMs < endMs) {
      "Start must be less than end in $this"
    }
  }

  public operator fun contains(position: Duration): Boolean = position.inWholeMilliseconds in startMs..endMs
  public operator fun contains(positionMs: Long): Boolean = positionMs in startMs..endMs
}

public val ChapterMark.durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)

public fun Chapter.markForPosition(positionInChapterMs: Long): ChapterMark {
  return chapterMarks.find { positionInChapterMs in it.startMs..it.endMs }
    ?: chapterMarks.firstOrNull { positionInChapterMs == it.endMs }
    // Past the last mark's end (e.g. a session that ran to the very end of the file): the last
    // mark at-or-before the position, not the first — every surface (player, bookmarks, log)
    // should attribute such a position to the chapter it is actually in.
    ?: chapterMarks.lastOrNull { positionInChapterMs >= it.startMs }
    ?: chapterMarks.first()
}
