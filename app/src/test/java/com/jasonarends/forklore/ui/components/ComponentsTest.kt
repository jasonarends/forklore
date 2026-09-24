package com.jasonarends.forklore.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jasonarends.forklore.data.db.DishStatus
import com.jasonarends.forklore.data.db.PlaceStatus
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.data.db.TemperatureRating
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComponentsTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun tappingARatingSelectsIt() {
    var rating: Rating? by mutableStateOf(null)
    compose.setContent {
      ForkloreTheme { RatingPicker(rating = rating, onRatingChange = { rating = it }) }
    }

    compose.onNodeWithText("Life-changing").performClick()

    assertEquals(Rating.LIFE_CHANGING, rating)
    compose.onNodeWithText("Life-changing").assertIsSelected()
    compose.onNodeWithText("Mid").assertIsNotSelected()
  }

  @Test
  fun tappingTheSelectedRatingClearsIt() {
    var rating: Rating? by mutableStateOf(Rating.GOOD)
    compose.setContent {
      ForkloreTheme { RatingPicker(rating = rating, onRatingChange = { rating = it }) }
    }

    compose.onNodeWithText("Good").performClick()

    assertNull(rating)
    compose.onNodeWithText("Good").assertIsNotSelected()
  }

  @Test
  fun theTopOfTheRatingScaleIsNotFlattened() {
    assertEquals(RatingEmphasis.Strong, Rating.PHENOMENAL.emphasis)
    assertEquals(RatingEmphasis.Strong, Rating.LIFE_CHANGING.emphasis)
    assertNotEquals(Rating.EXCELLENT.emphasis, Rating.LIFE_CHANGING.emphasis)
  }

  @Test
  fun tappingATemperatureSelectsIt_andTappingItAgainClearsIt() {
    var temperature: TemperatureRating? by mutableStateOf(null)
    compose.setContent {
      ForkloreTheme {
        TemperaturePicker(temperature = temperature, onTemperatureChange = { temperature = it })
      }
    }

    compose.onNodeWithText("Lacking").performClick()
    assertEquals(TemperatureRating.LACKING, temperature)
    compose.onNodeWithText("Lacking").assertIsSelected()
    compose.onNodeWithText("Phenomenal").assertIsNotSelected()

    compose.onNodeWithText("Lacking").performClick()
    assertNull(temperature)
    compose.onNodeWithText("Lacking").assertIsNotSelected()
  }

  @Test
  fun everyTemperatureIsOnScreenAtOnce_soTheTopOfTheRampIsNeverHidden() {
    compose.setContent {
      ForkloreTheme { TemperaturePicker(temperature = null, onTemperatureChange = {}) }
    }

    TemperatureRating.entries.forEach { compose.onNodeWithText(it.label).assertExists() }
  }

  @Test
  fun theTopOfTheTemperatureRampIsNotFlattened() {
    assertEquals(RatingEmphasis.Strong, TemperatureRating.PHENOMENAL.emphasis)
    assertNotEquals(TemperatureRating.ADEQUATE.emphasis, TemperatureRating.PHENOMENAL.emphasis)
    assertNotEquals(TemperatureRating.INEDIBLE.emphasis, TemperatureRating.ADEQUATE.emphasis)
  }

  @Test
  fun avoidAndNeverAgainDoNotLookLikeNotTriedYet() {
    assertEquals(StatusTone.Warning, PlaceStatus.AVOID.tone)
    assertEquals(StatusTone.Warning, DishStatus.NEVER_AGAIN.tone)
    assertEquals(StatusTone.Pending, PlaceStatus.WANT.tone)
    assertEquals(StatusTone.Pending, DishStatus.WANT.tone)
  }

  @Test
  fun dishStatusPickerReportsNeverAgain() {
    var status by mutableStateOf(DishStatus.WANT)
    compose.setContent {
      ForkloreTheme { DishStatusPicker(status = status, onStatusChange = { status = it }) }
    }

    compose.onNodeWithText("Never again").performClick()

    assertEquals(DishStatus.NEVER_AGAIN, status)
    compose.onNodeWithText("Never again").assertIsSelected()
  }

  @Test
  fun noteFieldTakesLongMultilineTextUntouched() {
    val note = "Skip the bread.\nAsk for the sauce on the side.\n".repeat(200)
    var saved = ""
    compose.setContent {
      ForkloreTheme {
        var value by remember { mutableStateOf("") }
        NoteField(
          value = value,
          onValueChange = {
            value = it
            saved = it
          },
        )
      }
    }

    compose.onNode(hasSetTextAction()).performTextInput(note)

    assertEquals(note, saved)
  }
}
