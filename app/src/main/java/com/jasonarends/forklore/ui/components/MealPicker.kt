package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.data.db.Meal
import com.jasonarends.forklore.ui.theme.ForkloreTheme

val Meal.label: String
  get() =
    when (this) {
      Meal.BREAKFAST -> "Breakfast"
      Meal.BRUNCH -> "Brunch"
      Meal.LUNCH -> "Lunch"
      Meal.DINNER -> "Dinner"
      Meal.DESSERT -> "Dessert"
      Meal.DRINKS -> "Drinks"
    }

/**
 * Tapping the already-selected meal clears it — the same "not every visit records this" affordance
 * as [RatingPicker]/[RevisitIntentPicker]. A visit with no meal recorded is a real, common case
 * (see issue #5's source notes), not something to force a guess at.
 */
@Composable
fun MealPicker(meal: Meal?, onMealChange: (Meal?) -> Unit, modifier: Modifier = Modifier) {
  ChoiceChips(
    options = Meal.entries,
    selected = meal,
    onSelect = { picked -> onMealChange(picked.takeUnless { it == meal }) },
    label = { it.label },
    modifier = modifier,
  )
}

@PreviewLightDark
@Composable
private fun MealPickerPreview() {
  ForkloreTheme {
    Surface {
      MealPicker(meal = Meal.DINNER, onMealChange = {}, modifier = Modifier.padding(16.dp))
    }
  }
}
