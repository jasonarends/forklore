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

  /**
   * Renames a person, respecting the same unique index [findOrCreate] relies on. A rename that
   * would collide with a *different* person's normalized name is refused rather than silently
   * merging the two — merging would mean deciding what happens to every row the other person
   * already authored, which is a real feature of its own, not a side effect of a rename. Renaming
   * onto your own current name (e.g. fixing capitalization) is always allowed.
   */
  suspend fun rename(id: String, name: String): RenameResult {
    val current = personDao.byId(id) ?: return RenameResult.Success
    val normalized = normalizeDishName(name)
    val collision = personDao.byNormalizedName(normalized)
    if (collision != null && collision.id != id) return RenameResult.NameTaken
    personDao.update(
      current.copy(name = name.trim(), normalizedName = normalized, updatedAt = clock.nowMillis())
    )
    return RenameResult.Success
  }

  suspend fun setHouseholdMember(id: String, isHouseholdMember: Boolean) {
    val current = personDao.byId(id) ?: return
    personDao.update(
      current.copy(isHouseholdMember = isHouseholdMember, updatedAt = clock.nowMillis())
    )
  }

  sealed interface RenameResult {
    data object Success : RenameResult

    data object NameTaken : RenameResult
  }
}
