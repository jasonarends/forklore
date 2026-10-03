package com.jasonarends.forklore.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.DishStatus
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceListEntity
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.data.db.TemperatureRating
import com.jasonarends.forklore.data.db.VisitEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

  // ---- Dish interests (issue #7) -----------------------------------------------------

  private suspend fun person(id: String, name: String, household: Boolean = true) = id.also {
    db
      .personDao()
      .insert(
        PersonEntity(
          id = id,
          name = name,
          normalizedName = name.lowercase(),
          isHouseholdMember = household,
          createdAt = 0,
          updatedAt = 0,
        )
      )
  }

  @Test
  fun setInterest_storesAllFourOptionalDimensions_readBackFromTheDatabase() = runTest {
    val robin = person("robin", "Robin")
    val dale = person("dale", "Dale", household = false)
    val dishId = repository.findOrCreateDish(placeEntryId, "Barrel Potatoes")

    repository.setInterest(
      dishId,
      DishStatus.WANT,
      forPersonId = robin,
      recommendedById = dale,
      modification = "add a Chilli bomb",
      note = "  ask about the sauce  ",
    )

    val stored = db.dishInterestDao().observeForPlaceEntry(placeEntryId).first().single()
    assertEquals(DishStatus.WANT, stored.status)
    assertEquals(robin, stored.forPersonId)
    assertEquals(dale, stored.recommendedById)
    assertEquals("add a Chilli bomb", stored.modification)
    // Free text is stored verbatim (CLAUDE.md rule 4).
    assertEquals("  ask about the sauce  ", stored.note)
  }

  @Test
  fun setInterest_storesABlankModificationAsNull_andTrimsARealOne() = runTest {
    val dishId = repository.findOrCreateDish(placeEntryId, "Reuben")

    repository.setInterest(dishId, DishStatus.WANT, modification = "   ")
    now = 2_000L
    repository.setInterest(dishId, DishStatus.TRIED, modification = " chopped ")

    val stored = db.dishInterestDao().observeForPlaceEntry(placeEntryId).first()
    assertEquals(listOf(null, "chopped"), stored.map { it.modification })
  }

  @Test
  fun setInterest_allowsSeveralInterestsOnOneDish() = runTest {
    val robin = person("robin", "Robin")
    val dishId = repository.findOrCreateDish(placeEntryId, "Chicken")

    repository.setInterest(dishId, DishStatus.WANT, forPersonId = robin)
    repository.setInterest(dishId, DishStatus.NEVER_AGAIN)

    val stored = db.dishInterestDao().observeForPlaceEntry(placeEntryId).first()
    assertEquals(setOf(DishStatus.WANT, DishStatus.NEVER_AGAIN), stored.map { it.status }.toSet())
  }

  @Test
  fun updateInterest_replacesEveryEditableField_andStampsUpdatedAt() = runTest {
    val robin = person("robin", "Robin")
    val dale = person("dale", "Dale", household = false)
    val dishId = repository.findOrCreateDish(placeEntryId, "Arancini")
    val id =
      repository.setInterest(
        dishId,
        DishStatus.WANT,
        forPersonId = robin,
        recommendedById = dale,
        modification = "extra sauce",
        note = "old",
      )

    now = 5_000L
    repository.updateInterest(
      id,
      status = DishStatus.NEVER_AGAIN,
      forPersonId = null,
      recommendedById = null,
      modification = "",
      note = "greasy",
    )

    val stored = db.dishInterestDao().byId(id)!!
    assertEquals(DishStatus.NEVER_AGAIN, stored.status)
    assertNull(stored.forPersonId)
    assertNull(stored.recommendedById)
    assertNull(stored.modification)
    assertEquals("greasy", stored.note)
    assertEquals(1_000L, stored.createdAt)
    assertEquals(5_000L, stored.updatedAt)
  }

  @Test
  fun removeInterest_softDeletes_soEveryReadDropsIt_butTheRowRemains() = runTest {
    val dishId = repository.findOrCreateDish(placeEntryId, "Bread")
    val id = repository.setInterest(dishId, DishStatus.NEVER_AGAIN)

    now = 7_000L
    repository.removeInterest(id)

    assertEquals(emptyList<Any>(), db.dishInterestDao().observeForPlaceEntry(placeEntryId).first())
    assertEquals(
      emptyList<Any>(),
      db.dishInterestDao().observeByStatus("list", DishStatus.NEVER_AGAIN).first(),
    )
    assertEquals(emptyList<Any>(), repository.observeListedInterests("list").first())
    val tombstone = db.dishInterestDao().byId(id)!!
    assertEquals(7_000L, tombstone.deletedAt)
    assertEquals(7_000L, tombstone.updatedAt)
  }

  @Test
  fun aRemovedInterest_isNotResurrectedByALaterUpdate_norRestamped() = runTest {
    val dishId = repository.findOrCreateDish(placeEntryId, "Bread")
    val id = repository.setInterest(dishId, DishStatus.NEVER_AGAIN)
    now = 7_000L
    repository.removeInterest(id)

    now = 9_000L
    repository.updateInterest(id, DishStatus.WANT, null, null, null, "stale editor")
    repository.removeInterest(id)

    val tombstone = db.dishInterestDao().byId(id)!!
    assertEquals(DishStatus.NEVER_AGAIN, tombstone.status)
    assertEquals(7_000L, tombstone.deletedAt)
    assertEquals(7_000L, tombstone.updatedAt)
  }

  @Test
  fun observeListedInterests_carriesTheNamesAListWideViewNeeds() = runTest {
    val robin = person("robin", "Robin")
    val dale = person("dale", "Dale", household = false)
    db
      .placeDao()
      .insert(
        PlaceEntity(
          id = "branchy",
          name = "Fifth Avenue Social",
          branchLabel = "Plaza",
          createdAt = 0,
          updatedAt = 0,
        )
      )
    db
      .placeEntryDao()
      .insert(
        PlaceEntryEntity(
          id = "entry-b",
          placeListId = "list",
          placeId = "branchy",
          createdAt = 0,
          updatedAt = 0,
        )
      )
    repository.setInterest(
      repository.findOrCreateDish(placeEntryId, "Barrel Potatoes"),
      DishStatus.WANT,
      forPersonId = robin,
      recommendedById = dale,
      modification = "add a Chilli bomb",
    )
    repository.setInterest(
      repository.findOrCreateDish("entry-b", "Chicken Pot Pie"),
      DishStatus.NEVER_AGAIN,
    )

    val listed = repository.observeListedInterests("list").first()

    // Ordered by place name, so "Fifth Avenue Social" precedes "Halberd".
    assertEquals(listOf("Chicken Pot Pie", "Barrel Potatoes"), listed.map { it.dishName })
    val halberd = listed.single { it.dishName == "Barrel Potatoes" }
    assertEquals(placeEntryId, halberd.placeEntryId)
    assertEquals("Halberd", halberd.placeName)
    assertNull(halberd.branchLabel)
    assertEquals("Robin", halberd.forPersonName)
    assertEquals("Dale", halberd.recommendedByName)
    assertEquals("add a Chilli bomb", halberd.interest.modification)
    val plaza = listed.single { it.dishName == "Chicken Pot Pie" }
    assertEquals("Plaza", plaza.branchLabel)
    assertNull(plaza.forPersonName)
  }

  /** Rule 6: the same restaurant on a private list must not leak its dishes into another. */
  @Test
  fun observeListedInterests_isScopedToOneList() = runTest {
    db
      .placeListDao()
      .insert(PlaceListEntity(id = "private", name = "Mine", createdAt = 0, updatedAt = 0))
    db
      .placeEntryDao()
      .insert(
        PlaceEntryEntity(
          id = "private-entry",
          placeListId = "private",
          placeId = "place",
          createdAt = 0,
          updatedAt = 0,
        )
      )
    repository.setInterest(
      repository.findOrCreateDish(placeEntryId, "Shared Dish"),
      DishStatus.WANT,
    )
    repository.setInterest(
      repository.findOrCreateDish("private-entry", "Private Dish"),
      DishStatus.NEVER_AGAIN,
      note = "private note",
    )

    assertEquals(
      listOf("Shared Dish"),
      repository.observeListedInterests("list").first().map { it.dishName },
    )
    assertEquals(
      listOf("Private Dish"),
      repository.observeListedInterests("private").first().map { it.dishName },
    )
  }

  @Test
  fun observeListedInterests_keepsAnInterest_whoseCitedPersonWasRemoved() = runTest {
    val dale = person("dale", "Dale", household = false)
    repository.setInterest(
      repository.findOrCreateDish(placeEntryId, "Pot Pie"),
      DishStatus.WANT,
      recommendedById = dale,
    )
    val stored = db.personDao().byId(dale)!!
    db.personDao().update(stored.copy(deletedAt = 3_000L))

    val listed = repository.observeListedInterests("list").first().single()

    assertNull(listed.recommendedByName)
    assertEquals(dale, listed.interest.recommendedById)
  }

  // ---- Opinions (issue #8) ------------------------------------------------------------

  private suspend fun visit(id: String, entryId: String = placeEntryId) =
    db.visitDao().insert(VisitEntity(id = id, placeEntryId = entryId, createdAt = 0, updatedAt = 0))

  private suspend fun otherPlaceEntry(): String {
    db.placeDao().insert(PlaceEntity(id = "place2", name = "Verano", createdAt = 0, updatedAt = 0))
    db
      .placeEntryDao()
      .insert(
        PlaceEntryEntity(
          id = "entry2",
          placeListId = "list",
          placeId = "place2",
          createdAt = 0,
          updatedAt = 0,
        )
      )
    return "entry2"
  }

  @Test
  fun recordOpinion_twoAuthorsOfOneDish_bothSurviveWithTheirOwnRatingAndTemperature() = runTest {
    person("ana", "Ana")
    person("sam", "Sam")
    val dishId = repository.findOrCreateDish(placeEntryId, "Arancini")

    repository.recordOpinion(
      dishId,
      "ana",
      Rating.BAD,
      note = "Skip these.",
      temperature = TemperatureRating.LACKING,
    )
    repository.recordOpinion(
      dishId,
      "sam",
      Rating.MID,
      note = "They were fine.",
      temperature = TemperatureRating.ADEQUATE,
    )

    val opinions = db.dishOpinionDao().observeForDish(dishId).first()
    assertEquals(2, opinions.size)
    val ana = opinions.single { it.authorId == "ana" }
    val sam = opinions.single { it.authorId == "sam" }
    assertEquals(Rating.BAD, ana.rating)
    assertEquals(TemperatureRating.LACKING, ana.temperature)
    assertEquals("Skip these.", ana.note)
    assertEquals(Rating.MID, sam.rating)
    assertEquals(TemperatureRating.ADEQUATE, sam.temperature)
    assertEquals("They were fine.", sam.note)
  }

  @Test
  fun recordOpinion_aNoteAloneIsACompleteOpinion() = runTest {
    person("ana", "Ana")
    val dishId = repository.findOrCreateDish(placeEntryId, "Arancini")

    repository.recordOpinion(dishId, "ana", rating = null, note = "Too greasy for us.")

    val opinion = db.dishOpinionDao().observeForDish(dishId).first().single()
    assertEquals(null, opinion.rating)
    assertEquals(null, opinion.temperature)
    assertEquals("Too greasy for us.", opinion.note)
  }

  @Test
  fun recordOpinion_sameAuthorTwice_keepsBothRows() = runTest {
    person("ana", "Ana")
    val dishId = repository.findOrCreateDish(placeEntryId, "Arancini")

    repository.recordOpinion(dishId, "ana", Rating.BAD)
    repository.recordOpinion(dishId, "ana", Rating.GOOD, note = "Better the second time.")

    assertEquals(2, db.dishOpinionDao().observeForDish(dishId).first().size)
  }

  @Test
  fun recordOpinion_linksAVisitAtTheSamePlaceEntry() = runTest {
    person("ana", "Ana")
    visit("v1")
    val dishId = repository.findOrCreateDish(placeEntryId, "Arancini")

    repository.recordOpinion(dishId, "ana", Rating.GOOD, visitId = "v1")

    assertEquals("v1", db.dishOpinionDao().observeForDish(dishId).first().single().visitId)
  }

  @Test
  fun recordOpinion_rejectsAVisitFromAnotherPlaceEntry_andWritesNothing() = runTest {
    person("ana", "Ana")
    val otherEntry = otherPlaceEntry()
    visit("elsewhere", entryId = otherEntry)
    val dishId = repository.findOrCreateDish(placeEntryId, "Arancini")

    val failure = runCatching {
      repository.recordOpinion(dishId, "ana", Rating.GOOD, visitId = "elsewhere")
    }

    assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    assertEquals(0, db.dishOpinionDao().observeForDish(dishId).first().size)
  }

  @Test
  fun updateOpinion_rewritesTheFields_stampsUpdatedAt_andLeavesTheOtherAuthorAlone() = runTest {
    person("ana", "Ana")
    person("sam", "Sam")
    visit("v1")
    val dishId = repository.findOrCreateDish(placeEntryId, "Arancini")
    now = 1_000L
    val anaId = repository.recordOpinion(dishId, "ana", Rating.BAD, note = "Skip these.")
    val samId = repository.recordOpinion(dishId, "sam", Rating.MID, note = "Fine.")

    now = 5_000L
    repository.updateOpinion(
      opinionId = anaId,
      authorId = "ana",
      rating = Rating.FINE,
      temperature = TemperatureRating.MID,
      note = "Okay on a second look.",
      visitId = "v1",
    )

    val opinions = db.dishOpinionDao().observeForDish(dishId).first()
    val ana = opinions.single { it.id == anaId }
    assertEquals(Rating.FINE, ana.rating)
    assertEquals(TemperatureRating.MID, ana.temperature)
    assertEquals("Okay on a second look.", ana.note)
    assertEquals("v1", ana.visitId)
    assertEquals(1_000L, ana.createdAt)
    assertEquals(5_000L, ana.updatedAt)
    val sam = opinions.single { it.id == samId }
    assertEquals(Rating.MID, sam.rating)
    assertEquals("Fine.", sam.note)
    assertEquals(1_000L, sam.updatedAt)
  }

  @Test
  fun updateOpinion_canClearTheRatingTemperatureAndVisit() = runTest {
    person("ana", "Ana")
    visit("v1")
    val dishId = repository.findOrCreateDish(placeEntryId, "Arancini")
    val id =
      repository.recordOpinion(
        dishId,
        "ana",
        Rating.GOOD,
        note = "keep",
        visitId = "v1",
        temperature = TemperatureRating.PHENOMENAL,
      )

    repository.updateOpinion(id, "ana", null, null, "keep", null)

    val opinion = db.dishOpinionDao().observeForDish(dishId).first().single()
    assertEquals(null, opinion.rating)
    assertEquals(null, opinion.temperature)
    assertEquals(null, opinion.visitId)
    assertEquals("keep", opinion.note)
  }

  @Test
  fun updateOpinion_stillWorks_afterItsLinkedVisitWasSoftDeleted() = runTest {
    person("ana", "Ana")
    visit("v1")
    val dishId = repository.findOrCreateDish(placeEntryId, "Arancini")
    val id = repository.recordOpinion(dishId, "ana", Rating.GOOD, visitId = "v1")
    val gone = db.visitDao().byId("v1")!!
    db.visitDao().update(gone.copy(deletedAt = 1L))

    repository.updateOpinion(id, "ana", Rating.GOOD, null, "edited after the visit went", "v1")

    val opinion = db.dishOpinionDao().observeForDish(dishId).first().single()
    assertEquals("edited after the visit went", opinion.note)
  }

  @Test
  fun updateOpinion_rejectsANewLinkToAVisitAtAnotherPlaceEntry() = runTest {
    person("ana", "Ana")
    val otherEntry = otherPlaceEntry()
    visit("elsewhere", entryId = otherEntry)
    val dishId = repository.findOrCreateDish(placeEntryId, "Arancini")
    val id = repository.recordOpinion(dishId, "ana", Rating.GOOD)

    val failure = runCatching {
      repository.updateOpinion(id, "ana", Rating.GOOD, null, "", "elsewhere")
    }

    assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    assertEquals(null, db.dishOpinionDao().observeForDish(dishId).first().single().visitId)
  }

  @Test
  fun updateOpinion_onAMissingOpinion_isANoOp() = runTest {
    person("ana", "Ana")

    repository.updateOpinion("nope", "ana", Rating.GOOD, null, "", null)

    assertEquals(0, db.dishOpinionDao().observeForPlaceEntry(placeEntryId).first().size)
  }

  @Test
  fun deleteOpinion_tombstonesTheRow_ratherThanRemovingIt() = runTest {
    person("ana", "Ana")
    person("sam", "Sam")
    val dishId = repository.findOrCreateDish(placeEntryId, "Arancini")
    val anaId = repository.recordOpinion(dishId, "ana", Rating.BAD)
    repository.recordOpinion(dishId, "sam", Rating.FINE)

    now = 9_000L
    repository.deleteOpinion(anaId)

    val tombstone = db.dishOpinionDao().byId(anaId)
    assertEquals(9_000L, tombstone?.deletedAt)
    assertEquals(9_000L, tombstone?.updatedAt)
    assertEquals(
      listOf("sam"),
      repository.observeOpinionsForPlaceEntry(placeEntryId).first().map { it.authorId },
    )
  }

  @Test
  fun observeOpinionsForPlaceEntry_onlyReturnsThisEntrysOpinions_oldestFirst() = runTest {
    person("ana", "Ana")
    person("sam", "Sam")
    val otherEntry = otherPlaceEntry()
    val here = repository.findOrCreateDish(placeEntryId, "Arancini")
    val there = repository.findOrCreateDish(otherEntry, "Arancini")
    now = 100L
    repository.recordOpinion(here, "sam", Rating.FINE)
    now = 200L
    repository.recordOpinion(there, "ana", Rating.PHENOMENAL, note = "the other list's writing")
    now = 50L
    repository.recordOpinion(here, "ana", Rating.BAD)

    val opinions = repository.observeOpinionsForPlaceEntry(placeEntryId).first()

    assertEquals(listOf("ana", "sam"), opinions.map { it.authorId })
    assertTrue(opinions.none { it.note == "the other list's writing" })
  }
}
