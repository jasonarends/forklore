package com.jasonarends.forklore.ui.dishinterests

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jasonarends.forklore.data.db.DishInterestEntity
import com.jasonarends.forklore.data.db.DishStatus
import com.jasonarends.forklore.data.db.ListedDishInterest
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DishInterestListTest {
  @get:Rule val compose = createComposeRule()

  private fun listed(
    dish: String,
    place: String,
    status: DishStatus,
    entryId: String = "entry-$place",
    branch: String? = null,
    forPerson: String? = null,
    recommendedBy: String? = null,
    modification: String? = null,
  ) =
    ListedDishInterest(
      interest =
        DishInterestEntity(
          dishId = "dish-$dish",
          status = status,
          modification = modification,
          createdAt = 0,
          updatedAt = 0,
        ),
      dishName = dish,
      placeEntryId = entryId,
      placeName = place,
      branchLabel = branch,
      forPersonName = forPerson,
      recommendedByName = recommendedBy,
    )

  @Test
  fun showsWantAndNeverAgain_asSeparateGroups_withDishPlaceAndDetails() {
    val want =
      listed(
        "Barrel Potatoes",
        "Halberd",
        DishStatus.WANT,
        forPerson = "Robin",
        recommendedBy = "Dale",
        modification = "add a Chilli bomb",
      )
    val never = listed("Arancini", "Cafe Mirabel", DishStatus.NEVER_AGAIN, branch = "Plaza")
    compose.setContent {
      ForkloreTheme {
        DishInterestList(want = listOf(want), neverAgain = listOf(never), onPlaceClick = {})
      }
    }

    compose.onNodeWithText("Barrel Potatoes").assertExists()
    compose.onNodeWithText("Halberd").assertExists()
    compose.onNodeWithText("for Robin · recommended by Dale").assertExists()
    compose.onNodeWithText("add a Chilli bomb").assertExists()
    compose.onNodeWithText("Arancini").assertExists()
    compose.onNodeWithText("Cafe Mirabel · Plaza").assertExists()
    // Each status appears as the group header and again on the row itself, so a never-again dish
    // says so in words even out of its section.
    compose.onAllNodesWithText("Want to try").assertCountEquals(2)
    compose.onAllNodesWithText("Never again").assertCountEquals(2)
  }

  @Test
  fun tappingARow_opensItsPlaceEntry() {
    var opened: String? = null
    val want = listed("Burrata", "Halberd", DishStatus.WANT, entryId = "halberd-entry")
    compose.setContent {
      ForkloreTheme {
        DishInterestList(
          want = listOf(want),
          neverAgain = emptyList(),
          onPlaceClick = { opened = it },
        )
      }
    }

    compose.onNodeWithTag("listed-interest-${want.interest.id}").performClick()

    assertEquals("halberd-entry", opened)
  }

  @Test
  fun emptyGroups_sayWhatIsMissing() {
    compose.setContent {
      ForkloreTheme {
        DishInterestList(want = emptyList(), neverAgain = emptyList(), onPlaceClick = {})
      }
    }

    compose.onNodeWithText("Nothing on the want list yet.").assertExists()
    compose.onNodeWithText("Nothing to skip. Yet.").assertExists()
  }
}
