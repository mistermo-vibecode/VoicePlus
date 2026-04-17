package voice.features.bookOverview.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import voice.features.bookOverview.overview.BookOverviewCategory
import voice.core.strings.R as StringsR

@Composable
internal fun Header(
  category: BookOverviewCategory,
  modifier: Modifier = Modifier,
  bookCount: Int = 0,
  expanded: Boolean = true,
  onToggle: (() -> Unit)? = null,
) {
  val title = stringResource(id = category.nameRes)
  if (onToggle == null) {
    Text(
      modifier = modifier,
      text = title,
      style = MaterialTheme.typography.headlineSmall,
    )
  } else {
    val toggleLabel = stringResource(
      if (expanded) {
        StringsR.string.book_header_collapse_section
      } else {
        StringsR.string.book_header_expand_section
      },
      title,
    )
    Row(
      modifier = modifier
        .fillMaxWidth()
        .clickable(
          onClick = onToggle,
          onClickLabel = toggleLabel,
          role = Role.Button,
        ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.headlineSmall,
        )
        if (!expanded && bookCount > 0) {
          Text(
            text = " (${bookCount})",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Icon(
        imageVector = Icons.Outlined.ExpandMore,
        contentDescription = null,
        modifier = Modifier.rotate(if (expanded) 180f else 0f),
      )
    }
  }
}
