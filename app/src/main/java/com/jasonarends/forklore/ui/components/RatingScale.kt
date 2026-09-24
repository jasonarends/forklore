package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType
import com.jasonarends.forklore.ui.theme.ZillaSlab
import com.jasonarends.forklore.ui.theme.circledSelection

val Rating.label: String
  get() =
    when (this) {
      Rating.BAD -> "Bad"
      Rating.MID -> "Mid"
      Rating.FINE -> "Fine"
      Rating.GOOD -> "Good"
      Rating.EXCELLENT -> "Excellent"
      Rating.PHENOMENAL -> "Phenomenal"
      Rating.LIFE_CHANGING -> "Life-changing"
    }

/**
 * Escalating size/weight/italic/color per step, straight from issue #15's typography table — the
 * top of the scale is what the verbal scale exists to preserve, so it reads loudest.
 */
@Composable
private fun Rating.ratingStyle(): Pair<TextStyle, Color> {
  val colors = ForkloreTheme.colors
  fun style(size: Float, weight: FontWeight, italic: Boolean = false) =
    TextStyle(
      fontFamily = ZillaSlab,
      fontSize = size.sp,
      fontWeight = weight,
      fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    )
  return when (this) {
    Rating.BAD -> style(12f, FontWeight.Normal) to colors.ink2
    Rating.MID -> style(13f, FontWeight.Normal) to colors.ink2
    Rating.FINE -> style(14f, FontWeight.Normal) to colors.ink2
    Rating.GOOD -> style(15f, FontWeight.Normal) to colors.ink
    Rating.EXCELLENT -> style(17f, FontWeight.SemiBold) to colors.ink
    Rating.PHENOMENAL -> style(20f, FontWeight.Bold, italic = true) to colors.rust
    Rating.LIFE_CHANGING -> style(24f, FontWeight.Bold, italic = true) to colors.rust
  }
}

/**
 * The whole scale, always visible, escalating toward "Life-changing" — the ramp itself is the
 * picker, not a row of chips. The selected word is circled, per issue #15's "circled rating"
 * component. Tapping the circled word clears it: "not rated" is a real answer, and a picker that
 * can't return to it forces people to invent an opinion.
 */
@Composable
fun RatingPicker(
  rating: Rating?,
  onRatingChange: (Rating?) -> Unit,
  modifier: Modifier = Modifier,
) {
  RampPicker(
    options = Rating.entries,
    selected = rating,
    onSelectionChange = onRatingChange,
    label = { it.label },
    style = { it.ratingStyle() },
    modifier = modifier,
  )
}

/**
 * The escalating-type picker [RatingPicker] and [TemperaturePicker] share: every option visible at
 * once, styled by [style], the selected one circled, tapping it again clears it. Kept generic so
 * the two scales can't drift apart in how they behave, only in their vocabulary.
 */
@Composable
internal fun <T> RampPicker(
  options: List<T>,
  selected: T?,
  onSelectionChange: (T?) -> Unit,
  label: (T) -> String,
  style: @Composable (T) -> Pair<TextStyle, Color>,
  modifier: Modifier = Modifier,
) {
  val stampColor = ForkloreTheme.colors.stamp
  FlowRow(
    modifier = modifier.selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    options.forEach { option ->
      val isSelected = option == selected
      val (textStyle, color) = style(option)
      Text(
        text = label(option),
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .selectable(
              selected = isSelected,
              onClick = { onSelectionChange(option.takeUnless { it == selected }) },
              role = Role.RadioButton,
            )
            .then(if (isSelected) Modifier.circledSelection(stampColor) else Modifier),
        style = textStyle,
        color = color,
      )
    }
  }
}

internal enum class RatingEmphasis {
  Muted,
  Plain,
  Strong,
}

/** The top of the scale is what the verbal scale exists to preserve, so it reads loudest. */
internal val Rating.emphasis: RatingEmphasis
  get() =
    when (this) {
      Rating.BAD,
      Rating.MID -> RatingEmphasis.Muted
      Rating.FINE,
      Rating.GOOD,
      Rating.EXCELLENT -> RatingEmphasis.Plain
      Rating.PHENOMENAL,
      Rating.LIFE_CHANGING -> RatingEmphasis.Strong
    }

/**
 * A rating as read-only inline text — used in the place-list row, where only one rating shows at a
 * time, so the full escalating ramp would be overkill.
 */
@Composable
fun RatingLabel(rating: Rating, modifier: Modifier = Modifier) {
  RampLabel(text = rating.label, emphasis = rating.emphasis, modifier = modifier)
}

/** The read-only word for one step of a ramp, shared by [RatingLabel] and [TemperatureLabel]. */
@Composable
internal fun RampLabel(
  text: String,
  emphasis: RatingEmphasis,
  modifier: Modifier = Modifier,
  style: TextStyle = ForkloreType.inlineRating,
) {
  val colors = ForkloreTheme.colors
  Text(
    text = text,
    modifier = modifier,
    style =
      if (emphasis == RatingEmphasis.Strong) {
        style.copy(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
      } else {
        style
      },
    color =
      when (emphasis) {
        RatingEmphasis.Muted -> colors.ink2
        RatingEmphasis.Plain -> colors.ink
        RatingEmphasis.Strong -> colors.rust
      },
  )
}

@PreviewLightDark
@Composable
private fun RatingPickerPreview() {
  ForkloreTheme {
    Surface {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RatingPicker(rating = Rating.PHENOMENAL, onRatingChange = {})
        Rating.entries.forEach { RatingLabel(it) }
      }
    }
  }
}
