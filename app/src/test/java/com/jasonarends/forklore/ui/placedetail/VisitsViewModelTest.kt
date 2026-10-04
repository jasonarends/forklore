package com.jasonarends.forklore.ui.placedetail

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.DatePrecision
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.Meal
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceListEntity
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.PersonRepository
import com.jasonarends.forklore.data.repository.VisitRepository
import com.jasonarends.forklore.testing.MainDispatcherRule
import java.time.ZoneId
import kotlinx.coroutines.flow.first
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
class VisitsViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private lateinit var db: ForkloreDatabase
  private lateinit var visitRepository: VisitRepository
  private lateinit var personRepository: PersonRepository
  private lateinit var viewModel: VisitsViewModel
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
    db
      .placeEntryDao()
      .insert(
        PlaceEntryEntity(
          id = entryId,
          placeListId = listId,
          placeId = placeId,
          createdAt = 0,
          updatedAt = 0,
        )
      )
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

  private fun newViewModel(entry: String, zone: ZoneId = ZoneId.of("UTC")) =
    VisitsViewModel(visitRepository, personRepository, entry, Clock { PINNED_NOW }, { zone })

  @Test
  fun startAdd_opensADraftDatedToday() = runTest {
    viewModel.startAdd()

    val draft = viewModel.draft.value!!
    assertNull(draft.visitId)
    assertEquals(DatePrecision.DAY, draft.precision)
    assertEquals(OCT_3_2026, draft.resolveDate().getOrThrow())
    assertEquals("", draft.note)
    assertTrue(draft.attendees.isEmpty())
  }

  @Test
  fun startAdd_honoursTheTimeZoneWhenPickingToday() = runTest {
    val la = newViewModel(entryId, ZoneId.of("America/Los_Angeles"))

    la.startAdd()

    val draft = la.draft.value!!
    assertEquals(DatePrecision.DAY, draft.precision)
    assertEquals("2", draft.day)
    assertEquals(OCT_2_2026, draft.resolveDate().getOrThrow())
  }

  @Test
  fun savingANewVisitWithoutTouchingTheDate_storesTodayAtDayPrecision() = runTest {
    viewModel.startAdd()

    viewModel.save()

    val stored = db.visitDao().observeForPlaceEntry(entryId).first().single().visit
    assertEquals(DatePrecision.DAY, stored.datePrecision)
    assertEquals(OCT_3_2026, stored.dateEpochDay)
  }

  @Test
  fun quickDate_yesterday_setsTheDayBeforeToday() = runTest {
    viewModel.startAdd()
    viewModel.onPrecisionChange(DatePrecision.UNKNOWN)

    viewModel.onQuickDate(QuickDate.YESTERDAY)

    val draft = viewModel.draft.value!!
    assertEquals(DatePrecision.DAY, draft.precision)
    assertEquals(OCT_2_2026, draft.resolveDate().getOrThrow())
  }

  @Test
  fun quickDate_today_replacesAnEarlierPick() = runTest {
    viewModel.startAdd()
    viewModel.onQuickDate(QuickDate.YESTERDAY)

    viewModel.onQuickDate(QuickDate.TODAY)

    assertEquals(OCT_3_2026, viewModel.draft.value!!.resolveDate().getOrThrow())
  }

  @Test
  fun quickDate_onAnEditedVisit_isSavedAsTheNewDate() = runTest {
    visitRepository.record(
      placeEntryId = entryId,
      dateEpochDay = 20_000,
      datePrecision = DatePrecision.DAY,
    )
    viewModel.startEdit(db.visitDao().observeForPlaceEntry(entryId).first().single())

    viewModel.onQuickDate(QuickDate.YESTERDAY)
    viewModel.save()

    val stored = db.visitDao().observeForPlaceEntry(entryId).first().single().visit
    assertEquals(OCT_2_2026, stored.dateEpochDay)
  }

  @Test
  fun cancelDraft_closesTheForm() = runTest {
    viewModel.startAdd()

    viewModel.cancelDraft()

    assertNull(viewModel.draft.value)
  }

  @Test
  fun savingADayPreciseVisit_writesTheExactDateAndClosesTheDraft() = runTest {
    viewModel.startAdd()
    viewModel.onPrecisionChange(DatePrecision.DAY)
    viewModel.onYearChange("2026")
    viewModel.onMonthChange("6")
    viewModel.onDayChange("21")
    viewModel.onMealChange(Meal.BREAKFAST)
    viewModel.onNoteChange("Great coffee")
    viewModel.onAttendeesChange(setOf(ana))

    viewModel.save()

    assertNull(viewModel.draft.value)
    val stored = db.visitDao().observeForPlaceEntry(entryId).first().single()
    assertEquals(DatePrecision.DAY, stored.visit.datePrecision)
    assertEquals(20_625L, stored.visit.dateEpochDay)
    assertEquals(Meal.BREAKFAST, stored.visit.meal)
    assertEquals("Great coffee", stored.visit.note)
    assertEquals(listOf("Ana"), stored.attendees.map { it.name })
  }

  @Test
  fun savingAMonthPreciseVisit_usesTheFirstOfTheMonth() = runTest {
    viewModel.startAdd()
    viewModel.onPrecisionChange(DatePrecision.MONTH)
    viewModel.onYearChange("2026")
    viewModel.onMonthChange("6")

    viewModel.save()

    val stored = db.visitDao().observeForPlaceEntry(entryId).first().single().visit
    assertEquals(DatePrecision.MONTH, stored.datePrecision)
    assertEquals(20_605L, stored.dateEpochDay)
  }

  @Test
  fun savingWithNoDate_storesNoEpochDay() = runTest {
    viewModel.startAdd()
    viewModel.onPrecisionChange(DatePrecision.UNKNOWN)
    viewModel.onNoteChange("Verano")

    viewModel.save()

    val stored = db.visitDao().observeForPlaceEntry(entryId).first().single().visit
    assertEquals(DatePrecision.UNKNOWN, stored.datePrecision)
    assertNull(stored.dateEpochDay)
    assertEquals("Verano", stored.note)
  }

  @Test
  fun savingADayPreciseVisit_withAnImpossibleDate_reportsAnErrorAndKeepsTheDraftOpen() = runTest {
    viewModel.startAdd()
    viewModel.onPrecisionChange(DatePrecision.DAY)
    viewModel.onYearChange("2026")
    viewModel.onMonthChange("2")
    viewModel.onDayChange("30") // no such day

    viewModel.save()

    val draft = viewModel.draft.value
    assertNotNull(draft)
    assertNotNull(draft!!.error)
    assertEquals(0, db.visitDao().observeForPlaceEntry(entryId).first().size)
  }

  @Test
  fun savingADayPreciseVisit_withABlankField_reportsAnError() = runTest {
    viewModel.startAdd()
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
    // failure with no mocking framework involved. Its own ViewModel, not the shared `viewModel`
    // field, since that one is wired to a real entryId.
    val brokenViewModel = newViewModel("no-such-entry")
    brokenViewModel.startAdd()

    brokenViewModel.save()

    val draft = brokenViewModel.draft.value
    assertNotNull(draft)
    assertFalse(draft!!.saving)
    assertNotNull(draft.error)
  }

  @Test
  fun startEdit_prefillsTheDraftFromTheExistingVisit() = runTest {
    val visitId =
      visitRepository.record(
        placeEntryId = entryId,
        dateEpochDay = 20_625,
        datePrecision = DatePrecision.DAY,
        meal = Meal.DINNER,
        note = "Loud but good",
        attendees = listOf(ana),
      )
    val visit = db.visitDao().observeForPlaceEntry(entryId).first().single()

    viewModel.startEdit(visit)

    val draft = viewModel.draft.value!!
    assertEquals(visitId, draft.visitId)
    assertEquals(DatePrecision.DAY, draft.precision)
    assertEquals("2026", draft.year)
    assertEquals("6", draft.month)
    assertEquals("21", draft.day)
    assertEquals(Meal.DINNER, draft.meal)
    assertEquals("Loud but good", draft.note)
    assertEquals(setOf(ana), draft.attendees)
  }

  @Test
  fun startEdit_keepsAnExistingVisitsDateRatherThanDefaultingToToday() = runTest {
    visitRepository.record(
      placeEntryId = entryId,
      dateEpochDay = 20_625,
      datePrecision = DatePrecision.DAY,
    )

    viewModel.startEdit(db.visitDao().observeForPlaceEntry(entryId).first().single())
    viewModel.save()

    val stored = db.visitDao().observeForPlaceEntry(entryId).first().single().visit
    assertEquals(DatePrecision.DAY, stored.datePrecision)
    assertEquals(20_625L, stored.dateEpochDay)
  }

  @Test
  fun editingAnUndatedVisit_leavesItUndated() = runTest {
    visitRepository.record(
      placeEntryId = entryId,
      dateEpochDay = null,
      datePrecision = DatePrecision.UNKNOWN,
      note = "Verano",
    )
    viewModel.startEdit(db.visitDao().observeForPlaceEntry(entryId).first().single())
    assertEquals(DatePrecision.UNKNOWN, viewModel.draft.value!!.precision)

    viewModel.onNoteChange("Verano, again")
    viewModel.save()

    val stored = db.visitDao().observeForPlaceEntry(entryId).first().single().visit
    assertEquals(DatePrecision.UNKNOWN, stored.datePrecision)
    assertNull(stored.dateEpochDay)
  }

  @Test
  fun savingAnEditedVisit_updatesTheStoredRowInPlaceRatherThanAddingASecondOne() = runTest {
    visitRepository.record(
      placeEntryId = entryId,
      dateEpochDay = 20_625,
      datePrecision = DatePrecision.DAY,
      note = "First note",
    )
    val visit = db.visitDao().observeForPlaceEntry(entryId).first().single()

    viewModel.startEdit(visit)
    viewModel.onNoteChange("Corrected note")
    viewModel.save()

    val visits = db.visitDao().observeForPlaceEntry(entryId).first()
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
    visitRepository.record(
      placeEntryId = entryId,
      dateEpochDay = null,
      datePrecision = DatePrecision.UNKNOWN,
      attendees = listOf(ana),
    )
    val visit = db.visitDao().observeForPlaceEntry(entryId).first().single()

    viewModel.startEdit(visit)
    viewModel.onAttendeesChange(setOf(bo))
    viewModel.save()

    val attendees = db.visitDao().observeForPlaceEntry(entryId).first().single().attendees
    assertEquals(listOf("Bo"), attendees.map { it.name })
  }

  @Test
  fun creatingAPerson_addsThemToTheDraftsAttendees() = runTest {
    viewModel.startAdd()

    viewModel.onCreatePerson("Casey")

    val draft = viewModel.draft.value!!
    val casey = db.personDao().observeAll().first().single { it.name == "Casey" }
    assertEquals(setOf(casey.id), draft.attendees)
  }

  @Test
  fun uiState_reflectsVisitsNewestFirst() = runTest {
    visitRepository.record(
      placeEntryId = entryId,
      dateEpochDay = 20_619,
      datePrecision = DatePrecision.DAY,
    )
    visitRepository.record(
      placeEntryId = entryId,
      dateEpochDay = 20_675,
      datePrecision = DatePrecision.DAY,
    )

    val success =
      viewModel.uiState.first { it is VisitsUiState.Success && it.visits.size == 2 }
        as VisitsUiState.Success
    assertEquals(listOf(20_675L, 20_619L), success.visits.map { it.visit.dateEpochDay })
  }
}
