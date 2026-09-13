package com.jasonarends.forklore.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.PlaceListEntity
import com.jasonarends.forklore.data.db.newId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Repository-level coverage for the hand-entry path added by issue #1: a place typed in by hand
 * shows up in the list it was added to, and — because this is Room, not an in-memory list — a
 * restarted app finds it again. See AcceptanceSpecTest for the full data-layer contract; this file
 * covers the repository methods the UI actually calls.
 */
@RunWith(RobolectricTestRunner::class)
class PlaceRepositoryTest {
  private val now = 1_757_000_000_000L

  @After
  fun tearDown() {
    ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase(DB_NAME)
  }

  @Test
  fun addPlaceToList_thenObserveList_returnsTheNewEntry() = runTest {
    val db = inMemoryDatabase()
    val listId = createList(db, "Ours")
    val repository = PlaceRepository(db.placeDao(), db.placeEntryDao(), Clock { now })

    repository.addPlaceToList(
      placeListId = listId,
      name = "Halberd",
      branchLabel = "Westport",
      address = "1526 Westport Rd",
      note = "great patio",
      warning = "cash only",
    )

    val entries = repository.observeList(listId).first()

    assertEquals(1, entries.size)
    val added = entries.single()
    assertEquals("Halberd", added.place.name)
    assertEquals("Westport", added.place.branchLabel)
    assertEquals("1526 Westport Rd", added.place.address)
    assertEquals("great patio", added.place.note)
    assertEquals("cash only", added.place.warning)
    db.close()
  }

  @Test
  fun addPlace_withOnlyAName_leavesEverythingElseUnset() = runTest {
    val db = inMemoryDatabase()
    val listId = createList(db, "Ours")
    val repository = PlaceRepository(db.placeDao(), db.placeEntryDao(), Clock { now })

    repository.addPlaceToList(placeListId = listId, name = "Halberd")

    val added = repository.observeList(listId).first().single().place
    assertEquals("Halberd", added.name)
    assertNull(added.branchLabel)
    assertNull(added.address)
    assertNull(added.warning)
    assertEquals("", added.note)
    db.close()
  }

  @Test
  fun placeAddedByHand_survivesARestart() = runTest {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    context.deleteDatabase(DB_NAME)

    val firstRun = Room.databaseBuilder(context, ForkloreDatabase::class.java, DB_NAME).build()
    val listId = createList(firstRun, "Ours")
    val repository = PlaceRepository(firstRun.placeDao(), firstRun.placeEntryDao(), Clock { now })
    repository.addPlaceToList(placeListId = listId, name = "Halberd", branchLabel = "Westport")
    // Simulates the process dying and Room reopening the same on-disk file, the way it does on
    // an actual app restart — a fresh instance is what proves this isn't just in-memory state.
    firstRun.close()

    val secondRun = Room.databaseBuilder(context, ForkloreDatabase::class.java, DB_NAME).build()
    val entries = secondRun.placeEntryDao().observeForList(listId).first()

    assertEquals(1, entries.size)
    assertEquals("Halberd", entries.single().place.name)
    secondRun.close()
  }

  private fun inMemoryDatabase(): ForkloreDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        ForkloreDatabase::class.java,
      )
      .allowMainThreadQueries()
      .build()

  private suspend fun createList(db: ForkloreDatabase, name: String): String =
    newId().also {
      db
        .placeListDao()
        .insert(PlaceListEntity(id = it, name = name, createdAt = now, updatedAt = now))
    }

  private companion object {
    const val DB_NAME = "place-repository-test.db"
  }
}
