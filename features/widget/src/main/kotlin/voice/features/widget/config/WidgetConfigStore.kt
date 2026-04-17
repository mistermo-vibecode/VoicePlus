package voice.features.widget.config

import android.content.Context
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@Inject
class WidgetConfigStore(private val context: Context) {
  private val prefs = context.getSharedPreferences("widget_config", Context.MODE_PRIVATE)

  fun getAlpha(widgetId: Int): Int {
    return prefs.getInt("alpha_$widgetId", 255)
  }

  fun setAlpha(widgetId: Int, alpha: Int) {
    prefs.edit { putInt("alpha_$widgetId", alpha) }
  }

  fun getTextScale(widgetId: Int): Float {
    return prefs.getFloat("scale_$widgetId", 1.0f)
  }

  fun setTextScale(widgetId: Int, scale: Float) {
    prefs.edit { putFloat("scale_$widgetId", scale) }
  }

  fun clear(widgetId: Int) {
    prefs.edit {
      remove("alpha_$widgetId")
      remove("scale_$widgetId")
    }
  }
}
