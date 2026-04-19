package voice.features.settings.views

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.navigation.Navigator
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface OpenSourceLicensesGraph {
  val navigator: Navigator
}

@ContributesTo(AppScope::class)
interface OpenSourceLicensesProvider {
  @Provides
  @IntoSet
  fun openSourceLicensesNavEntryProvider(): NavEntryProvider<*> =
    NavEntryProvider<Destination.OpenSourceLicenses> { key ->
      NavEntry(key) {
        OpenSourceLicensesScreen()
      }
    }
}

@Composable
fun OpenSourceLicensesScreen() {
  val navigator = rootGraphAs<OpenSourceLicensesGraph>().navigator
  OpenSourceLicensesScreen(onClose = navigator::goBack)
}

@Composable
internal fun OpenSourceLicensesScreen(onClose: () -> Unit) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(StringsR.string.open_source_licenses)) },
        navigationIcon = {
          IconButton(onClick = onClose) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = stringResource(StringsR.string.close),
            )
          }
        },
      )
    },
  ) { paddingValues ->
    LibrariesContainer(contentPadding = paddingValues)
  }
}
