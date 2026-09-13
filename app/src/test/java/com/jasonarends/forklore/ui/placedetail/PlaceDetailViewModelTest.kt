package com.jasonarends.forklore.ui.placedetail

import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceListEntity
import com.jasonarends.forklore.data.db.PlaceStatus
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.PlaceRepository
import com.jasonarends.forklore.testing.MainDispatcherRule
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A real in-memory Room database, per
 * [com.jasonarends.forklore.data.repository.PlaceRepositoryTest] — not hand-rolled DAO fakes. A
 * fake DAO backed by an in-memory `MutableStateFlow` duplicates Room's query behaviour and drifts
 * from it silently as the real DAOs grow; a real database exercises the same code the app ships.
 */
@RunWith(RobolectricTestRunner::class)
class PlaceDetailViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  @get:Rule val mainDispatcherRule = MainDispatcherRule(testDispatcher)

  private lateinit var db: ForkloreDatabase
  private lateinit var repository: PlaceRepository
  private lateinit var appScope: CoroutineScope
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
          // Room's default transaction executor is a real background thread, invisible to
          // advanceUntilIdle(): a fire-and-forget viewModelScope.launch whose write hops onto one
          // would resume on real wall-clock time, after the test's assertions have already run. A
          // same-thread executor keeps the write inside the coroutine the test controls.
          .setTransactionExecutor(Executor { it.run() })
          .build()
      repository = PlaceRepository(db, db.placeDao(), db.placeEntryDao(), Clock { 0L })
      appScope = CoroutineScope(testDispatcher)

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

  private fun viewModel() = PlaceDetailViewModel(repository, entryId, appScope)

  @Test
  fun statusChanges_writeThroughImmediately() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      backgroundScope.launch { viewModel.uiState.collect {} }

      viewModel.updateStatus(PlaceStatus.VISITED)
      advanceUntilIdle()

      assertEquals(PlaceStatus.VISITED, db.placeEntryDao().byId(entryId)!!.status)
    }

  @Test
  fun typingSeveralCharacters_writesTheNoteOnce_afterSettling() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      backgroundScope.launch { viewModel.uiState.collect {} }

      // Nobody reads a note mid-keystroke, so a write per character would be pure overhead.
      viewModel.updateNote("G")
      viewModel.updateNote("Gr")
      viewModel.updateNote("Great pasta")
      advanceUntilIdle()

      assertEquals("Great pasta", db.placeEntryDao().byId(entryId)!!.note)
    }

  @Test
  fun theNoteFieldShowsWhatWasTyped_beforeTheDebouncedWriteLands() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      backgroundScope.launch { viewModel.uiState.collect {} }

      viewModel.updateNote("Great pasta")

      // The debounced write has not run yet, but the field must already show it — a note field
      // with no edit mode cannot wait for Room to echo back.
      assertEquals("", db.placeEntryDao().byId(entryId)!!.note)
      val state = viewModel.uiState.value as PlaceDetailUiState.Success
      assertEquals("Great pasta", state.entry.entry.note)
    }

  /**
   * Regression coverage for the note being handed back to whatever Room reported right after the
   * write landed, which could race Room's real, asynchronous re-emission of the row just written
   * and flash the field back to its pre-edit value. The fix removes the hand-back entirely
   * ([PlaceDetailViewModel.pendingNote] is never reset), which this asserts the outcome of; the
   * race itself is real-thread timing and was not reproducible here without risking a flaky or
   * deadlocking test (tried: real Room's default executors broke unrelated assertions because a
   * fire-and-forget `viewModelScope.launch` isn't tracked by `advanceUntilIdle()`; a manual
   * queueing executor deadlocked Room's own invalidation-tracker startup).
   */
  @Test
  fun theNoteStaysWhatWasTyped_afterTheDebouncedWriteLands() =
    runTest(testDispatcher) {
      val viewModel = viewModel()
      backgroundScope.launch { viewModel.uiState.collect {} }

      viewModel.updateNote("Great pasta")
      advanceUntilIdle()

      val state = viewModel.uiState.value as PlaceDetailUiState.Success
      assertEquals("Great pasta", state.entry.entry.note)
    }

  @Test
  fun clearingTheViewModel_flushesAPendingNoteEdit() =
    runTest(testDispatcher) {
      // The real lifecycle cancels viewModelScope immediately after onCleared() returns, which
      // would cancel the debounce mid-flight; onCleared must flush the last edit through appScope
      // instead. Reproduced here rather than just called: onCleared() alone proves nothing unless
      // viewModelScope is also cancelled right after, same as the real ViewModelStore does.
      val viewModel = viewModel()
      backgroundScope.launch { viewModel.uiState.collect {} }

      viewModel.updateNote("Great pasta")
      viewModel.onCleared()
      viewModel.viewModelScope.cancel()
      advanceUntilIdle()

      assertEquals("Great pasta", db.placeEntryDao().byId(entryId)!!.note)
    }
}
