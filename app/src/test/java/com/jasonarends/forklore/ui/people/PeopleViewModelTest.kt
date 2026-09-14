package com.jasonarends.forklore.ui.people

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.PersonDao
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.PersonRepository
import com.jasonarends.forklore.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 * `viewModelScope` works at all.
 *
 * [db] is built with a synchronous query/transaction executor (`Executor { it.run() }`), not Room's
 * default background thread pool. Most tests here still read `uiState` with a suspending `first {
 * ... }`, which is simply a safe, ordering-independent way to wait for a `StateFlow` to reach a
 * condition and works the same whether the write behind it is synchronous or not. The
 * `rename_landingLate*` tests are different: they need to inspect state at one exact moment —
 * immediately after a deliberately delayed write is released — and a `first { ... }` re-subscribe
 * at that moment can't tell "the write already landed" apart from "the write hasn't happened yet
 * but the predicate matched anyway", which is a real race if the write runs on a different thread.
 * The synchronous executor removes that thread entirely: combined with [MainDispatcherRule]'s
 * `UnconfinedTestDispatcher`, releasing the gate in those tests drives the delayed write to
 * completion inline, on the calling thread, before the next line of the test runs — so those tests
 * keep one live collector on `uiState` and assert on `.value` directly, no `first { ... }`
 * involved.
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
        .setQueryExecutor { it.run() }
        .setTransactionExecutor { it.run() }
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

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun rename_landingLateAsSuccess_doesNotCloseADifferentRowOpenedMeanwhile() =
    runTest(UnconfinedTestDispatcher()) {
      val (ana, _, bo) = threePeople()
      val gate = CompletableDeferred<Unit>()
      val gated = gatedViewModel(gate)
      val collector = launch { gated.uiState.collect {} }

      gated.startRename(ana.id)
      gated.rename(ana.id, "Ana Marsh") // suspends before the collision check, waiting on the gate
      gated.startRename(bo.id)
      // Resolves the gate synchronously, on this thread: the query executor set in `setUp` runs
      // inline rather than hopping to a background pool, so Ana's rename — collision check, write,
      // and the guarded `_editing` update — all complete before `complete` returns.
      gate.complete(Unit)

      val success = gated.uiState.value as PeopleUiState.Success
      assertEquals(bo.id, success.editing?.personId)
      assertNull(success.editing?.error)
      assertEquals("Ana Marsh", db.personDao().byId(ana.id)!!.name)
      collector.cancel()
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun rename_landingLateAsNameTaken_doesNotStampADifferentOpenRowsError() =
    runTest(UnconfinedTestDispatcher()) {
      val (ana, marvin, bo) = threePeople()
      val gate = CompletableDeferred<Unit>()
      val gated = gatedViewModel(gate)
      val collector = launch { gated.uiState.collect {} }

      gated.startRename(ana.id)
      gated.rename(ana.id, marvin.name) // will resolve to NameTaken once the gate opens
      gated.startRename(bo.id)
      gate.complete(Unit)

      // Bo's row must still be open with no error: Ana's collision is not Bo's to show.
      val success = gated.uiState.value as PeopleUiState.Success
      assertEquals(bo.id, success.editing?.personId)
      assertNull(success.editing?.error)
      collector.cancel()
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun rename_landingLateAfterBeingCancelledAsSuccess_doesNotReopenTheRow() =
    runTest(UnconfinedTestDispatcher()) {
      val (ana, _, _) = threePeople()
      val gate = CompletableDeferred<Unit>()
      val gated = gatedViewModel(gate)
      val collector = launch { gated.uiState.collect {} }

      gated.startRename(ana.id)
      gated.rename(ana.id, "Ana Marsh")
      gated.cancelRename()
      gate.complete(Unit)

      // Can't distinguish the guard from the pre-fix code by itself: the old code also set
      // `_editing.value = null` unconditionally on Success, which is what an already-null
      // `_editing` needs anyway. Kept alongside the NameTaken variant below — the one that
      // actually exercises the old `?: RenameEdit(id)` fallback — as a direct assertion of the
      // guard's Success-branch behaviour, so a future change that makes Success reopen the row
      // some other way still has a test in its way.
      val success = gated.uiState.value as PeopleUiState.Success
      assertNull(success.editing)
      assertEquals("Ana Marsh", db.personDao().byId(ana.id)!!.name)
      collector.cancel()
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun rename_landingLateAfterBeingCancelledAsNameTaken_doesNotReopenTheRow() =
    runTest(UnconfinedTestDispatcher()) {
      val (ana, marvin, _) = threePeople()
      val gate = CompletableDeferred<Unit>()
      val gated = gatedViewModel(gate)
      val collector = launch { gated.uiState.collect {} }

      gated.startRename(ana.id)
      gated.rename(ana.id, marvin.name) // will resolve to NameTaken once the gate opens
      gated.cancelRename()
      gate.complete(Unit)

      // This is the variant that actually distinguishes the fix: the old
      // `(current ?: RenameEdit(id)).copy(error = message)` fallback only fired on NameTaken, and
      // would have reopened Ana's row here with her collision error even though she'd cancelled.
      val success = gated.uiState.value as PeopleUiState.Success
      assertNull(success.editing)
      collector.cancel()
    }

  private data class ThreePeople(
    val ana: PersonEntity,
    val marvin: PersonEntity,
    val bo: PersonEntity,
  )

  private suspend fun threePeople(): ThreePeople {
    viewModel.addPerson("Ana")
    viewModel.addPerson("Marvin")
    viewModel.addPerson("Bo")
    val people = db.personDao().observeAll().first { it.size >= 3 }
    return ThreePeople(
      ana = people.single { it.name == "Ana" },
      marvin = people.single { it.name == "Marvin" },
      bo = people.single { it.name == "Bo" },
    )
  }

  /**
   * A [PeopleViewModel] whose repository delays exactly at
   * [PersonDao.byNormalizedNameIncludingDeleted] — reached before the collision check in
   * `PersonRepository.rename` and hit regardless of whether that check ends in success or a
   * collision (`personDao.byId(id)` suspends first, but always resolves immediately against the
   * synchronous executor) — until [gate] completes. This is what lets a test start a second rename
   * on a different person while the first is still in flight, deterministically rather than by
   * timing.
   */
  private fun gatedViewModel(gate: CompletableDeferred<Unit>): PeopleViewModel {
    val dao =
      object : PersonDao by db.personDao() {
        override suspend fun byNormalizedNameIncludingDeleted(
          normalizedName: String
        ): PersonEntity? {
          gate.await()
          return db.personDao().byNormalizedNameIncludingDeleted(normalizedName)
        }
      }
    return PeopleViewModel(PersonRepository(dao, Clock { now }))
  }
}
