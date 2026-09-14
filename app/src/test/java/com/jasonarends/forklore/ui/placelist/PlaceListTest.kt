package com.jasonarends.forklore.ui.placelist

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceEntryWithPlace
import com.jasonarends.forklore.data.db.PlaceStatus
import com.jasonarends.forklore.data.db.Rating
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaceListTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun showsEachPlaceWithItsBranchStatusAndFoodRating() {
    composeTestRule.setContent {
      PlaceList(
        entries =
          listOf(
            entry("Halberd"),
            entry(
              "Fifth Avenue Social",
              "Kansas",
              status = PlaceStatus.AVOID,
              foodRating = Rating.EXCELLENT,
            ),
          ),
        onAddPlace = {},
        onPlaceClick = {},
      )
    }

    composeTestRule.onNodeWithText("Halberd").assertExists()
    composeTestRule.onNodeWithText("Fifth Avenue Social · Kansas").assertExists()
    composeTestRule.onAllNodesWithText("Want to go").assertCountEquals(1)
    composeTestRule.onNodeWithText("Avoid").assertExists()
    composeTestRule.onAllNodesWithText("Food:").assertCountEquals(1)
    composeTestRule.onNodeWithText("Excellent").assertExists()
  }

  @Test
  fun tellsTheUserWhenTheListIsEmpty() {
    composeTestRule.setContent {
      PlaceList(entries = emptyList(), onAddPlace = {}, onPlaceClick = {})
    }

    composeTestRule
      .onNodeWithText("Nothing here yet — add the first place you don't want to forget.")
      .assertExists()
  }

  @Test
  fun tappingAddPlace_invokesTheCallback() {
    var clicked = false
    composeTestRule.setContent {
      PlaceList(entries = emptyList(), onAddPlace = { clicked = true }, onPlaceClick = {})
    }

    composeTestRule.onNodeWithText("Add a place").performClick()

    assertEquals(true, clicked)
  }

  @Test
  fun tappingAPlace_invokesTheCallbackWithItsEntryId() {
    var clickedId: String? = null
    val place = entry("Halberd")
    composeTestRule.setContent {
      PlaceList(entries = listOf(place), onAddPlace = {}, onPlaceClick = { clickedId = it })
    }

    composeTestRule.onNodeWithText("Halberd").performClick()

    assertEquals(place.entry.id, clickedId)
  }

  private fun entry(
    name: String,
    branch: String? = null,
    status: PlaceStatus = PlaceStatus.WANT,
    foodRating: Rating? = null,
  ): PlaceEntryWithPlace {
    val place = PlaceEntity(name = name, branchLabel = branch, createdAt = 0, updatedAt = 0)
    return PlaceEntryWithPlace(
      entry =
        PlaceEntryEntity(
          placeListId = "list",
          placeId = place.id,
          status = status,
          foodRating = foodRating,
          createdAt = 0,
          updatedAt = 0,
        ),
      place = place,
    )
  }
}
