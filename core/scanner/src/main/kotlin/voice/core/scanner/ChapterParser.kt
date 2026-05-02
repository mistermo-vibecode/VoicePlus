package voice.core.scanner

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.isAudioFile
import voice.core.data.repo.ChapterRepo
import voice.core.data.repo.getOrPut
import voice.core.data.store.IgnoreFileTagsStore
import voice.core.documentfile.CachedDocumentFile
import java.time.Instant

@Inject
internal class ChapterParser(
  private val chapterRepo: ChapterRepo,
  private val mediaAnalyzer: MediaAnalyzer,
  @IgnoreFileTagsStore
  private val ignoreFileTagsStore: DataStore<Boolean>,
) {

  // Use the filename when the title tag is identical to the album tag — this means
  // the TIT2 tag contains the book title rather than a unique chapter title, which
  // is common for multi-file audiobooks (e.g. "01 - BookTitle.mp3" where every
  // file's TIT2 = album = "BookTitle"). The filename always contains the track number.
  private fun chapterName(metaData: Metadata, ignoreFileTags: Boolean): String {
    if (ignoreFileTags) return metaData.fileName
    val title = metaData.title
    return if (title != null && title != metaData.album) title else metaData.fileName
  }

  suspend fun parse(documentFile: CachedDocumentFile): List<Chapter> {
    val result = mutableListOf<Chapter>()
    val ignoreFileTags = ignoreFileTagsStore.data.first()

    suspend fun parseChapters(file: CachedDocumentFile) {
      if (file.isAudioFile()) {
        val id = ChapterId(file.uri)
        val chapter = chapterRepo.getOrPut(id, Instant.ofEpochMilli(file.lastModified)) {
          val metaData = mediaAnalyzer.analyze(file) ?: return@getOrPut null
          Chapter(
            id = id,
            duration = metaData.duration,
            fileLastModified = Instant.ofEpochMilli(file.lastModified),
            name = chapterName(metaData, ignoreFileTags),
            markData = metaData.chapters,
          )
        }
        if (chapter != null) {
          result.add(chapter)
        }
      } else if (file.isDirectory) {
        file.children
          .forEach {
            parseChapters(it)
          }
      }
    }

    parseChapters(file = documentFile)
    return result.sorted()
  }
}
