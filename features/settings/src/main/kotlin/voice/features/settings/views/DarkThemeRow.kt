package voice.features.settings.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import voice.core.strings.R as StringsR

@Composable
internal fun DarkThemeRow(
  useDarkTheme: Boolean,
  toggle: () -> Unit,
) {
  ListItem(
    modifier = Modifier
      .clickable {
        toggle()
      }
      .fillMaxWidth(),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    leadingContent = {
      Icon(Icons.Outlined.DarkMode, contentDescription = null)
    },
    headlineContent = {
      Text(text = stringResource(StringsR.string.pref_theme_dark))
    },
    trailingContent = {
      Switch(
        checked = useDarkTheme,
        onCheckedChange = {
          toggle()
        },
      )
    },
  )
}
