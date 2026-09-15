package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The four glyphs issue #15 calls for: "stroke icons, no fills." Four icons isn't worth pulling in
 * material-icons-extended, so they're drawn directly here — legibility at chip size matters more
 * than fidelity, so keep them simple if you're tempted to add detail.
 */
internal enum class LedgerGlyph {
  Back,
  Bookmark,
  Check,
  CircleSlash,
}

@Composable
internal fun LedgerIcon(
  glyph: LedgerGlyph,
  tint: Color,
  modifier: Modifier = Modifier,
  size: Dp = 14.dp,
  strokeWidth: Dp = 1.6.dp,
) {
  Canvas(modifier.size(size)) {
    val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = this.size.width
    val h = this.size.height
    when (glyph) {
      LedgerGlyph.Back -> {
        val path =
          Path().apply {
            moveTo(w * 0.62f, h * 0.15f)
            lineTo(w * 0.28f, h * 0.5f)
            lineTo(w * 0.62f, h * 0.85f)
          }
        drawPath(path, tint, style = stroke)
      }
      LedgerGlyph.Bookmark -> {
        val path =
          Path().apply {
            moveTo(w * 0.26f, h * 0.12f)
            lineTo(w * 0.74f, h * 0.12f)
            lineTo(w * 0.74f, h * 0.88f)
            lineTo(w * 0.5f, h * 0.68f)
            lineTo(w * 0.26f, h * 0.88f)
            close()
          }
        drawPath(path, tint, style = stroke)
      }
      LedgerGlyph.Check -> {
        val path =
          Path().apply {
            moveTo(w * 0.18f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.76f)
            lineTo(w * 0.84f, h * 0.24f)
          }
        drawPath(path, tint, style = stroke)
      }
      LedgerGlyph.CircleSlash -> {
        drawCircle(tint, radius = w * 0.38f, style = stroke)
        val path =
          Path().apply {
            moveTo(w * 0.24f, h * 0.76f)
            lineTo(w * 0.76f, h * 0.24f)
          }
        drawPath(path, tint, style = stroke)
      }
    }
  }
}
