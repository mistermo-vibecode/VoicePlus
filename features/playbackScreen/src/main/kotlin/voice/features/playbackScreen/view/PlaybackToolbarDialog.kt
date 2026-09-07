package voice.features.playbackScreen.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import voice.core.data.PlaybackToolbarAction
import voice.core.strings.R as StringsR

/** Picks which [PlaybackToolbarAction]s are pinned as icons on the now-playing app bar. */
@Composable
internal fun PlaybackToolbarDialog(
  pinnedActions: Set<PlaybackToolbarAction>,
  onActionToggle: (PlaybackToolbarAction, Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(StringsR.string.playback_toolbar_customize)) },
    text = {
      Column {
        Text(
          text = stringResource(StringsR.string.playback_toolbar_description, PlaybackToolbarAction.MAX_PINNED),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 8.dp),
        )
        val atCapacity = pinnedActions.size >= PlaybackToolbarAction.MAX_PINNED
        PlaybackToolbarAction.entries.forEach { action ->
          val checked = action in pinnedActions
          val enabled = checked || !atCapacity
          Row(
            Modifier
              .fillMaxWidth()
              .toggleable(
                value = checked,
                enabled = enabled,
                onValueChange = { onActionToggle(action, it) },
                role = Role.Checkbox,
              ),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = checked,
              onCheckedChange = null,
              enabled = enabled,
            )
            Icon(
              imageVector = action.icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
              text = stringResource(action.labelRes),
              color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(StringsR.string.close))
      }
    },
  )
}
