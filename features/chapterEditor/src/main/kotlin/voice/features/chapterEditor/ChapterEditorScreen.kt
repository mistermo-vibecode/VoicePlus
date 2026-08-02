package voice.features.chapterEditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.core.strings.R as StringsR

@Composable
public fun ChapterEditorScreen(viewModel: ChapterEditorViewModel) {
  val viewState = viewModel.viewState() ?: return
  ChapterEditorContent(
    viewState = viewState,
    onBack = viewModel::onBack,
    onOffsetDecrement = viewModel::onOffsetDecrement,
    onOffsetIncrement = viewModel::onOffsetIncrement,
    onOffsetSet = viewModel::onOffsetSet,
    onEditChapterClick = viewModel::onEditChapterClick,
    onDeleteOverride = viewModel::onDeleteOverride,
    onResetAllClick = viewModel::onResetAllClick,
    onEditConfirm = viewModel::onEditConfirm,
    onEditDismiss = viewModel::onEditDismiss,
    onResetAllConfirm = viewModel::onResetAllConfirm,
    onResetAllDismiss = viewModel::onResetAllDismiss,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterEditorContent(
  viewState: ChapterEditorViewState,
  onBack: () -> Unit,
  onOffsetDecrement: () -> Unit,
  onOffsetIncrement: () -> Unit,
  onOffsetSet: (Int) -> Unit,
  onEditChapterClick: (ChapterItemState) -> Unit,
  onDeleteOverride: (ChapterItemState) -> Unit,
  onResetAllClick: () -> Unit,
  onEditConfirm: (ChapterItemState, String) -> Unit,
  onEditDismiss: () -> Unit,
  onResetAllConfirm: () -> Unit,
  onResetAllDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(StringsR.string.edit_chapter_names)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = stringResource(StringsR.string.close),
            )
          }
        },
        actions = {
          Box {
            IconButton(onClick = { menuExpanded = true }) {
              Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(StringsR.string.more),
              )
            }
            DropdownMenu(
              expanded = menuExpanded,
              onDismissRequest = { menuExpanded = false },
            ) {
              DropdownMenuItem(
                text = { Text(text = stringResource(StringsR.string.chapter_editor_reset_all)) },
                onClick = {
                  menuExpanded = false
                  onResetAllClick()
                },
              )
            }
          }
        },
      )
    },
  ) { contentPadding ->
    val listState = rememberLazyListState()

    // Scroll to the current chapter once, on open. Keyed on Unit with the initial index captured,
    // so playback advancing across chapters doesn't yank the list away while the user is editing.
    val initialChapterIndex = remember { viewState.currentChapterIndex }
    LaunchedEffect(Unit) {
      listState.animateScrollToItem(initialChapterIndex.coerceAtLeast(0))
    }

    // The offset control is pinned above the scrolling chapter list so it stays reachable no
    // matter how far the user scrolls.
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(top = contentPadding.calculateTopPadding()),
    ) {
      OffsetRow(
        offset = viewState.offset,
        onDecrement = onOffsetDecrement,
        onIncrement = onOffsetIncrement,
        onOffsetSet = onOffsetSet,
        modifier = Modifier
          .padding(horizontal = 16.dp, vertical = 8.dp),
      )

      Text(
        text = stringResource(StringsR.string.chapter_editor_chapters),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
          .padding(horizontal = 16.dp, vertical = 12.dp),
      )

      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(
          bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
      ) {
        // Chapter items
        itemsIndexed(
          items = viewState.chapters,
          key = { _, item -> "${item.chapterId.value}_${item.markStartMs}" },
        ) { index, item ->
          ChapterRow(
            item = item,
            onEdit = { onEditChapterClick(item) },
            modifier = Modifier.padding(horizontal = 16.dp),
          )
          if (index < viewState.chapters.lastIndex) {
            HorizontalDivider(
              modifier = Modifier.padding(start = 64.dp, end = 16.dp),
              color = MaterialTheme.colorScheme.outlineVariant,
            )
          }
        }
      }
    }
  }

  // Edit chapter dialog
  val editingChapter = viewState.editingChapter
  if (editingChapter != null) {
    EditChapterDialog(
      item = editingChapter,
      onConfirm = { text -> onEditConfirm(editingChapter, text) },
      onDismiss = onEditDismiss,
      onRestore = if (editingChapter.hasOverride) {
        {
          onDeleteOverride(editingChapter)
          onEditDismiss()
        }
      } else {
        null
      },
    )
  }

  // Reset all confirm dialog
  if (viewState.showResetConfirm) {
    AlertDialog(
      onDismissRequest = onResetAllDismiss,
      title = { Text(text = stringResource(StringsR.string.chapter_editor_reset_confirm_title)) },
      text = {
        Text(text = stringResource(StringsR.string.chapter_editor_reset_confirm_message))
      },
      confirmButton = {
        TextButton(onClick = onResetAllConfirm) {
          Text(text = stringResource(StringsR.string.chapter_editor_reset_confirm_button))
        }
      },
      dismissButton = {
        TextButton(onClick = onResetAllDismiss) {
          Text(text = stringResource(StringsR.string.dialog_cancel))
        }
      },
    )
  }
}

@Composable
private fun OffsetRow(
  offset: Int,
  onDecrement: () -> Unit,
  onIncrement: () -> Unit,
  onOffsetSet: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  var editing by remember { mutableStateOf(false) }
  var editText by remember(offset) { mutableStateOf(offset.toString()) }

  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = MaterialTheme.shapes.large,
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(StringsR.string.chapter_offset_label),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
      )
      OutlinedIconButton(onClick = onDecrement) {
        Icon(
          imageVector = Icons.Outlined.Remove,
          contentDescription = stringResource(StringsR.string.chapter_editor_offset_decrement),
        )
      }
      if (editing) {
        OutlinedTextField(
          value = editText,
          onValueChange = { editText = it },
          modifier = Modifier.width(72.dp),
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(
            onDone = {
              val parsed = editText.toIntOrNull()
              if (parsed != null) onOffsetSet(parsed)
              editing = false
            },
          ),
        )
      } else {
        TextButton(onClick = { editing = true }) {
          Text(
            text = if (offset > 0) "+$offset" else offset.toString(),
            style = MaterialTheme.typography.titleLarge,
          )
        }
      }
      OutlinedIconButton(onClick = onIncrement) {
        Icon(
          imageVector = Icons.Outlined.Add,
          contentDescription = stringResource(StringsR.string.chapter_editor_offset_increment),
        )
      }
    }
  }
}

@Composable
private fun ChapterRow(
  item: ChapterItemState,
  onEdit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val status = when {
    item.isCurrent && item.hasOverride -> stringResource(StringsR.string.chapter_editor_playing_custom_name)
    item.isCurrent -> stringResource(StringsR.string.chapter_editor_playing)
    item.hasOverride -> stringResource(StringsR.string.chapter_editor_custom_name)
    else -> null
  }

  ListItem(
    modifier = modifier
      .then(if (item.isCurrent) Modifier.clip(MaterialTheme.shapes.large) else Modifier),
    colors = ListItemDefaults.colors(
      containerColor = if (item.isCurrent) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surface
      },
    ),
    leadingContent = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (item.isCurrent) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
          )
        }
        Text(
          text = item.displayNumber.toString(),
          style = MaterialTheme.typography.titleMedium,
        )
      }
    },
    headlineContent = {
      Text(
        text = item.displayName,
        color = if (item.isCurrent) {
          MaterialTheme.colorScheme.onPrimaryContainer
        } else {
          MaterialTheme.colorScheme.onSurface
        },
      )
    },
    supportingContent = status?.let {
      {
        Text(
          text = it,
          color = if (item.isCurrent) {
            MaterialTheme.colorScheme.onPrimaryContainer
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        )
      }
    },
    trailingContent = {
      IconButton(onClick = onEdit) {
        Icon(
          imageVector = Icons.Default.Edit,
          contentDescription = stringResource(StringsR.string.chapter_editor_edit_name),
          tint = if (item.isCurrent) {
            MaterialTheme.colorScheme.onPrimaryContainer
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        )
      }
    },
  )
}

@Composable
private fun EditChapterDialog(
  item: ChapterItemState,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
  onRestore: (() -> Unit)?,
) {
  var text by remember(item) { mutableStateOf(item.displayName) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(StringsR.string.chapter_editor_edit_name)) },
    text = {
      Column {
        OutlinedTextField(
          value = text,
          onValueChange = { if (it.length <= 200) text = it },
          label = { Text(text = stringResource(StringsR.string.chapter_name_label)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (text.length >= 180) {
          Text(
            text = stringResource(StringsR.string.chapter_name_length_counter, text.length),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
              .align(Alignment.End)
              .padding(top = 4.dp),
          )
        }
        onRestore?.let {
          TextButton(
            onClick = it,
            modifier = Modifier.align(Alignment.Start),
          ) {
            Text(text = stringResource(StringsR.string.chapter_editor_restore_original_name))
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onConfirm(text) },
        enabled = text.isNotBlank(),
      ) {
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

@ContributesTo(AppScope::class)
public interface ChapterEditorProvider {
  @Provides
  @IntoSet
  public fun chapterEditorNavEntryProvider(factory: ChapterEditorViewModel.Factory): NavEntryProvider<*> =
    NavEntryProvider<Destination.ChapterEditor> { key ->
      NavEntry(key) {
        val viewModel = remember(key) { factory.create(key.bookId) }
        ChapterEditorScreen(viewModel = viewModel)
      }
    }
}
