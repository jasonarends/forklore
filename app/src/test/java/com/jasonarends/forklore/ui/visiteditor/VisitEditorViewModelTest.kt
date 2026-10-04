package com.jasonarends.forklore.ui.visiteditor

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.DatePrecision
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.Meal
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceListEntity
import com.jasonarends.forklore.data.db.VisitDao
import com.jasonarends.forklore.data.db.VisitWithAttendees
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.PersonRepository
import com.jasonarends.forklore.data.repository.VisitRepository
import com.jasonarends.forklore.testing.MainDispatcherRule
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// 02:00 UTC on 3 Oct 2026: still the evening of the 2nd in Los Angeles (UTC-7).
private const val PINNED_NOW = 1_790_992_800_000L
private const val OCT_3_2026 = 20_729L
private const val OCT_2_2026 = 20_728L

/**
 * Runs against a real in-memory Room database, per
 * [com.jasonarends.forklore.ui.people.PeopleViewModelTest] — not hand-rolled DAO fakes, so a wrong
 * query drifts against real SQLite behaviour rather than a fake's approximation of it.
 */
@RunWith(RobolectricTestRunner::class)
class VisitEditorViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private lateinit var db: ForkloreDatabase
  private lateinit var visitRepository: VisitRepository
  private lateinit var personRepository: PersonRepository
  private lateinit var viewModel: VisitEditorViewModel
  private lateinit var entryId: String
  private lateinit var ana: String

  @Before
  fun setUp() = runTest {
    db =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          ForkloreDatabase::class.java,
        )
        .allowMainThreadQueries()
        .setQueryExecutor { it.run() }
        .setTransactionExecutor { it.run() }
        .build()
    visitRepository = VisitRepository(db, db.visitDao(), Clock { 0L })
    personRepository = PersonRepository(db.personDao(), Clock { 0L })

    val listId = "list"
    db
      .placeListDao()
      .insert(PlaceListEntity(id = listId, name = "Ours", createdAt = 0, updatedAt = 0))
    val placeId = "place"
    db.placeDao().insert(PlaceEntity(id = placeId, name = "Halberd", createdAt = 0, updatedAt = 0))
    entryId = "entry"
    insertEntry(entryId, listId, placeId)
    ana = "ana"
    db
      .personDao()
      .insert(
        PersonEntity(id = ana, name = "Ana", normalizedName = "ana", createdAt = 0, updatedAt = 0)
      )

    // Built here, not in a field initializer: stateIn launches on viewModelScope
    // (Dispatchers.Main) as soon as the ViewModel exists, so it must not run before
    // MainDispatcherRule replaces Dispatchers.Main, which happens after field initializers.
    viewModel = newViewModel(entryId)
  }

  @After fun tearDown() = db.close()

  private suspend fun insertEntry(id: String, listId: String, placeId: String) {
    db
      .placeEntryDao()
      .insert(
        PlaceEntryEntity(
          id = id,
          placeListId = listId,
          placeId = placeId,
          createdAt = 0,
          updatedAt = 0,
        )
      )
  }

  private fun newViewModel(
    entry: String,
    visitId: String? = null,
    zone: ZoneId = ZoneId.of("UTC"),
  ) =
    VisitEditorViewModel(
      visitRepository,
      personRepository,
      entry,
      visitId,
      Clock { PINNED_NOW },
      { zone },
    )

  private suspend fun recordVisit(
    dateEpochDay: Long? = 20_625,
    precision: DatePrecision = DatePrecision.DAY,
    meal: Meal? = null,
    note: String = "",
    attendees: List<String> = emptyList(),
    entry: String = entryId,
  ): String =
    visitRepository.record(
      placeEntryId = entry,
      dateEpochDay = dateEpochDay,
      datePrecision = precision,
      meal = meal,
      note = note,
      attendees = attendees,
    )

  private suspend fun storedVisits() = db.visitDao().observeForPlaceEntry(entryId).first()

  @Test
  fun anAddOpensWithADraftDatedToday() = runTest {
    val draft = viewModel.draft.value!!
    assertEquals(DatePrecision.DAY, draft.precision)
    assertEquals(OCT_3_2026, draft.resolveDate().getOrThrow())
    assertEquals("", draft.note)
    assertTrue(draft.attendees.isEmpty())
  }

  @Test
  fun anAdd_honoursTheTimeZoneWhenPickingToday() = runTest {
    val la = newViewModel(entryId, zone = ZoneId.of("America/Los_Angeles"))

    val draft = la.draft.value!!
    assertEquals(DatePrecision.DAY, draft.precision)
    assertEquals("2", draft.day)
    assertEquals(OCT_2_2026, draft.resolveDate().getOrThrow())
  }

  @Test
  fun savingANewVisitWithoutTouchingTheDate_storesTodayAtDayPrecision() = runTest {
    viewModel.save()

    val stored = storedVisits().single().visit
    assertEquals(DatePrecision.DAY, stored.datePrecision)
    assertEquals(OCT_3_2026, stored.dateEpochDay)
  }

  @Test
  fun quickDate_yesterday_setsTheDayBeforeToday() = runTest {
    viewModel.onPrecisionChange(DatePrecision.UNKNOWN)

    viewModel.onQuickDate(QuickDate.YESTERDAY)

    val draft = viewModel.draft.value!!
    assertEquals(DatePrecision.DAY, draft.precision)
    assertEquals(OCT_2_2026, draft.resolveDate().getOrThrow())
  }

  @Test
  fun quickDate_today_replacesAnEarlierPick() = runTest {
    viewModel.onQuickDate(QuickDate.YESTERDAY)

    viewModel.onQuickDate(QuickDate.TODAY)

    assertEquals(OCT_3_2026, viewModel.draft.value!!.resolveDate().getOrThrow())
  }

  @Test
  fun quickDate_onAnEditedVisit_isSavedAsTheNewDate() = runTest {
    val editor = newViewModel(entryId, recordVisit(dateEpochDay = 20_000))

    editor.onQuickDate(QuickDate.YESTERDAY)
    editor.save()

    assertEquals(OCT_2_2026, storedVisits().single().visit.dateEpochDay)
  }

  @Test
  fun savingADayPreciseVisit_writesTheExactDateAndReportsSaved() = runTest {
    viewModel.onPrecisionChange(DatePrecision.DAY)
    viewModel.onYearChange("2026")
    viewModel.onMonthChange("6")
    viewModel.onDayChange("21")
    viewModel.onMealChange(Meal.BREAKFAST)
    viewModel.onNoteChange("Great coffee")
    viewModel.onAttendeesChange(setOf(ana))

    viewModel.save()

    assertTrue(viewModel.saved.value)
    val stored = storedVisits().single()
    assertEquals(DatePrecision.DAY, stored.visit.datePrecision)
    assertEquals(20_625L, stored.visit.dateEpochDay)
    assertEquals(Meal.BREAKFAST, stored.visit.meal)
    assertEquals("Great coffee", stored.visit.note)
    assertEquals(listOf("Ana"), stored.attendees.map { it.name })
  }

  @Test
  fun savingAMonthPreciseVisit_usesTheFirstOfTheMonth() = runTest {
    viewModel.onPrecisionChange(DatePrecision.MONTH)
    viewModel.onYearChange("2026")
    viewModel.onMonthChange("6")

    viewModel.save()

    val stored = storedVisits().single().visit
    assertEquals(DatePrecision.MONTH, stored.datePrecision)
    assertEquals(20_605L, stored.dateEpochDay)
  }

  @Test
  fun savingWithNoDate_storesNoEpochDay() = runTest {
    viewModel.onPrecisionChange(DatePrecision.UNKNOWN)
    viewModel.onNoteChange("Verano")

    viewModel.save()

    val stored = storedVisits().single().visit
    assertEquals(DatePrecision.UNKNOWN, stored.datePrecision)
    assertNull(stored.dateEpochDay)
    assertEquals("Verano", stored.note)
  }

  @Test
  fun savingAnImpossibleDate_reportsAnErrorAndStaysOpen() = runTest {
    viewModel.onPrecisionChange(DatePrecision.DAY)
    viewModel.onYearChange("2026")
    viewModel.onMonthChange("2")
    viewModel.onDayChange("30") // no such day

    viewModel.save()

    assertNotNull(viewModel.draft.value!!.error)
    assertFalse(viewModel.saved.value)
    assertEquals(0, storedVisits().size)
  }

  @Test
  fun savingADayPreciseVisit_withABlankField_reportsAnError() = runTest {
    viewModel.onPrecisionChange(DatePrecision.DAY)
    viewModel.onYearChange("2026")
    viewModel.onMonthChange("6")
    viewModel.onDayChange("") // the default fills today's day; blank it

    viewModel.save()

    assertNotNull(viewModel.draft.value?.error)
  }

  @Test
  fun save_recoversFromAWriteFailure_insteadOfCrashing() = runTest {
    // "no-such-entry" doesn't exist, so the visit insert violates the same foreign key
    // AcceptanceSpecTest and VisitRepositoryTest already exercise — a real, deterministic write
    // failure with no mocking framework involved.
    val brokenViewModel = newViewModel("no-such-entry")

    brokenViewModel.save()

    val draft = brokenViewModel.draft.value
    assertNotNull(draft)
    assertFalse(draft!!.saving)
    assertNotNull(draft.error)
    assertFalse(brokenViewModel.saved.value)
  }

  @Test
  fun anEdit_prefillsTheDraftFromTheExistingVisit() = runTest {
    val visitId = recordVisit(meal = Meal.DINNER, note = "Loud but good", attendees = listOf(ana))

    val draft = newViewModel(entryId, visitId).draft.value!!

    assertEquals(DatePrecision.DAY, draft.precision)
    assertEquals("2026", draft.year)
    assertEquals("6", draft.month)
    assertEquals("21", draft.day)
    assertEquals(Meal.DINNER, draft.meal)
    assertEquals("Loud but good", draft.note)
    assertEquals(setOf(ana), draft.attendees)
  }

  @Test
  fun anEdit_keepsAnExistingVisitsDateRatherThanDefaultingToToday() = runTest {
    val editor = newViewModel(entryId, recordVisit())

    editor.save()

    val stored = storedVisits().single().visit
    assertEquals(DatePrecision.DAY, stored.datePrecision)
    assertEquals(20_625L, stored.dateEpochDay)
  }

  @Test
  fun editingAnUndatedVisit_leavesItUndated() = runTest {
    val editor =
      newViewModel(
        entryId,
        recordVisit(dateEpochDay = null, precision = DatePrecision.UNKNOWN, note = "Verano"),
      )
    assertEquals(DatePrecision.UNKNOWN, editor.draft.value!!.precision)

    editor.onNoteChange("Verano, again")
    editor.save()

    val stored = storedVisits().single().visit
    assertEquals(DatePrecision.UNKNOWN, stored.datePrecision)
    assertNull(stored.dateEpochDay)
  }

  @Test
  fun savingAnEditedVisit_updatesTheStoredRowInPlaceRatherThanAddingASecondOne() = runTest {
    val editor = newViewModel(entryId, recordVisit(note = "First note"))

    editor.onNoteChange("Corrected note")
    editor.save()

    val visits = storedVisits()
    assertEquals(1, visits.size)
    assertEquals("Corrected note", visits.single().visit.note)
  }

  @Test
  fun savingAnEditedVisit_replacesAttendeesToMatchTheDraftExactly() = runTest {
    val bo = "bo"
    db
      .personDao()
      .insert(
        PersonEntity(id = bo, name = "Bo", normalizedName = "bo", createdAt = 0, updatedAt = 0)
      )
    val editor =
      newViewModel(
        entryId,
        recordVisit(
          dateEpochDay = null,
          precision = DatePrecision.UNKNOWN,
          attendees = listOf(ana),
        ),
      )

    editor.onAttendeesChange(setOf(bo))
    editor.save()

    assertEquals(listOf("Bo"), storedVisits().single().attendees.map { it.name })
  }

  @Test
  fun creatingAPerson_addsThemToTheDraftsAttendees() = runTest {
    viewModel.onCreatePerson("Casey", false)

    val casey = db.personDao().observeAll().first().single { it.name == "Casey" }
    assertEquals(setOf(casey.id), viewModel.draft.value!!.attendees)
  }

  @Test
  fun creatingAPerson_recordsTheUsersHouseholdChoice_readBackFromTheDatabase() = runTest {
    viewModel.onCreatePerson("Casey", true)
    viewModel.onCreatePerson("Dale", false)

    val stored = db.personDao().observeAll().first().associateBy { it.name }
    assertTrue(stored.getValue("Casey").isHouseholdMember)
    assertFalse(stored.getValue("Dale").isHouseholdMember)
  }

  @Test
  fun uiState_offersEveryoneAsAnAttendee_onceTheFormIsReady() = runTest {
    val ready =
      viewModel.uiState.first { it is VisitEditorUiState.Ready } as VisitEditorUiState.Ready
    assertEquals(listOf("Ana"), ready.people.map { it.name })
  }

  @Test
  fun anUntouchedAdd_hasNoUnsavedChanges() = runTest {
    assertFalse(viewModel.hasUnsavedChanges.first())
  }

  @Test
  fun typingANote_marksUnsavedChanges_andClearingItAgainUnmarksThem() = runTest {
    viewModel.onNoteChange("Loud")
    assertTrue(viewModel.hasUnsavedChanges.value)

    viewModel.onNoteChange("")
    assertFalse(viewModel.hasUnsavedChanges.value)
  }

  @Test
  fun changingAnythingOnAnAdd_marksUnsavedChanges() = runTest {
    viewModel.onAttendeesChange(setOf(ana))
    assertTrue(viewModel.hasUnsavedChanges.value)
  }

  @Test
  fun aFailedSave_leavesTheChangesUnsaved() = runTest {
    val brokenViewModel = newViewModel("no-such-entry")
    brokenViewModel.onNoteChange("Loud")

    brokenViewModel.save()

    assertTrue(brokenViewModel.hasUnsavedChanges.value)
  }

  @Test
  fun aSuccessfulSave_isNoLongerUnsaved() = runTest {
    viewModel.onNoteChange("Loud")

    viewModel.save()

    assertFalse(viewModel.hasUnsavedChanges.value)
  }

  @Test
  fun anUntouchedEdit_hasNoUnsavedChanges_andAnEditedOneDoes() = runTest {
    val editor = newViewModel(entryId, recordVisit(note = "Loud but good", attendees = listOf(ana)))
    assertFalse(editor.hasUnsavedChanges.first())

    editor.onNoteChange("Quiet")

    assertTrue(editor.hasUnsavedChanges.value)
  }

  @Test
  fun editingOneVisit_neverShowsAnotherVisitsDraft() = runTest {
    val a = newViewModel(entryId, recordVisit(note = "Visit A"))
    val b = newViewModel(entryId, recordVisit(note = "Visit B"))

    a.onNoteChange("Visit A, edited")

    assertEquals("Visit B", b.draft.value!!.note)
    assertFalse(b.hasUnsavedChanges.value)
  }

  @Test
  fun aVisitThatDoesNotExist_isNotFound_withNoDraft() = runTest {
    val editor = newViewModel(entryId, "no-such-visit")

    assertEquals(
      VisitEditorUiState.NotFound,
      editor.uiState.first { it !is VisitEditorUiState.Loading },
    )
    assertNull(editor.draft.value)
    assertFalse(editor.hasUnsavedChanges.value)
  }

  @Test
  fun aFailedLoad_isAnError_notNotFound() = runTest {
    val visitId = recordVisit()
    val failingDao =
      object : VisitDao by db.visitDao() {
        override fun observeForPlaceEntry(placeEntryId: String): Flow<List<VisitWithAttendees>> =
          flow {
            throw SQLiteException("disk I/O error")
          }
      }
    val editor =
      VisitEditorViewModel(
        VisitRepository(db, failingDao, Clock { 0L }),
        personRepository,
        entryId,
        visitId,
        Clock { PINNED_NOW },
      )

    val state = editor.uiState.first { it !is VisitEditorUiState.Loading }

    assertTrue(state is VisitEditorUiState.Error)
    assertNull(editor.draft.value)
  }

  @Test
  fun aVisitFromAnotherPlaceEntry_isNotFound() = runTest {
    db
      .placeDao()
      .insert(PlaceEntity(id = "place2", name = "Elsewhere", createdAt = 0, updatedAt = 0))
    insertEntry("other", "list", "place2")
    val elsewhere = recordVisit(entry = "other")

    val editor = newViewModel(entryId, elsewhere)

    assertEquals(
      VisitEditorUiState.NotFound,
      editor.uiState.first { it !is VisitEditorUiState.Loading },
    )
    assertNull(editor.draft.value)
  }
}
