package voice.core.playback.player

import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.Chapter
import voice.core.data.ListeningEventType
import voice.core.data.repo.BookRepository
import voice.core.data.repo.ChapterRepo
import voice.core.data.store.AutoRewindAmountStore
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.SeekTimeStore
import voice.core.logging.api.Logger
import voice.core.playback.history.PlaybackIntentHolder
import voice.core.playback.misc.Decibel
import voice.core.playback.misc.VolumeGain
import voice.core.playback.session.MediaId
import voice.core.playback.session.MediaItemProvider
import voice.core.playback.session.toMediaIdOrNull
import voice.core.sleeptimer.SleepTimer
import voice.core.sleeptimer.SleepTimerState
import java.time.Instant
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Inject
class VoicePlayer(
  private val player: Player,
  private val repo: BookRepository,
  @CurrentBookStore
  private val currentBookStoreId: DataStore<BookId?>,
  @SeekTimeStore
  private val seekTimeStore: DataStore<Int>,
  @AutoRewindAmountStore
  private val autoRewindAmountStore: DataStore<Int>,
  private val mediaItemProvider: MediaItemProvider,
  private val scope: CoroutineScope,
  private val chapterRepo: ChapterRepo,
  private val volumeGain: VolumeGain,
  private val sleepTimer: SleepTimer,
  private val intentHolder: PlaybackIntentHolder,
) : ForwardingPlayer(player) {

  fun forceSeekToNext() {
    // Tag as a next-chapter (Next) seek so the recorder can label the resulting discontinuity.
    intentHolder.pendingSeekIntent = ListeningEventType.Next
    scope.launch {
      val bookId = currentBookStoreId.data.first() ?: return@launch
      val book = repo.get(bookId) ?: return@launch
      val chapterIndex = player.currentMediaItemIndex
      val chapter = book.chapters.getOrNull(chapterIndex) ?: return@launch
      val marks = chapter.chapterMarks
      val currentMarkIndex = marks.indexOfFirst { mark ->
        player.currentPosition in mark.startMs..mark.endMs
      }
      val nextMark = marks.getOrNull(currentMarkIndex + 1)
      if (nextMark != null) {
        player.seekTo(nextMark.startMs)
      } else {
        val nextChapterIndex = chapterIndex + 1
        if (nextChapterIndex < book.chapters.size) {
          player.seekTo(nextChapterIndex, 0L)
        }
      }
    }
  }

  private suspend fun MediaItem.chapter(): Chapter? {
    val mediaId = mediaId.toMediaIdOrNull() ?: return null
    if (mediaId !is MediaId.Chapter) return null
    return chapterRepo.get(mediaId.chapterId)
  }

  fun forceSeekToPrevious() {
    // Tag as a previous-chapter (Previous) seek so the recorder can label the resulting discontinuity.
    intentHolder.pendingSeekIntent = ListeningEventType.Previous
    scope.launch {
      val bookId = currentBookStoreId.data.first() ?: return@launch
      val book = repo.get(bookId) ?: return@launch
      val chapterIndex = player.currentMediaItemIndex
      val chapter = book.chapters.getOrNull(chapterIndex) ?: return@launch
      val marks = chapter.chapterMarks
      val currentPosition = player.currentPosition
      val currentMark = marks.firstOrNull { mark ->
        currentPosition in mark.startMs..mark.endMs
      } ?: marks.last()

      if (currentPosition - currentMark.startMs > THRESHOLD_FOR_BACK_SEEK_MS) {
        player.seekTo(currentMark.startMs)
      } else {
        val currentMarkIndex = marks.indexOf(currentMark)
        val previousMark = marks.getOrNull(currentMarkIndex - 1)
        if (previousMark != null) {
          player.seekTo(previousMark.startMs)
        } else {
          if (chapterIndex > 0) {
            val previousChapterIndex = chapterIndex - 1
            val previousChapterMarks = book.chapters.getOrNull(previousChapterIndex)?.chapterMarks
              ?: return@launch
            player.seekTo(previousChapterIndex, previousChapterMarks.last().startMs)
          } else {
            player.seekTo(0)
          }
        }
      }
    }
  }

  override fun getAvailableCommands(): Player.Commands {
    // On Android 13, the notification always shows the "skip to next" and "skip to previous"
    // actions.
    // However these are also used internally when seeking for example through a bluetooth headset
    // We use these and delegate them to fast forward / rewind.
    // The player however only advertises the seek to next and previous item in the case
    // that it's not the first or last track. Therefore we manually advertise that these
    // are available.
    return super.getAvailableCommands()
      .buildUpon()
      .addAll(
        COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        COMMAND_SEEK_TO_PREVIOUS,
        COMMAND_SEEK_TO_NEXT,
        COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
      )
      .build()
  }

  override fun seekToPreviousMediaItem() {
    seekBack()
  }

  override fun seekToNextMediaItem() {
    seekForward()
  }

  override fun seekToPrevious() {
    seekBack()
  }

  override fun seekToNext() {
    seekForward()
  }

  override fun seekBack() {
    // Tag as a rewind (Back) seek so the recorder can label the resulting discontinuity.
    intentHolder.pendingSeekIntent = ListeningEventType.Back
    sleepTimer.reset()
    scope.launch {
      val skipAmount = seekTimeStore.data.first().seconds

      val currentPosition = player.currentPosition.takeUnless { it == C.TIME_UNSET }
        ?.milliseconds
        ?.coerceAtLeast(ZERO)
        ?: return@launch

      val newPosition = currentPosition - skipAmount
      if (newPosition < ZERO) {
        val previousMediaItemIndex = previousMediaItemIndex.takeUnless { it == C.INDEX_UNSET }
        if (previousMediaItemIndex == null) {
          player.seekTo(0)
        } else {
          val previousMediaItem = player.getMediaItemAt(previousMediaItemIndex)
          val chapter = previousMediaItem.chapter() ?: return@launch
          val previousMediaItemDuration = chapter.duration.milliseconds
          player.seekTo(previousMediaItemIndex, (previousMediaItemDuration - newPosition.absoluteValue).inWholeMilliseconds)
        }
      } else {
        player.seekTo(newPosition.inWholeMilliseconds)
      }
    }
  }

  override fun seekForward() {
    // Tag as a fast-forward (Forward) seek so the recorder can label the resulting discontinuity.
    intentHolder.pendingSeekIntent = ListeningEventType.Forward
    sleepTimer.reset()
    scope.launch {
      val skipAmount = seekTimeStore.data.first().seconds

      val currentPosition = player.currentPosition.takeUnless { it == C.TIME_UNSET }
        ?.milliseconds
        ?.coerceAtLeast(ZERO)
        ?: return@launch
      val newPosition = currentPosition + skipAmount

      val duration = player.duration.takeUnless { it == C.TIME_UNSET }
        ?.milliseconds
        ?: return@launch

      if (newPosition > duration) {
        val nextMediaItemIndex = nextMediaItemIndex.takeUnless { it == C.INDEX_UNSET }
          ?: return@launch
        player.seekTo(nextMediaItemIndex, (duration - newPosition).absoluteValue.inWholeMilliseconds)
      } else {
        player.seekTo(newPosition.inWholeMilliseconds)
      }
    }
  }

  override fun play() {
    playWhenReady = true
  }

  override fun setPlayWhenReady(playWhenReady: Boolean) {
    Logger.d("setPlayWhenReady=$playWhenReady")
    if (playWhenReady) {
      updateLastPlayedAt()
    } else {
      val currentPosition = player.currentPosition.takeUnless { it == C.TIME_UNSET }?.milliseconds ?: ZERO
      // Stash the true end position before the auto-rewind seek moves it; the recorder reads this on pause.
      intentHolder.pendingPauseEndPositionMs = currentPosition.inWholeMilliseconds
      if (currentPosition > ZERO) {
        val autoRewindAmount = runBlocking { autoRewindAmountStore.data.first().seconds }
        // The imminent auto-rewind seek is internal bookkeeping, not a user action — tell the recorder to ignore it.
        intentHolder.suppressNextSeek = true
        seekTo((currentPosition - autoRewindAmount).coerceAtLeast(ZERO).inWholeMilliseconds)
      }
    }
    super.setPlayWhenReady(playWhenReady)
  }

  override fun pause() {
    playWhenReady = false
  }

  private fun updateLastPlayedAt() {
    scope.launch {
      currentBookStoreId.data.first()?.let { bookId ->
        repo.updateBook(bookId) {
          val lastPlayedAt = Instant.now()
          Logger.v("Update ${it.name}: lastPlayedAt to $lastPlayedAt")
          it.copy(lastPlayedAt = lastPlayedAt)
        }
      }
    }
  }

  override fun getPlaybackState(): Int = when (val state = super.getPlaybackState()) {
    // redirect buffering to ready to prevent visual artifacts on seeking
    STATE_BUFFERING -> STATE_READY
    else -> state
  }

  override fun setMediaItem(
    mediaItem: MediaItem,
    startPositionMs: Long,
  ) {
    setBook(mediaItem)
  }

  override fun setMediaItem(
    mediaItem: MediaItem,
    resetPosition: Boolean,
  ) {
    setBook(mediaItem)
  }

  override fun setMediaItems(mediaItems: List<MediaItem>) {
    val first = mediaItems.firstOrNull() ?: return
    setBook(first)
  }

  override fun setMediaItems(
    mediaItems: List<MediaItem>,
    resetPosition: Boolean,
  ) {
    val first = mediaItems.firstOrNull() ?: return
    setBook(first)
  }

  override fun setMediaItem(mediaItem: MediaItem) {
    setBook(mediaItem)
  }

  override fun setMediaItems(
    mediaItems: List<MediaItem>,
    startIndex: Int,
    startPositionMs: Long,
  ) {
    val first = mediaItems.firstOrNull() ?: return
    setBook(first)
  }

  private fun setBook(mediaItem: MediaItem) {
    Logger.v("setBook(${mediaItem.mediaId})")
    val mediaId = mediaItem.mediaId.toMediaIdOrNull()
    if (mediaId != null) {
      if (mediaId is MediaId.Book) {
        val bookWithChapters = runBlocking {
          val book = repo.get(mediaId.id) ?: return@runBlocking null
          book to mediaItemProvider.chapters(book)
        }
        if (bookWithChapters != null) {
          val (book, chapters) = bookWithChapters
          player.setPlaybackSpeed(book.content.playbackSpeed)
          setSkipSilenceEnabled(book.content.skipSilence)
          volumeGain.gain = Decibel(book.content.gain)
          player.setMediaItems(
            chapters,
            book.content.currentChapterIndex,
            book.content.positionInChapter,
          )
          registerChapterMarkCallbacks(book.chapters)
        }
      } else {
        Logger.w("Unexpected mediaId=$mediaId")
      }
    }
  }

  private fun registerChapterMarkCallbacks(chapters: List<Chapter>) {
    if (player is ExoPlayer) {
      val boundaryHandler = PlayerMessage.Target { _, payload ->
        if (payload is ChapterPausePayload &&
          payload != ChapterPausePayload.Zero &&
          sleepTimer.state.value is SleepTimerState.Enabled.WithEndOfChapter
        ) {
          Logger.v("Chapter mark reached at $payload, pausing as per sleep timer")
          sleepTimer.onChapterBoundaryReached()
          if (sleepTimer.state.value == SleepTimerState.Disabled) {
            player.seekTo(payload.chapterIndex, payload.positionMs)
            player.pause()
          }
        }
      }
      chapters.forEachIndexed { chapterIndex, chapter ->
        chapter.chapterMarks.forEach { chapterMark ->
          player.createMessage(boundaryHandler)
            .setPosition(chapterIndex, chapterMark.startMs + 1)
            .setPayload(ChapterPausePayload(chapterIndex, chapterMark.startMs))
            .setDeleteAfterDelivery(false)
            .setLooper(Looper.getMainLooper())
            .send()
        }
      }
    }
  }

  private data class ChapterPausePayload(
    val chapterIndex: Int,
    val positionMs: Long,
  ) {
    companion object {
      val Zero = ChapterPausePayload(0, 0L)
    }
  }

  override fun setPlaybackSpeed(speed: Float) {
    super.setPlaybackSpeed(speed)
    scope.launch {
      updateBook { it.copy(playbackSpeed = speed) }
    }
  }

  fun setSkipSilenceEnabled(enabled: Boolean) {
    scope.launch {
      updateBook { it.copy(skipSilence = enabled) }
    }
    if (player is ExoPlayer) {
      player.skipSilenceEnabled = enabled
    }
  }

  fun setGain(gain: Decibel) {
    volumeGain.gain = gain
    scope.launch {
      updateBook { it.copy(gain = gain.value) }
    }
  }

  private suspend fun updateBook(update: (BookContent) -> BookContent) {
    val bookId = currentBookStoreId.data.first() ?: return
    repo.updateBook(bookId, update)
  }
}

private const val THRESHOLD_FOR_BACK_SEEK_MS = 2000
