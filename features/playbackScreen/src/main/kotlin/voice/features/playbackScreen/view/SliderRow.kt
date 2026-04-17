package voice.features.playbackScreen.view

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import voice.core.ui.formatTime
import kotlin.time.Duration

@Composable
internal fun SliderRow(
  duration: Duration,
  playedTime: Duration,
  onSeek: (Duration) -> Unit,
  enabled: Boolean = true,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    var localValue by remember { mutableFloatStateOf(0F) }
    var isSeeking by remember { mutableStateOf(false) }
    Text(
      text = formatTime(
        timeMs = if (isSeeking) {
          (duration * localValue.toDouble()).inWholeMilliseconds
        } else {
          playedTime.inWholeMilliseconds
        },
        durationMs = duration.inWholeMilliseconds,
      ),
    )
    Slider(
      modifier = Modifier
        .weight(1F)
        .padding(horizontal = 8.dp),
      enabled = enabled,
      value = if (isSeeking) {
        localValue
      } else {
        (playedTime / duration).toFloat()
          .coerceIn(0F, 1F)
      },
      onValueChange = {
        isSeeking = true
        localValue = it
      },
      onValueChangeFinished = {
        onSeek(duration * localValue.toDouble())
        isSeeking = false
      },
    )
    Text(
      text = formatTime(
        timeMs = duration.inWholeMilliseconds,
        durationMs = duration.inWholeMilliseconds,
      ),
    )
  }
}
