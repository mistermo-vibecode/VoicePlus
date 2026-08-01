package voice.core.scanner

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

  public fun scan(
    restartIfScanning: Boolean = false,
    forceReParse: Boolean = false,
  ) {
    scanInternal(restartIfScanning = restartIfScanning, forceReParse = forceReParse)
  }

  // Returns the Job that will (or already does) run this scan. The check-then-launch is guarded so two
  // concurrent callers can't both read the same prior job and leak duplicate scans.
  @IgnorableReturnValue
  private fun scanInternal(
    restartIfScanning: Boolean,
    forceReParse: Boolean,
  ): Job = synchronized(scanLock) {
    Logger.i("scanForFiles with restartIfScanning=$restartIfScanning, forceReParse=$forceReParse")
    val current = scanningJob
    if (current?.isActive == true && !restartIfScanning) {
      return@synchronized current
    }
    val job = scope.launch {
      // Cancel any in-flight scan before marking active, so a restart can't leave the flag stuck.
      current?.cancelAndJoin()
      _scannerActive.value = true
      try {
        measureTime {
          val folders: Map<FolderType, List<CachedDocumentFile>> = audiobookFolders.all()
            .first()
            .mapValues { (_, documentFilesWithUri) ->
              documentFilesWithUri.map {
                documentFileFactory.create(it.documentFile.uri)
              }
            }
          scanner.scan(folders, forceReParse)
        }.also {
          Logger.i("scan took $it")
        }
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
