package com.jasonarends.forklore.ui.placedetail

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceListEntity
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.DishRepository
import com.jasonarends.forklore.testing.MainDispatcherRule
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A real in-memory Room database, per [PlaceDetailViewModelTest] — not hand-rolled DAO fakes, so
 * [DishesViewModel.suggestions] is exercised against the same alias matching [DishRepository]
 * actually ships.
 */
@RunWith(RobolectricTestRunner::class)
class DishesViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  @get:Rule val mainDispatcherRule = MainDispatcherRule(testDispatcher)

  private lateinit var db: ForkloreDatabase
  private lateinit var repository: DishRepository
  private lateinit var entryId: String

  @Before
  fun setUp() =
    runTest(testDispatcher) {
      db =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ForkloreDatabase::class.java,
          )
          .allowMainThreadQueries()
          // Room runs @Transaction reads (DishDao.observeForPlaceEntry) on this executor;
          // same-thread keeps them visible to advanceUntilIdle(), per PlaceDetailViewModelTest.
          .setTransactionExecutor(Executor { it.run() })
          .build()
      repository =
        DishRepository(db.dishDao(), db.dishInterestDao(), db.dishOpinionDao(), Clock { 0L })

      val listId = "list"
      db
        .placeListDao()
        .insert(PlaceListEntity(id = listId, name = "Ours", createdAt = 0, updatedAt = 0))
      val placeId = "place"
      db
        .placeDao()
        .insert(PlaceEntity(id = placeId, name = "Halberd", createdAt = 0, updatedAt = 0))
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
    }

  @After fun tearDown() = db.close()

  private fun viewModel() = DishesViewModel(repository, entryId)

  @Test
  fun addDish_createsIt_andClearsTheQuery() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      backgroundScope.launch { viewModel.uiState.collect {} }
      backgroundScope.launch { viewModel.suggestions.collect {} }

      viewModel.onQueryChange("Barrel Potatoes")
      viewModel.addDish("Barrel Potatoes")
      advanceUntilIdle()

      val state = viewModel.uiState.value as DishesUiState.Success
      assertEquals(listOf("Barrel Potatoes"), state.dishes.map { it.dish.canonicalName })
      assertEquals("", viewModel.query.value)
    }

  @Test
  fun addDish_calledTwiceWithTheSameSpelling_doesNotCreateADuplicate() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      backgroundScope.launch { viewModel.uiState.collect {} }
      backgroundScope.launch { viewModel.suggestions.collect {} }

      viewModel.addDish("Barrel Potatoes")
      advanceUntilIdle()
      viewModel.addDish("barrel   potatoes")
      advanceUntilIdle()

      val state = viewModel.uiState.value as DishesUiState.Success
      assertEquals(1, state.dishes.size)
    }

  /**
   * The issue #6 "done when" case: typing a dish under a spelling already taught to an existing
   * dish (via [DishesViewModel.addAlias]) offers that dish as a suggestion, and submitting it
   * resolves to the same row rather than creating a second one.
   */
  @Test
  fun typingAKnownDishUnderADifferentSpelling_offersTheExistingDish_ratherThanCreatingASecondOne() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      backgroundScope.launch { viewModel.uiState.collect {} }
      backgroundScope.launch { viewModel.suggestions.collect {} }
      viewModel.addDish("Barrel Potatoes")
      advanceUntilIdle()
      val existingId = (viewModel.uiState.value as DishesUiState.Success).dishes.single().dish.id
      viewModel.addAlias(existingId, "barrel tots")
      advanceUntilIdle()

      viewModel.onQueryChange("Barrel Tots")
      advanceUntilIdle()
      val suggestions = viewModel.suggestions.value
      assertEquals(1, suggestions.size)
      assertEquals(existingId, suggestions.single().dish.id)

      // The user picks the suggestion rather than typing a brand new dish, so this is what
      // tapping the suggestion chip submits.
      viewModel.addDish("Barrel Tots")
      advanceUntilIdle()

      val dishes = db.dishDao().observeForPlaceEntry(entryId).first()
      assertEquals(1, dishes.size)
      assertEquals(existingId, dishes.single().dish.id)
    }

  @Test
  fun blankQuery_offersNoSuggestions() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      backgroundScope.launch { viewModel.uiState.collect {} }
      backgroundScope.launch { viewModel.suggestions.collect {} }
      viewModel.addDish("Barrel Potatoes")
      advanceUntilIdle()

      viewModel.onQueryChange("")
      advanceUntilIdle()

      assertTrue(viewModel.suggestions.value.isEmpty())
    }

  @Test
  fun aQueryMatchingNoRecordedDish_offersNoSuggestions() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      backgroundScope.launch { viewModel.uiState.collect {} }
      backgroundScope.launch { viewModel.suggestions.collect {} }
      viewModel.addDish("Barrel Potatoes")
      advanceUntilIdle()

      viewModel.onQueryChange("wedge salad")
      advanceUntilIdle()

      assertTrue(viewModel.suggestions.value.isEmpty())
    }

  @Test
  fun addAlias_isReflectedInTheDishesList() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      backgroundScope.launch { viewModel.uiState.collect {} }
      backgroundScope.launch { viewModel.suggestions.collect {} }
      viewModel.addDish("Crème Brûlée")
      advanceUntilIdle()
      val dishId = (viewModel.uiState.value as DishesUiState.Success).dishes.single().dish.id

      // A genuinely different spelling, not just the dish's own normalized name repeated — that
      // case is already covered by [DishRepositoryTest] and wouldn't insert a new alias row.
      viewModel.addAlias(dishId, "Burnt Cream")
      advanceUntilIdle()

      val state = viewModel.uiState.value as DishesUiState.Success
      assertEquals(listOf("Burnt Cream"), state.dishes.single().aliases.map { it.alias })
    }
}
