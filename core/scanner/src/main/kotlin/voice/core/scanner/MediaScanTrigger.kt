package voice.core.scanner

import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.data.MediaScanWaiter
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.data.repo.BookRepository
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.logging.api.Logger
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.measureTime

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
public class MediaScanTrigger
internal constructor(
  private val audiobookFolders: AudiobookFolders,
  private val scanner: MediaScanner,
  private val coverScanner: CoverScanner,
  private val bookRepo: BookRepository,
  private val documentFileFactory: CachedDocumentFileFactory,
  private val dispatcherProvider: DispatcherProvider,
) : MediaScanWaiter {

  private val _scannerActive = MutableStateFlow(false)
  public val scannerActive: Flow<Boolean> = _scannerActive

  // SupervisorJob + handler so an exception in one scan (e.g. a SAF SecurityException or Room
  // error) is logged instead of cancelling the scope — otherwise every later scan would silently
  // no-op until the app restarts. The dispatcher is injected (not a hardcoded Dispatchers.IO) so a
  // test can run the scan on a single TestDispatcher and control exactly when it resumes relative to
  // a folder removal — the seam that makes the remove-during-scan race deterministically reproducible.
  private val scope = CoroutineScope(
    dispatcherProvider.io + SupervisorJob() +
      CoroutineExceptionHandler { _, throwable ->
        Logger.e(throwable, "Error while scanning for media")
      },
  )
  private val scanLock = Any()
  private var scanningJob: Job? = null

  @Volatile
  private var lastScanCompletedAt: Instant? = null

  // The folder configuration the last completed scan actually saw. Freshness is time AND
  // configuration: nothing triggers a scan when a folder is added or removed (the overview's
  // entry scan is what picks it up), so a purely time-based cooldown would hide a folder the
  // user just added until the window lapsed.
  @Volatile
  private var lastScanFolderKeys: Set<Pair<FolderType, Uri>>? = null

  // Test seam, mirroring ListeningEventRecorder.clock.
  internal var clock: () -> Instant = { Instant.now() }

  public fun scan(
    restartIfScanning: Boolean = false,
    forceReParse: Boolean = false,
    // The book overview triggers a scan on every entry into composition — i.e. every navigation
    // back to the library. Without a freshness window that is a full SAF re-walk (seconds of
    // progress bar) per navigation. Explicit scans (delete-rescan, restore, tag-mode toggle) pass
    // null or restart/force and are never skipped; only a scan that COMPLETED, over the SAME
    // folder set that is configured now, counts as fresh.
    skipIfCompletedWithin: Duration? = null,
  ) {
    val skipWindow = skipIfCompletedWithin.takeUnless { restartIfScanning || forceReParse }
    scanInternal(restartIfScanning = restartIfScanning, forceReParse = forceReParse, skipIfCompletedWithin = skipWindow)
  }

  // Returns the Job that will (or already does) run this scan. The check-then-launch is guarded so two
  // concurrent callers can't both read the same prior job and leak duplicate scans.
  @IgnorableReturnValue
  private fun scanInternal(
    restartIfScanning: Boolean,
    forceReParse: Boolean,
    skipIfCompletedWithin: Duration? = null,
  ): Job = synchronized(scanLock) {
    Logger.i("scanForFiles with restartIfScanning=$restartIfScanning, forceReParse=$forceReParse")
    val current = scanningJob
    if (current?.isActive == true && !restartIfScanning) {
      return@synchronized current
    }
    val job = scope.launch {
      // Cancel any in-flight scan before marking active, so a restart can't leave the flag stuck.
      current?.cancelAndJoin()
      val foldersWithUri = audiobookFolders.all().first()
      val folderKeys = foldersWithUri
        .flatMap { (type, files) -> files.map { type to it.uri } }
        .toSet()
      if (skipIfCompletedWithin != null &&
        isFresh(lastScanCompletedAt, clock(), skipIfCompletedWithin) &&
        folderKeys == lastScanFolderKeys
      ) {
        Logger.v("Skipping scan; last one completed within $skipIfCompletedWithin over the same folders")
        return@launch
      }
      _scannerActive.value = true
      try {
        measureTime {
          val folders: Map<FolderType, List<CachedDocumentFile>> = foldersWithUri
            .mapValues { (_, documentFilesWithUri) ->
              documentFilesWithUri.map {
                documentFileFactory.create(it.documentFile.uri)
              }
            }
          scanner.scan(folders, forceReParse)
        }.also {
          Logger.i("scan took $it")
        }
        // Only a run that got this far refreshes the window; a cancelled or thrown scan must not
        // suppress the retry.
        lastScanCompletedAt = clock()
        lastScanFolderKeys = folderKeys
      } finally {
        // Always clear the flag, even if the scan throws, so the progress indicator can't hang.
        _scannerActive.value = false
      }

      val books = bookRepo.all()
      coverScanner.scan(books)
    }
    scanningJob = job
    job
  }

  /**
   * Starts a scan and suspends until it (and its active-book reconcile inside [MediaScanner.scan]) has
   * finished. Joins the EXACT job it started (not the shared [scanningJob] field, which a concurrent scan
   * could overwrite) — and never observes the racy [scannerActive] flag. Always scans with
   * `restartIfScanning = true` so a restore's scan is not collapsed into an in-flight App-start /
   * overview scan by the re-entrancy guard — a guarantee of the interface, not a caller choice.
   */
  override suspend fun scanAndAwait() {
    scanInternal(restartIfScanning = true, forceReParse = false).join()
  }
}

// Pure so the cooldown window is testable without the scan rig.
internal fun isFresh(
  lastCompletedAt: Instant?,
  now: Instant,
  window: Duration,
): Boolean {
  if (lastCompletedAt == null) return false
  return java.time.Duration.between(lastCompletedAt, now).toMillis() < window.inWholeMilliseconds
}
