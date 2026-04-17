package voice.features.bookOverview.deleteBook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import voice.core.strings.R as StringsR

@Composable
internal fun DeleteBookDialog(
  viewState: DeleteBookViewState,
  onDismiss: () -> Unit,
  onConfirmRemove: () -> Unit,
  onToggleDeleteFiles: (Boolean) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(stringResource(StringsR.string.remove_book_title))
    },
    confirmButton = {
      Button(
        onClick = onConfirmRemove,
        colors = if (viewState.alsoDeleteFiles) {
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.error,
          )
        } else {
          ButtonDefaults.buttonColors()
        },
      ) {
        Text(
          if (viewState.alsoDeleteFiles) {
            stringResource(StringsR.string.remove_book_and_delete)
          } else {
            stringResource(StringsR.string.remove_book_confirm)
          },
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(StringsR.string.dialog_cancel))
      }
    },
    text = {
      Column {
        Text(stringResource(StringsR.string.remove_book_description))

        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = viewState.fileToDelete,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = stringResource(StringsR.string.remove_book_restore_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleDeleteFiles(!viewState.alsoDeleteFiles) },
        ) {
          Checkbox(
            checked = viewState.alsoDeleteFiles,
            onCheckedChange = onToggleDeleteFiles,
          )
          Text(stringResource(StringsR.string.remove_book_also_delete_files))
        }
      }
    },
  )
}
