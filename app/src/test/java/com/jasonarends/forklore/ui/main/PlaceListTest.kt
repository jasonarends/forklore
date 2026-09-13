package com.jasonarends.forklore.ui.main

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceEntryWithPlace
import com.jasonarends.forklore.data.db.PlaceStatus
import com.jasonarends.forklore.data.db.Rating
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
          )
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
    composeTestRule.setContent { PlaceList(entries = emptyList()) }

    composeTestRule.onNodeWithText("Nothing here yet.").assertExists()
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
