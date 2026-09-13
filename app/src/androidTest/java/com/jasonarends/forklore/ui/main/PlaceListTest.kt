package com.jasonarends.forklore.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceEntryWithPlace
import org.junit.Rule
import org.junit.Test

class PlaceListTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun showsEachPlaceWithItsBranch() {
    composeTestRule.setContent {
      PlaceList(entries = listOf(entry("Halberd"), entry("Fifth Avenue Social", "Kansas")))
    }

    composeTestRule.onNodeWithText("Halberd").assertExists()
    composeTestRule.onNodeWithText("Fifth Avenue Social · Kansas").assertExists()
  }

  @Test
  fun tellsTheUserWhenTheListIsEmpty() {
    composeTestRule.setContent { PlaceList(entries = emptyList()) }

    composeTestRule.onNodeWithText("Nothing here yet.").assertExists()
  }

  private fun entry(name: String, branch: String? = null): PlaceEntryWithPlace {
    val place = PlaceEntity(name = name, branchLabel = branch, createdAt = 0, updatedAt = 0)
    return PlaceEntryWithPlace(
      entry =
        PlaceEntryEntity(placeListId = "list", placeId = place.id, createdAt = 0, updatedAt = 0),
      place = place,
    )
  }
}
