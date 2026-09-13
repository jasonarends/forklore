package com.jasonarends.forklore.data.repository

import com.jasonarends.forklore.testing.FakePersonDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonRepositoryTest {
  private val dao = FakePersonDao()
  private var now = 1_000L
  private val repository = PersonRepository(dao, Clock { now })

  @Test
  fun findOrCreate_createsANewPersonOnFirstCall() = runTest {
    val id = repository.findOrCreate("Robin", isHouseholdMember = true)

    val stored = dao.byId(id)!!
    assertEquals("Robin", stored.name)
    assertTrue(stored.isHouseholdMember)
  }

  @Test
  fun findOrCreate_returnsTheExistingPerson_whenTheNameAlreadyMatches() = runTest {
    val first = repository.findOrCreate("Dale")

    val second = repository.findOrCreate("  dale")

    assertEquals(first, second)
    assertEquals(1, dao.observeAll().first().size)
  }

  @Test
  fun rename_updatesTheNameAndStampsUpdatedAt() = runTest {
    val id = repository.findOrCreate("Val")
    now = 2_000L

    val result = repository.rename(id, "Valerie")

    assertEquals(PersonRepository.RenameResult.Success, result)
    val stored = dao.byId(id)!!
    assertEquals("Valerie", stored.name)
    assertEquals(2_000L, stored.updatedAt)
  }

  @Test
  fun rename_refusesToCollideWithAnotherPersonsName() = runTest {
    repository.findOrCreate("Marvin")
    val dale = repository.findOrCreate("Dale")

    val result = repository.rename(dale, "Marvin")

    assertEquals(PersonRepository.RenameResult.NameTaken, result)
    assertEquals("Dale", dao.byId(dale)!!.name)
  }

  @Test
  fun rename_allowsFixingOnlyTheCapitalizationOfYourOwnName() = runTest {
    val id = repository.findOrCreate("val")

    val result = repository.rename(id, "Val")

    assertEquals(PersonRepository.RenameResult.Success, result)
    assertEquals("Val", dao.byId(id)!!.name)
  }

  @Test
  fun rename_isANoOp_whenThePersonNoLongerExists() = runTest {
    val result = repository.rename("no-such-person", "Anyone")

    assertEquals(PersonRepository.RenameResult.Success, result)
    assertNull(dao.byId("no-such-person"))
  }

  @Test
  fun setHouseholdMember_togglesTheFlagAndStampsUpdatedAt() = runTest {
    val id = repository.findOrCreate("Robin", isHouseholdMember = false)
    now = 3_000L

    repository.setHouseholdMember(id, true)

    val stored = dao.byId(id)!!
    assertTrue(stored.isHouseholdMember)
    assertEquals(3_000L, stored.updatedAt)
  }
}
