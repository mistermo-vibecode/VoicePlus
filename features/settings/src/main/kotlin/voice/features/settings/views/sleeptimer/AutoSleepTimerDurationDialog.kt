package voice.features.settings.views.sleeptimer

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import voice.core.strings.R as StringsR

@Composable
internal fun AutoSleepTimerDurationDialog(
  initialDurationMinutes: Int,
  onConfirm: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  var duration by remember { mutableIntStateOf(initialDurationMinutes) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(text = stringResource(StringsR.string.auto_sleep_timer_duration_dialog_title))
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = pluralStringResource(StringsR.plurals.minutes, duration, duration),
          style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.size(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          ContinuousPressIcon(
            onEmit = { if (duration > 1) duration-- },
            icon = Icons.Outlined.Remove,
            contentDescription = stringResource(StringsR.string.sleep_timer_button_decrement),
          )
          Spacer(modifier = Modifier.size(24.dp))
          ContinuousPressIcon(
            onEmit = { duration++ },
            icon = Icons.Outlined.Add,
            contentDescription = stringResource(StringsR.string.sleep_timer_button_increment),
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(duration) }) {
        Text(text = stringResource(StringsR.string.dialog_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(StringsR.string.dialog_cancel))
      }
    },
  )
}

@Composable
private fun ContinuousPressIcon(
  onEmit: () -> Unit,
  icon: ImageVector,
  contentDescription: String,
  modifier: Modifier = Modifier,
) {
  var isPressed by remember { mutableStateOf(false) }

  LaunchedEffect(isPressed, onEmit) {
    if (isPressed) {
      delay(500)
      while (isPressed) {
        onEmit()
        delay(100)
      }
    }
  }
  val interactionSource = remember { MutableInteractionSource() }
  Icon(
    imageVector = icon,
    contentDescription = contentDescription,
    modifier = modifier
      .size(64.dp)
      .combinedClickable(
        interactionSource = interactionSource,
        indication = ripple(),
        onClick = onEmit,
        onLongClick = { isPressed = true },
      )
      .padding(16.dp),
  )
  LaunchedEffect(interactionSource) {
    interactionSource.interactions.collect { interaction ->
      if (interaction is PressInteraction.Release ||
        interaction is PressInteraction.Cancel
      ) {
        isPressed = false
      }
    }
  }
}
