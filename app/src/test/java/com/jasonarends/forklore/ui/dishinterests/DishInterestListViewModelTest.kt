package com.jasonarends.forklore.ui.dishinterests

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.DishStatus
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceListEntity
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.DishRepository
import com.jasonarends.forklore.testing.MainDispatcherRule
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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

@RunWith(RobolectricTestRunner::class)
class DishInterestListViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  @get:Rule val mainDispatcherRule = MainDispatcherRule(testDispatcher)

  private lateinit var db: ForkloreDatabase
  private lateinit var repository: DishRepository

  @Before
  fun setUp() =
    runTest(testDispatcher) {
      db =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ForkloreDatabase::class.java,
          )
          .allowMainThreadQueries()
          .setTransactionExecutor(Executor { it.run() })
          .build()
      repository =
        DishRepository(db.dishDao(), db.dishInterestDao(), db.dishOpinionDao(), Clock { 0L })
      db
        .placeDao()
        .insert(PlaceEntity(id = "place", name = "Halberd", createdAt = 0, updatedAt = 0))
    }

  @After fun tearDown() = db.close()

  private suspend fun entry(listId: String, entryId: String) {
    db
      .placeListDao()
      .insert(PlaceListEntity(id = listId, name = listId, createdAt = 0, updatedAt = 0))
    db
      .placeEntryDao()
      .insert(
        PlaceEntryEntity(
          id = entryId,
          placeListId = listId,
          placeId = "place",
          createdAt = 0,
          updatedAt = 0,
        )
      )
  }

  private fun TestScope.viewModel(listId: MutableStateFlow<String?>): DishInterestListViewModel {
    val viewModel = DishInterestListViewModel(repository, listId)
    backgroundScope.launch { viewModel.uiState.collect {} }
    return viewModel
  }

  private fun DishInterestListViewModel.success() = uiState.value as DishInterestListUiState.Success

  @Test
  fun waitsForTheCurrentListToBeResolved() =
    runTest(testDispatcher) {
      val viewModel = viewModel(MutableStateFlow(null))
      advanceUntilIdle()

      assertEquals(DishInterestListUiState.Loading, viewModel.uiState.value)
    }

  @Test
  fun groupsWantAndNeverAgain_andLeavesTriedToTheDishScreen() =
    runTest(testDispatcher) {
      entry("list", "entry")
      val viewModel = viewModel(MutableStateFlow("list"))
      repository.setInterest(repository.findOrCreateDish("entry", "Burrata"), DishStatus.WANT)
      repository.setInterest(
        repository.findOrCreateDish("entry", "Arancini"),
        DishStatus.NEVER_AGAIN,
      )
      repository.setInterest(repository.findOrCreateDish("entry", "Meatballs"), DishStatus.TRIED)
      advanceUntilIdle()

      assertEquals(listOf("Burrata"), viewModel.success().want.map { it.dishName })
      assertEquals(listOf("Arancini"), viewModel.success().neverAgain.map { it.dishName })
    }

  @Test
  fun showsOnlyTheCurrentLists_andFollowsASwitch() =
    runTest(testDispatcher) {
      entry("ours", "ours-entry")
      entry("mine", "mine-entry")
      repository.setInterest(repository.findOrCreateDish("ours-entry", "Shared"), DishStatus.WANT)
      repository.setInterest(repository.findOrCreateDish("mine-entry", "Private"), DishStatus.WANT)
      val listId = MutableStateFlow<String?>("ours")
      val viewModel = viewModel(listId)
      advanceUntilIdle()

      assertEquals(listOf("Shared"), viewModel.success().want.map { it.dishName })

      listId.value = "mine"
      advanceUntilIdle()

      assertEquals(listOf("Private"), viewModel.success().want.map { it.dishName })
    }

  @Test
  fun aRemovedInterest_leavesTheList() =
    runTest(testDispatcher) {
      entry("list", "entry")
      val viewModel = viewModel(MutableStateFlow("list"))
      val id =
        repository.setInterest(repository.findOrCreateDish("entry", "Burrata"), DishStatus.WANT)
      advanceUntilIdle()
      assertEquals(1, viewModel.success().want.size)

      repository.removeInterest(id)
      advanceUntilIdle()

      assertTrue(viewModel.success().want.isEmpty())
    }
}
