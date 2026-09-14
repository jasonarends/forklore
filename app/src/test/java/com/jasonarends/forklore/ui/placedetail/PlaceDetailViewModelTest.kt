package com.jasonarends.forklore.ui.placedetail

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
          // Room only reuses this transaction executor for suspend DAO calls made *while already
          // inside* a database.withTransaction block (verified against the KSP-generated
          // PlaceEntryDao_Impl and androidx.room.util.DBUtil.performSuspending/getCoroutineContext
          // in the installed room-runtime-2.8.5 jar: absent an ambient TransactionElement, a call
          // falls back to RoomDatabase.getQueryContext(), which wraps the query executor, not this
          // one). PlaceRepository.updateEntry's read-modify-write runs inside withTransaction, so
          // its writes land on this same-thread executor, keeping them visible to
          // advanceUntilIdle(); observeById's Flow re-queries are independent of that transaction
          // and still run on the query executor, left at Room's real default below.
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
      // An incrementing clock turns "how many writes happened" into a single, checkable number:
      // if a write landed per keystroke rather than once after the debounce, updatedAt would have
      // ticked past 1.
      var t = 0L
      val incrementingRepository =
        PlaceRepository(db, db.placeDao(), db.placeEntryDao(), Clock { ++t })
      val viewModel = PlaceDetailViewModel(incrementingRepository, entryId, appScope)
      backgroundScope.launch { viewModel.uiState.collect {} }

      // Nobody reads a note mid-keystroke, so a write per character would be pure overhead.
      viewModel.updateNote("G")
      viewModel.updateNote("Gr")
      viewModel.updateNote("Great pasta")
      advanceUntilIdle()

      val updated = db.placeEntryDao().byId(entryId)!!
      assertEquals("Great pasta", updated.note)
      assertEquals(1L, updated.updatedAt)
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

  /**
   * Goes through the real lifecycle rather than calling a method directly: a [ViewModelProvider]
   * backed by a real [ViewModelStore], then [ViewModelStore.clear], which is what an Activity or
   * NavEntry actually does when a screen goes away. [PlaceDetailViewModel.updateNote]'s debounce
   * runs on [appScope], not `viewModelScope`, so clearing the store — which cancels
   * `viewModelScope` — must not cancel it.
   */
  @Test
  fun clearingTheViewModelStore_doesNotCancelAPendingNoteEdit() =
    runTest(testDispatcher) {
      val store = ViewModelStore()
      val factory = viewModelFactory {
        initializer { PlaceDetailViewModel(repository, entryId, appScope) }
      }
      val viewModel = ViewModelProvider(store, factory).get(PlaceDetailViewModel::class)
      backgroundScope.launch { viewModel.uiState.collect {} }

      viewModel.updateNote("Great pasta")
      store.clear()
      advanceUntilIdle()

      assertEquals("Great pasta", db.placeEntryDao().byId(entryId)!!.note)
    }
}
