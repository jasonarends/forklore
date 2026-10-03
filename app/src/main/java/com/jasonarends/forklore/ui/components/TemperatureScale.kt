package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jasonarends.forklore.data.db.TemperatureRating
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ZillaSlab

val TemperatureRating.label: String
  get() =
    when (this) {
      TemperatureRating.INEDIBLE -> "Inedible"
      TemperatureRating.LACKING -> "Lacking"
      TemperatureRating.MID -> "Mid"
      TemperatureRating.ADEQUATE -> "Adequate"
      TemperatureRating.PHENOMENAL -> "Phenomenal"
    }

/**
 * Suitability, not heat: a correctly-cold dish and a correctly-hot one both land at the top, so the
 * words are about how well the temperature served the dish, never "hot" or "cold". That makes it a
 * ramp toward better exactly like [com.jasonarends.forklore.data.db.Rating], so it reuses the same
 * escalating-type treatment at five steps rather than inventing a second idiom.
 */
@Composable
private fun TemperatureRating.rampStyle(): Pair<TextStyle, Color> {
  val colors = ForkloreTheme.colors
  fun style(size: Float, weight: FontWeight, italic: Boolean = false) =
    TextStyle(
      fontFamily = ZillaSlab,
      fontSize = size.sp,
      fontWeight = weight,
      fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    )
  return when (this) {
    TemperatureRating.INEDIBLE -> style(12f, FontWeight.Normal) to colors.ink2
    TemperatureRating.LACKING -> style(13f, FontWeight.Normal) to colors.ink2
    TemperatureRating.MID -> style(14f, FontWeight.Normal) to colors.ink2
    TemperatureRating.ADEQUATE -> style(16f, FontWeight.SemiBold) to colors.ink
    TemperatureRating.PHENOMENAL -> style(20f, FontWeight.Bold, italic = true) to colors.rust
  }
}

/** [RatingPicker]'s counterpart for [TemperatureRating]: the whole ramp, selected word circled. */
@Composable
fun TemperaturePicker(
  temperature: TemperatureRating?,
  onTemperatureChange: (TemperatureRating?) -> Unit,
  modifier: Modifier = Modifier,
) {
  RampPicker(
    options = TemperatureRating.entries,
    selected = temperature,
    onSelectionChange = onTemperatureChange,
    label = { it.label },
    style = { it.rampStyle() },
    modifier = modifier,
  )
}

internal val TemperatureRating.emphasis: RatingEmphasis
  get() =
    when (this) {
      TemperatureRating.INEDIBLE,
      TemperatureRating.LACKING,
      TemperatureRating.MID -> RatingEmphasis.Muted
      TemperatureRating.ADEQUATE -> RatingEmphasis.Plain
      TemperatureRating.PHENOMENAL -> RatingEmphasis.Strong
    }

/** A temperature as read-only inline text, for an opinion card. */
@Composable
fun TemperatureLabel(temperature: TemperatureRating, modifier: Modifier = Modifier) {
  RampLabel(text = temperature.label, emphasis = temperature.emphasis, modifier = modifier)
}

@PreviewLightDark
@Composable
private fun TemperaturePickerPreview() {
  ForkloreTheme {
    Surface {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TemperaturePicker(temperature = TemperatureRating.ADEQUATE, onTemperatureChange = {})
        TemperatureRating.entries.forEach { TemperatureLabel(it) }
      }
    }
  }
}
