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

  /**
   * Returns the existing dish if this place entry already has one under any recorded spelling,
   * otherwise creates it. Callers should always come through here rather than inserting: it is what
   * keeps "barrel tots" and "potatoe barrels" one row. Goes through [DishDao.findOrInsert] (a
   * single `@Transaction`) rather than a separate find then insert, so two concurrent calls for the
   * same name can't both see no match and both insert.
   */
  suspend fun findOrCreateDish(placeEntryId: String, name: String): String {
    val normalized = normalizeDishName(name)
    val now = clock.nowMillis()
    val dish =
      dishDao.findOrInsert(
        placeEntryId = placeEntryId,
        normalized = normalized,
        dish =
          DishEntity(
            placeEntryId = placeEntryId,
            canonicalName = name.trim(),
            normalizedName = normalized,
            createdAt = now,
            updatedAt = now,
          ),
      )
    return dish.id
  }

  /** No-op when the spelling already resolves to this dish. See [findOrCreateDish] on the race. */
  suspend fun addAlias(dishId: String, placeEntryId: String, alias: String) {
    val normalized = normalizeDishName(alias)
    val now = clock.nowMillis()
    dishDao.findOrInsertAlias(
      placeEntryId = placeEntryId,
      normalized = normalized,
      alias =
        DishAliasEntity(
          dishId = dishId,
          alias = alias.trim(),
          normalized = normalized,
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
