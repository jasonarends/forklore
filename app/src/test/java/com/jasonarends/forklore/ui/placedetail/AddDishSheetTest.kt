package com.jasonarends.forklore.ui.placedetail

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jasonarends.forklore.data.db.DishAliasEntity
import com.jasonarends.forklore.data.db.DishEntity
import com.jasonarends.forklore.data.db.DishWithAliases
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The add-a-dish bottom sheet; the tests here were the inline field's, moved with it. */
@RunWith(RobolectricTestRunner::class)
class AddDishSheetTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun typingADishName_andPressingAdd_submitsIt() {
    var added: String? = null
    compose.setContent {
      ForkloreTheme {
        AddDishSheet(
          query = "Burnt Ends",
          suggestions = emptyList(),
          onQueryChange = {},
          onSubmit = { added = it },
          onDismiss = {},
        )
      }
    }

    compose.onNodeWithTag("dish-submit").performClick()

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
    val entity =
      DishEntity(
        placeEntryId = "entry",
        canonicalName = "Barrel Potatoes",
        normalizedName = "barrel potatoes",
        createdAt = 0,
        updatedAt = 0,
      )
    val existing =
      DishWithAliases(
        dish = entity,
        aliases =
          listOf(
            DishAliasEntity(
              dishId = entity.id,
              alias = "barrel tots",
              normalized = "barrel tots",
              createdAt = 0,
              updatedAt = 0,
            )
          ),
      )
    compose.setContent {
      ForkloreTheme {
        AddDishSheet(
          query = "barrel tots",
          suggestions = listOf(existing),
          onQueryChange = {},
          onSubmit = { added = it },
          onDismiss = {},
        )
      }
    }

    compose.onNodeWithTag("dish-suggestion-${entity.id}").performClick()

    assertEquals("Barrel Potatoes", added)
  }
}
