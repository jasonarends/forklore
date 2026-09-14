package com.jasonarends.forklore.ui.addplace

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.PlaceListEntity
import com.jasonarends.forklore.data.db.newId
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.PlaceRepository
import com.jasonarends.forklore.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Uses a real in-memory Room db under Robolectric rather than a hand-rolled DAO fake: a fake is
 * blind to Room's actual async/transactional behaviour, which is exactly what these tests are
 * checking (an in-flight save landing on a real background thread, a transaction that really
 * commits). See PlaceRepositoryTest for the equivalent repository-level coverage.
 */
@RunWith(RobolectricTestRunner::class)
class AddPlaceViewModelTest {
  // Standard, not Unconfined: Unconfined runs viewModelScope.launch eagerly, so `saved == false`
  // right after save() only proved true "by luck" — it happened to still be racing Room's real
  // background thread rather than being genuinely gated on the coroutine not having run yet.
  // Standard requires an explicit advanceUntilIdle() (see waitUntil) before anything queued on it
  // runs at all.
  private val testDispatcher = StandardTestDispatcher()

  @get:Rule val mainDispatcherRule = MainDispatcherRule(testDispatcher)

  private val now = 1_757_000_000_000L
  private lateinit var db: ForkloreDatabase
  private lateinit var repository: PlaceRepository
  private lateinit var listId: String

  @Before
  fun setUp() {
    db =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          ForkloreDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()
    repository = PlaceRepository(db, db.placeDao(), db.placeEntryDao(), Clock { now })
    listId = runBlocking {
      newId().also {
        db
          .placeListDao()
          .insert(PlaceListEntity(id = it, name = "Ours", createdAt = now, updatedAt = now))
      }
    }
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun save_writesNothing_whenTheNameIsBlank() {
    val viewModel = AddPlaceViewModel(repository, MutableStateFlow(listId))
    viewModel.onNameChange("   ")

    viewModel.save()

    assertFalse(viewModel.uiState.value.saved)
    assertEquals(0, entriesInList().size)
  }

  @Test
  fun save_flipsSavedOnlyAfterTheRepositoryReturns() {
    val viewModel = AddPlaceViewModel(repository, MutableStateFlow(listId))
    viewModel.onNameChange("Halberd")

    viewModel.save()
    waitUntil(viewModel) { it.saved }

    assertFalse(viewModel.uiState.value.saving)
    assertTrue(viewModel.uiState.value.saved)
    assertEquals(1, entriesInList().size)
  }

  @Test
  fun save_isIgnored_whileAnEarlierSaveIsStillInFlight() {
    val viewModel = AddPlaceViewModel(repository, MutableStateFlow(listId))
    viewModel.onNameChange("Halberd")

    viewModel.save()
    viewModel.save() // a double tap, landed before the first save's coroutine has returned

    waitUntil(viewModel) { it.saved }

    assertEquals(1, entriesInList().size)
  }

  @Test
  fun save_recoversFromAWriteFailure_insteadOfCrashing() {
    // "no-such-list" doesn't exist, so the entry insert violates the same-named foreign key
    // AcceptanceSpecTest and PlaceRepositoryTest already exercise — a real, deterministic write
    // failure with no mocking framework involved.
    val viewModel = AddPlaceViewModel(repository, MutableStateFlow("no-such-list"))
    viewModel.onNameChange("Halberd")

    viewModel.save()
    waitUntil(viewModel) { !it.saving }

    assertFalse(viewModel.uiState.value.saving)
    assertFalse(viewModel.uiState.value.saved)
    assertNotNull(viewModel.uiState.value.error)
  }

  @Test
  fun editingAField_clearsAPreviousError() {
    val viewModel = AddPlaceViewModel(repository, MutableStateFlow("no-such-list"))
    viewModel.onNameChange("Halberd")
    viewModel.save()
    waitUntil(viewModel) { it.error != null }

    viewModel.onNameChange("Halberd 2")

    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun save_doesNothing_whileTheDefaultListIsStillLoading() {
    val placeListId = MutableStateFlow<String?>(null)
    val viewModel = AddPlaceViewModel(repository, placeListId)
    viewModel.onNameChange("Halberd")

    viewModel.save()

    assertFalse(viewModel.uiState.value.saving)
    assertFalse(viewModel.uiState.value.saved)
    assertFalse(viewModel.placeListReady.value)
  }

  private fun entriesInList(): List<*> = runBlocking { repository.observeList(listId).first() }

  /**
   * The queued continuation after save()'s repository call needs `advanceUntilIdle()` to run at all
   * (see [testDispatcher]), but that call alone won't block for Room's real background thread — so
   * this still polls, advancing the scheduler each pass to pick up the continuation the moment that
   * real thread resumes it.
   */
  private fun waitUntil(
    viewModel: AddPlaceViewModel,
    timeoutMillis: Long = 5_000,
    condition: (AddPlaceUiState) -> Boolean,
  ) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (true) {
      testDispatcher.scheduler.advanceUntilIdle()
      if (condition(viewModel.uiState.value)) return
      check(System.currentTimeMillis() < deadline) { "Timed out waiting for the condition" }
      Thread.sleep(5)
    }
  }
}
