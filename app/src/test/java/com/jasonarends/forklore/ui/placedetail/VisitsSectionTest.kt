package com.jasonarends.forklore.ui.placedetail

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jasonarends.forklore.data.db.DatePrecision
import com.jasonarends.forklore.data.db.Meal
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.data.db.VisitEntity
import com.jasonarends.forklore.data.db.VisitWithAttendees
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The visit list on place detail: read-only, with every edit leaving for the editor screen. */
@RunWith(RobolectricTestRunner::class)
class VisitsSectionTest {
  @get:Rule val compose = createComposeRule()

  private val ana =
    PersonEntity(id = "ana", name = "Ana", normalizedName = "ana", createdAt = 0, updatedAt = 0)

  private val visit =
    VisitWithAttendees(
      visit =
        VisitEntity(
          id = "visit-1",
          placeEntryId = "entry",
          dateEpochDay = 20_625,
          datePrecision = DatePrecision.DAY,
          meal = Meal.BREAKFAST,
          note = "Great coffee",
          createdAt = 0,
          updatedAt = 0,
        ),
      attendees = listOf(ana),
    )

  @Test
  fun existingVisits_showTheirDateAttendeesAndNote() {
    compose.setContent {
      ForkloreTheme { VisitsSection(listOf(visit), onAddVisit = {}, onEditVisit = {}) }
    }

    compose.onNodeWithText("6/21/26 · Breakfast").assertExists()
    compose.onNodeWithText("Ana").assertExists()
    compose.onNodeWithText("Great coffee").assertExists()
  }

  @Test
  fun tappingAVisit_asksToEditThatVisit() {
    var edited: String? = null
    compose.setContent {
      ForkloreTheme { VisitsSection(listOf(visit), onAddVisit = {}, onEditVisit = { edited = it }) }
    }

    compose.onNodeWithTag("visit-row-visit-1").performClick()

    assertEquals("visit-1", edited)
  }

  @Test
  fun theEditLink_asksToEditThatVisit() {
    var edited: String? = null
    compose.setContent {
      ForkloreTheme { VisitsSection(listOf(visit), onAddVisit = {}, onEditVisit = { edited = it }) }
    }

    compose.onNodeWithTag("visit-edit-visit-1").performClick()

    assertEquals("visit-1", edited)
  }

  @Test
  fun addAVisit_asksToAdd_andNoFormOpensInline() {
    var added = 0
    compose.setContent {
      ForkloreTheme { VisitsSection(emptyList(), onAddVisit = { added++ }, onEditVisit = {}) }
    }

    compose.onNodeWithText("No visits yet.").assertExists()
    compose.onNodeWithTag("visits-add-button").performClick()

    assertEquals(1, added)
    compose.onNodeWithTag("visit-save").assertDoesNotExist()
  }
}
