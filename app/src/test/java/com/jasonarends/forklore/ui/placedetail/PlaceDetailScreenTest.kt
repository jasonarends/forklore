package com.jasonarends.forklore.ui.placedetail

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jasonarends.forklore.data.db.DishAliasEntity
import com.jasonarends.forklore.data.db.DishEntity
import com.jasonarends.forklore.data.db.DishWithAliases
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceEntryWithPlace
import com.jasonarends.forklore.data.db.PlaceStatus
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.data.db.RevisitIntent
import com.jasonarends.forklore.ui.theme.ForkloreTheme
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
      ForkloreTheme {
        PlaceDetail(
          entry = entry("Halberd"),
          onStatusChange = {},
          onFoodRatingChange = {},
          onServiceRatingChange = {},
          onRevisitIntentChange = {},
          onNoteChange = {},
        )
      }
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
      ForkloreTheme {
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
      ForkloreTheme {
        PlaceDetail(
          entry = entry("Halberd"),
          onStatusChange = {},
          onFoodRatingChange = {},
          onServiceRatingChange = {},
          onRevisitIntentChange = {},
          onNoteChange = { note = it },
        )
      }
    }

    compose.onNode(hasSetTextAction()).performTextInput("Great pasta")

    assertEquals("Great pasta", note)
  }

  @Test
  fun pickingAFoodRating_reportsIt_andLeavesServiceAlone() {
    var foodRating: Rating? = null
    var serviceRating: Rating? = null
    compose.setContent {
      ForkloreTheme {
        PlaceDetail(
          entry = entry("Halberd"),
          onStatusChange = {},
          onFoodRatingChange = { foodRating = it },
          onServiceRatingChange = { serviceRating = it },
          onRevisitIntentChange = {},
          onNoteChange = {},
        )
      }
    }

    compose.onNode(hasText("Life-changing") and hasAnyAncestor(hasTestTag("food"))).performClick()

    assertEquals(Rating.LIFE_CHANGING, foodRating)
    assertEquals(null, serviceRating)
  }

  @Test
  fun dishesRecordedAtThisEntry_areListedWithTheirAliases() {
    compose.setContent {
      ForkloreTheme {
        DishesSection(
          state =
            DishesUiState.Success(
              listOf(dish("Barrel Potatoes", aliases = listOf("potatoe barrels")))
            ),
          query = "",
          suggestions = emptyList(),
          onQueryChange = {},
          onAddDish = {},
          onAddAlias = { _, _ -> },
        )
      }
    }

    compose.onNodeWithText("Barrel Potatoes").assertExists()
    compose.onNodeWithText("also: potatoe barrels").assertExists()
  }

  @Test
  fun typingADishName_andPressingAdd_submitsIt() {
    var added: String? = null
    compose.setContent {
      ForkloreTheme {
        DishesSection(
          state = DishesUiState.Success(emptyList()),
          query = "Burnt Ends",
          suggestions = emptyList(),
          onQueryChange = {},
          onAddDish = { added = it },
          onAddAlias = { _, _ -> },
        )
      }
    }

    compose.onNodeWithText("Add").performClick()

    assertEquals("Burnt Ends", added)
  }

  /**
   * Issue #6's "done when": typing a spelling already taught to an existing dish (via an alias)
   * offers that dish as a suggestion, and picking it submits the same name a fresh lookup would
   * resolve back to the existing row — never a hand-typed duplicate.
   */
  @Test
  fun typingAKnownAlias_offersTheExistingDishAsASuggestion_thatSubmitsIt() {
    var added: String? = null
    val existing = dish("Barrel Potatoes", aliases = listOf("barrel tots"))
    compose.setContent {
      ForkloreTheme {
        DishesSection(
          state = DishesUiState.Success(listOf(existing)),
          query = "barrel tots",
          suggestions = listOf(existing),
          onQueryChange = {},
          onAddDish = { added = it },
          onAddAlias = { _, _ -> },
        )
      }
    }

    compose.onNodeWithTag("dish-suggestion-${existing.dish.id}").performClick()

    assertEquals("Barrel Potatoes", added)
  }

  private fun dish(name: String, aliases: List<String> = emptyList()): DishWithAliases {
    val entity =
      DishEntity(
        placeEntryId = "entry",
        canonicalName = name,
        normalizedName = name.lowercase(),
        createdAt = 0,
        updatedAt = 0,
      )
    return DishWithAliases(
      dish = entity,
      aliases =
        aliases.map {
          DishAliasEntity(
            dishId = entity.id,
            alias = it,
            normalized = it.lowercase(),
            createdAt = 0,
            updatedAt = 0,
          )
        },
    )
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
