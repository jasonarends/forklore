package com.jasonarends.forklore.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.DatePrecision
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.Meal
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceListEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs against a real in-memory Room database, not a hand-written fake — see
 * [PersonRepositoryTest]'s KDoc for why: the unique index on (visitId, personId) is exactly what
 * [VisitRepository.setAttendees]'s tombstone-resurrection has to work around, and a fake DAO has no
 * index of its own to get that wrong against.
 */
@RunWith(RobolectricTestRunner::class)
class VisitRepositoryTest {
  private lateinit var db: ForkloreDatabase
  private lateinit var repository: VisitRepository
  private lateinit var entryId: String
  private lateinit var ana: String
  private lateinit var bo: String
  private var now = 1_000L

  @Before
  fun setUp() = runTest {
    db =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          ForkloreDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()
    repository = VisitRepository(db.visitDao(), Clock { now })

    val listId = "list"
    db
      .placeListDao()
      .insert(PlaceListEntity(id = listId, name = "Ours", createdAt = 0, updatedAt = 0))
    val placeId = "place"
    db.placeDao().insert(PlaceEntity(id = placeId, name = "Halberd", createdAt = 0, updatedAt = 0))
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
    ana = "ana"
    db
      .personDao()
      .insert(
        PersonEntity(
          id = ana,
          name = "Ana",
          normalizedName = "ana",
          createdAt = 0,
          updatedAt = 0,
        )
      )
    bo = "bo"
    db
      .personDao()
      .insert(
        PersonEntity(id = bo, name = "Bo", normalizedName = "bo", createdAt = 0, updatedAt = 0)
      )
  }

  @After fun tearDown() = db.close()

  @Test
  fun record_storesTheVisitAndItsAttendees() = runTest {
    val visitId =
      repository.record(
        placeEntryId = entryId,
        dateEpochDay = 20_619,
        datePrecision = DatePrecision.DAY,
        meal = Meal.DINNER,
        note = "Great bread",
        attendees = listOf(ana, bo),
      )

    val stored = db.visitDao().byId(visitId)!!
    assertEquals(20_619L, stored.dateEpochDay)
    assertEquals(Meal.DINNER, stored.meal)
    assertEquals("Great bread", stored.note)
    val attendees = db.visitDao().observeForPlaceEntry(entryId).first().single().attendees
    assertEquals(setOf("Ana", "Bo"), attendees.map { it.name }.toSet())
  }

  @Test
  fun update_changesTheVisitButLeavesAttendeesAlone() = runTest {
    val visitId =
      repository.record(
        placeEntryId = entryId,
        dateEpochDay = 20_619,
        datePrecision = DatePrecision.DAY,
        attendees = listOf(ana),
      )

    repository.update(visitId) { it.copy(note = "Updated note") }

    val stored = db.visitDao().byId(visitId)!!
    assertEquals("Updated note", stored.note)
    val attendees = db.visitDao().observeForPlaceEntry(entryId).first().single().attendees
    assertEquals(listOf("Ana"), attendees.map { it.name })
  }

  @Test
  fun setAttendees_addsAndRemovesToMatchExactly() = runTest {
    val visitId =
      repository.record(
        placeEntryId = entryId,
        dateEpochDay = null,
        datePrecision = DatePrecision.UNKNOWN,
        attendees = listOf(ana),
      )

    repository.setAttendees(visitId, setOf(bo))

    val attendees = db.visitDao().observeForPlaceEntry(entryId).first().single().attendees
    assertEquals(listOf("Bo"), attendees.map { it.name })
  }

  @Test
  fun setAttendees_softDeletesRatherThanRemovingTheRow() = runTest {
    val visitId =
      repository.record(
        placeEntryId = entryId,
        dateEpochDay = null,
        datePrecision = DatePrecision.UNKNOWN,
        attendees = listOf(ana),
      )

    repository.setAttendees(visitId, emptySet())

    val dropped = db.visitDao().attendeeIncludingDeleted(visitId, ana)
    assertTrue(dropped != null)
    assertEquals(now, dropped!!.deletedAt)
  }

  @Test
  fun setAttendees_reAddingADroppedPerson_resurrectsTheirRowRatherThanCollidingWithTheUniqueIndex() =
    runTest {
      val visitId =
        repository.record(
          placeEntryId = entryId,
          dateEpochDay = null,
          datePrecision = DatePrecision.UNKNOWN,
          attendees = listOf(ana),
        )
      repository.setAttendees(visitId, emptySet())

      repository.setAttendees(visitId, setOf(ana))

      val attendees = db.visitDao().observeForPlaceEntry(entryId).first().single().attendees
      assertEquals(listOf("Ana"), attendees.map { it.name })
      assertNull(db.visitDao().attendeeIncludingDeleted(visitId, ana)!!.deletedAt)
    }
}
