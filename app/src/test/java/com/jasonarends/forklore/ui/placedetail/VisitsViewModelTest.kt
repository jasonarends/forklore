package com.jasonarends.forklore.ui.placedetail

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.DatePrecision
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceListEntity
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.VisitRepository
import com.jasonarends.forklore.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The add/edit form's tests live with
 * [com.jasonarends.forklore.ui.visiteditor.VisitEditorViewModel]; this ViewModel only lists a place
 * entry's visits.
 */
@RunWith(RobolectricTestRunner::class)
class VisitsViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val db: ForkloreDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        ForkloreDatabase::class.java,
      )
      .allowMainThreadQueries()
      .setQueryExecutor { it.run() }
      .setTransactionExecutor { it.run() }
      .build()

  @After fun tearDown() = db.close()

  private suspend fun entry(id: String, placeId: String) {
    db.placeDao().insert(PlaceEntity(id = placeId, name = placeId, createdAt = 0, updatedAt = 0))
    db
      .placeEntryDao()
      .insert(
        PlaceEntryEntity(
          id = id,
          placeListId = "list",
          placeId = placeId,
          createdAt = 0,
          updatedAt = 0,
        )
      )
  }

  @Test
  fun uiState_reflectsVisitsNewestFirst() = runTest {
    db
      .placeListDao()
      .insert(PlaceListEntity(id = "list", name = "Ours", createdAt = 0, updatedAt = 0))
    entry("entry", "place")
    val visitRepository = VisitRepository(db, db.visitDao(), Clock { 0L })
    visitRepository.record("entry", 20_619, DatePrecision.DAY)
    visitRepository.record("entry", 20_675, DatePrecision.DAY)
    val viewModel = VisitsViewModel(visitRepository, "entry")

    val success =
      viewModel.uiState.first { it is VisitsUiState.Success && it.visits.size == 2 }
        as VisitsUiState.Success
    assertEquals(listOf(20_675L, 20_619L), success.visits.map { it.visit.dateEpochDay })
  }

  @Test
  fun uiState_onlyListsTheGivenEntrysVisits() = runTest {
    db
      .placeListDao()
      .insert(PlaceListEntity(id = "list", name = "Ours", createdAt = 0, updatedAt = 0))
    entry("entry", "place")
    entry("other", "place2")
    val visitRepository = VisitRepository(db, db.visitDao(), Clock { 0L })
    visitRepository.record("other", 20_619, DatePrecision.DAY)
    visitRepository.record("entry", 20_675, DatePrecision.DAY)
    val viewModel = VisitsViewModel(visitRepository, "entry")

    val success = viewModel.uiState.first { it is VisitsUiState.Success } as VisitsUiState.Success
    assertEquals(listOf(20_675L), success.visits.map { it.visit.dateEpochDay })
  }
}
