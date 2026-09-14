package com.jasonarends.forklore.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

  @Query("SELECT * FROM people WHERE deletedAt IS NULL ORDER BY name")
  fun observeAll(): Flow<List<PersonEntity>>

  @Query("SELECT * FROM people WHERE isHouseholdMember = 1 AND deletedAt IS NULL ORDER BY name")
  fun observeHousehold(): Flow<List<PersonEntity>>

  @Query("SELECT * FROM people WHERE id = :id") suspend fun byId(id: String): PersonEntity?

  /**
   * Matches regardless of [PersonEntity.deletedAt]: the unique index on
   * [PersonEntity.normalizedName] covers tombstoned rows too, so a caller deciding whether a name
   * is free has to see them or it will try to insert a second row and crash on the index. Live
   * callers that only care about people currently in use should filter [PersonEntity.deletedAt]
   * themselves, the way every other read in this codebase does.
   */
  @Query("SELECT * FROM people WHERE normalizedName = :normalizedName")
  suspend fun byNormalizedNameIncludingDeleted(normalizedName: String): PersonEntity?
}

@Dao
interface PlaceDao {
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(place: PlaceEntity)

  @Update suspend fun update(place: PlaceEntity)

  @Query("SELECT * FROM places WHERE id = :id") suspend fun byId(id: String): PlaceEntity?

  /**
   * [query] is matched literally: callers pass raw user input, so LIKE's own wildcards are escaped
   * rather than honoured. Typing "%" should find places named "%", not every row.
   */
  @Query(
    "SELECT * FROM places WHERE deletedAt IS NULL " +
      "AND name LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY name"
  )
  fun search(query: String): Flow<List<PlaceEntity>>

  @Query(
    "SELECT * FROM places WHERE provider = :provider AND providerId = :providerId AND deletedAt IS NULL"
  )
  suspend fun byProviderId(provider: String, providerId: String): PlaceEntity?
}

/** Escapes LIKE metacharacters so user input matches literally. Pair with `ESCAPE '\'`. */
fun escapeLike(raw: String): String =
  raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

@Dao
interface PlaceEntryDao {
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(entry: PlaceEntryEntity)

  @Update suspend fun update(entry: PlaceEntryEntity)

  @Query("SELECT * FROM place_entries WHERE id = :id")
  suspend fun byId(id: String): PlaceEntryEntity?

  @Transaction
  @Query("SELECT * FROM place_entries WHERE placeListId = :placeListId AND deletedAt IS NULL")
  fun observeForList(placeListId: String): Flow<List<PlaceEntryWithPlace>>

  @Transaction
  @Query(
    "SELECT * FROM place_entries WHERE placeListId = :placeListId AND status = :status AND deletedAt IS NULL"
  )
  fun observeByStatus(placeListId: String, status: PlaceStatus): Flow<List<PlaceEntryWithPlace>>
}

@Dao
interface VisitDao {
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(visit: VisitEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun addAttendee(attendee: VisitAttendeeEntity)

  @Update suspend fun update(visit: VisitEntity)

  /**
   * Undated visits sort last rather than first: a note with no date is the least useful thing to
   * lead a timeline with.
   */
  @Transaction
  @Query(
    "SELECT * FROM visits WHERE placeEntryId = :placeEntryId AND deletedAt IS NULL " +
      "ORDER BY dateEpochDay IS NULL, dateEpochDay DESC"
  )
  fun observeForPlaceEntry(placeEntryId: String): Flow<List<VisitWithAttendees>>

  @Query("SELECT * FROM visits WHERE id = :id") suspend fun byId(id: String): VisitEntity?
}

@Dao
interface DishDao {
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(dish: DishEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAlias(alias: DishAliasEntity)

  @Update suspend fun update(dish: DishEntity)

  @Query("SELECT * FROM dishes WHERE id = :id") suspend fun byId(id: String): DishEntity?

  @Transaction
  @Query(
    "SELECT * FROM dishes WHERE placeEntryId = :placeEntryId AND deletedAt IS NULL ORDER BY canonicalName"
  )
  fun observeForPlaceEntry(placeEntryId: String): Flow<List<DishWithAliases>>

  @Transaction
  @Query(
    "SELECT * FROM dishes WHERE placeEntryId = :placeEntryId AND deletedAt IS NULL ORDER BY canonicalName"
  )
  fun observeWithOpinions(placeEntryId: String): Flow<List<DishWithOpinions>>

  /**
   * Resolves any spelling of a dish back to the dish itself, matching the stored normalized name or
   * any recorded alias. Both sides are pre-normalized columns, so this uses the unique indices and
   * is not defeated by punctuation or accents the way LOWER() would be.
   */
  @Query(
    "SELECT d.* FROM dishes d LEFT JOIN dish_aliases a ON a.dishId = d.id AND a.deletedAt IS NULL " +
      "WHERE d.placeEntryId = :placeEntryId AND d.deletedAt IS NULL " +
      "AND (d.normalizedName = :normalized OR a.normalized = :normalized) LIMIT 1"
  )
  suspend fun findByAnyName(placeEntryId: String, normalized: String): DishEntity?
}

@Dao
interface DishInterestDao {
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(interest: DishInterestEntity)

  @Update suspend fun update(interest: DishInterestEntity)

  @Query(
    "SELECT i.* FROM dish_interests i JOIN dishes d ON d.id = i.dishId " +
      "WHERE d.placeEntryId = :placeEntryId AND i.deletedAt IS NULL AND d.deletedAt IS NULL"
  )
  fun observeForPlaceEntry(placeEntryId: String): Flow<List<DishInterestEntity>>

  /** Every want (or never-again) across a whole list, for the "what should we order" view. */
  @Query(
    "SELECT i.* FROM dish_interests i " +
      "JOIN dishes d ON d.id = i.dishId " +
      "JOIN place_entries pe ON pe.id = d.placeEntryId " +
      "WHERE pe.placeListId = :placeListId AND i.status = :status " +
      "AND i.deletedAt IS NULL AND d.deletedAt IS NULL AND pe.deletedAt IS NULL"
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
}
