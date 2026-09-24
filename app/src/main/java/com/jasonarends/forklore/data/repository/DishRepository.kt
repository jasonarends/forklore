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
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.data.db.TemperatureRating
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

  fun observeOpinions(dishId: String): Flow<List<DishOpinionEntity>> =
    opinionDao.observeForDish(dishId)

  /** Every live opinion on every dish at [placeEntryId], oldest first. */
  fun observeOpinionsForPlaceEntry(placeEntryId: String): Flow<List<DishOpinionEntity>> =
    opinionDao.observeForPlaceEntry(placeEntryId)

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
        modification = modification,
        note = note,
        createdAt = now,
        updatedAt = now,
      )
    interestDao.insert(interest)
    return interest.id
  }

  /**
   * Adds one opinion row. Never looks for an existing opinion by [authorId]: the schema
   * deliberately allows one author several opinions of a dish (a second visit, a changed mind), and
   * two authors' rows are never merged (CLAUDE.md rule 5). [rating], [temperature] and [note] are
   * all optional; nothing here requires any of them. [visitId], if given, must be one of this
   * dish's own place entry's visits.
   */
  suspend fun recordOpinion(
    dishId: String,
    authorId: String,
    rating: Rating?,
    note: String = "",
    visitId: String? = null,
    temperature: TemperatureRating? = null,
  ): String {
    requireVisitAtDishsEntry(dishId, visitId)
    val now = clock.nowMillis()
    val opinion =
      DishOpinionEntity(
        dishId = dishId,
        authorId = authorId,
        visitId = visitId,
        rating = rating,
        temperature = temperature,
        note = note,
        createdAt = now,
        updatedAt = now,
      )
    opinionDao.insert(opinion)
    return opinion.id
  }

  /**
   * Rewrites one opinion in place. The dish is fixed for the life of an opinion; everything else
   * the editor shows may change, including [authorId] (fixing "I tapped the wrong person"). A
   * missing or already-deleted opinion is a no-op rather than an error, so an edit racing a delete
   * doesn't crash the screen.
   */
  suspend fun updateOpinion(
    opinionId: String,
    authorId: String,
    rating: Rating?,
    temperature: TemperatureRating?,
    note: String,
    visitId: String?,
  ) {
    val current = opinionDao.byId(opinionId)?.takeIf { it.deletedAt == null } ?: return
    // Re-validating an unchanged link would reject an opinion whose visit was deleted since it was
    // written, blocking any other edit to it.
    if (visitId != current.visitId) requireVisitAtDishsEntry(current.dishId, visitId)
    opinionDao.update(
      current.copy(
        authorId = authorId,
        visitId = visitId,
        rating = rating,
        temperature = temperature,
        note = note,
        updatedAt = clock.nowMillis(),
      )
    )
  }

  /** Soft delete: the tombstone is what sync propagates (CLAUDE.md rule 7). */
  suspend fun deleteOpinion(opinionId: String) {
    val current = opinionDao.byId(opinionId)?.takeIf { it.deletedAt == null } ?: return
    val now = clock.nowMillis()
    opinionDao.update(current.copy(deletedAt = now, updatedAt = now))
  }

  private suspend fun requireVisitAtDishsEntry(dishId: String, visitId: String?) {
    if (visitId == null) return
    require(opinionDao.countVisitAtDishsEntry(dishId, visitId) > 0) {
      "Visit $visitId is not a live visit at the same place entry as dish $dishId"
    }
  }
}
