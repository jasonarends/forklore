package com.jasonarends.forklore.ui.placedetail

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.DishStatus
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceListEntity
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.DishRepository
import com.jasonarends.forklore.data.repository.PersonRepository
import com.jasonarends.forklore.testing.MainDispatcherRule
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A real in-memory Room database, per [DishesViewModelTest]. */
@RunWith(RobolectricTestRunner::class)
class DishInterestsViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  @get:Rule val mainDispatcherRule = MainDispatcherRule(testDispatcher)

  private lateinit var db: ForkloreDatabase
  private lateinit var dishRepository: DishRepository
  private lateinit var personRepository: PersonRepository
  private lateinit var entryId: String
  private lateinit var dishId: String

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
      dishRepository =
        DishRepository(db.dishDao(), db.dishInterestDao(), db.dishOpinionDao(), Clock { 0L })
      personRepository = PersonRepository(db.personDao(), Clock { 0L })

      db
        .placeListDao()
        .insert(PlaceListEntity(id = "list", name = "Ours", createdAt = 0, updatedAt = 0))
      db
        .placeDao()
        .insert(PlaceEntity(id = "place", name = "Halberd", createdAt = 0, updatedAt = 0))
      entryId = "entry"
      db
        .placeEntryDao()
        .insert(
          PlaceEntryEntity(
            id = entryId,
            placeListId = "list",
            placeId = "place",
            createdAt = 0,
            updatedAt = 0,
          )
        )
      dishId = dishRepository.findOrCreateDish(entryId, "Barrel Potatoes")
    }

  @After fun tearDown() = db.close()

  private fun TestScope.viewModel(): DishInterestsViewModel {
    val viewModel = DishInterestsViewModel(dishRepository, personRepository, entryId)
    backgroundScope.launch { viewModel.uiState.collect {} }
    return viewModel
  }

  private fun DishInterestsViewModel.success() = uiState.value as DishInterestsUiState.Success

  @Test
  fun savingANewInterest_withAllFourDimensions_landsInRoom_andClosesTheForm() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      val robin = personRepository.findOrCreate("Robin", isHouseholdMember = true)
      val dale = personRepository.findOrCreate("Dale")

      viewModel.startAdd(dishId)
      viewModel.onStatusChange(DishStatus.WANT)
      viewModel.onForPersonChange(robin)
      viewModel.onRecommendedByChange(dale)
      viewModel.onModificationChange("add a Chilli bomb")
      viewModel.onNoteChange("Dale insisted")
      viewModel.save()
      advanceUntilIdle()

      val stored = db.dishInterestDao().observeForPlaceEntry(entryId).first().single()
      assertEquals(DishStatus.WANT, stored.status)
      assertEquals(robin, stored.forPersonId)
      assertEquals(dale, stored.recommendedById)
      assertEquals("add a Chilli bomb", stored.modification)
      assertEquals("Dale insisted", stored.note)
      assertNull(viewModel.draft.value)
      assertEquals(listOf(stored.id), viewModel.success().forDish(dishId).map { it.id })
    }

  @Test
  fun aBareInterest_needsNothingButAStatus() =
    runTest(testDispatcher) {
      val viewModel = viewModel()

      viewModel.startAdd(dishId)
      viewModel.onStatusChange(DishStatus.NEVER_AGAIN)
      viewModel.save()
      advanceUntilIdle()

      val stored = db.dishInterestDao().observeForPlaceEntry(entryId).first().single()
      assertEquals(DishStatus.NEVER_AGAIN, stored.status)
      assertNull(stored.forPersonId)
      assertNull(stored.recommendedById)
      assertNull(stored.modification)
      assertEquals("", stored.note)
    }

  @Test
  fun editingAnInterest_prefillsTheForm_andSavesOverTheSameRow() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      val robin = personRepository.findOrCreate("Robin", isHouseholdMember = true)
      val id =
        dishRepository.setInterest(dishId, DishStatus.WANT, forPersonId = robin, modification = "x")
      advanceUntilIdle()

      viewModel.startEdit(viewModel.success().interests.single())
      assertEquals(robin, viewModel.draft.value!!.forPersonId)
      assertEquals("x", viewModel.draft.value!!.modification)
      viewModel.onForPersonChange(null)
      viewModel.onStatusChange(DishStatus.TRIED)
      viewModel.save()
      advanceUntilIdle()

      val stored = db.dishInterestDao().observeForPlaceEntry(entryId).first().single()
      assertEquals(id, stored.id)
      assertEquals(DishStatus.TRIED, stored.status)
      assertNull(stored.forPersonId)
    }

  @Test
  fun removing_softDeletesTheInterest_andClosesTheForm() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      val id = dishRepository.setInterest(dishId, DishStatus.NEVER_AGAIN)
      advanceUntilIdle()

      viewModel.startEdit(viewModel.success().interests.single())
      viewModel.remove()
      advanceUntilIdle()

      assertEquals(emptyList<Any>(), viewModel.success().interests)
      assertNotNull(db.dishInterestDao().byId(id)!!.deletedAt)
      assertNull(viewModel.draft.value)
    }

  @Test
  fun removing_whileAdding_doesNothing() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      viewModel.startAdd(dishId)

      viewModel.remove()
      advanceUntilIdle()

      assertNotNull(viewModel.draft.value)
    }

  @Test
  fun cancelling_discardsTheDraft_withoutWriting() =
    runTest(testDispatcher) {
      val viewModel = viewModel()

      viewModel.startAdd(dishId)
      viewModel.onModificationChange("never saved")
      viewModel.cancelDraft()
      advanceUntilIdle()

      assertNull(viewModel.draft.value)
      assertEquals(emptyList<Any>(), db.dishInterestDao().observeForPlaceEntry(entryId).first())
    }

  @Test
  fun startingAnotherDishsForm_replacesTheOpenOne() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      val other = dishRepository.findOrCreateDish(entryId, "Reuben")

      viewModel.startAdd(dishId)
      viewModel.startAdd(other)

      assertEquals(other, viewModel.draft.value!!.dishId)
    }

  @Test
  fun creatingAPersonFromEitherPicker_selectsThemInThatField_only() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      viewModel.startAdd(dishId)

      viewModel.onCreateRecommender("Marvin")
      advanceUntilIdle()
      viewModel.onCreateForPerson("Robin")
      advanceUntilIdle()

      val people = viewModel.success().people.associateBy { it.name }
      assertEquals(people.getValue("Marvin").id, viewModel.draft.value!!.recommendedById)
      assertEquals(people.getValue("Robin").id, viewModel.draft.value!!.forPersonId)
    }

  @Test
  fun interests_appearOnlyUnderTheirOwnDish() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      val other = dishRepository.findOrCreateDish(entryId, "Reuben")
      dishRepository.setInterest(dishId, DishStatus.WANT)
      dishRepository.setInterest(other, DishStatus.NEVER_AGAIN)
      advanceUntilIdle()

      assertEquals(listOf(DishStatus.WANT), viewModel.success().forDish(dishId).map { it.status })
      assertEquals(
        listOf(DishStatus.NEVER_AGAIN),
        viewModel.success().forDish(other).map { it.status },
      )
    }
}
