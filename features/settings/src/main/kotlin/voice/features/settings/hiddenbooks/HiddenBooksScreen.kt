package voice.features.settings.hiddenbooks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.ui.rememberScoped
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface HiddenBooksGraph {
  val hiddenBooksViewModel: HiddenBooksViewModel
}

@ContributesTo(AppScope::class)
interface HiddenBooksProvider {

  @Provides
  @IntoSet
  fun hiddenBooksNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.HiddenBooks> { key ->
    NavEntry(key) {
      HiddenBooksScreen()
    }
  }
}

@Composable
fun HiddenBooksScreen() {
  val viewModel = rememberScoped { rootGraphAs<HiddenBooksGraph>().hiddenBooksViewModel }
  val viewState = viewModel.viewState()
  HiddenBooksScreen(
    viewState = viewState,
    onClose = viewModel::onClose,
    onRestore = viewModel::restore,
    onRestoreAll = viewModel::restoreAll,
  )
}

@Composable
internal fun HiddenBooksScreen(
  viewState: HiddenBooksViewState,
  onClose: () -> Unit,
  onRestore: (id: String) -> Unit,
  onRestoreAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(StringsR.string.hidden_books)) },
        navigationIcon = {
          IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(StringsR.string.close))
          }
        },
        actions = {
          if (viewState.books.isNotEmpty()) {
            TextButton(onClick = onRestoreAll) {
              Text(stringResource(StringsR.string.hidden_books_restore_all))
            }
          }
        },
      )
    },
  ) { paddingValues ->
    if (viewState.books.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(32.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(StringsR.string.hidden_books_empty),
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      LazyColumn(contentPadding = paddingValues) {
        items(items = viewState.books, key = { it.id }) { book ->
          ListItem(
            headlineContent = { Text(book.name) },
            trailingContent = {
              IconButton(onClick = { onRestore(book.id) }) {
                Icon(
                  imageVector = Icons.Outlined.RestoreFromTrash,
                  contentDescription = stringResource(StringsR.string.hidden_books_restore),
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            },
          )
        }
        item { Spacer(Modifier.size(16.dp)) }
      }
    }
  }
}
