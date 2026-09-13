package com.jasonarends.forklore.ui.people

import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.PersonRepository
import com.jasonarends.forklore.testing.FakePersonDao
import com.jasonarends.forklore.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Runs on an [UnconfinedTestDispatcher], not the default `StandardTestDispatcher` `runTest` gives
 * you: `uiState` is a `stateIn(..., WhileSubscribed(5000), ...)`, so nothing flows until a
 * collector subscribes, and a launched collector needs to actually *run* — not just get scheduled —
 * before the assertions below read `uiState.value`.
 *
 * The ViewModel is built in [setUp], not a field initializer: `stateIn` launches its sharing
 * coroutine on `viewModelScope` (`Dispatchers.Main.immediate`) as soon as the ViewModel exists, so
 * it must not be constructed before [MainDispatcherRule] has replaced `Dispatchers.Main`. A field
 * initializer runs before `@Rule`s start; `@Before` runs after.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PeopleViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val dao = FakePersonDao()
  private val repository = PersonRepository(dao, Clock { 1_000L })
  private lateinit var viewModel: PeopleViewModel

  @Before
  fun setUp() {
    viewModel = PeopleViewModel(repository)
  }

  @Test
  fun uiState_reflectsPeopleAddedThroughTheViewModel() =
    runTest(UnconfinedTestDispatcher()) {
      val job = launch { viewModel.uiState.collect {} }

      viewModel.addPerson("Robin", isHouseholdMember = true)

      val success = viewModel.uiState.value as PeopleUiState.Success
      assertEquals(listOf("Robin"), success.people.map { it.name })
      assertEquals(true, success.people.single().isHouseholdMember)
      job.cancel()
    }

  @Test
  fun addPerson_dedupesOnNormalizedName() =
    runTest(UnconfinedTestDispatcher()) {
      val job = launch { viewModel.uiState.collect {} }

      viewModel.addPerson("Val")
      viewModel.addPerson("val ")

      val success = viewModel.uiState.value as PeopleUiState.Success
      assertEquals(1, success.people.size)
      job.cancel()
    }

  @Test
  fun setHouseholdMember_updatesTheStoredPerson() =
    runTest(UnconfinedTestDispatcher()) {
      val job = launch { viewModel.uiState.collect {} }
      viewModel.addPerson("Robin", isHouseholdMember = false)
      val id = dao.observeAll().first().single().id

      viewModel.setHouseholdMember(id, true)

      assertEquals(true, dao.byId(id)!!.isHouseholdMember)
      job.cancel()
    }

  @Test
  fun rename_surfacesAConflictWithoutRenamingTheOtherPerson() =
    runTest(UnconfinedTestDispatcher()) {
      val job = launch { viewModel.uiState.collect {} }
      viewModel.addPerson("Robin")
      viewModel.addPerson("Dale")
      val dale = dao.observeAll().first().single { it.name == "Dale" }

      viewModel.rename(dale.id, "Robin")

      assertEquals("Someone is already named \"Robin\".", viewModel.renameError.value)
      assertEquals("Dale", dao.byId(dale.id)!!.name)
      job.cancel()
    }

  @Test
  fun rename_succeedingClearsAPriorConflict() =
    runTest(UnconfinedTestDispatcher()) {
      val job = launch { viewModel.uiState.collect {} }
      viewModel.addPerson("Robin")
      viewModel.addPerson("Dale")
      val dale = dao.observeAll().first().single { it.name == "Dale" }
      viewModel.rename(dale.id, "Robin")

      viewModel.rename(dale.id, "Dale Marsh")

      assertNull(viewModel.renameError.value)
      assertEquals("Dale Marsh", dao.byId(dale.id)!!.name)
      job.cancel()
    }
}
