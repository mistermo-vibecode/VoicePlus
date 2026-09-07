package voice.features.bookOverview.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.TransformResult
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import voice.core.strings.R as StringsR

/**
 * The "finished" stamp on a library card: Material 3 Expressive's nine-sided cookie, in the
 * primary container role so it follows the same dynamic hue as the progress bar it replaces.
 *
 * The polygon is `MaterialShapes.Cookie9Sided` rebuilt on `graphics-shapes` directly, because
 * the material3 1.4 line on the BOM ships the Expressive opt-in but not the shape catalogue.
 */
@Composable
internal fun CompletedMedal(
  size: Dp,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .size(size)
      .clip(CookieShape)
      .background(MaterialTheme.colorScheme.primaryContainer),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Filled.Check,
      contentDescription = stringResource(StringsR.string.book_header_completed),
      tint = MaterialTheme.colorScheme.onPrimaryContainer,
      modifier = Modifier.size(size * 0.55f),
    )
  }
}

// Same recipe as material3's MaterialShapes.cookie9(): a 9-point star with the inner radius at
// 80% and half-rounded corners, turned so a lobe points straight up, then normalized to a unit box.
private fun cookie9Sided(): RoundedPolygon {
  return RoundedPolygon.star(
    numVerticesPerRadius = 9,
    innerRadius = 0.8f,
    rounding = CornerRounding(radius = 0.5f),
  )
    .transformed { x, y -> TransformResult(y, -x) }
    .normalized()
}

// Parameter-free and immutable, so one instance serves every card.
private val CookieShape: Shape = RoundedPolygonShape(cookie9Sided())

private class RoundedPolygonShape(polygon: RoundedPolygon) : Shape {
  private val unitPath: Path = polygon.toPath().asComposePath()

  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val path = Path().apply { addPath(unitPath) }
    path.transform(Matrix().apply { scale(x = size.width, y = size.height) })
    path.translate(size.center - path.getBounds().center)
    return Outline.Generic(path)
  }
}
