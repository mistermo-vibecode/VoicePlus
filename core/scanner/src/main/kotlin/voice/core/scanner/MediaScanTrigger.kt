package voice.core.scanner

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
) : MediaScanWaiter {

  private val _scannerActive = MutableStateFlow(false)
  public val scannerActive: Flow<Boolean> = _scannerActive

  // SupervisorJob + handler so an exception in one scan (e.g. a SAF SecurityException or Room
  // error) is logged instead of cancelling the scope — otherwise every later scan would silently
  // no-op until the app restarts.
  private val scope = CoroutineScope(
    Dispatchers.IO + SupervisorJob() +
      CoroutineExceptionHandler { _, throwable ->
        Logger.e(throwable, "Error while scanning for media")
      },
  )
  private var scanningJob: Job? = null

  public fun scan(
    restartIfScanning: Boolean = false,
    forceReParse: Boolean = false,
  ) {
    Logger.i("scanForFiles with restartIfScanning=$restartIfScanning, forceReParse=$forceReParse")
    if (scanningJob?.isActive == true && !restartIfScanning) {
      return
    }
    val oldJob = scanningJob
    scanningJob = scope.launch {
      // Cancel any in-flight scan before marking active, so a restart can't leave the flag stuck.
      oldJob?.cancelAndJoin()
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
  }

  /**
   * Starts a scan and suspends until it (and its active-book reconcile) has finished. Joins the actual
   * [scanningJob] rather than observing [scannerActive] — the flag is racy (you can miss the whole scan or
   * read a stale `false`). Defaults to `restartIfScanning = true` so a restore's scan is never silently
   * collapsed into an in-flight App-start / overview scan by the re-entrancy guard in [scan].
   */
  override suspend fun scanAndAwait(
    restartIfScanning: Boolean,
    forceReParse: Boolean,
  ) {
    scan(restartIfScanning = restartIfScanning, forceReParse = forceReParse)
    scanningJob?.join()
  }
}
