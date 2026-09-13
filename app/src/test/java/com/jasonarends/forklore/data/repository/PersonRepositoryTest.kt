package com.jasonarends.forklore.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.ForkloreDatabase
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
 * Runs against a real in-memory Room database, not a hand-written fake: a fake DAO has no unique
 * index of its own to enforce, so it can't catch [PersonRepository] getting the tombstone-aware
 * dedupe wrong the way the real `people` table's unique index on `normalizedName` does.
 */
@RunWith(RobolectricTestRunner::class)
class PersonRepositoryTest {
  private lateinit var db: ForkloreDatabase
  private lateinit var repository: PersonRepository
  private var now = 1_000L

  @Before
  fun setUp() {
    db =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          ForkloreDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()
    repository = PersonRepository(db.personDao(), Clock { now })
  }

  @After fun tearDown() = db.close()

  @Test
  fun findOrCreate_createsANewPersonOnFirstCall() = runTest {
    val id = repository.findOrCreate("Robin", isHouseholdMember = true)

    val stored = db.personDao().byId(id)!!
    assertEquals("Robin", stored.name)
    assertTrue(stored.isHouseholdMember)
  }

  @Test
  fun findOrCreate_returnsTheExistingPerson_whenTheNameAlreadyMatches() = runTest {
    val first = repository.findOrCreate("Dale")

    val second = repository.findOrCreate("  dale")

    assertEquals(first, second)
    assertEquals(1, db.personDao().observeAll().first().size)
  }

  @Test
  fun findOrCreate_resurrectsASoftDeletedPersonWithTheSameName() = runTest {
    val id = repository.findOrCreate("Val")
    db.personDao().update(db.personDao().byId(id)!!.copy(deletedAt = 5_000L))
    now = 6_000L

    val resurrectedId = repository.findOrCreate("val ")

    assertEquals(id, resurrectedId)
    val stored = db.personDao().byId(id)!!
    assertNull(stored.deletedAt)
    assertEquals(6_000L, stored.updatedAt)
    assertEquals(1, db.personDao().observeAll().first().size)
  }

  @Test
  fun rename_updatesTheNameAndStampsUpdatedAt() = runTest {
    val id = repository.findOrCreate("Val")
    now = 2_000L

    val result = repository.rename(id, "Valerie")

    assertEquals(PersonRepository.RenameResult.Success, result)
    val stored = db.personDao().byId(id)!!
    assertEquals("Valerie", stored.name)
    assertEquals(2_000L, stored.updatedAt)
  }

  @Test
  fun rename_refusesToCollideWithAnotherPersonsName() = runTest {
    repository.findOrCreate("Marvin")
    val dale = repository.findOrCreate("Dale")

    val result = repository.rename(dale, "Marvin")

    assertEquals(PersonRepository.RenameResult.NameTaken, result)
    assertEquals("Dale", db.personDao().byId(dale)!!.name)
  }

  @Test
  fun rename_refusesToCollideWithASoftDeletedPersonsName() = runTest {
    val marvin = repository.findOrCreate("Marvin")
    db.personDao().update(db.personDao().byId(marvin)!!.copy(deletedAt = 5_000L))
    val dale = repository.findOrCreate("Dale")

    val result = repository.rename(dale, "Marvin")

    assertEquals(PersonRepository.RenameResult.NameTaken, result)
    assertEquals("Dale", db.personDao().byId(dale)!!.name)
  }

  @Test
  fun rename_allowsFixingOnlyTheCapitalizationOfYourOwnName() = runTest {
    val id = repository.findOrCreate("val")

    val result = repository.rename(id, "Val")

    assertEquals(PersonRepository.RenameResult.Success, result)
    assertEquals("Val", db.personDao().byId(id)!!.name)
  }

  @Test
  fun rename_isNotFound_whenThePersonNeverExisted() = runTest {
    val result = repository.rename("no-such-person", "Anyone")

    assertEquals(PersonRepository.RenameResult.NotFound, result)
  }

  @Test
  fun rename_isNotFound_forASoftDeletedPerson() = runTest {
    val id = repository.findOrCreate("Val")
    db.personDao().update(db.personDao().byId(id)!!.copy(deletedAt = 5_000L))

    val result = repository.rename(id, "Valerie")

    assertEquals(PersonRepository.RenameResult.NotFound, result)
  }

  @Test
  fun setHouseholdMember_togglesTheFlagAndStampsUpdatedAt() = runTest {
    val id = repository.findOrCreate("Robin", isHouseholdMember = false)
    now = 3_000L

    repository.setHouseholdMember(id, true)

    val stored = db.personDao().byId(id)!!
    assertTrue(stored.isHouseholdMember)
    assertEquals(3_000L, stored.updatedAt)
  }

  @Test
  fun setHouseholdMember_isANoOp_forASoftDeletedPerson() = runTest {
    val id = repository.findOrCreate("Robin", isHouseholdMember = false)
    db.personDao().update(db.personDao().byId(id)!!.copy(deletedAt = 5_000L))

    repository.setHouseholdMember(id, true)

    assertEquals(false, db.personDao().byId(id)!!.isHouseholdMember)
  }
}
