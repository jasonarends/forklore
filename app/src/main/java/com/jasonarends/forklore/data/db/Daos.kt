package com.jasonarends.forklore.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceListDao {
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(placeList: PlaceListEntity)

  @Update suspend fun update(placeList: PlaceListEntity)

  @Query("SELECT * FROM place_lists WHERE deletedAt IS NULL ORDER BY name")
  fun observeAll(): Flow<List<PlaceListEntity>>

  @Query("SELECT * FROM place_lists WHERE id = :id") suspend fun byId(id: String): PlaceListEntity?
}

@Dao
interface PersonDao {
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(person: PersonEntity)

  @Update suspend fun update(person: PersonEntity)

  @Query(
    "SELECT * FROM people WHERE placeListId = :placeListId AND deletedAt IS NULL ORDER BY name"
  )
  fun observeForList(placeListId: String): Flow<List<PersonEntity>>

  @Query("SELECT * FROM people WHERE id = :id") suspend fun byId(id: String): PersonEntity?
}

@Dao
interface PlaceDao {
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(place: PlaceEntity)

  @Update suspend fun update(place: PlaceEntity)

  @Query("SELECT * FROM places WHERE id = :id") suspend fun byId(id: String): PlaceEntity?

  @Query(
    "SELECT * FROM places WHERE deletedAt IS NULL AND name LIKE '%' || :query || '%' ORDER BY name"
  )
  fun search(query: String): Flow<List<PlaceEntity>>
}

@Dao
interface PlaceEntryDao {
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(entry: PlaceEntryEntity)

  @Update suspend fun update(entry: PlaceEntryEntity)

  @Query("SELECT * FROM place_entries WHERE placeListId = :placeListId AND deletedAt IS NULL")
  fun observeForList(placeListId: String): Flow<List<PlaceEntryEntity>>

  @Query(
    "SELECT * FROM place_entries WHERE placeListId = :placeListId AND status = :status AND deletedAt IS NULL"
  )
  fun observeByStatus(placeListId: String, status: PlaceStatus): Flow<List<PlaceEntryEntity>>

  @Query("SELECT * FROM place_entries WHERE id = :id")
  suspend fun byId(id: String): PlaceEntryEntity?
}

@Dao
interface VisitDao {
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(visit: VisitEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun addAttendee(attendee: VisitAttendeeEntity)

  @Update suspend fun update(visit: VisitEntity)

  /**
   * Undated visits sort last rather than first: a note with no date is the least useful thing to
   * lead a timeline with.
   */
  @Query(
    "SELECT * FROM visits WHERE placeEntryId = :placeEntryId AND deletedAt IS NULL " +
      "ORDER BY dateEpochDay IS NULL, dateEpochDay DESC"
  )
  fun observeForPlaceEntry(placeEntryId: String): Flow<List<VisitEntity>>

  @Query(
    "SELECT * FROM people p JOIN visit_attendees va ON va.personId = p.id WHERE va.visitId = :visitId"
  )
  suspend fun attendees(visitId: String): List<PersonEntity>
}

@Dao
interface DishDao {
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(dish: DishEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAlias(alias: DishAliasEntity)

  @Update suspend fun update(dish: DishEntity)

  @Query(
    "SELECT * FROM dishes WHERE placeId = :placeId AND deletedAt IS NULL ORDER BY canonicalName"
  )
  fun observeForPlace(placeId: String): Flow<List<DishEntity>>

  @Query("SELECT * FROM dishes WHERE id = :id") suspend fun byId(id: String): DishEntity?

  @Query("SELECT * FROM dish_aliases WHERE dishId = :dishId")
  suspend fun aliases(dishId: String): List<DishAliasEntity>

  /**
   * Resolves any spelling of a dish back to the dish itself, matching the canonical name or any
   * recorded alias. This is what stops "barrel tots" becoming a second row.
   */
  @Query(
    "SELECT d.* FROM dishes d LEFT JOIN dish_aliases a ON a.dishId = d.id " +
      "WHERE d.placeId = :placeId AND d.deletedAt IS NULL " +
      "AND (LOWER(d.canonicalName) = :normalized OR a.normalized = :normalized) LIMIT 1"
  )
  suspend fun findByAnyName(placeId: String, normalized: String): DishEntity?
}

@Dao
interface DishInterestDao {
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(interest: DishInterestEntity)

  @Update suspend fun update(interest: DishInterestEntity)

  @Query(
    "SELECT i.* FROM dish_interests i JOIN dishes d ON d.id = i.dishId " +
      "WHERE i.placeListId = :placeListId AND d.placeId = :placeId AND i.deletedAt IS NULL"
  )
  fun observeForPlace(placeListId: String, placeId: String): Flow<List<DishInterestEntity>>

  @Query(
    "SELECT i.* FROM dish_interests i WHERE i.placeListId = :placeListId AND i.status = :status " +
      "AND i.deletedAt IS NULL"
  )
  fun observeByStatus(placeListId: String, status: DishStatus): Flow<List<DishInterestEntity>>
}

@Dao
interface DishOpinionDao {
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(opinion: DishOpinionEntity)

  @Update suspend fun update(opinion: DishOpinionEntity)

  @Query(
    "SELECT * FROM dish_opinions WHERE dishId = :dishId AND deletedAt IS NULL ORDER BY createdAt"
  )
  fun observeForDish(dishId: String): Flow<List<DishOpinionEntity>>

  @Query("SELECT * FROM dish_opinions WHERE dishId = :dishId AND deletedAt IS NULL")
  suspend fun forDish(dishId: String): List<DishOpinionEntity>
}
