package voice.features.playbackScreen.view

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import voice.core.data.PlaybackToolbarAction
import voice.core.strings.R as StringsR

@Composable
internal fun OverflowMenu(
  skipSilence: Boolean,
  onSkipSilenceClick: () -> Unit,
  onVolumeBoostClick: () -> Unit,
  overflowActions: List<PlaybackToolbarAction>,
  onActionClick: (PlaybackToolbarAction) -> Unit,
  onQuickBookmarkClick: (() -> Unit)?,
  onCustomizeToolbarClick: () -> Unit,
) {
  Box {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
      onClick = {
        expanded = !expanded
      },
    ) {
      Icon(
        imageVector = Icons.Outlined.MoreVert,
        contentDescription = stringResource(id = StringsR.string.more),
      )
    }
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
    ) {
      DropdownMenuItem(
        onClick = {
          expanded = false
          onSkipSilenceClick()
        },
        leadingIcon = {
          Icon(imageVector = Icons.Outlined.GraphicEq, contentDescription = null)
        },
        text = {
          Text(text = stringResource(id = StringsR.string.skip_silence))
        },
        trailingIcon = {
          Checkbox(
            checked = skipSilence,
            onCheckedChange = {
              expanded = false
              onSkipSilenceClick()
            },
          )
        },
      )
      DropdownMenuItem(
        onClick = {
          expanded = false
          onVolumeBoostClick()
        },
        leadingIcon = {
          Icon(imageVector = Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null)
        },
        text = {
          Text(text = stringResource(id = StringsR.string.volume_boost))
        },
      )
      overflowActions.forEach { action ->
        DropdownMenuItem(
          onClick = {
            expanded = false
            onActionClick(action)
          },
          leadingIcon = {
            Icon(imageVector = action.icon, contentDescription = null)
          },
          text = {
            Text(text = stringResource(id = action.labelRes))
          },
        )
      }
      onQuickBookmarkClick?.let { onClick ->
        DropdownMenuItem(
          onClick = {
            expanded = false
            onClick()
          },
          leadingIcon = {
            Icon(imageVector = Icons.Outlined.BookmarkAdd, contentDescription = null)
          },
          text = {
            Text(text = stringResource(id = StringsR.string.bookmark_type_quick))
          },
        )
      }
      HorizontalDivider()
      DropdownMenuItem(
        onClick = {
          expanded = false
          onCustomizeToolbarClick()
        },
        leadingIcon = {
          Icon(imageVector = Icons.Outlined.Tune, contentDescription = null)
        },
        text = {
          Text(text = stringResource(id = StringsR.string.playback_toolbar_customize))
        },
      )
    }
  }
}
