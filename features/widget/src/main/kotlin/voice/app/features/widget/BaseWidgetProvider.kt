package voice.app.features.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.zacsweers.metro.Inject
import voice.core.common.rootGraph
import voice.features.widget.WidgetGraph
import voice.features.widget.WidgetUpdater
import voice.features.widget.config.WidgetConfigStore

class BaseWidgetProvider : AppWidgetProvider() {

  @Inject
  lateinit var widgetUpdater: WidgetUpdater

  @Inject
  lateinit var widgetConfigStore: WidgetConfigStore

  override fun onReceive(
    context: Context,
    intent: Intent?,
  ) {
    (rootGraph as WidgetGraph).inject(this)
    super.onReceive(context, intent)
  }

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    widgetUpdater.update()
  }

  override fun onAppWidgetOptionsChanged(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    newOptions: Bundle,
  ) {
    widgetUpdater.update()
  }

  override fun onDeleted(
    context: Context,
    appWidgetIds: IntArray,
  ) {
    appWidgetIds.forEach { widgetConfigStore.clear(it) }
  }
}
