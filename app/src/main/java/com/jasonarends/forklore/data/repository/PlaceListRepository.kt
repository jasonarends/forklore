package com.jasonarends.forklore.data.repository

import com.jasonarends.forklore.data.db.PlaceListDao
import com.jasonarends.forklore.data.db.PlaceListEntity
import kotlinx.coroutines.flow.Flow

/** Lists of places. A personal list is one with a single member. */
class PlaceListRepository(
  private val placeListDao: PlaceListDao,
  private val clock: Clock = Clock.System,
) {
  fun observeAll(): Flow<List<PlaceListEntity>> = placeListDao.observeAll()

  suspend fun create(name: String): String {
    val now = clock.nowMillis()
    val list = PlaceListEntity(name = name, createdAt = now, updatedAt = now)
    placeListDao.insert(list)
    return list.id
  }

  suspend fun rename(id: String, name: String) {
    val current = placeListDao.byId(id) ?: return
    placeListDao.update(current.copy(name = name, updatedAt = clock.nowMillis()))
  }
}
