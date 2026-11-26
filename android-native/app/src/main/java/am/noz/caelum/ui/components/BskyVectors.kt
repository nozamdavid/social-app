package am.noz.caelum.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Builds a tintable 24-unit icon from the original Bluesky SVG paths. */
internal fun bskyVector(
    name: String,
    vararg paths: String,
    pathFillType: PathFillType = PathFillType.NonZero,
): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
    for (path in paths) {
        addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            pathFillType = pathFillType,
            fill = SolidColor(Color.Black),
        )
    }
}.build()
