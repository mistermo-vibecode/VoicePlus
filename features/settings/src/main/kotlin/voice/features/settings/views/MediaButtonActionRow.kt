package voice.features.settings.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import voice.core.data.MediaButtonClickAction
import voice.core.strings.R as StringsR

@Composable
internal fun MediaButtonActionRow(
  title: String,
  currentAction: MediaButtonClickAction,
  onClick: () -> Unit,
) {
  ListItem(
    modifier = Modifier
      .clickable { onClick() }
      .fillMaxWidth(),
    leadingContent = {
      Icon(
        imageVector = Icons.Outlined.TouchApp,
        contentDescription = title,
      )
    },
    headlineContent = {
      Text(text = title)
    },
    supportingContent = {
      Text(text = stringResource(currentAction.toLabelRes()))
    },
  )
}

@Composable
internal fun MediaButtonActionDialog(
  title: String,
  currentAction: MediaButtonClickAction,
  onActionConfirm: (MediaButtonClickAction) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = title) },
    text = {
      Column(Modifier.selectableGroup()) {
        MediaButtonClickAction.entries.forEach { action ->
          Row(
            Modifier
              .fillMaxWidth()
              .selectable(
                selected = (action == currentAction),
                onClick = { onActionConfirm(action) },
                role = Role.RadioButton,
              ),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(
              selected = (action == currentAction),
              onClick = null,
            )
            Text(text = stringResource(action.toLabelRes()))
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

private fun MediaButtonClickAction.toLabelRes(): Int = when (this) {
  MediaButtonClickAction.NONE -> StringsR.string.media_button_action_none
  MediaButtonClickAction.SKIP_FORWARD -> StringsR.string.media_button_action_skip_forward
  MediaButtonClickAction.SKIP_BACKWARD -> StringsR.string.media_button_action_skip_backward
  MediaButtonClickAction.SKIP_FORWARD_CHAPTER -> StringsR.string.media_button_action_skip_forward_chapter
  MediaButtonClickAction.SKIP_BACKWARD_CHAPTER -> StringsR.string.media_button_action_skip_backward_chapter
  MediaButtonClickAction.QUICK_BOOKMARK -> StringsR.string.media_button_action_quick_bookmark
}
