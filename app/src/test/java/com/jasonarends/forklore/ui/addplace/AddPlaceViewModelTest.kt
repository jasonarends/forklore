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
import org.junit.After
import org.junit.Assert.assertEquals
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
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

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
    repository = PlaceRepository(db.placeDao(), db.placeEntryDao(), db, Clock { now })
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

    assertEquals(false, viewModel.uiState.value.saved)
    assertEquals(0, entriesInList().size)
  }

  @Test
  fun save_flipsSavedOnlyAfterTheRepositoryReturns() {
    val viewModel = AddPlaceViewModel(repository, MutableStateFlow(listId))
    viewModel.onNameChange("Halberd")

    viewModel.save()

    // The DAO insert genuinely hops to Room's background query executor, so the moment save()
    // returns control here, the write has not landed yet — saved must still be false.
    assertEquals(true, viewModel.uiState.value.saving)
    assertEquals(false, viewModel.uiState.value.saved)

    waitUntilSaved(viewModel)

    assertEquals(false, viewModel.uiState.value.saving)
    assertEquals(true, viewModel.uiState.value.saved)
    assertEquals(1, entriesInList().size)
  }

  @Test
  fun save_isIgnored_whileAnEarlierSaveIsStillInFlight() {
    val viewModel = AddPlaceViewModel(repository, MutableStateFlow(listId))
    viewModel.onNameChange("Halberd")

    viewModel.save()
    viewModel.save() // a double tap, landed before the first save's coroutine has returned

    waitUntilSaved(viewModel)

    assertEquals(1, entriesInList().size)
  }

  @Test
  fun save_doesNothing_whileTheDefaultListIsStillLoading() {
    val placeListId = MutableStateFlow<String?>(null)
    val viewModel = AddPlaceViewModel(repository, placeListId)
    viewModel.onNameChange("Halberd")

    viewModel.save()

    assertEquals(false, viewModel.uiState.value.saving)
    assertEquals(false, viewModel.uiState.value.saved)
    assertEquals(false, viewModel.uiState.value.placeListReady)
  }

  private fun entriesInList(): List<*> = runBlocking { repository.observeList(listId).first() }

  private fun waitUntilSaved(viewModel: AddPlaceViewModel, timeoutMillis: Long = 5_000) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (!viewModel.uiState.value.saved) {
      check(System.currentTimeMillis() < deadline) { "Timed out waiting for save() to complete" }
      Thread.sleep(5)
    }
  }
}
