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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
    viewModel = VisitsViewModel(visitRepository, personRepository, entryId)
  }

  @After fun tearDown() = db.close()

  @Test
  fun startAdd_opensABlankDraft() = runTest {
    viewModel.startAdd()

    val draft = viewModel.draft.value!!
    assertNull(draft.visitId)
    assertEquals(DatePrecision.UNKNOWN, draft.precision)
    assertEquals("", draft.note)
    assertTrue(draft.attendees.isEmpty())
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
    // day left blank

    viewModel.save()

    assertNotNull(viewModel.draft.value?.error)
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
