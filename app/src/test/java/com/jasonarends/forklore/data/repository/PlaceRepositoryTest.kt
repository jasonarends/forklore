package com.jasonarends.forklore.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.PlaceListEntity
import com.jasonarends.forklore.data.db.PlaceStatus
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.data.db.RevisitIntent
import com.jasonarends.forklore.data.db.newId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises [PlaceRepository] itself, not the DAOs underneath it: every assertion here would catch
 * a repository method that forgot to stamp `updatedAt`, that read the DAO directly, or that skipped
 * the soft-delete filter. Data is always read back out of the database, per CLAUDE.md.
 */
@RunWith(RobolectricTestRunner::class)
class PlaceRepositoryTest {
  private lateinit var db: ForkloreDatabase
  private lateinit var repository: PlaceRepository
  private lateinit var listId: String

  private var now = 1_757_000_000_000L
  private val clock = Clock { now }

  @Before
  fun setUp() = runTest {
    db =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          ForkloreDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()
    repository = PlaceRepository(db, db.placeDao(), db.placeEntryDao(), clock)
    listId = "list"
    db
      .placeListDao()
      .insert(PlaceListEntity(id = listId, name = "Ours", createdAt = now, updatedAt = now))
  }

  @After fun tearDown() = db.close()

  @Test
  fun addPlaceToList_thenObserveList_returnsTheNewEntry() = runTest {
    repository.addPlaceToList(
      placeListId = listId,
      name = "Halberd",
      branchLabel = "Westport",
      address = "1526 Westport Rd",
      note = "great patio",
      warning = "cash only",
    )

    val added = repository.observeList(listId).first().single()

    assertEquals("Halberd", added.place.name)
    assertEquals("Westport", added.place.branchLabel)
    assertEquals("1526 Westport Rd", added.place.address)
    // The note is what this list says about the place, so it lives on the entry, not the
    // (list-independent) Place row — see CLAUDE.md rule 6.
    assertEquals("great patio", added.entry.note)
    assertEquals("cash only", added.place.warning)
  }

  @Test
  fun addPlace_withOnlyAName_leavesEverythingElseUnset() = runTest {
    repository.addPlaceToList(placeListId = listId, name = "Halberd")

    val added = repository.observeList(listId).first().single()
    assertEquals("Halberd", added.place.name)
    assertNull(added.place.branchLabel)
    assertNull(added.place.address)
    assertNull(added.place.warning)
    assertEquals("", added.entry.note)
  }

  @Test
  fun addPlaceToList_rollsBackThePlace_whenTheEntryInsertFails() = runTest {
    // No list "no-such-list" exists, so the entry insert violates the foreign key. One transaction
    // means the Place half of the write must not survive that failure either.
    assertThrows(SQLiteConstraintException::class.java) {
      runBlocking { repository.addPlaceToList(placeListId = "no-such-list", name = "Halberd") }
    }

    assertEquals(0, db.placeDao().search("Halberd").first().size)
  }

  @Test
  fun placeAddedByHand_survivesARestart() = runTest {
    // Robolectric gives every test its own fresh data dir, so there is no stale file to clear.
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    val firstRun = Room.databaseBuilder(context, ForkloreDatabase::class.java, DB_NAME).build()
    val onDiskListId = newId()
    try {
      firstRun
        .placeListDao()
        .insert(PlaceListEntity(id = onDiskListId, name = "Ours", createdAt = now, updatedAt = now))
      PlaceRepository(firstRun, firstRun.placeDao(), firstRun.placeEntryDao(), clock)
        .addPlaceToList(placeListId = onDiskListId, name = "Halberd", branchLabel = "Westport")
    } finally {
      // A fresh instance over the same on-disk file is what proves this isn't in-memory state.
      firstRun.close()
    }

    val secondRun = Room.databaseBuilder(context, ForkloreDatabase::class.java, DB_NAME).build()
    try {
      val entries = secondRun.placeEntryDao().observeForList(onDiskListId).first()

      assertEquals(1, entries.size)
      assertEquals("Halberd", entries.single().place.name)
    } finally {
      secondRun.close()
    }
  }

  @Test
  fun everyPlaceEntryField_roundTripsThroughTheRepository() = runTest {
    val placeId = repository.addPlace("Hotel Brannock", address = "123 Main St")
    val entryId = repository.addToList(listId, placeId)
    now += 60_000L

    // Divine pasta, rude servers — food and service must both survive, independently.
    repository.updateEntry(entryId) {
      it.copy(
        status = PlaceStatus.VISITED,
        foodRating = Rating.LIFE_CHANGING,
        serviceRating = Rating.BAD,
        revisitIntent = RevisitIntent.WAIT,
        note = "Ask for the sauce on the side.",
      )
    }

    val updated = repository.observeEntry(entryId).first()!!.entry

    assertEquals(PlaceStatus.VISITED, updated.status)
    assertEquals(Rating.LIFE_CHANGING, updated.foodRating)
    assertEquals(Rating.BAD, updated.serviceRating)
    assertEquals(RevisitIntent.WAIT, updated.revisitIntent)
    assertEquals("Ask for the sauce on the side.", updated.note)
    // The repository's clock stamps updatedAt; a caller-supplied timestamp is never trusted.
    assertEquals(now, updated.updatedAt)
  }

  @Test
  fun observeEntry_joinsThePlaceItPointsAt() = runTest {
    val placeId =
      repository.addPlace("Aioe all in one eatery", branchLabel = "Downtown", address = "1 Way St")
    val entryId = repository.addToList(listId, placeId)

    val entry = repository.observeEntry(entryId).first()!!

    assertEquals("Aioe all in one eatery", entry.place.name)
    assertEquals("Downtown", entry.place.branchLabel)
    assertEquals("1 Way St", entry.place.address)
  }

  @Test
  fun observeEntry_isNull_forAnEntryThatWasNeverThere() = runTest {
    assertNull(repository.observeEntry("no-such-entry").first())
  }

  @Test
  fun observeEntry_hidesAnEntryOnceItIsSoftDeleted() = runTest {
    val placeId = repository.addPlace("Halberd")
    val entryId = repository.addToList(listId, placeId)
    assertEquals(entryId, repository.observeEntry(entryId).first()!!.entry.id)

    repository.removeEntry(entryId)

    // The row survives for sync's sake, but the read model treats it as gone.
    assertNull(repository.observeEntry(entryId).first())
  }

  @Test
  fun updateEntry_doesNothing_forAnEntryThatDoesNotExist() = runTest {
    // Must not throw: a stale nav destination pointing at a deleted entry is a real scenario.
    repository.updateEntry("no-such-entry") { it.copy(status = PlaceStatus.AVOID) }
  }

  private companion object {
    const val DB_NAME = "place-repository-test.db"
  }
}
