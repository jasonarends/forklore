package com.jasonarends.forklore.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.jasonarends.forklore.data.db.PersonDao
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.data.db.normalizeDishName
import kotlinx.coroutines.flow.Flow

/** People whose preferences or recommendations are tracked, app users or not. */
class PersonRepository(private val personDao: PersonDao, private val clock: Clock = Clock.System) {
  fun observeAll(): Flow<List<PersonEntity>> = personDao.observeAll()

  fun observeHousehold(): Flow<List<PersonEntity>> = personDao.observeHousehold()

  /**
   * Returns the existing person if the name already matches one — including a soft-deleted one, so
   * "Val " isn't a second Val even if the first Val was removed and re-added. A tombstoned match is
   * resurrected (its `deletedAt` cleared) rather than left buried under a row the unique index
   * would otherwise refuse to create, which keeps whatever visits, wants and opinions already
   * reference that id attached instead of orphaning them under a fresh one.
   *
   * The check-then-insert isn't transactional either — same reasoning as [rename] — so two calls
   * racing on the same new name can both miss the check; the loser's insert is caught and resolved
   * to the winner's id instead of crashing.
   */
  suspend fun findOrCreate(name: String, isHouseholdMember: Boolean = false): String {
    val normalized = normalizeDishName(name)
    val now = clock.nowMillis()

    // Local, not a class member, so it can close over this call's `name`/`isHouseholdMember`
    // rather than needing them threaded through as extra parameters at both call sites below.
    // Resurrecting also refreshes `name` and `isHouseholdMember` to what was just typed: the
    // tombstoned row's old values are exactly what a caller found stale enough to type over.
    suspend fun resolveExisting(normalized: String, now: Long): String? {
      val existing = personDao.byNormalizedNameIncludingDeleted(normalized) ?: return null
      if (existing.deletedAt != null) {
        personDao.update(
          existing.copy(
            deletedAt = null,
            name = name.trim(),
            isHouseholdMember = isHouseholdMember,
            updatedAt = now,
          )
        )
      }
      return existing.id
    }

    resolveExisting(normalized, now)?.let {
      return it
    }

    val person =
      PersonEntity(
        name = name.trim(),
        normalizedName = normalized,
        isHouseholdMember = isHouseholdMember,
        createdAt = now,
        updatedAt = now,
      )
    return try {
      personDao.insert(person)
      person.id
    } catch (e: SQLiteConstraintException) {
      // Lost a race with a concurrent findOrCreate/rename for the same name; the winner might
      // itself be a tombstone if it was mid-resurrection, so this goes through the same resolver
      // rather than a bare lookup that could hand back a still-deleted id.
      resolveExisting(normalized, now) ?: throw e
    }
  }

  /**
   * Renames a person, respecting the same unique index [findOrCreate] relies on. A rename that
   * would collide with a *different* person's normalized name is refused — deleted or not, since a
   * soft-deleted row still occupies the index — rather than silently merging the two: merging would
   * mean deciding what happens to every row the other person already authored, which is a real
   * feature of its own, not a side effect of a rename. Renaming onto your own current name (e.g.
   * fixing capitalization) is always allowed. A missing or already-deleted person is [NotFound]
   * rather than a silent no-op, so a caller racing a delete finds out rather than believing it
   * worked.
   *
   * The check-then-update isn't wrapped in a single transaction, so two renames landing on the same
   * name concurrently can both pass the check; the unique index is the real backstop, and a
   * constraint violation on the update itself is caught and reported as [NameTaken] too.
   */
  suspend fun rename(id: String, name: String): RenameResult {
    val current =
      personDao.byId(id)?.takeIf { it.deletedAt == null } ?: return RenameResult.NotFound
    val normalized = normalizeDishName(name)
    val collision = personDao.byNormalizedNameIncludingDeleted(normalized)
    if (collision != null && collision.id != id) return RenameResult.NameTaken
    return try {
      personDao.update(
        current.copy(name = name.trim(), normalizedName = normalized, updatedAt = clock.nowMillis())
      )
      RenameResult.Success
    } catch (e: SQLiteConstraintException) {
      RenameResult.NameTaken
    }
  }

  suspend fun setHouseholdMember(id: String, isHouseholdMember: Boolean) {
    val current = personDao.byId(id)?.takeIf { it.deletedAt == null } ?: return
    personDao.update(
      current.copy(isHouseholdMember = isHouseholdMember, updatedAt = clock.nowMillis())
    )
  }

  sealed interface RenameResult {
    data object Success : RenameResult

    data object NameTaken : RenameResult

    data object NotFound : RenameResult
  }
}
