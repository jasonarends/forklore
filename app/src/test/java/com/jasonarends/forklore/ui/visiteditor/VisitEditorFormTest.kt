package com.jasonarends.forklore.ui.visiteditor

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.jasonarends.forklore.data.db.DatePrecision
import com.jasonarends.forklore.data.db.Meal
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val today: LocalDate = LocalDate.of(2026, 10, 3)

/**
 * Drives [VisitEditorForm] the way [VisitEditorScreen] really does: [draftState] is created once,
 * outside composition (see [setVisitEditorContent]'s KDoc), and mutated only through the same
 * callbacks the real [VisitEditorViewModel] would supply, so these tests exercise the same round
 * trip a person's taps take, not just that a lambda fired. The form scrolls itself, and the form's
 * lower fields (note, Cancel, Save) render with zero size on Robolectric's small virtual window, so
 * every test that reaches them needs `.performScrollTo()` first, same as
 * [com.jasonarends.forklore.ui.addplace.AddPlaceFormTest].
 */
@RunWith(RobolectricTestRunner::class)
class VisitEditorFormTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun addingADayPreciseVisit_savesTheExactDate() {
    val draftState = mutableStateOf<VisitDraft>(VisitDraft().withDay(today))
    var saved: VisitDraft? = null
    compose.setVisitEditorContent(draftState, onSaveVisit = { saved = draftState.value })

    compose.onNodeWithText("Exact date").performClick()
    compose.onNodeWithTag("visit-date-month").performTextReplacement("6")
    compose.onNodeWithTag("visit-date-day").performTextReplacement("21")
    compose.onNodeWithTag("visit-date-year").performTextReplacement("2026")
    compose.onNodeWithText("Dinner").performScrollTo().performClick()
    compose.onNodeWithTag("visit-save").performScrollTo().performClick()

    assertEquals(DatePrecision.DAY, saved?.precision)
    assertEquals("6", saved?.month)
    assertEquals("21", saved?.day)
    assertEquals("2026", saved?.year)
    assertEquals(Meal.DINNER, saved?.meal)
  }

  @Test
  fun newVisit_showsTodaysDateFilledIn() {
    val draftState = mutableStateOf<VisitDraft>(VisitDraft().withDay(today))
    compose.setVisitEditorContent(draftState)

    compose.onNodeWithTag("visit-date-month").assert(hasText("10"))
    compose.onNodeWithTag("visit-date-day").assert(hasText("3"))
    compose.onNodeWithTag("visit-date-year").assert(hasText("2026"))
  }

  @Test
  fun tappingYesterday_setsYesterdaysDate() {
    val draftState = mutableStateOf<VisitDraft>(VisitDraft().withDay(today))
    compose.setVisitEditorContent(draftState)

    compose.onNodeWithTag("visit-date-yesterday").performClick()

    val draft = draftState.value
    assertEquals(DatePrecision.DAY, draft.precision)
    assertEquals("2026", draft.year)
    assertEquals("10", draft.month)
    assertEquals("2", draft.day)
    compose.onNodeWithTag("visit-date-day").assert(hasText("2"))
  }

  @Test
  fun tappingToday_afterYesterday_restoresTodaysDate() {
    val draftState = mutableStateOf<VisitDraft>(VisitDraft().withDay(today))
    compose.setVisitEditorContent(draftState)

    compose.onNodeWithTag("visit-date-yesterday").performClick()
    compose.onNodeWithTag("visit-date-today").performClick()

    assertEquals("3", draftState.value.day)
  }

  @Test
  fun addingAMonthOnlyVisit_leavesTheDayFieldOffScreen() {
    val draftState = mutableStateOf<VisitDraft>(VisitDraft().withDay(today))
    var saved: VisitDraft? = null
    compose.setVisitEditorContent(draftState, onSaveVisit = { saved = draftState.value })

    compose.onNodeWithText("Month only").performClick()
    compose.onNodeWithTag("visit-date-month").performTextReplacement("6")
    compose.onNodeWithTag("visit-date-year").performTextReplacement("2026")
    compose.onNodeWithTag("visit-date-day").assertDoesNotExist()
    compose.onNodeWithTag("visit-save").performScrollTo().performClick()

    assertEquals(DatePrecision.MONTH, saved?.precision)
    assertEquals("6", saved?.month)
    assertEquals("2026", saved?.year)
  }

  @Test
  fun addingAVisitWithNoDate_needsNoDateFieldsAtAll() {
    val draftState = mutableStateOf<VisitDraft>(VisitDraft().withDay(today))
    var saved: VisitDraft? = null
    compose.setVisitEditorContent(draftState, onSaveVisit = { saved = draftState.value })

    // Matches issue #5's "Verano — no date at all" source note: the date defaults to today
    // (issue #32), and "No date" is still one tap away.
    compose.onNodeWithText("No date").performClick()
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
  fun aNonHouseholdPerson_isOfferedAsAnAttendee_withNoRevealStep() {
    val dale =
      PersonEntity(
        name = "Dale",
        normalizedName = "dale",
        isHouseholdMember = false,
        createdAt = 0,
        updatedAt = 0,
      )
    val draftState = mutableStateOf<VisitDraft>(VisitDraft().withDay(today))
    compose.setVisitEditorContent(draftState, people = listOf(dale))

    compose.onNodeWithTag("person-picker-chip-${dale.id}").performScrollTo().performClick()

    assertEquals(setOf(dale.id), draftState.value.attendees)
  }

  @Test
  fun cancellingTheForm_reportsCancelWithoutSaving() {
    val draftState = mutableStateOf<VisitDraft>(VisitDraft().withDay(today))
    var saveCount = 0
    var cancelCount = 0
    compose.setVisitEditorContent(
      draftState,
      onSaveVisit = { saveCount++ },
      onCancel = { cancelCount++ },
    )

    compose.onNodeWithTag("visit-cancel").performScrollTo().performClick()

    assertEquals(1, cancelCount)
    assertEquals(0, saveCount)
  }

  /**
   * [draftState] is created by the caller, outside `setContent`, and only read/written from inside
   * it — the same shape `PersonPickerTest` uses for a `MutableState` a test drives through taps.
   * Declaring it *inside* the `setContent` lambda instead would recreate a fresh, un-`remember`ed
   * `State` on every recomposition and silently discard every prior keystroke, which is exactly the
   * bug this shape avoids.
   */
  private fun ComposeContentTestRule.setVisitEditorContent(
    draftState: MutableState<VisitDraft>,
    people: List<PersonEntity> = emptyList(),
    onCreatePerson: (String, Boolean) -> Unit = { _, _ -> },
    onSaveVisit: () -> Unit = {},
    onCancel: () -> Unit = {},
  ) {
    setContent {
      ForkloreTheme {
        VisitEditorForm(
          draft = draftState.value,
          people = people,
          onPrecisionChange = {
            draftState.value = draftState.value.copy(precision = it, error = null)
          },
          onQuickDate = {
            draftState.value =
              draftState.value.withDay(today.minusDays(it.daysAgo)).copy(error = null)
          },
          onYearChange = { draftState.value = draftState.value.copy(year = it) },
          onMonthChange = { draftState.value = draftState.value.copy(month = it) },
          onDayChange = { draftState.value = draftState.value.copy(day = it) },
          onMealChange = { draftState.value = draftState.value.copy(meal = it) },
          onNoteChange = { draftState.value = draftState.value.copy(note = it) },
          onAttendeesChange = { draftState.value = draftState.value.copy(attendees = it) },
          onCreatePerson = onCreatePerson,
          onSave = onSaveVisit,
          onCancel = onCancel,
        )
      }
    }
  }
}
