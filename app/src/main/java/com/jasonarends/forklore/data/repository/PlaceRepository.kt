package com.jasonarends.forklore.data.repository

import androidx.room.withTransaction
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.PlaceDao
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryDao
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceEntryWithPlace
import com.jasonarends.forklore.data.db.PlaceStatus
import com.jasonarends.forklore.data.db.escapeLike
import kotlinx.coroutines.flow.Flow

/**
 * Places and their membership in lists.
 *
 * Repositories, not DAOs, are what ViewModels depend on, and they exist to own the three things a
 * raw DAO call always gets wrong: stamping [PlaceEntity.updatedAt] on every write, soft-deleting
 * rather than hard-deleting, and escaping user input before it reaches LIKE.
 */
class PlaceRepository(
  private val database: ForkloreDatabase,
  private val placeDao: PlaceDao,
  private val placeEntryDao: PlaceEntryDao,
  private val clock: Clock = Clock.System,
) {
  fun observeList(placeListId: String): Flow<List<PlaceEntryWithPlace>> =
    placeEntryDao.observeForList(placeListId)

  fun observeEntry(entryId: String): Flow<PlaceEntryWithPlace?> = placeEntryDao.observeById(entryId)

  fun observeByStatus(placeListId: String, status: PlaceStatus): Flow<List<PlaceEntryWithPlace>> =
    placeEntryDao.observeByStatus(placeListId, status)

  /** [query] is raw user input; LIKE metacharacters in it are matched literally. */
  fun search(query: String): Flow<List<PlaceEntity>> = placeDao.search(escapeLike(query))

  /**
   * [note] is deliberately not a parameter here: a [PlaceEntity] is global across every list that
   * includes it (see CLAUDE.md rule 6), so free text a person writes when adding a place belongs on
   * their list's [PlaceEntryEntity], via [addToList], not here. [warning] stays on the place — it
   * documents the restaurant itself (a pricing or policy gotcha), not one list's opinion of it.
   */
  suspend fun addPlace(
    name: String,
    branchLabel: String? = null,
    address: String? = null,
    warning: String? = null,
  ): String {
    val now = clock.nowMillis()
    val place =
      PlaceEntity(
        name = name,
        branchLabel = branchLabel,
        address = address,
        warning = warning,
        createdAt = now,
        updatedAt = now,
      )
    placeDao.insert(place)
    return place.id
  }

  suspend fun addToList(
    placeListId: String,
    placeId: String,
    status: PlaceStatus = PlaceStatus.WANT,
    note: String = "",
  ): String {
    val now = clock.nowMillis()
    val entry =
      PlaceEntryEntity(
        placeListId = placeListId,
        placeId = placeId,
        status = status,
        note = note,
        createdAt = now,
        updatedAt = now,
      )
    placeEntryDao.insert(entry)
    return entry.id
  }

  /**
   * The hand-entry path: a place that doesn't exist anywhere yet, created and added to
   * [placeListId] in one call. Place lookup/dedupe against an existing row is M2 (provider search);
   * until then every manual entry is its own [PlaceEntity]. One transaction: an entry insert that
   * fails (e.g. a bad [placeListId]) must not leave an orphan [PlaceEntity] with nothing pointing
   * at it.
   */
  suspend fun addPlaceToList(
    placeListId: String,
    name: String,
    branchLabel: String? = null,
    address: String? = null,
    note: String = "",
    warning: String? = null,
  ): String = database.withTransaction {
    val placeId = addPlace(name, branchLabel, address, warning)
    addToList(placeListId, placeId, note = note)
  }

  /**
   * Read-modify-write, so it runs inside a transaction: two concurrent calls (a status tap and a
   * note save, say) reading the same pre-write row would otherwise silently revert each other.
   */
  suspend fun updateEntry(entryId: String, change: (PlaceEntryEntity) -> PlaceEntryEntity) {
    database.withTransaction {
      val current = placeEntryDao.byId(entryId) ?: return@withTransaction
      placeEntryDao.update(change(current).copy(updatedAt = clock.nowMillis()))
    }
  }

  /**
   * Soft delete: the row survives so M3 sync can propagate the tombstone. Children are hidden by
   * their own reads once the parent entry is gone from list queries; a child that must be
   * independently revivable gets its own tombstone at that point.
   */
  suspend fun removeEntry(entryId: String) {
    database.withTransaction {
      val now = clock.nowMillis()
      val current = placeEntryDao.byId(entryId) ?: return@withTransaction
      placeEntryDao.update(current.copy(deletedAt = now, updatedAt = now))
    }
  }
}
