package voice.features.settings.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.ui.VoiceTheme
import voice.core.ui.rememberScoped
import voice.features.settings.SettingsListener
import voice.features.settings.SettingsViewModel
import voice.features.settings.SettingsViewState
import voice.features.settings.views.sleeptimer.AutoSleepTimerCard
import voice.features.settings.views.sleeptimer.AutoSleepTimerDurationDialog
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.core.strings.R as StringsR

@Composable
@Preview
private fun SettingsPreview() {
  VoiceTheme {
    Settings(
      SettingsViewState.preview(),
      SettingsListener.noop(),
    )
  }
}

@Composable
private fun Settings(
  viewState: SettingsViewState,
  listener: SettingsListener,
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      TopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
          Text(stringResource(StringsR.string.action_settings))
        },
        navigationIcon = {
          IconButton(
            onClick = {
              listener.close()
            },
          ) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = stringResource(StringsR.string.close),
            )
          }
        },
      )
    },
  ) { contentPadding ->
    LazyColumn(contentPadding = contentPadding) {
      if (viewState.showFolderPickerEntry) {
        item {
          ListItem(
            modifier = Modifier.clickable { listener.openFolderPicker() },
            leadingContent = {
              Icon(
                imageVector = Icons.Outlined.Book,
                contentDescription = stringResource(StringsR.string.audiobook_folders_title),
              )
            },
            headlineContent = {
              Text(stringResource(StringsR.string.audiobook_folders_title))
            },
            supportingContent = {
              Text(stringResource(StringsR.string.pref_audiobook_folders_explanation))
            },
          )
        }
      }
      if (viewState.showDarkThemePref) {
        item {
          DarkThemeRow(viewState.useDarkTheme, listener::toggleDarkTheme)
        }
      }
      item {
        ListItem(
          modifier = Modifier.clickable { listener.toggleGrid() },
          leadingContent = {
            val imageVector = if (viewState.useGrid) {
              Icons.Outlined.GridView
            } else {
              Icons.AutoMirrored.Outlined.ViewList
            }
            Icon(imageVector, stringResource(StringsR.string.pref_use_grid))
          },
          headlineContent = { Text(stringResource(StringsR.string.pref_use_grid)) },
          trailingContent = {
            Switch(
              checked = viewState.useGrid,
              onCheckedChange = {
                listener.toggleGrid()
              },
            )
          },
        )
      }

      item {
        SeekTimeRow(viewState.seekTimeInSeconds) {
          listener.onSeekAmountRowClick()
        }
      }

      item {
        AutoRewindRow(viewState.autoRewindInSeconds) {
          listener.onAutoRewindRowClick()
        }
      }

      item {
        AutoSleepTimerCard(viewState.autoSleepTimer, listener)
      }
      item {
        MediaButtonActionRow(
          title = stringResource(StringsR.string.pref_media_button_double_click),
          currentAction = viewState.mediaButtonDoubleClickAction,
          onClick = listener::onMediaButtonDoubleClickRowClick,
        )
      }
      item {
        MediaButtonActionRow(
          title = stringResource(StringsR.string.pref_media_button_triple_click),
          currentAction = viewState.mediaButtonTripleClickAction,
          onClick = listener::onMediaButtonTripleClickRowClick,
        )
      }

      item {
        ListItem(
          modifier = Modifier.clickable { listener.openListeningStats() },
          leadingContent = {
            Icon(
              imageVector = Icons.Outlined.BarChart,
              contentDescription = stringResource(StringsR.string.listening_stats),
            )
          },
          headlineContent = {
            Text(stringResource(StringsR.string.listening_stats))
          },
        )
      }

      item {
        ListItem(
          modifier = Modifier.clickable { listener.openHiddenBooks() },
          leadingContent = {
            Icon(
              imageVector = Icons.Outlined.VisibilityOff,
              contentDescription = stringResource(StringsR.string.hidden_books),
            )
          },
          headlineContent = {
            Text(stringResource(StringsR.string.hidden_books))
          },
        )
      }

      item {
        AppVersion(
          appVersion = viewState.appVersion,
          onClick = listener::onAppVersionClick,
        )
      }
    }
    Dialog(viewState, listener)
  }
}

@ContributesTo(AppScope::class)
interface SettingsGraph {
  val settingsViewModel: SettingsViewModel
}

@ContributesTo(AppScope::class)
interface SettingsProvider {

  @Provides
  @IntoSet
  fun settingsNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.Settings> { key ->
    NavEntry(key) {
      Settings()
    }
  }
}

@Composable
fun Settings() {
  val viewModel = rememberScoped { rootGraphAs<SettingsGraph>().settingsViewModel }
  val viewState = viewModel.viewState()
  Settings(viewState, viewModel)
}

@Composable
private fun Dialog(
  viewState: SettingsViewState,
  listener: SettingsListener,
) {
  val dialog = viewState.dialog ?: return
  when (dialog) {
    SettingsViewState.Dialog.AutoRewindAmount -> {
      AutoRewindAmountDialog(
        currentSeconds = viewState.autoRewindInSeconds,
        onSecondsConfirm = listener::autoRewindAmountChang,
        onDismiss = listener::dismissDialog,
      )
    }
    SettingsViewState.Dialog.SeekTime -> {
      SeekAmountDialog(
        currentSeconds = viewState.seekTimeInSeconds,
        onSecondsConfirm = listener::seekAmountChanged,
        onDismiss = listener::dismissDialog,
      )
    }
    SettingsViewState.Dialog.AutoSleepTimerDuration -> {
      AutoSleepTimerDurationDialog(
        initialDurationMinutes = viewState.autoSleepTimer.duration.inWholeMinutes.toInt(),
        onConfirm = { minutes ->
          listener.setAutoSleepTimerDuration(minutes)
          listener.dismissDialog()
        },
        onDismiss = listener::dismissDialog,
      )
    }
    SettingsViewState.Dialog.MediaButtonDoubleClickAction -> {
      MediaButtonActionDialog(
        title = stringResource(StringsR.string.pref_media_button_double_click),
        currentAction = viewState.mediaButtonDoubleClickAction,
        onActionConfirm = {
          listener.setMediaButtonDoubleClickAction(it)
          listener.dismissDialog()
        },
        onDismiss = listener::dismissDialog,
      )
    }
    SettingsViewState.Dialog.MediaButtonTripleAction -> {
      MediaButtonActionDialog(
        title = stringResource(StringsR.string.pref_media_button_triple_click),
        currentAction = viewState.mediaButtonTripleClickAction,
        onActionConfirm = {
          listener.setMediaButtonTripleClickAction(it)
          listener.dismissDialog()
        },
        onDismiss = listener::dismissDialog,
      )
    }
  }
}
