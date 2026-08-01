package voice.core.data.store.snapshot

import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.room.RoomDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import voice.core.data.BookCharacter
import voice.core.data.BookContent
import voice.core.data.Chapter
import voice.core.data.ChapterId
import voice.core.data.ListeningEvent
import voice.core.data.ListeningSession
import voice.core.data.MediaScanWaiter
import voice.core.data.repo.BookContentRepo
import voice.core.data.repo.internals.dao.BookCharacterDao
import voice.core.data.repo.internals.dao.BookContentDao
import voice.core.data.repo.internals.dao.BookmarkDao
import voice.core.data.repo.internals.dao.ChapterDao
import voice.core.data.repo.internals.dao.ChapterNameOverrideDao
import voice.core.data.repo.internals.dao.ListeningSessionDao
import voice.core.data.repo.internals.transaction
import voice.core.data.store.ExcludedBooksStore
import voice.core.data.store.snapshot.identity.DeviceRelativePath
import voice.core.data.store.snapshot.identity.IdentityStampBuilder
import voice.core.data.store.snapshot.rekey.BookIdentityStamp
import voice.core.data.store.snapshot.rekey.ChildEntry
import voice.core.data.store.snapshot.rekey.ReKeyResult
import voice.core.data.store.snapshot.rekey.RestoreReKeyer
import voice.core.data.store.snapshot.rekey.ScannedBook
import voice.core.data.store.snapshot.rekey.ScannedChapter
import voice.core.data.store.snapshot.rekey.SnapChapter
import voice.core.data.store.snapshot.rekey.SnapshotBook
import voice.core.logging.api.Logger

/**
 * Restores an external backup bundle after an OS-level wipe (uninstall/reinstall → SAF re-grant changes every
 * `content://` URI). Unlike the on-device [BackupRestorer] (same URIs, stored-id), this path must:
 *  1. trigger a scan and wait for it to finish, so the library exists under the NEW post-re-grant URIs and
 *     the scan's active-book reconcile has run;
 *  2. re-key the snapshot (keyed to dead URIs) onto those freshly-scanned books by volume-namespaced relPath,
 *     via the strict, pure [RestoreReKeyer];
 *  3. write the matched, NEW-id-keyed rows additively, never clobbering a fresher live position.
 *
 * Books that can't be safely matched are returned in [ReKeyResult.unmatched] for the UI to surface — never
 * silently dropped, never guessed onto a neighbouring book.
 */
@SingleIn(AppScope::class)
@Inject
internal class OsWipeRestorer(
  private val scanWaiter: MediaScanWaiter,
  private val contentRepo: BookContentRepo,
  private val bookContentDao: BookContentDao,
  private val chapterDao: ChapterDao,
  private val bookmarkDao: BookmarkDao,
  private val bookCharacterDao: BookCharacterDao,
  private val chapterNameOverrideDao: ChapterNameOverrideDao,
  private val listeningSessionDao: ListeningSessionDao,
  @ExcludedBooksStore private val excludedBooksStore: DataStore<Set<String>>,
  private val appDb: RoomDatabase,
  private val restoreGate: RestoreGate,
) {

  @IgnorableReturnValue
  suspend fun run(snapshot: LibrarySnapshot): ReKeyResult {
    val outcome = doRestore(snapshot)
    // A clean, complete restore: flush the re-keyed state to the ring + external bundle now (the gate has
    // cleared) rather than waiting on the debounce. On a PARTIAL restore (some books surfaced as unmatched)
    // we deliberately do NOT flush — the external bundle still holds those books' data for a re-grant-and-retry,
    // and an export would overwrite it.
    if (outcome.matched.isNotEmpty() && outcome.unmatched.isEmpty()) {
      restoreGate.requestFlush()
    }
    return outcome
  }

  private suspend fun doRestore(snapshot: LibrarySnapshot): ReKeyResult = restoreGate.withRestoreActive {
    // 1. Make the freshly-scanned, new-URI books exist AND the scan's setAllInactiveExcept reconcile complete
    // before we read them. scanAndAwait joins the actual scan job (not the racy scannerActive flag).
    scanWaiter.scanAndAwait(restartIfScanning = true)

    // 2. Scanned side: build a stamp + chapter anchors for each freshly-scanned active book.
    val liveBooks = bookContentDao.all()
    val chapterById = chapterDao.all().associateBy { it.id.value }
    val scannedBooks = liveBooks.filter { it.isActive }.map { book ->
      toScannedBook(book, book.chapters.mapNotNull { chapterById[it.value] })
    }

    // 3. Snapshot side: filter user-deleted books, then attach each book's stamp + chapters + user data.
    val excludedIds = excludedBooksStore.data.first()
    val snapChapterById = snapshot.chapters.associateBy { it.id }
    val bookmarksByBook = snapshot.bookmarks.groupBy { it.bookId }
    val overridesByBook = snapshot.chapterNameOverrides.groupBy { it.bookId }
    val sessionsByBook = snapshot.sessions.groupBy { it.bookId }
    val charactersByBook = snapshot.characters.groupBy { it.bookId }
    val snapshotBooks = snapshot.books
      // Hidden books DO participate: the scan has already put their files back on disk under new
      // ids, so skipping them would leave them visible and dataless. They are re-keyed with their
      // data and their NEW ids are added to the hidden set below, so they stay hidden.
      .filter { it.id !in excludedIds || it.id in snapshot.hiddenBooks }
      .map { dto ->
        toSnapshotBook(dto, snapChapterById, bookmarksByBook, overridesByBook, sessionsByBook, charactersByBook)
      }

    // 4. Pure, deterministic, never-cross-attach re-key.
    val result = RestoreReKeyer.reKey(snapshotBooks, scannedBooks)

    // 5. Persist the matched books, keyed entirely to the new ids. Atomic; additive; freshness-aware.
    val liveById = liveBooks.associateBy { it.id.value }
    appDb.transaction {
      // Seed the natural-key dedup sets INSIDE the transaction so a concurrent caller can't make us
      // double-insert the autoGenerate-PK rows (sessions / characters).
      val seenSessionKeys = listeningSessionDao.all().mapTo(mutableSetOf()) { it.naturalKey() }
      val seenCharacterKeys = bookCharacterDao.all().mapTo(mutableSetOf()) { it.naturalKey() }
      result.matched.forEach { matched ->
        val live = liveById[matched.content.id.value]
        // Never overwrite a position the user has since advanced past the snapshot; just re-activate and
        // additively re-apply the user-authored data below.
        val content = if (live != null && live.lastPlayedAt > matched.sourceLastPlayedAt) {
          live.copy(isActive = true)
        } else {
          matched.content
        }
        bookContentDao.insert(content)
        // chapters2 rows already exist from the scan (matched.content.chapters are the scanned ids), so
        // BookRepository.book() resolves without re-inserting anything.
        matched.bookmarks.forEach { bookmarkDao.addBookmark(it) }
        matched.overrides.forEach { chapterNameOverrideDao.insert(it) }
        matched.sessions.forEach { session ->
          // ListeningSession has an autoGenerate PK; dedup on a natural key so a re-run can't double-count,
          // and insert with a fresh id so a snapshot-generation id can't collide with a live row.
          if (seenSessionKeys.add(session.naturalKey())) listeningSessionDao.insert(session.copy(id = 0))
        }
        matched.characters.forEach { character ->
          // BookCharacter also has an autoGenerate PK; same natural-key dedup + fresh id.
          if (seenCharacterKeys.add(character.naturalKey())) bookCharacterDao.insert(character.copy(id = 0))
        }
        // Listening EVENTS are deliberately not re-keyed: they are seek/skip decorations on the log, and
        // carrying them across dead-URI chapter ids isn't worth the mapping surface. The same-device
        // direct restore path (BackupRestorer.applyDirect) does restore them.
      }
    }

    // Translate the hidden set onto the new ids: the snapshot's hidden ids died with the wipe, and
    // without this the freshly-scanned copies of hidden books would resurface visible.
    val hiddenNewIds = result.matched
      .filter { it.sourceId in snapshot.hiddenBooks }
      .map { it.content.id.value }
    if (hiddenNewIds.isNotEmpty()) {
      excludedBooksStore.updateData { it + hiddenNewIds }
    }

    // 6. Refresh the fill-once content cache so the restored books render this session, not after a restart.
    contentRepo.invalidateCache()
    Logger.i("OS-wipe restore: ${result.matched.size} re-keyed, ${result.unmatched.size} surfaced")
    result
  }

  private fun toScannedBook(
    book: BookContent,
    chapters: List<Chapter>,
  ): ScannedBook {
    val relPath = DeviceRelativePath.documentId(book.id.value.toUri())
    return ScannedBook(
      newBookId = book.id,
      stamp = IdentityStampBuilder.build(book, chapters),
      chapters = chapters.map {
        ScannedChapter(
          newId = it.id,
          relName = DeviceRelativePath.relName(it.id.value.toUri(), relPath),
          duration = it.duration,
        )
      },
    )
  }

  private fun toSnapshotBook(
    dto: BookContentDto,
    snapChapterById: Map<String, ChapterDto>,
    bookmarksByBook: Map<String, List<BookmarkDto>>,
    overridesByBook: Map<String, List<ChapterNameOverrideDto>>,
    sessionsByBook: Map<String, List<ListeningSessionDto>>,
    charactersByBook: Map<String, List<BookCharacterDto>>,
  ): SnapshotBook {
    val bookRelPath = DeviceRelativePath.documentId(dto.id.toUri())
    val bookChapters = dto.chapters.mapNotNull { snapChapterById[it] }
    val stamp = reconstructStamp(dto, bookChapters, bookRelPath)
    val snapChapters = bookChapters.map { chapter ->
      // Prefer the stored relName; fall back to deriving it (legacy bundles written before relNames existed).
      val relName = chapter.relName.ifEmpty { DeviceRelativePath.relName(chapter.id.toUri(), bookRelPath) }
      SnapChapter(oldId = ChapterId(chapter.id), relName = relName, duration = chapter.duration)
    }
    return SnapshotBook(
      stamp = stamp,
      content = dto,
      chapters = snapChapters,
      bookmarks = bookmarksByBook[dto.id].orEmpty(),
      overrides = overridesByBook[dto.id].orEmpty(),
      sessions = sessionsByBook[dto.id].orEmpty(),
      characters = charactersByBook[dto.id].orEmpty(),
    )
  }

  /** Build the snapshot side's stamp from its stored URIs — the URIs in the bundle ARE the identity. */
  private fun reconstructStamp(
    dto: BookContentDto,
    chapters: List<ChapterDto>,
    bookRelPath: String,
  ): BookIdentityStamp {
    val children = chapters
      .map { ChildEntry(relName = DeviceRelativePath.relName(it.id.toUri(), bookRelPath), size = 0L) }
      .sortedBy { it.relName }
    return BookIdentityStamp(
      authority = DeviceRelativePath.authority(dto.id.toUri()),
      isSingleFile = children.size == 1 && children.single().relName.isEmpty(),
      relPath = bookRelPath,
      folderName = bookRelPath.substringAfterLast('/'),
      children = children,
    )
  }
}

// Natural keys for the autoGenerate-PK tables, shared by both restore paths (this one and
// BackupRestorer). Snapshot row ids belong to a different database generation, so restores insert
// with a fresh id and dedup on these keys instead.
internal fun ListeningSession.naturalKey(): String = "${bookId.value}|${startedAt.toEpochMilli()}|$startPositionMs"

internal fun BookCharacter.naturalKey(): String = "${bookId.value}|$name|${createdAt.toEpochMilli()}"

internal fun ListeningEvent.naturalKey(): String = "${bookId.value}|${at.toEpochMilli()}|$type|$positionMs"
