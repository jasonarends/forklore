package com.jasonarends.forklore.ui.placedetail

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceEntryWithPlace
import com.jasonarends.forklore.data.db.PlaceStatus
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.data.db.RevisitIntent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaceDetailScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun aPlaceWithNoRatingsAndNoNote_rendersCleanly() {
    compose.setContent {
      PlaceDetail(
        entry = entry("Halberd"),
        onStatusChange = {},
        onFoodRatingChange = {},
        onServiceRatingChange = {},
        onRevisitIntentChange = {},
        onNoteChange = {},
      )
    }

    compose.onNodeWithText("Halberd").assertExists()
    compose.onNodeWithText("Food").assertExists()
    compose.onNodeWithText("Service").assertExists()
    compose.onNodeWithText("Note").assertExists()
  }

  @Test
  fun aPopulatedPlace_showsEveryFieldDistinctly() {
    val place =
      PlaceEntity(
        name = "Hotel Brannock",
        branchLabel = "Kansas",
        address = "123 Main St",
        warning = "\$27 per person even if you order one thing",
        createdAt = 0,
        updatedAt = 0,
      )
    compose.setContent {
      PlaceDetail(
        entry =
          PlaceEntryWithPlace(
            entry =
              PlaceEntryEntity(
                placeListId = "list",
                placeId = place.id,
                status = PlaceStatus.VISITED,
                foodRating = Rating.LIFE_CHANGING,
                serviceRating = Rating.BAD,
                revisitIntent = RevisitIntent.WAIT,
                note = "Servers are rude, food was incredible.",
                createdAt = 0,
                updatedAt = 0,
              ),
            place = place,
          ),
        onStatusChange = {},
        onFoodRatingChange = {},
        onServiceRatingChange = {},
        onRevisitIntentChange = {},
        onNoteChange = {},
      )
    }

    compose.onNodeWithText("Hotel Brannock · Kansas").assertExists()
    compose.onNodeWithText("123 Main St").assertExists()
    compose.onNodeWithText("\$27 per person even if you order one thing").assertExists()
    compose.onNodeWithText("Been").assertExists()
    compose.onNodeWithText("Wait a while").assertExists()
    compose.onNodeWithText("Servers are rude, food was incredible.").assertExists()
    // Food and service each get their own rating picker, so "Life-changing" and "Bad" each
    // appear twice on screen (once per picker); the testTag on each picker is what tells them
    // apart, since only one instance of each label is selected and it must be the right one, or
    // the two verdicts have silently swapped.
    compose
      .onNode(hasText("Life-changing") and hasAnyAncestor(hasTestTag("food")))
      .assertIsSelected()
    compose.onNode(hasText("Bad") and hasAnyAncestor(hasTestTag("service"))).assertIsSelected()
  }

  @Test
  fun editingTheNote_reportsEveryKeystroke_withNoEditMode() {
    var note = ""
    compose.setContent {
      PlaceDetail(
        entry = entry("Halberd"),
        onStatusChange = {},
        onFoodRatingChange = {},
        onServiceRatingChange = {},
        onRevisitIntentChange = {},
        onNoteChange = { note = it },
      )
    }

    compose.onNode(hasSetTextAction()).performTextInput("Great pasta")

    assertEquals("Great pasta", note)
  }

  @Test
  fun pickingAFoodRating_reportsIt_andLeavesServiceAlone() {
    var foodRating: Rating? = null
    var serviceRating: Rating? = null
    compose.setContent {
      PlaceDetail(
        entry = entry("Halberd"),
        onStatusChange = {},
        onFoodRatingChange = { foodRating = it },
        onServiceRatingChange = { serviceRating = it },
        onRevisitIntentChange = {},
        onNoteChange = {},
      )
    }

    compose.onNode(hasText("Life-changing") and hasAnyAncestor(hasTestTag("food"))).performClick()

    assertEquals(Rating.LIFE_CHANGING, foodRating)
    assertEquals(null, serviceRating)
  }

  private fun entry(name: String, status: PlaceStatus = PlaceStatus.WANT): PlaceEntryWithPlace {
    val place = PlaceEntity(name = name, createdAt = 0, updatedAt = 0)
    return PlaceEntryWithPlace(
      entry =
        PlaceEntryEntity(
          placeListId = "list",
          placeId = place.id,
          status = status,
          createdAt = 0,
          updatedAt = 0,
        ),
      place = place,
    )
  }
}
