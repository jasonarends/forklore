package com.jasonarends.forklore.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceListEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs against a real in-memory Room database, not hand-written DAO fakes — [DishDao.findByAnyName]
 * relies on the same normalized-column matching the unique index does, so a fake would have to
 * reimplement that logic and could silently drift from it. See [PersonRepositoryTest] for the same
 * reasoning applied to people.
 */
@RunWith(RobolectricTestRunner::class)
class DishRepositoryTest {
  private lateinit var db: ForkloreDatabase
  private lateinit var repository: DishRepository
  private lateinit var placeEntryId: String
  private var now = 1_000L

  @Before
  fun setUp() = runTest {
    db =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          ForkloreDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()
    repository =
      DishRepository(db.dishDao(), db.dishInterestDao(), db.dishOpinionDao(), Clock { now })

    val listId = "list"
    db
      .placeListDao()
      .insert(PlaceListEntity(id = listId, name = "Ours", createdAt = 0, updatedAt = 0))
    val placeId = "place"
    db.placeDao().insert(PlaceEntity(id = placeId, name = "Halberd", createdAt = 0, updatedAt = 0))
    placeEntryId = "entry"
    db
      .placeEntryDao()
      .insert(
        PlaceEntryEntity(
          id = placeEntryId,
          placeListId = listId,
          placeId = placeId,
          createdAt = 0,
          updatedAt = 0,
        )
      )
  }

  @After fun tearDown() = db.close()

  @Test
  fun findOrCreateDish_createsANewDishOnFirstCall() = runTest {
    val id = repository.findOrCreateDish(placeEntryId, "Barrel Potatoes")

    val stored = db.dishDao().byId(id)!!
    assertEquals("Barrel Potatoes", stored.canonicalName)
    assertEquals("barrel potatoes", stored.normalizedName)
  }

  @Test
  fun findOrCreateDish_returnsTheExistingDish_forTheSameSpellingAgain() = runTest {
    val first = repository.findOrCreateDish(placeEntryId, "Barrel Potatoes")

    val second = repository.findOrCreateDish(placeEntryId, "  barrel   potatoes ")

    assertEquals(first, second)
    assertEquals(1, db.dishDao().observeForPlaceEntry(placeEntryId).first().size)
  }

  /**
   * The issue #6 "done when" case: a dish recorded under one spelling is offered, not duplicated,
   * once a second spelling has been taught to it via [DishRepository.addAlias].
   */
  @Test
  fun findOrCreateDish_resolvesToTheSameDish_onceAnAliasHasBeenRecorded() = runTest {
    val dishId = repository.findOrCreateDish(placeEntryId, "Barrel Potatoes")
    repository.addAlias(dishId, placeEntryId, "barrel tots")

    val resolvedId = repository.findOrCreateDish(placeEntryId, "Barrel Tots")

    assertEquals(dishId, resolvedId)
    assertEquals(1, db.dishDao().observeForPlaceEntry(placeEntryId).first().size)
  }

  /**
   * Regresses a real check-then-act race: two callers hitting "Add" for the same dish at once used
   * to both observe [DishDao.findByAnyName] as null and both insert, one of them throwing against
   * the unique index. [DishDao.findOrInsert] closes this by running find-then-insert as one
   * `@Transaction`, which Room serializes on its single write connection — dispatching onto
   * [Dispatchers.IO] (real threads, not the test dispatcher's virtual time) is what actually
   * exercises that serialization rather than just interleaving suspension points on one thread.
   */
  @Test
  fun findOrCreateDish_concurrentCallsForTheSameSpelling_createOnlyOneDish() = runTest {
    val ids = coroutineScope {
      (1..8)
        .map {
          async(Dispatchers.IO) { repository.findOrCreateDish(placeEntryId, "Barrel Potatoes") }
        }
        .awaitAll()
    }

    // Every caller must agree on the same id, not just leave one row behind — findOrInsert
    // returns the entity it was passed on the losing path too, so a return-value bug (e.g.
    // switching insert to OnConflictStrategy.IGNORE) could hand a caller a phantom id while the
    // row count alone would still read 1.
    assertEquals(1, ids.toSet().size)
    assertEquals(1, db.dishDao().observeForPlaceEntry(placeEntryId).first().size)
  }

  @Test
  fun findOrCreateDish_treatsDishesAtDifferentPlaceEntriesAsSeparate() = runTest {
    db.placeDao().insert(PlaceEntity(id = "place2", name = "Verano", createdAt = 0, updatedAt = 0))
    val otherEntryId = "entry2"
    db
      .placeEntryDao()
      .insert(
        PlaceEntryEntity(
          id = otherEntryId,
          placeListId = "list",
          placeId = "place2",
          createdAt = 0,
          updatedAt = 0,
        )
      )

    val first = repository.findOrCreateDish(placeEntryId, "Arancini")
    val second = repository.findOrCreateDish(otherEntryId, "Arancini")

    assertNotEquals(first, second)
  }

  @Test
  fun addAlias_letsAKnownDishResolveUnderTheNewSpelling() = runTest {
    // "Burnt cream" is a genuinely different spelling of crème brûlée, unlike its own
    // normalized name — addAlias only has something to prove when the spelling isn't already
    // covered by DishEntity.normalizedName.
    val dishId = repository.findOrCreateDish(placeEntryId, "Crème Brûlée")

    repository.addAlias(dishId, placeEntryId, "Burnt Cream")

    assertEquals(dishId, db.dishDao().findByAnyName(placeEntryId, "burnt cream")?.id)
    assertEquals(1, db.dishDao().observeForPlaceEntry(placeEntryId).first().single().aliases.size)
  }

  @Test
  fun addAlias_isANoOp_whenTheSpellingAlreadyResolvesToThisDish() = runTest {
    val dishId = repository.findOrCreateDish(placeEntryId, "Burnt Ends")

    // The canonical spelling itself already resolves via normalizedName — recording it again as
    // an alias would be redundant and would collide with the alias table's own unique index.
    repository.addAlias(dishId, placeEntryId, "burnt ends")

    assertEquals(0, db.dishDao().observeForPlaceEntry(placeEntryId).first().single().aliases.size)
  }
}
