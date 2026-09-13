package com.jasonarends.forklore.ui.placedetail

import com.jasonarends.forklore.data.db.PlaceDao
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryDao
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceEntryWithPlace
import com.jasonarends.forklore.data.db.PlaceStatus
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.PlaceRepository
import com.jasonarends.forklore.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Uses fakes rather than a mocking framework or a real Room database, so this exercises the
 * ViewModel's own logic — the note debounce, in particular — with no Robolectric and no real disk
 * I/O. [PlaceRepositoryTest] is where the repository itself is proven against real SQLite.
 */
class PlaceDetailViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  @get:Rule val mainDispatcherRule = MainDispatcherRule(testDispatcher)

  private val fakePlaceDao = FakePlaceDao()
  private val fakePlaceEntryDao = FakePlaceEntryDao(fakePlaceDao)
  private val repository = PlaceRepository(fakePlaceDao, fakePlaceEntryDao, Clock { 0L })

  private val place = PlaceEntity(name = "Halberd", createdAt = 0, updatedAt = 0)
  private val entryId = "entry-1"

  private fun seedEntry(status: PlaceStatus = PlaceStatus.WANT) {
    fakePlaceDao.places.value = mapOf(place.id to place)
    fakePlaceEntryDao.entries.value =
      mapOf(
        entryId to
          PlaceEntryEntity(
            id = entryId,
            placeListId = "list",
            placeId = place.id,
            status = status,
            createdAt = 0,
            updatedAt = 0,
          )
      )
  }

  @Test
  fun statusChanges_writeThroughImmediately() =
    runTest(testDispatcher) {
      seedEntry()
      val viewModel = PlaceDetailViewModel(repository, entryId)
      backgroundScope.launch { viewModel.uiState.collect {} }

      viewModel.updateStatus(PlaceStatus.VISITED)

      assertEquals(PlaceStatus.VISITED, fakePlaceEntryDao.entries.value.getValue(entryId).status)
    }

  @Test
  fun typingSeveralCharacters_writesTheNoteOnce_afterSettling() =
    runTest(testDispatcher) {
      seedEntry()
      val viewModel = PlaceDetailViewModel(repository, entryId)
      backgroundScope.launch { viewModel.uiState.collect {} }

      // Nobody reads a note mid-keystroke, so a write per character would be pure overhead.
      viewModel.updateNote("G")
      viewModel.updateNote("Gr")
      viewModel.updateNote("Great pasta")
      advanceUntilIdle()

      assertEquals("Great pasta", fakePlaceEntryDao.entries.value.getValue(entryId).note)
      assertEquals(1, fakePlaceEntryDao.updateCount)
    }

  @Test
  fun theNoteFieldShowsWhatWasTyped_beforeTheDebouncedWriteLands() =
    runTest(testDispatcher) {
      seedEntry()
      val viewModel = PlaceDetailViewModel(repository, entryId)
      backgroundScope.launch { viewModel.uiState.collect {} }

      viewModel.updateNote("Great pasta")

      // The debounced write has not run yet (advanceUntilIdle was not called), but the field
      // must already show it — a note field with no edit mode cannot wait for Room to echo back.
      assertEquals(0, fakePlaceEntryDao.updateCount)
      val state = viewModel.uiState.value as PlaceDetailUiState.Success
      assertEquals("Great pasta", state.entry.entry.note)
    }
}

private class FakePlaceDao : PlaceDao {
  val places = MutableStateFlow<Map<String, PlaceEntity>>(emptyMap())

  override suspend fun insert(place: PlaceEntity) {
    places.update { it + (place.id to place) }
  }

  override suspend fun update(place: PlaceEntity) {
    places.update { it + (place.id to place) }
  }

  override suspend fun byId(id: String): PlaceEntity? = places.value[id]

  override fun search(query: String): Flow<List<PlaceEntity>> =
    error("not exercised by PlaceDetailViewModel")

  override suspend fun byProviderId(provider: String, providerId: String): PlaceEntity? =
    error("not exercised by PlaceDetailViewModel")
}

private class FakePlaceEntryDao(private val placeDao: FakePlaceDao) : PlaceEntryDao {
  val entries = MutableStateFlow<Map<String, PlaceEntryEntity>>(emptyMap())
  var updateCount = 0
    private set

  override suspend fun insert(entry: PlaceEntryEntity) {
    entries.update { it + (entry.id to entry) }
  }

  override suspend fun update(entry: PlaceEntryEntity) {
    updateCount++
    entries.update { it + (entry.id to entry) }
  }

  override suspend fun byId(id: String): PlaceEntryEntity? = entries.value[id]

  override fun observeById(id: String): Flow<PlaceEntryWithPlace?> = entries.map { byId ->
    byId[id]
      ?.takeIf { it.deletedAt == null }
      ?.let { entry ->
        PlaceEntryWithPlace(entry, placeDao.places.value.getValue(entry.placeId))
      }
  }

  override fun observeForList(placeListId: String): Flow<List<PlaceEntryWithPlace>> =
    error("not exercised by PlaceDetailViewModel")

  override fun observeByStatus(
    placeListId: String,
    status: PlaceStatus,
  ): Flow<List<PlaceEntryWithPlace>> = error("not exercised by PlaceDetailViewModel")
}
