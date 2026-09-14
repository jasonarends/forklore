package com.jasonarends.forklore.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.PlaceListEntity
import com.jasonarends.forklore.data.db.newId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
    try {
      val listId = createList(db, "Ours")
      val repository = PlaceRepository(db, db.placeDao(), db.placeEntryDao(), Clock { now })

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
      // The note is what this list says about the place, so it lives on the entry, not the
      // (list-independent) Place row — see CLAUDE.md rule 6.
      assertEquals("great patio", added.entry.note)
      assertEquals("cash only", added.place.warning)
    } finally {
      db.close()
    }
  }

  @Test
  fun addPlace_withOnlyAName_leavesEverythingElseUnset() = runTest {
    val db = inMemoryDatabase()
    try {
      val listId = createList(db, "Ours")
      val repository = PlaceRepository(db, db.placeDao(), db.placeEntryDao(), Clock { now })

      repository.addPlaceToList(placeListId = listId, name = "Halberd")

      val added = repository.observeList(listId).first().single()
      assertEquals("Halberd", added.place.name)
      assertNull(added.place.branchLabel)
      assertNull(added.place.address)
      assertNull(added.place.warning)
      assertEquals("", added.entry.note)
    } finally {
      db.close()
    }
  }

  @Test
  fun addPlaceToList_rollsBackThePlace_whenTheEntryInsertFails() = runTest {
    val db = inMemoryDatabase()
    try {
      val repository = PlaceRepository(db, db.placeDao(), db.placeEntryDao(), Clock { now })

      // No list "no-such-list" exists, so the entry insert violates the foreign key. One
      // transaction means the Place half of the write must not survive that failure either.
      assertConstraintViolation {
        repository.addPlaceToList(placeListId = "no-such-list", name = "Halberd")
      }

      assertEquals(0, db.placeDao().search("Halberd").first().size)
    } finally {
      db.close()
    }
  }

  @Test
  fun placeAddedByHand_survivesARestart() = runTest {
    // Robolectric gives every test its own fresh data dir, so there is no stale file from a
    // previous run to clear first.
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    val firstRun = Room.databaseBuilder(context, ForkloreDatabase::class.java, DB_NAME).build()
    val listId: String
    try {
      listId = createList(firstRun, "Ours")
      val repository =
        PlaceRepository(firstRun, firstRun.placeDao(), firstRun.placeEntryDao(), Clock { now })
      repository.addPlaceToList(placeListId = listId, name = "Halberd", branchLabel = "Westport")
    } finally {
      // Simulates the process dying and Room reopening the same on-disk file, the way it does
      // on an actual app restart — a fresh instance is what proves this isn't just in-memory
      // state. Closed in `finally` so a failed assertion above doesn't leak an open db handle.
      firstRun.close()
    }

    val secondRun = Room.databaseBuilder(context, ForkloreDatabase::class.java, DB_NAME).build()
    try {
      val entries = secondRun.placeEntryDao().observeForList(listId).first()

      assertEquals(1, entries.size)
      assertEquals("Halberd", entries.single().place.name)
    } finally {
      secondRun.close()
    }
  }

  /**
   * Mirrors AcceptanceSpecTest's helper: runs with `runBlocking` rather than the enclosing
   * `runTest`, since a nested `runTest` fails before SQLite is ever reached.
   */
  private fun assertConstraintViolation(block: suspend () -> Unit) {
    assertThrows(SQLiteConstraintException::class.java) { runBlocking { block() } }
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
