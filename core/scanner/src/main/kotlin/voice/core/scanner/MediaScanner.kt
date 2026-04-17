package voice.core.scanner

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import voice.core.data.BookId
import voice.core.data.audioFileCount
import voice.core.data.folders.FolderType
import voice.core.data.isAudioFile
import voice.core.data.repo.BookContentRepo
import voice.core.data.store.ExcludedBooksStore
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.walk
import voice.core.logging.api.Logger

@Inject
internal class MediaScanner(
  private val contentRepo: BookContentRepo,
  private val chapterParser: ChapterParser,
  private val bookParser: BookParser,
  private val deviceHasPermissionBug: DeviceHasStoragePermissionBug,
  @ExcludedBooksStore
  private val excludedBooksStore: DataStore<Set<String>>,
) {

  suspend fun scan(folders: Map<FolderType, List<CachedDocumentFile>>) {
    val excludedIds = excludedBooksStore.data.first()
    val files = folders.flatMap { (folderType, files) ->
      when (folderType) {
        FolderType.SingleFile, FolderType.SingleFolder -> {
          files
        }
        FolderType.Root -> {
          files.flatMap { file ->
            file.children
          }
        }
        FolderType.Author -> {
          files.flatMap { folder ->
            folder.children.flatMap { author ->
              if (author.isFile) {
                listOf(author)
              } else {
                author.children.flatMap {
                  author.children
                }
              }
            }
          }
        }
      }
    }

    // Excluded books are kept inactive even when their files are found
    val activeIds = files.map { BookId(it.uri) }.filter { it.value !in excludedIds }
    contentRepo.setAllInactiveExcept(activeIds)

    val probeFile = folders.values.flatten().findProbeFile()
    if (probeFile != null) {
      if (deviceHasPermissionBug.checkForBugAndSet(probeFile)) {
        Logger.w("Device has permission bug, aborting scan! Probed $probeFile")
        return
      }
    }

    files
      .sortedBy { it.audioFileCount() }
      .forEach { file ->
        scan(file)
      }
  }

  private fun List<CachedDocumentFile>.findProbeFile(): CachedDocumentFile? {
    return asSequence().flatMap { it.walk() }
      .firstOrNull { child ->
        child.isAudioFile() && child.uri.authority == "com.android.externalstorage.documents"
      }
  }

  private suspend fun scan(file: CachedDocumentFile) {
    val excludedIds = excludedBooksStore.data.first()
    if (BookId(file.uri).value in excludedIds) return

    val chapters = chapterParser.parse(file)
    if (chapters.isEmpty()) return

    val content = bookParser.parseAndStore(chapters, file)

    val chapterIds = chapters.map { it.id }
    val currentChapterGone = content.currentChapter !in chapterIds
    val currentChapter = if (currentChapterGone) chapterIds.first() else content.currentChapter
    val positionInChapter = if (currentChapterGone) 0 else content.positionInChapter
    val updated = content.copy(
      chapters = chapterIds,
      currentChapter = currentChapter,
      positionInChapter = positionInChapter,
      isActive = true,
    )
    if (content != updated) {
      validateIntegrity(updated, chapters)
      contentRepo.put(updated)
    }
  }
}
