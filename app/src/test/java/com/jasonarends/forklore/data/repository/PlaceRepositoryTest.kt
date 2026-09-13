package com.jasonarends.forklore.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.PlaceListEntity
import com.jasonarends.forklore.data.db.PlaceStatus
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.data.db.RevisitIntent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
