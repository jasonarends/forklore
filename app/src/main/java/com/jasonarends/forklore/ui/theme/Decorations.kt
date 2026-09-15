package com.jasonarends.forklore.ui.theme

import androidx.compose.foundation.layout.padding
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
 * selected value is circled, not filled or highlighted. `drawWithContent` sits *outside* the
 * `padding` in this chain — the same ordering as the familiar `background().padding()` idiom — so
 * the oval's draw bounds are the full padded box, not just the text's own bounds, and `FlowRow`
 * reserves that padded size for the item. That's what keeps the circle from overlapping neighboring
 * words. Single caller ([ui.components.RatingScale]'s `RatingPicker`), so the
 * padding/stroke/rotation are spec constants, not parameters.
 */
fun Modifier.circledSelection(color: Color): Modifier =
  this.drawWithContent {
      drawContent()
      rotate(-3f, pivot = center) {
        drawOval(color = color, style = Stroke(width = 2.5.dp.toPx()))
      }
    }
    .padding(horizontal = 8.dp, vertical = 7.dp)

/**
 * A wavy stamp-colored underline under the selected revisit-intent word. Single caller
 * ([ui.components.RevisitIntent]), so amplitude/wavelength/stroke width are spec constants.
 */
fun Modifier.wavyUnderline(color: Color): Modifier = drawBehind {
  val amplitudePx = 1.5.dp.toPx()
  val wavelengthPx = 6.dp.toPx()
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
  drawPath(path, color = color, style = Stroke(width = 1.5.dp.toPx()))
}

/**
 * A dashed rounded-rect outline — the warning [ui.components.NoteField] variant on place detail.
 * `drawWithContent` (content painted first, dashes on top) rather than `drawBehind`, since the
 * dashes sit on a `Surface` with an opaque fill: painting behind it let the fill cover the inner
 * half of the stroke, rendering as a 1dp line instead of the spec's 2dp. The rect is inset by half
 * the stroke width so the full stroke paints inside the element's bounds. Single caller
 * ([ui.components] `NoteField`'s warning variant is rendered by `PlaceDetailScreen.WarningCard`),
 * so stroke/corner/dash metrics are spec constants.
 */
fun Modifier.dashedBorder(color: Color): Modifier = drawWithContent {
  drawContent()
  val strokePx = 2.dp.toPx()
  val inset = strokePx / 2
  drawRoundRect(
    color = color,
    topLeft = Offset(inset, inset),
    size = Size(size.width - strokePx, size.height - strokePx),
    cornerRadius = CornerRadius(3.dp.toPx()),
    style =
      Stroke(
        width = strokePx,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
      ),
  )
}
