package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.ui.theme.ForkloreTheme

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
 * Picks a [Rating], or none. Tapping the current rating clears it: "not rated" is a real answer,
 * and a picker that can't return to it forces people to invent an opinion.
 */
@Composable
fun RatingPicker(
  rating: Rating?,
  onRatingChange: (Rating?) -> Unit,
  modifier: Modifier = Modifier,
) {
  ChoiceChips(
    options = Rating.entries,
    selected = rating,
    onSelect = { picked -> onRatingChange(picked.takeUnless { it == rating }) },
    label = { it.label },
    modifier = modifier,
  )
}

/** A rating as read-only text. */
@Composable
fun RatingLabel(rating: Rating, modifier: Modifier = Modifier) {
  val emphasis = rating.emphasis
  Text(
    text = rating.label,
    modifier = modifier,
    style = MaterialTheme.typography.labelLarge,
    color =
      when (emphasis) {
        RatingEmphasis.Muted -> MaterialTheme.colorScheme.onSurfaceVariant
        RatingEmphasis.Plain -> Color.Unspecified
        RatingEmphasis.Strong -> MaterialTheme.colorScheme.tertiary
      },
    fontWeight = if (emphasis == RatingEmphasis.Strong) FontWeight.Bold else null,
  )
}

@PreviewLightDark
@Composable
private fun RatingPickerPreview() {
  ForkloreTheme {
    Surface {
      var rating by remember { mutableStateOf<Rating?>(Rating.PHENOMENAL) }
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RatingPicker(rating = rating, onRatingChange = { rating = it })
        Rating.entries.forEach { RatingLabel(it) }
      }
    }
  }
}
