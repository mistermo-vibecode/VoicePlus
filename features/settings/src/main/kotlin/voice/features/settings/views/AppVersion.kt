package voice.features.settings.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import voice.core.strings.R as StringsR

@Composable
internal fun AppVersion(
  appVersion: String,
  onClick: () -> Unit,
) {
  ListItem(
    modifier = Modifier
      .fillMaxWidth()
      .clickable {
        onClick()
      },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    leadingContent = {
      Icon(
        imageVector = Icons.Outlined.Tag,
        contentDescription = stringResource(StringsR.string.pref_app_version),
      )
    },
    headlineContent = { Text(text = stringResource(StringsR.string.pref_app_version)) },
    supportingContent = {
      Text(
        text = "Based on Voice by Paul Woitaschek",
        color = LocalContentColor.current.copy(alpha = 0.7F),
        style = MaterialTheme.typography.bodySmall,
      )
    },
    trailingContent = {
      Text(
        text = appVersion,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
  )
}
