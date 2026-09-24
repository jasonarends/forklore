package com.jasonarends.forklore.data.repository

import com.jasonarends.forklore.data.db.DishAliasEntity
import com.jasonarends.forklore.data.db.DishDao
import com.jasonarends.forklore.data.db.DishEntity
import com.jasonarends.forklore.data.db.DishInterestDao
import com.jasonarends.forklore.data.db.DishInterestEntity
import com.jasonarends.forklore.data.db.DishOpinionDao
import com.jasonarends.forklore.data.db.DishOpinionEntity
import com.jasonarends.forklore.data.db.DishStatus
import com.jasonarends.forklore.data.db.DishWithAliases
import com.jasonarends.forklore.data.db.DishWithOpinions
import com.jasonarends.forklore.data.db.ListedDishInterest
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.data.db.normalizeDishName
import kotlinx.coroutines.flow.Flow

/**
 * Dishes, what a list wants from them, and what each person thought.
 *
 * The normalization rule lives here and nowhere else: [DishEntity.normalizedName] and
 * [DishAliasEntity.normalized] must always be [normalizeDishName] of their display text, or the
 * unique index stops catching duplicates.
 */
class DishRepository(
  private val dishDao: DishDao,
  private val interestDao: DishInterestDao,
  private val opinionDao: DishOpinionDao,
  private val clock: Clock = Clock.System,
) {
  fun observeForPlaceEntry(placeEntryId: String): Flow<List<DishWithAliases>> =
    dishDao.observeForPlaceEntry(placeEntryId)

  fun observeWithOpinions(placeEntryId: String): Flow<List<DishWithOpinions>> =
    dishDao.observeWithOpinions(placeEntryId)

  fun observeInterests(placeEntryId: String): Flow<List<DishInterestEntity>> =
    interestDao.observeForPlaceEntry(placeEntryId)

  fun observeByStatus(placeListId: String, status: DishStatus): Flow<List<DishInterestEntity>> =
    interestDao.observeByStatus(placeListId, status)

  /** Every interest on one list, across all its places, with the names to display them. */
  fun observeListedInterests(placeListId: String): Flow<List<ListedDishInterest>> =
    interestDao.observeListedForList(placeListId)

  fun observeOpinions(dishId: String): Flow<List<DishOpinionEntity>> =
    opinionDao.observeForDish(dishId)

  /**
   * Returns the existing dish if this place entry already has one under any recorded spelling,
   * otherwise creates it. Callers should always come through here rather than inserting: it is what
   * keeps "barrel tots" and "potatoe barrels" one row. Goes through [DishDao.findOrInsert] (a
   * single `@Transaction`) rather than a separate find then insert, so two concurrent calls for the
   * same name can't both see no match and both insert.
   */
  suspend fun findOrCreateDish(placeEntryId: String, name: String): String {
    val now = clock.nowMillis()
    val dish =
      dishDao.findOrInsert(
        DishEntity(
          placeEntryId = placeEntryId,
          canonicalName = name.trim(),
          normalizedName = normalizeDishName(name),
          createdAt = now,
          updatedAt = now,
        )
      )
    return dish.id
  }

  /** No-op when the spelling already resolves to this dish. See [findOrCreateDish] on the race. */
  suspend fun addAlias(dishId: String, placeEntryId: String, alias: String) {
    val now = clock.nowMillis()
    dishDao.findOrInsertAlias(
      placeEntryId = placeEntryId,
      alias =
        DishAliasEntity(
          dishId = dishId,
          alias = alias.trim(),
          normalized = normalizeDishName(alias),
          createdAt = now,
          updatedAt = now,
        ),
    )
  }

  /**
   * Records one stance on [dishId]. A dish can carry several — "Robin wants chicken" and "never
   * again for anyone else" are two rows, not one overwritten — so this always inserts; use
   * [updateInterest] to change one that exists. [modification] is trimmed and stored as null when
   * blank: an empty string would read as "has instructions" to anything checking for null. [note]
   * is stored verbatim (CLAUDE.md rule 4).
   */
  suspend fun setInterest(
    dishId: String,
    status: DishStatus,
    forPersonId: String? = null,
    recommendedById: String? = null,
    modification: String? = null,
    note: String = "",
  ): String {
    val now = clock.nowMillis()
    val interest =
      DishInterestEntity(
        dishId = dishId,
        status = status,
        forPersonId = forPersonId,
        recommendedById = recommendedById,
        modification = modification.blankToNull(),
        note = note,
        createdAt = now,
        updatedAt = now,
      )
    interestDao.insert(interest)
    return interest.id
  }

  /**
   * Replaces every editable field of an existing interest: an editor saves the whole form, so
   * passing null for [forPersonId] means "no longer for one person", not "leave it". A missing or
   * already-removed interest is a no-op rather than being resurrected by a stale editor.
   */
  suspend fun updateInterest(
    interestId: String,
    status: DishStatus,
    forPersonId: String?,
    recommendedById: String?,
    modification: String?,
    note: String,
  ) {
    val current = interestDao.byId(interestId)?.takeIf { it.deletedAt == null } ?: return
    interestDao.update(
      current.copy(
        status = status,
        forPersonId = forPersonId,
        recommendedById = recommendedById,
        modification = modification.blankToNull(),
        note = note,
        updatedAt = clock.nowMillis(),
      )
    )
  }

  /** Soft delete (CLAUDE.md rule 7). Removing an already-removed interest keeps its first stamp. */
  suspend fun removeInterest(interestId: String) {
    val current = interestDao.byId(interestId)?.takeIf { it.deletedAt == null } ?: return
    val now = clock.nowMillis()
    interestDao.update(current.copy(deletedAt = now, updatedAt = now))
  }

  suspend fun recordOpinion(
    dishId: String,
    authorId: String,
    rating: Rating?,
    note: String = "",
    visitId: String? = null,
  ): String {
    val now = clock.nowMillis()
    val opinion =
      DishOpinionEntity(
        dishId = dishId,
        authorId = authorId,
        visitId = visitId,
        rating = rating,
        note = note,
        createdAt = now,
        updatedAt = now,
      )
    opinionDao.insert(opinion)
    return opinion.id
  }
}

private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
