package com.jasonarends.forklore.ui.placedetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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

/**
 * Drives [VisitsSection] the way [PlaceDetailScreen] really does: [draftState] is created once,
 * outside composition (see [setVisitsSectionContent]'s KDoc), and mutated only through the same
 * callbacks the real [VisitsViewModel] would supply, so these tests exercise the same round trip a
 * person's taps take, not just that a lambda fired. Content is wrapped in the same `verticalScroll`
 * [PlaceDetail] provides in production — without it, the form's lower fields (note, Cancel, Save)
 * render with zero size on Robolectric's small virtual window and `performClick()` silently hits
 * nothing, so every test that reaches them needs `.performScrollTo()` first, same as
 * [com.jasonarends.forklore.ui.addplace.AddPlaceFormTest].
 */
@RunWith(RobolectricTestRunner::class)
class VisitsSectionTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun addingADayPreciseVisit_savesTheExactDate() {
    val draftState = mutableStateOf<VisitDraft?>(null)
    var saved: VisitDraft? = null
    compose.setVisitsSectionContent(draftState, onSaveVisit = { saved = draftState.value })

    compose.onNodeWithTag("visits-add-button").performClick()
    compose.onNodeWithText("Exact date").performClick()
    compose.onNodeWithTag("visit-date-month").performTextInput("6")
    compose.onNodeWithTag("visit-date-day").performTextInput("21")
    compose.onNodeWithTag("visit-date-year").performTextInput("2026")
    compose.onNodeWithText("Dinner").performScrollTo().performClick()
    compose.onNodeWithTag("visit-save").performScrollTo().performClick()

    assertEquals(DatePrecision.DAY, saved?.precision)
    assertEquals("6", saved?.month)
    assertEquals("21", saved?.day)
    assertEquals("2026", saved?.year)
    assertEquals(Meal.DINNER, saved?.meal)
  }

  @Test
  fun addingAMonthOnlyVisit_leavesTheDayFieldOffScreen() {
    val draftState = mutableStateOf<VisitDraft?>(null)
    var saved: VisitDraft? = null
    compose.setVisitsSectionContent(draftState, onSaveVisit = { saved = draftState.value })

    compose.onNodeWithTag("visits-add-button").performClick()
    compose.onNodeWithText("Month only").performClick()
    compose.onNodeWithTag("visit-date-month").performTextInput("6")
    compose.onNodeWithTag("visit-date-year").performTextInput("2026")
    compose.onNodeWithTag("visit-date-day").assertDoesNotExist()
    compose.onNodeWithTag("visit-save").performScrollTo().performClick()

    assertEquals(DatePrecision.MONTH, saved?.precision)
    assertEquals("6", saved?.month)
    assertEquals("2026", saved?.year)
  }

  @Test
  fun addingAVisitWithNoDate_needsNoDateFieldsAtAll() {
    val draftState = mutableStateOf<VisitDraft?>(null)
    var saved: VisitDraft? = null
    compose.setVisitsSectionContent(draftState, onSaveVisit = { saved = draftState.value })

    compose.onNodeWithTag("visits-add-button").performClick()
    // "No date" is the default selection, so this covers a visit entered with no date touch at
    // all, matching issue #5's "Verano — no date at all" source note.
    compose.onNodeWithTag("visit-date-month").assertDoesNotExist()
    compose.onNodeWithTag("visit-date-year").assertDoesNotExist()
    compose
      .onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("visit-note")))
      .performScrollTo()
      .performTextInput("Verano")
    compose.onNodeWithTag("visit-save").performScrollTo().performClick()

    assertEquals(DatePrecision.UNKNOWN, saved?.precision)
    assertEquals("Verano", saved?.note)
  }

  @Test
  fun existingVisits_showTheirDateAndOfferAnEditAffordance() {
    val ana =
      PersonEntity(id = "ana", name = "Ana", normalizedName = "ana", createdAt = 0, updatedAt = 0)
    val visit =
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
    var editRequested: VisitWithAttendees? = null
    val draftState = mutableStateOf<VisitDraft?>(null)
    compose.setVisitsSectionContent(
      draftState,
      visits = listOf(visit),
      people = listOf(ana),
      onStartEdit = { editRequested = it },
    )

    compose.onNodeWithText("6/21/26 · Breakfast").assertExists()
    compose.onNodeWithText("Ana").assertExists()
    compose.onNodeWithText("Great coffee").assertExists()

    compose.onNodeWithTag("visit-edit-visit-1").performClick()

    assertEquals("visit-1", editRequested?.visit?.id)
  }

  @Test
  fun cancellingTheForm_closesItWithoutSaving() {
    val draftState = mutableStateOf<VisitDraft?>(null)
    var saveCount = 0
    compose.setVisitsSectionContent(draftState, onSaveVisit = { saveCount++ })

    compose.onNodeWithTag("visits-add-button").performClick()
    compose.onNodeWithTag("visit-cancel").performScrollTo().performClick()

    compose.onNodeWithTag("visits-add-button").assertExists()
    assertEquals(0, saveCount)
  }

  /**
   * [draftState] is created by the caller, outside `setContent`, and only read/written from inside
   * it — the same shape `PersonPickerTest` uses for a `MutableState` a test drives through taps.
   * Declaring it *inside* the `setContent` lambda instead would recreate a fresh, un-`remember`ed
   * `State` on every recomposition and silently discard every prior keystroke, which is exactly the
   * bug this shape avoids.
   */
  private fun ComposeContentTestRule.setVisitsSectionContent(
    draftState: MutableState<VisitDraft?>,
    visits: List<VisitWithAttendees> = emptyList(),
    people: List<PersonEntity> = emptyList(),
    onStartEdit: (VisitWithAttendees) -> Unit = {},
    onCreatePerson: (String) -> Unit = {},
    onSaveVisit: () -> Unit = {},
  ) {
    setContent {
      ForkloreTheme {
        Column(Modifier.verticalScroll(rememberScrollState())) {
          VisitsSection(
            visits = visits,
            people = people,
            draft = draftState.value,
            onStartAdd = { draftState.value = VisitDraft() },
            onStartEdit = onStartEdit,
            onCancelDraft = { draftState.value = null },
            onPrecisionChange = {
              draftState.value = draftState.value?.copy(precision = it, error = null)
            },
            onYearChange = { draftState.value = draftState.value?.copy(year = it) },
            onMonthChange = { draftState.value = draftState.value?.copy(month = it) },
            onDayChange = { draftState.value = draftState.value?.copy(day = it) },
            onMealChange = { draftState.value = draftState.value?.copy(meal = it) },
            onNoteChange = { draftState.value = draftState.value?.copy(note = it) },
            onAttendeesChange = { draftState.value = draftState.value?.copy(attendees = it) },
            onCreatePerson = onCreatePerson,
            onSaveVisit = onSaveVisit,
          )
        }
      }
    }
  }
}
