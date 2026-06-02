package voice.features.playbackScreen.view

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import voice.core.strings.R as StringsR

@Composable
internal fun OverflowMenu(
  skipSilence: Boolean,
  onSkipSilenceClick: () -> Unit,
  onVolumeBoostClick: () -> Unit,
  onListeningLogClick: () -> Unit,
  onCharacterListClick: () -> Unit,
  onEditChapterNamesClick: (() -> Unit)? = null,
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
        text = {
          Text(text = stringResource(id = StringsR.string.volume_boost))
        },
      )
      DropdownMenuItem(
        onClick = {
          expanded = false
          onListeningLogClick()
        },
        text = {
          Text(text = stringResource(id = StringsR.string.listening_log))
        },
      )
      DropdownMenuItem(
        onClick = {
          expanded = false
          onCharacterListClick()
        },
        text = {
          Text(text = stringResource(id = StringsR.string.character_list))
        },
      )
      onEditChapterNamesClick?.let { onClick ->
        DropdownMenuItem(
          text = { Text(stringResource(StringsR.string.edit_chapter_names)) },
          onClick = {
            expanded = false
            onClick()
          },
        )
      }
    }
  }
}
