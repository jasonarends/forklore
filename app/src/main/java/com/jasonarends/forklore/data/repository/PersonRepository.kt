package com.jasonarends.forklore.data.repository

import com.jasonarends.forklore.data.db.PersonDao
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.data.db.normalizeDishName
import kotlinx.coroutines.flow.Flow

/** People whose preferences or recommendations are tracked, app users or not. */
class PersonRepository(private val personDao: PersonDao, private val clock: Clock = Clock.System) {
  fun observeAll(): Flow<List<PersonEntity>> = personDao.observeAll()

  fun observeHousehold(): Flow<List<PersonEntity>> = personDao.observeHousehold()

  /** Returns the existing person if the name already matches one, so "Val " isn't a second Val. */
  suspend fun findOrCreate(name: String, isHouseholdMember: Boolean = false): String {
    val normalized = normalizeDishName(name)
    personDao.byNormalizedName(normalized)?.let {
      return it.id
    }
    val now = clock.nowMillis()
    val person =
      PersonEntity(
        name = name.trim(),
        normalizedName = normalized,
        isHouseholdMember = isHouseholdMember,
        createdAt = now,
        updatedAt = now,
      )
    personDao.insert(person)
    return person.id
  }
}
