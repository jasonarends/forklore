package com.jasonarends.forklore.ui.people

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.PersonRepository
import com.jasonarends.forklore.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs against a real in-memory Room database — see `PersonRepositoryTest`'s KDoc for why a fake
 * DAO isn't enough — via the same [MainDispatcherRule] every ViewModel test needs so
 * `viewModelScope` works at all. Room's own `Flow` emissions happen on Room's query executor, a
 * real thread outside any test dispatcher's control, so assertions read `uiState` with a suspending
 * `first { ... }` rather than a synchronous `.value`, which would just observe whatever was there
 * before Room caught up — and for the same reason, tests that need two writes to have landed in a
 * specific order await the first via `uiState.first { ... }` before issuing the second, rather than
 * firing both and hoping. `PersonRepositoryTest` covers the actual insert-collision race
 * deterministically; a timing-dependent version of it here would be flaky by construction.
 */
@RunWith(RobolectricTestRunner::class)
class PeopleViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private lateinit var db: ForkloreDatabase
  private lateinit var repository: PersonRepository
  private lateinit var viewModel: PeopleViewModel
  private var now = 1_000L

  @Before
  fun setUp() {
    db =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          ForkloreDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()
    repository = PersonRepository(db.personDao(), Clock { now })
    // Built here, not in a field initializer: stateIn launches its sharing coroutine on
    // viewModelScope (Dispatchers.Main) as soon as the ViewModel exists, so it must not run before
    // MainDispatcherRule has replaced Dispatchers.Main, which happens after field initializers.
    viewModel = PeopleViewModel(repository)
  }

  @After fun tearDown() = db.close()

  @Test
  fun uiState_reflectsPeopleAddedThroughTheViewModel() = runTest {
    viewModel.addPerson("Robin", isHouseholdMember = true)

    val success =
      viewModel.uiState.first { it is PeopleUiState.Success && it.people.isNotEmpty() }
        as PeopleUiState.Success
    assertEquals(listOf("Robin"), success.people.map { it.name })
    assertEquals(true, success.people.single().isHouseholdMember)
  }

  @Test
  fun addPerson_dedupesOnNormalizedName() = runTest {
    viewModel.addPerson("Val")
    // Awaited before the second add: two unawaited addPerson calls race each other's suspending
    // Room writes, which is a real bug (see PersonRepositoryTest's insert-collision tests) but not
    // one this test — which is only about the dedupe check, not the race — should depend on timing
    // to catch.
    viewModel.uiState.first { it is PeopleUiState.Success && it.people.isNotEmpty() }

    viewModel.addPerson("val ")

    val success =
      viewModel.uiState.first { it is PeopleUiState.Success && it.people.isNotEmpty() }
        as PeopleUiState.Success
    assertEquals(1, success.people.size)
  }

  @Test
  fun setHouseholdMember_updatesTheStoredPerson() = runTest {
    viewModel.addPerson("Robin", isHouseholdMember = false)
    val id = db.personDao().observeAll().first { it.isNotEmpty() }.single().id

    viewModel.setHouseholdMember(id, true)

    val success =
      viewModel.uiState.first {
        it is PeopleUiState.Success && it.people.any { p -> p.id == id && p.isHouseholdMember }
      } as PeopleUiState.Success
    assertEquals(true, success.people.single { it.id == id }.isHouseholdMember)
  }

  @Test
  fun rename_surfacesAConflictTiedToTheAffectedPerson() = runTest {
    viewModel.addPerson("Robin")
    viewModel.uiState.first { it is PeopleUiState.Success && it.people.isNotEmpty() }
    viewModel.addPerson("Dale")
    val dale = db.personDao().observeAll().first { it.size >= 2 }.single { it.name == "Dale" }
    viewModel.startRename(dale.id)

    viewModel.rename(dale.id, "Robin")

    val success =
      viewModel.uiState.first { it is PeopleUiState.Success && it.editing?.error != null }
        as PeopleUiState.Success
    val editing = success.editing!!
    assertEquals(dale.id, editing.personId)
    assertEquals("Someone is already named \"Robin\".", editing.error)
    assertEquals("Dale", db.personDao().byId(dale.id)!!.name)
  }

  @Test
  fun rename_succeedingClearsAPriorConflictAndClosesTheRow() = runTest {
    viewModel.addPerson("Robin")
    viewModel.uiState.first { it is PeopleUiState.Success && it.people.isNotEmpty() }
    viewModel.addPerson("Dale")
    val dale = db.personDao().observeAll().first { it.size >= 2 }.single { it.name == "Dale" }
    viewModel.startRename(dale.id)
    viewModel.rename(dale.id, "Robin")
    viewModel.uiState.first { it is PeopleUiState.Success && it.editing?.error != null }

    viewModel.rename(dale.id, "Dale Marsh")

    // The success condition lives inside the predicate, not asserted afterwards: a synchronous
    // `.value` read after this point could still observe the stale, error-carrying state if Room
    // hasn't caught up yet, which is exactly the class of bug this whole file exists to avoid.
    val success =
      viewModel.uiState.first {
        it is PeopleUiState.Success &&
          it.editing == null &&
          it.people.any { p -> p.id == dale.id && p.name == "Dale Marsh" }
      } as PeopleUiState.Success
    assertNull(success.editing)
  }

  @Test
  fun rename_withTheNameUnchanged_stillClosesTheRow() = runTest {
    viewModel.addPerson("Dale")
    val dale = db.personDao().observeAll().first { it.isNotEmpty() }.single()
    viewModel.startRename(dale.id)
    // Confirms the row actually opened before relying on it having closed: `editing` starts out
    // null, so waiting on `editing == null` after the rename would trivially pass even if nothing
    // about renaming ever changed it.
    viewModel.uiState.first { it is PeopleUiState.Success && it.editing?.personId == dale.id }

    viewModel.rename(dale.id, "Dale")

    val success =
      viewModel.uiState.first { it is PeopleUiState.Success && it.editing == null }
        as PeopleUiState.Success
    assertNull(success.editing)
  }

  @Test
  fun rename_withOnlyTrailingWhitespaceAdded_stillClosesTheRow() = runTest {
    viewModel.addPerson("Dale")
    val dale = db.personDao().observeAll().first { it.isNotEmpty() }.single()
    viewModel.startRename(dale.id)
    viewModel.uiState.first { it is PeopleUiState.Success && it.editing?.personId == dale.id }

    viewModel.rename(dale.id, "Dale   ")

    val success =
      viewModel.uiState.first { it is PeopleUiState.Success && it.editing == null }
        as PeopleUiState.Success
    assertNull(success.editing)
  }
}
