package voice.features.settings.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsSection(
  title: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      modifier = Modifier.padding(horizontal = 16.dp),
      text = title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
    )
    content()
  }
}

@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(content = { content() })
  }
}

@Composable
internal fun SettingsDivider() {
  HorizontalDivider(
    modifier = Modifier.padding(start = 56.dp),
    color = MaterialTheme.colorScheme.outlineVariant,
  )
}

@Composable
internal fun PrioritySettingsItem(
  title: String,
  supportingText: String,
  icon: ImageVector,
  containerColor: Color,
  contentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    onClick = onClick,
    shape = MaterialTheme.shapes.extraLarge,
    colors = CardDefaults.cardColors(
      containerColor = containerColor,
      contentColor = contentColor,
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.large,
        color = contentColor.copy(alpha = 0.10F),
        contentColor = contentColor,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
          )
        }
      }
      PrioritySettingsText(
        title = title,
        supportingText = supportingText,
      )
      Icon(
        imageVector = Icons.Outlined.ChevronRight,
        contentDescription = null,
      )
    }
  }
}

@Composable
private fun RowScope.PrioritySettingsText(
  title: String,
  supportingText: String,
) {
  Column(modifier = Modifier.weight(1F)) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
    )
    Text(
      text = supportingText,
      style = MaterialTheme.typography.bodyMedium,
      color = LocalContentColor.current.copy(alpha = 0.80F),
    )
  }
}
