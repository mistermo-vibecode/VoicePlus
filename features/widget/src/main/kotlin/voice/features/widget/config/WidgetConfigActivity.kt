package voice.features.widget.config

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.zacsweers.metro.Inject
import voice.core.common.rootGraph
import voice.core.ui.VoiceTheme
import voice.features.widget.R
import voice.features.widget.WidgetGraph
import voice.features.widget.WidgetUpdater
import java.util.Locale
import kotlin.math.roundToInt

class WidgetConfigActivity : ComponentActivity() {

  private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

  @Inject lateinit var configStore: WidgetConfigStore

  @Inject lateinit var widgetUpdater: WidgetUpdater

  @SuppressLint("InflateParams")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setResult(Activity.RESULT_CANCELED)

    (rootGraph as WidgetGraph).inject(this)

    appWidgetId = intent.extras?.getInt(
      AppWidgetManager.EXTRA_APPWIDGET_ID,
      AppWidgetManager.INVALID_APPWIDGET_ID,
    ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
      finish()
      return
    }

    setContent {
      VoiceTheme {
        Scaffold(
          topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(title = { Text("Widget Settings") })
          },
        ) { padding ->
          var alpha by remember { mutableStateOf(configStore.getAlpha(appWidgetId) / 255f) }
          var scale by remember { mutableStateOf(configStore.getTextScale(appWidgetId)) }

          Column(
            modifier = Modifier
              .padding(padding)
              .padding(16.dp)
              .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            AndroidView(
              modifier = Modifier.fillMaxWidth().height(100.dp),
              factory = { ctx ->
                LayoutInflater.from(ctx).inflate(R.layout.widget_preview, null, false).also { view ->
                  view.tag = PreviewViews(
                    background = view.findViewById(R.id.widgetBackground),
                    title = view.findViewById(R.id.title),
                    summary = view.findViewById(R.id.summary),
                    rewindText = view.findViewById(R.id.rewindText),
                    fastForwardText = view.findViewById(R.id.fastForwardText),
                  )
                }
              },
              update = { view ->
                val v = view.tag as PreviewViews
                v.background.imageAlpha = (alpha * 255).roundToInt().coerceIn(0, 255)
                v.title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * scale)
                v.summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * scale)
                v.rewindText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * scale)
                v.fastForwardText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * scale)
              },
            )

            Column {
              Text("Background opacity: ${(alpha * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
              Slider(value = alpha, onValueChange = { alpha = it }, valueRange = 0f..1f)
            }

            Column {
              Text("Text size: ${String.format(Locale.ROOT, "%.1f", scale)}x", style = MaterialTheme.typography.titleMedium)
              Slider(value = scale, onValueChange = { scale = it }, valueRange = 0.5f..2f)
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
              modifier = Modifier.fillMaxWidth(),
              onClick = {
                configStore.setAlpha(appWidgetId, (alpha * 255).roundToInt().coerceIn(0, 255))
                configStore.setTextScale(appWidgetId, scale)
                widgetUpdater.update()
                setResult(
                  Activity.RESULT_OK,
                  Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                  },
                )
                finish()
              },
            ) {
              Text("Add Widget")
            }
          }
        }
      }
    }
  }

  private data class PreviewViews(
    val background: ImageView,
    val title: TextView,
    val summary: TextView,
    val rewindText: TextView,
    val fastForwardText: TextView,
  )
}
