package voice.features.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import coil.imageLoader
import coil.request.ImageRequest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.app.features.widget.BaseWidgetProvider
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.SeekTimeStore
import voice.core.playback.notification.MainActivityIntentProvider
import voice.core.playback.playstate.PlayStateManager
import voice.core.playback.receiver.WidgetButtonReceiver
import voice.core.ui.dpToPxRounded
import voice.features.widget.config.WidgetConfigStore
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR

@SingleIn(AppScope::class)
@Inject
class WidgetUpdater(
  private val context: Context,
  private val repo: BookRepository,
  @CurrentBookStore
  private val currentBookStore: DataStore<BookId?>,
  @SeekTimeStore
  private val seekTimeStore: DataStore<Int>,
  private val configStore: WidgetConfigStore,
  private val playStateManager: PlayStateManager,
  private val mainActivityIntentProvider: MainActivityIntentProvider,
) {

  private val appWidgetManager = AppWidgetManager.getInstance(context)
  private val scope = CoroutineScope(Dispatchers.IO)

  fun update() {
    scope.launch {
      val book = currentBookStore.data.first()?.let { repo.get(it) }
      val seekSeconds = seekTimeStore.data.first()
      val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, BaseWidgetProvider::class.java))
      for (widgetId in ids) {
        val alpha = configStore.getAlpha(widgetId)
        val scale = configStore.getTextScale(widgetId)
        if (book != null) {
          initWidgetForPresentBook(widgetId, book, alpha, scale, seekSeconds)
        } else {
          initWidgetForAbsentBook(widgetId, alpha, scale, seekSeconds)
        }
      }
    }
  }

  private suspend fun initWidgetForPresentBook(
    widgetId: Int,
    book: Book,
    alpha: Int,
    scale: Float,
    seekSeconds: Int,
  ) {
    val opts = appWidgetManager.getAppWidgetOptions(widgetId)
    val useWidth = widgetWidth(opts)
    val useHeight = widgetHeight(opts)
    val remoteViews = RemoteViews(context.packageName, R.layout.widget_compact)
    initElements(remoteViews = remoteViews, book = book, coverSize = useHeight, alpha = alpha, scale = scale, seekSeconds = seekSeconds)
    if (useWidth > 0 && useHeight > 0) {
      setVisibilities(remoteViews, useWidth, useHeight)
    }
    appWidgetManager.updateAppWidget(widgetId, remoteViews)
  }

  private fun widgetWidth(opts: Bundle): Int {
    val key = if (isPortrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
    return context.dpToPxRounded(opts.getInt(key).toFloat())
  }

  private fun widgetHeight(opts: Bundle): Int {
    val key = if (isPortrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
    return context.dpToPxRounded(opts.getInt(key).toFloat())
  }

  private suspend fun initWidgetForAbsentBook(
    widgetId: Int,
    alpha: Int,
    scale: Float,
    seekSeconds: Int,
  ) {
    val remoteViews = RemoteViews(context.packageName, R.layout.widget_compact)
    remoteViews.setImageViewResource(R.id.imageView, UiR.drawable.album_art)
    remoteViews.setTextViewText(R.id.title, "")
    remoteViews.setViewVisibility(R.id.summary, View.GONE)
    remoteViews.setOnClickPendingIntent(R.id.wholeWidget, mainActivityIntentProvider.toCurrentBook())
    applyConfiguration(remoteViews, alpha, scale, seekSeconds)
    appWidgetManager.updateAppWidget(widgetId, remoteViews)
  }

  private val isPortrait: Boolean
    get() = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

  private suspend fun initElements(
    remoteViews: RemoteViews,
    book: Book,
    coverSize: Int,
    alpha: Int,
    scale: Float,
    seekSeconds: Int,
  ) {
    remoteViews.setOnClickPendingIntent(R.id.playPause, WidgetButtonReceiver.pendingIntent(context, WidgetButtonReceiver.Action.PlayPause))
    remoteViews.setOnClickPendingIntent(
      R.id.fastForward,
      WidgetButtonReceiver.pendingIntent(context, WidgetButtonReceiver.Action.FastForward),
    )
    remoteViews.setOnClickPendingIntent(R.id.rewind, WidgetButtonReceiver.pendingIntent(context, WidgetButtonReceiver.Action.Rewind))

    val playIcon = if (playStateManager.playState == PlayStateManager.PlayState.Playing) {
      UiR.drawable.ic_pause_white_36dp
    } else {
      UiR.drawable.ic_play_white_36dp
    }
    remoteViews.setImageViewResource(R.id.playPause, playIcon)
    remoteViews.setTextViewText(R.id.title, book.content.name)
    remoteViews.setTextViewText(R.id.summary, book.currentMark.name?.takeIf { it.isNotBlank() } ?: book.currentChapter.name)
    remoteViews.setOnClickPendingIntent(R.id.wholeWidget, mainActivityIntentProvider.toCurrentBook())

    if (book.content.cover != null && coverSize > 0) {
      val bitmap = context.imageLoader
        .execute(
          ImageRequest.Builder(context)
            .data(book.content.cover)
            .size(coverSize, coverSize)
            .fallback(UiR.drawable.album_art)
            .error(UiR.drawable.album_art)
            .allowHardware(false)
            .build(),
        )
        .drawable!!.toBitmap()
      remoteViews.setImageViewBitmap(R.id.imageView, bitmap)
    } else {
      remoteViews.setImageViewResource(R.id.imageView, UiR.drawable.album_art)
    }

    applyConfiguration(remoteViews, alpha, scale, seekSeconds)
  }

  private fun applyConfiguration(
    remoteViews: RemoteViews,
    alpha: Int,
    scale: Float,
    seekSeconds: Int,
  ) {
    val skipText = "${seekSeconds}s"
    remoteViews.setTextViewText(R.id.rewindText, skipText)
    remoteViews.setTextViewText(R.id.fastForwardText, skipText)
    remoteViews.setContentDescription(R.id.fastForward, context.getString(StringsR.string.widget_skip_forward, seekSeconds))
    remoteViews.setContentDescription(R.id.rewind, context.getString(StringsR.string.widget_skip_back, seekSeconds))
    remoteViews.setInt(R.id.widgetBackground, "setImageAlpha", alpha)
    val iconTint = context.getColor(R.color.widget_icon_tint)
    remoteViews.setInt(R.id.playPause, "setColorFilter", iconTint)
    remoteViews.setInt(R.id.rewind, "setColorFilter", iconTint)
    remoteViews.setInt(R.id.fastForward, "setColorFilter", iconTint)
    remoteViews.setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_SP, 14f * scale)
    remoteViews.setTextViewTextSize(R.id.summary, TypedValue.COMPLEX_UNIT_SP, 12f * scale)
    remoteViews.setTextViewTextSize(R.id.rewindText, TypedValue.COMPLEX_UNIT_SP, 11f * scale)
    remoteViews.setTextViewTextSize(R.id.fastForwardText, TypedValue.COMPLEX_UNIT_SP, 11f * scale)
  }

  private fun setVisibilities(
    remoteViews: RemoteViews,
    width: Int,
    height: Int,
  ) {
    setHorizontalVisibility(remoteViews, width, height)
    setVerticalVisibility(remoteViews, height)
  }

  private fun setHorizontalVisibility(
    remoteViews: RemoteViews,
    widgetWidth: Int,
    coverSize: Int,
  ) {
    val singleButtonSize = context.dpToPxRounded(8F + 36F + 8F)
    var summarizedItemWidth = 3 * singleButtonSize + coverSize

    remoteViews.setViewVisibility(R.id.imageView, View.VISIBLE)
    remoteViews.setViewVisibility(R.id.rewindContainer, View.VISIBLE)
    remoteViews.setViewVisibility(R.id.fastForwardContainer, View.VISIBLE)

    if (summarizedItemWidth > widgetWidth) {
      remoteViews.setViewVisibility(R.id.fastForwardContainer, View.GONE)
      summarizedItemWidth -= singleButtonSize
    }
    if (summarizedItemWidth > widgetWidth) {
      remoteViews.setViewVisibility(R.id.rewindContainer, View.GONE)
    }
  }

  private fun setVerticalVisibility(
    remoteViews: RemoteViews,
    widgetHeight: Int,
  ) {
    val labelSize = context.resources.getDimensionPixelSize(R.dimen.widget_skip_label_size)
    val buttonSize = context.dpToPxRounded(8F + 36F + 8F) + labelSize
    val titleSize = context.resources.getDimensionPixelSize(R.dimen.list_text_primary_size)
    val summarySize = context.resources.getDimensionPixelSize(R.dimen.list_text_secondary_size)
    var summarizedItemsHeight = buttonSize + titleSize + summarySize

    remoteViews.setViewVisibility(R.id.summary, View.VISIBLE)
    remoteViews.setViewVisibility(R.id.title, View.VISIBLE)

    if (summarizedItemsHeight > widgetHeight) {
      remoteViews.setViewVisibility(R.id.summary, View.GONE)
      summarizedItemsHeight -= summarySize
    }
    if (summarizedItemsHeight > widgetHeight) {
      remoteViews.setViewVisibility(R.id.title, View.GONE)
    }
  }
}
