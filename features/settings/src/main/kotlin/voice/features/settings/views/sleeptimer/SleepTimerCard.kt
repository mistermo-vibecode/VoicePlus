package voice.features.settings.views.sleeptimer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import voice.core.ui.VoiceTheme
import voice.features.settings.SettingsListener
import voice.features.settings.SettingsViewState
import voice.core.strings.R as StringsR

@Composable
internal fun SleepTimerCard(
  autoSleepTimer: SettingsViewState.AutoSleepTimerViewState,
  autoResetEnabled: Boolean,
  listener: SettingsListener,
) {
  OutlinedCard(modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
      AutoSleepTimerRow(
        autoSleepTimer = autoSleepTimer.enabled,
        start = autoSleepTimer.startTime,
        end = autoSleepTimer.endTime,
        toggleAutoSleepTimer = listener::setAutoSleepTimer,
      )
      Row(Modifier.padding(start = 44.dp, end = 8.dp)) {
        AutoSleepTimerSetting(
          time = autoSleepTimer.startTime,
          label = stringResource(StringsR.string.auto_sleep_timer_start),
          enabled = autoSleepTimer.enabled,
          setAutoSleepTime = listener::setAutoSleepTimerStart,
        )
        AutoSleepTimerSetting(
          time = autoSleepTimer.endTime,
          label = stringResource(StringsR.string.auto_sleep_timer_end),
          enabled = autoSleepTimer.enabled,
          setAutoSleepTime = listener::setAutoSleepTimerEnd,
        )
      }
      HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
      )
      ListItem(
        modifier = Modifier.clickable(enabled = autoSleepTimer.enabled) {
          listener.onAutoSleepTimerDurationClick()
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
          Text(text = stringResource(StringsR.string.auto_sleep_timer_duration_label))
        },
        trailingContent = {
          Text(
            text = pluralStringResource(
              StringsR.plurals.minutes,
              autoSleepTimer.duration.inWholeMinutes.toInt(),
              autoSleepTimer.duration.inWholeMinutes.toInt(),
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = if (autoSleepTimer.enabled) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
          )
        },
      )
      HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
      )
      ListItem(
        modifier = Modifier.clickable { listener.setSleepTimerAutoReset(!autoResetEnabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
          Text(text = stringResource(StringsR.string.pref_sleep_timer_auto_reset))
        },
        trailingContent = {
          Row {
            IconButton(onClick = listener::onSleepTimerAutoResetInfoClick) {
              Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(StringsR.string.pref_sleep_timer_info),
              )
            }
            Switch(
              checked = autoResetEnabled,
              onCheckedChange = listener::setSleepTimerAutoReset,
            )
          }
        },
      )
    }
  }
}

@Composable
@Preview
private fun SleepTimerCardPreview() {
  VoiceTheme {
    SleepTimerCard(
      autoSleepTimer = SettingsViewState.AutoSleepTimerViewState.preview(),
      autoResetEnabled = true,
      listener = SettingsListener.noop(),
    )
  }
}
