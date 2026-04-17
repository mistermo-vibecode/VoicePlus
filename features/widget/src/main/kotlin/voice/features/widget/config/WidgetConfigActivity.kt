package voice.features.widget.config

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import voice.core.common.rootGraph
import dev.zacsweers.metro.Inject
import voice.features.widget.WidgetGraph
import voice.features.widget.WidgetUpdater
import voice.core.ui.VoiceTheme
import kotlin.math.roundToInt

class WidgetConfigActivity : ComponentActivity() {

  private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

  @Inject lateinit var configStore: WidgetConfigStore
  @Inject lateinit var widgetUpdater: WidgetUpdater

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setResult(Activity.RESULT_CANCELED)

    (rootGraph as WidgetGraph).inject(this)

    val intent = intent
    val extras = intent.extras
    if (extras != null) {
      appWidgetId = extras.getInt(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID,
      )
    }

    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
      finish()
      return
    }

    setContent {
      VoiceTheme {
        Scaffold(
          topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
              title = { Text("Widget Settings") },
            )
          },
        ) { padding: PaddingValues ->
          var alpha by remember { mutableStateOf(configStore.getAlpha(appWidgetId) / 255f) }
          var scale by remember { mutableStateOf(configStore.getTextScale(appWidgetId)) }

          Column(
            modifier = Modifier
              .padding(padding)
              .padding(16.dp)
              .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
          ) {
            Column {
              Text(
                text = "Background Transparency: ${(alpha * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
              )
              Slider(
                value = alpha,
                onValueChange = { alpha = it },
                valueRange = 0f..1f,
              )
            }

            Column {
              Text(
                text = "Text Size Scale: ${String.format("%.1f", scale)}x",
                style = MaterialTheme.typography.titleMedium,
              )
              Slider(
                value = scale,
                onValueChange = { scale = it },
                valueRange = 0.5f..2f,
              )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
              modifier = Modifier.fillMaxWidth(),
              onClick = {
                val alphaInt = (alpha * 255).roundToInt().coerceIn(0, 255)
                configStore.setAlpha(appWidgetId, alphaInt)
                configStore.setTextScale(appWidgetId, scale)

                // update the widget immediately
                widgetUpdater.update()

                val resultValue = Intent().apply {
                  putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                setResult(Activity.RESULT_OK, resultValue)
                finish()
              },
            ) {
              Text("Save")
            }
          }
        }
      }
    }
  }
}
