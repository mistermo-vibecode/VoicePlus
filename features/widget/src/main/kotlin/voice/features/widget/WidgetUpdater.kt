package voice.features.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import voice.app.features.widget.ChapterWidgetProvider
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.SeekTimeStore
import voice.core.playback.notification.MainActivityIntentProvider
import voice.core.playback.playstate.PlayStateManager
import voice.core.playback.receiver.WidgetButtonReceiver
import voice.features.widget.config.WidgetConfigActivity
import voice.features.widget.config.WidgetConfigStore
import voice.core.strings.R as StringsR
import voice.core.ui.dpToPxRounded
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
      val book = currentBookStore.data.first()?.let {
        repo.get(it)
      }
      for (variant in WidgetVariant.entries) {
        val componentName = ComponentName(context, variant.providerClass)
        val ids = appWidgetManager.getAppWidgetIds(componentName)
        for (widgetId in ids) {
          updateWidgetForId(book, widgetId, variant)
        }
      }
    }
  }

  private suspend fun updateWidgetForId(
    book: Book?,
    widgetId: Int,
    variant: WidgetVariant,
  ) {
    if (book != null) {
      initWidgetForPresentBook(widgetId, book, variant)
    } else {
      initWidgetForAbsentBook(widgetId, variant)
    }
  }

  private suspend fun initWidgetForPresentBook(
    widgetId: Int,
    book: Book,
    variant: WidgetVariant,
  ) {
    val opts = appWidgetManager.getAppWidgetOptions(widgetId)
    val useWidth = widgetWidth(opts)
    val useHeight = widgetHeight(opts)

    val remoteViews = RemoteViews(context.packageName, variant.layoutId)
    initElements(widgetId = widgetId, remoteViews = remoteViews, book = book, coverSize = useHeight, variant = variant)

    if (useWidth > 0 && useHeight > 0) {
      setVisibilities(remoteViews, useWidth, useHeight, variant)
    }
    appWidgetManager.updateAppWidget(widgetId, remoteViews)
  }

  private fun widgetWidth(opts: Bundle): Int {
    val key = if (isPortrait) {
      AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
    } else {
      AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
    }
    val dp = opts.getInt(key)
    return context.dpToPxRounded(dp.toFloat())
  }

  private fun widgetHeight(opts: Bundle): Int {
    val key = if (isPortrait) {
      AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
    } else {
      AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
    }
    val dp = opts.getInt(key)
    return context.dpToPxRounded(dp.toFloat())
  }

  private suspend fun initWidgetForAbsentBook(
    widgetId: Int,
    variant: WidgetVariant,
  ) {
    val remoteViews = RemoteViews(context.packageName, variant.layoutId)
    val wholeWidgetClickPI = mainActivityIntentProvider.toCurrentBook()
    remoteViews.setImageViewResource(R.id.imageView, UiR.drawable.album_art)
    remoteViews.setTextViewText(R.id.title, "")
    if (variant == WidgetVariant.WithChapter) {
      remoteViews.setViewVisibility(R.id.summary, View.GONE)
    }
    remoteViews.setOnClickPendingIntent(R.id.wholeWidget, wholeWidgetClickPI)

    applyConfiguration(widgetId, remoteViews, variant)

    appWidgetManager.updateAppWidget(widgetId, remoteViews)
  }

  private val isPortrait: Boolean
    get() {
      val orientation = context.resources.configuration.orientation
      return orientation == Configuration.ORIENTATION_PORTRAIT
    }

  private suspend fun initElements(
    widgetId: Int,
    remoteViews: RemoteViews,
    book: Book,
    coverSize: Int,
    variant: WidgetVariant,
  ) {
    val playPausePI = WidgetButtonReceiver.pendingIntent(context, WidgetButtonReceiver.Action.PlayPause)
    remoteViews.setOnClickPendingIntent(R.id.playPause, playPausePI)

    val fastForwardPI = WidgetButtonReceiver.pendingIntent(context, WidgetButtonReceiver.Action.FastForward)
    remoteViews.setOnClickPendingIntent(R.id.fastForward, fastForwardPI)
    val rewindPI = WidgetButtonReceiver.pendingIntent(context, WidgetButtonReceiver.Action.Rewind)
    remoteViews.setOnClickPendingIntent(R.id.rewind, rewindPI)

    val playIcon = if (playStateManager.playState == PlayStateManager.PlayState.Playing) {
      UiR.drawable.ic_pause_white_36dp
    } else {
      UiR.drawable.ic_play_white_36dp
    }
    remoteViews.setImageViewResource(R.id.playPause, playIcon)

    remoteViews.setTextViewText(R.id.title, book.content.name)
    if (variant == WidgetVariant.WithChapter) {
      val chapterLabel = book.currentMark.name?.takeIf { it.isNotBlank() } ?: book.currentChapter.name
      remoteViews.setTextViewText(R.id.summary, chapterLabel)
    }

    val wholeWidgetClickPI = mainActivityIntentProvider.toCurrentBook()
    remoteViews.setOnClickPendingIntent(R.id.wholeWidget, wholeWidgetClickPI)

    val coverFile = book.content.cover
    if (coverFile != null && coverSize > 0) {
      val bitmap = context.imageLoader
        .execute(
          ImageRequest.Builder(context)
            .data(coverFile)
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

    applyConfiguration(widgetId, remoteViews, variant)
  }

  private suspend fun applyConfiguration(widgetId: Int, remoteViews: RemoteViews, variant: WidgetVariant) {
    val alpha = configStore.getAlpha(widgetId)
    val scale = configStore.getTextScale(widgetId)
    val seekSeconds = seekTimeStore.data.first()
    
    // Set text skip labels
    val skipText = "${seekSeconds}s"
    remoteViews.setTextViewText(R.id.rewindText, skipText)
    remoteViews.setTextViewText(R.id.fastForwardText, skipText)
    
    // Setup standard content descriptions
    remoteViews.setContentDescription(
      R.id.fastForward,
      context.getString(StringsR.string.widget_skip_forward, seekSeconds),
    )
    remoteViews.setContentDescription(
      R.id.rewind,
      context.getString(StringsR.string.widget_skip_back, seekSeconds),
    )

    // Config settings (handled natively on long press via reconfigurable flag)
    val configIntent = Intent(context, WidgetConfigActivity::class.java).apply {
      putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val configPI = PendingIntent.getActivity(context, widgetId, configIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    // Apply alpha to background
    remoteViews.setInt(R.id.widgetBackground, "setImageAlpha", alpha)

    // Apply scaling
    remoteViews.setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_SP, 16f * scale)
    if (variant == WidgetVariant.WithChapter) {
       remoteViews.setTextViewTextSize(R.id.summary, TypedValue.COMPLEX_UNIT_SP, 14f * scale)
    }
    remoteViews.setTextViewTextSize(R.id.rewindText, TypedValue.COMPLEX_UNIT_SP, 11f * scale)
    remoteViews.setTextViewTextSize(R.id.fastForwardText, TypedValue.COMPLEX_UNIT_SP, 11f * scale)
  }

  private fun setVisibilities(
    remoteViews: RemoteViews,
    width: Int,
    height: Int,
    variant: WidgetVariant,
  ) {
    setHorizontalVisibility(remoteViews, width, height)
    when (variant) {
      WidgetVariant.Compact -> setVerticalVisibilityCompact(remoteViews, height)
      WidgetVariant.WithChapter -> setVerticalVisibilityWithChapter(remoteViews, height)
    }
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

    // Removed aggressive hiding of R.id.imageView

    if (summarizedItemWidth > widgetWidth) {
      remoteViews.setViewVisibility(R.id.fastForwardContainer, View.GONE)
      summarizedItemWidth -= singleButtonSize
    }

    if (summarizedItemWidth > widgetWidth) {
      remoteViews.setViewVisibility(R.id.rewindContainer, View.GONE)
    }
  }

  private fun setVerticalVisibilityCompact(
    remoteViews: RemoteViews,
    widgetHeight: Int,
  ) {
    val buttonSize = context.dpToPxRounded(8F + 36F + 8F)
    val titleSize = context.resources.getDimensionPixelSize(R.dimen.list_text_primary_size)
    var summarizedItemsHeight = buttonSize + titleSize

    remoteViews.setViewVisibility(R.id.title, View.VISIBLE)

    if (summarizedItemsHeight > widgetHeight) {
      remoteViews.setViewVisibility(R.id.title, View.GONE)
    }
  }

  private fun setVerticalVisibilityWithChapter(
    remoteViews: RemoteViews,
    widgetHeight: Int,
  ) {
    val buttonSize = context.dpToPxRounded(8F + 36F + 8F)
    val titleSize = context.resources.getDimensionPixelSize(R.dimen.list_text_primary_size)
    val summarySize = context.resources.getDimensionPixelSize(R.dimen.list_text_secondary_size)

    var summarizedItemsHeight = buttonSize + titleSize + summarySize

    remoteViews.setViewVisibility(R.id.summary, View.VISIBLE)
    remoteViews.setViewVisibility(R.id.title, View.VISIBLE)

    if (widgetHeight < summarizedItemsHeight) {
      remoteViews.setViewVisibility(R.id.summary, View.GONE)
      summarizedItemsHeight -= summarySize
    }

    if (summarizedItemsHeight > widgetHeight) {
      remoteViews.setViewVisibility(R.id.title, View.GONE)
    }
  }
}

private enum class WidgetVariant(
  val layoutId: Int,
  val providerClass: Class<out AppWidgetProvider>,
) {
  Compact(R.layout.widget_compact, BaseWidgetProvider::class.java),
  WithChapter(R.layout.widget_with_chapter, ChapterWidgetProvider::class.java),
}
