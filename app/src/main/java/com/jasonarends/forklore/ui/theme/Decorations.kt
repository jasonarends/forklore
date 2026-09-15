package com.jasonarends.forklore.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable hand-drawn-ledger decorations (issue #15, "Texture"/"Shape, borders, elevation"). These
 * are drawing primitives, not components — [ui.components] composes them onto real UI.
 */

/**
 * Horizontal ruled-paper lines behind content, spaced [spacing] apart. Used for the page background
 * (28dp) and inside note/opinion cards (23dp/22dp) — same primitive, different pitch.
 */
fun Modifier.ruledBackground(lineColor: Color, spacing: Dp, strokeWidth: Dp = 1.dp): Modifier =
  drawBehind {
    val spacingPx = spacing.toPx()
    val strokePx = strokeWidth.toPx()
    var y = spacingPx
    while (y < size.height) {
      drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = strokePx)
      y += spacingPx
    }
  }

/**
 * A hard offset shadow — a flat, unblurred copy of the shape behind content. Never
 * `Modifier.shadow`, which blurs: the mockup's depth is letterpress-style, always a crisp offset.
 */
fun Modifier.hardShadow(offsetX: Dp, offsetY: Dp, color: Color, cornerRadius: Dp = 0.dp): Modifier =
  drawBehind {
    drawRoundRect(
      color = color,
      topLeft = Offset(offsetX.toPx(), offsetY.toPx()),
      size = size,
      cornerRadius = CornerRadius(cornerRadius.toPx()),
    )
  }

/**
 * Rotates content around its own center — the Avoid/Never-again stamp and (in Wave 2) opinion
 * cards.
 */
fun Modifier.tilt(degrees: Float): Modifier = graphicsLayer { rotationZ = degrees }

/**
 * A hand-drawn-style ellipse around the selected rating word (issue #15's "circled rating"): the
 * selected value is circled, not filled or highlighted. Drawn after content, padded outward from
 * its bounds, rotated slightly for the hand-drawn feel.
 */
fun Modifier.circledSelection(
  color: Color,
  strokeWidth: Dp = 2.5.dp,
  horizontalPadding: Dp = 8.dp,
  verticalPadding: Dp = 7.dp,
  rotationDegrees: Float = -3f,
): Modifier = drawWithContent {
  drawContent()
  val padX = horizontalPadding.toPx()
  val padY = verticalPadding.toPx()
  rotate(rotationDegrees, pivot = center) {
    drawOval(
      color = color,
      topLeft = Offset(-padX, -padY),
      size = Size(size.width + padX * 2, size.height + padY * 2),
      style = Stroke(width = strokeWidth.toPx()),
    )
  }
}

/** A wavy stamp-colored underline under the selected revisit-intent word. */
fun Modifier.wavyUnderline(
  color: Color,
  strokeWidth: Dp = 1.5.dp,
  amplitude: Dp = 1.5.dp,
  wavelength: Dp = 6.dp,
): Modifier = drawBehind {
  val amplitudePx = amplitude.toPx()
  val wavelengthPx = wavelength.toPx()
  val y = size.height + amplitudePx
  val path =
    Path().apply {
      moveTo(0f, y)
      var x = 0f
      var up = true
      while (x < size.width) {
        val next = (x + wavelengthPx / 2).coerceAtMost(size.width)
        quadraticTo(x + wavelengthPx / 4, y + (if (up) -amplitudePx else amplitudePx), next, y)
        x = next
        up = !up
      }
    }
  drawPath(path, color = color, style = Stroke(width = strokeWidth.toPx()))
}

/**
 * A dashed rounded-rect outline — the warning [ui.components.NoteField] variant on place detail.
 */
fun Modifier.dashedBorder(
  color: Color,
  strokeWidth: Dp = 2.dp,
  cornerRadius: Dp = 3.dp,
  dashLength: Dp = 6.dp,
  gapLength: Dp = 4.dp,
): Modifier = drawBehind {
  val stroke =
    Stroke(
      width = strokeWidth.toPx(),
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), gapLength.toPx())),
    )
  drawRoundRect(color = color, cornerRadius = CornerRadius(cornerRadius.toPx()), style = stroke)
}
