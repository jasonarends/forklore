package com.jasonarends.forklore.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.PersonRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The acceptance spec for M1, derived from the real notes this app replaces.
 *
 * Each test corresponds to one capability those notes demand. This is the definition of done for
 * the data layer: if a line in fixtures/notes-sample.txt cannot be represented by these tables, a
 * test here fails. Add to this file before adding a feature, not after.
 *
 * Two rules for anything added here, both learned from review:
 * - Assert on data read back out of the database. A test that only inspects the objects it just
 *   built, or the declaration order of an enum, passes with the schema deleted.
 * - Constraints count as behaviour. Foreign keys, unique indices and cascades are the part that is
 *   expensive to change later, so they get tests of their own.
 */
@RunWith(RobolectricTestRunner::class)
class AcceptanceSpecTest {
  private lateinit var db: ForkloreDatabase
  private lateinit var listId: String
  private lateinit var ana: String
  private lateinit var sam: String

  private val now = 1_757_000_000_000L
  private val later = now + 60_000L

  @Before
  fun setUp() = runTest {
    db =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          ForkloreDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()
    listId = newId()
    db
      .placeListDao()
      .insert(PlaceListEntity(id = listId, name = "Ours", createdAt = now, updatedAt = now))
    ana = person("Ana", household = true)
    sam = person("Sam", household = true)
  }

  @After fun tearDown() = db.close()

  // ---- 1. A date can be day-precise, month-only, or absent entirely -------------------

  @Test
  fun visitDate_canBeDayPrecise_monthPrecise_orAbsent() = runTest {
    val entry = placeEntry(place("Hotel Brannock"))
    // "6/21/26 - breakfast at brannock"
    visit(entry, epochDay = 20_625, precision = DatePrecision.DAY, meal = Meal.BREAKFAST)
    // "Hotel Brannock June 2026" — the month is known, the day is not
    visit(entry, epochDay = 20_605, precision = DatePrecision.MONTH)
    // "Verano" — no date at all
    visit(entry, epochDay = null, precision = DatePrecision.UNKNOWN)

    val visits = db.visitDao().observeForPlaceEntry(entry).first().map { it.visit }

    assertEquals(3, visits.size)
    assertEquals(
      setOf(DatePrecision.DAY, DatePrecision.MONTH, DatePrecision.UNKNOWN),
      visits.map { it.datePrecision }.toSet(),
    )
    assertEquals(Meal.BREAKFAST, visits.first { it.datePrecision == DatePrecision.DAY }.meal)
    // An undated visit sorts last rather than leading the timeline.
    assertNull(visits.last().dateEpochDay)
  }

  // ---- 2. Several visits to one place, entered out of chronological order -------------

  @Test
  fun place_holdsMultipleVisits_enteredOutOfOrder() = runTest {
    val entry = placeEntry(place("Halberd"))
    visit(entry, epochDay = 20_619, precision = DatePrecision.DAY) // 6/15/26
    visit(entry, epochDay = 20_284, precision = DatePrecision.DAY) // 7/15/25, entered second
    visit(entry, epochDay = 20_675, precision = DatePrecision.DAY) // 8/10/26

    val visits = db.visitDao().observeForPlaceEntry(entry).first().map { it.visit }

    assertEquals(listOf(20_675L, 20_619L, 20_284L), visits.map { it.dateEpochDay })
  }

  @Test
  fun visit_carriesItsAttendees() = runTest {
    val entry = placeEntry(place("Halberd"))
    val visitId = visit(entry, epochDay = 20_619, precision = DatePrecision.DAY)
    // "Get bbq shrimp with Casey and Drew"
    val casey = person("Casey", household = false)
    attend(visitId, ana)
    attend(visitId, casey)

    val attendees = db.visitDao().observeForPlaceEntry(entry).first().single().attendees

    assertEquals(setOf("Ana", "Casey"), attendees.map { it.name }.toSet())
  }

  // ---- 3. One dish spelled three ways is still one dish -------------------------------

  @Test
  fun dish_resolvesFromAnyRecordedSpelling() = runTest {
    val entry = placeEntry(place("Halberd"))
    val dishId = dish(entry, "Barrel Potatoes", aliases = listOf("potatoe barrels", "barrel tots"))

    assertEquals(
      dishId,
      db.dishDao().findByAnyName(entry, normalizeDishName("barrel potatoes"))?.id,
    )
    assertEquals(
      dishId,
      db.dishDao().findByAnyName(entry, normalizeDishName("Potatoe Barrels"))?.id,
    )
    assertEquals(dishId, db.dishDao().findByAnyName(entry, normalizeDishName("BARREL TOTS"))?.id)
    assertEquals(1, db.dishDao().observeForPlaceEntry(entry).first().size)
  }

  @Test
  fun dish_resolvesAcrossPunctuationAndAccents() = runTest {
    val entry = placeEntry(place("The Lamplight"))
    // Punctuation and accents are exactly where a LOWER()-based match silently fails and
    // a second row for the same dish appears.
    val mac = dish(entry, "Mac 'n' Cheese")
    val creme = dish(entry, "Crème Brûlée")

    assertEquals(mac, db.dishDao().findByAnyName(entry, normalizeDishName("mac n cheese"))?.id)
    assertEquals(mac, db.dishDao().findByAnyName(entry, normalizeDishName("MAC  N   CHEESE"))?.id)
    assertEquals(creme, db.dishDao().findByAnyName(entry, normalizeDishName("creme brulee"))?.id)
  }

  @Test
  fun dish_cannotBeRecordedTwiceUnderOneEntry() = runTest {
    val entry = placeEntry(place("Halberd"))
    dish(entry, "Burnt Ends")

    // The unique (placeEntryId, normalizedName) index is what makes the alias table
    // meaningful; without it duplicate dishes reappear by another route.
    assertConstraintViolation { dish(entry, "burnt   ends") }
  }

  // ---- 4. Two people can disagree about the same dish ---------------------------------

  @Test
  fun dish_keepsBothAuthorsOpinions_whenTheyDisagree() = runTest {
    // 'Arancini (bad) ((Sam says they were "fine"))'
    val dishId = dish(placeEntry(place("Cafe Mirabel")), "arancini")
    opinion(dishId, ana, Rating.BAD, "bad")
    opinion(dishId, sam, Rating.FINE, "they were fine")

    val opinions = db.dishOpinionDao().observeForDish(dishId).first()

    assertEquals(2, opinions.size)
    assertEquals(Rating.BAD, opinions.single { it.authorId == ana }.rating)
    assertEquals(Rating.FINE, opinions.single { it.authorId == sam }.rating)
  }

  // ---- 5. "Never again" is not the same as "not tried yet" ----------------------------

  @Test
  fun dishInterest_distinguishesNeverAgain_fromWant_andTried() = runTest {
    val entry = placeEntry(place("Cafe Mirabel"))
    interest(dish(entry, "arancini"), DishStatus.NEVER_AGAIN) // "Skip bread and arancini"
    interest(dish(entry, "burrata"), DishStatus.WANT) // "What we want- burrata"
    interest(dish(entry, "lamb meatballs"), DishStatus.TRIED)

    val neverAgain = db.dishInterestDao().observeByStatus(listId, DishStatus.NEVER_AGAIN).first()
    val want = db.dishInterestDao().observeByStatus(listId, DishStatus.WANT).first()

    assertEquals(1, neverAgain.size)
    assertEquals(1, want.size)
    assertEquals(3, db.dishInterestDao().observeForPlaceEntry(entry).first().size)
  }

  // ---- 6. Ratings use the vocabulary people actually wrote ----------------------------

  @Test
  fun rating_roundTripsThroughTheDatabase_acrossItsWholeRange() = runTest {
    val entry = placeEntry(place("Fifth Avenue Social"))
    // Every constant must survive the converter: it stores names, so a bad mapping throws
    // on read rather than on write.
    val byRating =
      Rating.entries.associateWith { rating ->
        dish(entry, "dish ${rating.name}").also { opinion(it, ana, rating, rating.name) }
      }

    byRating.forEach { (rating, dishId) ->
      assertEquals(rating, db.dishOpinionDao().observeForDish(dishId).first().single().rating)
    }
    // "Fries mid" and "changed our lives" are both expressible, and ordered.
    assertTrue(Rating.MID.ordinal < Rating.PHENOMENAL.ordinal)
    assertTrue(Rating.PHENOMENAL.ordinal < Rating.LIFE_CHANGING.ordinal)
  }

  // ---- 7. Food and service are judged separately --------------------------------------

  @Test
  fun placeEntry_ratesFoodAndServiceIndependently() = runTest {
    // Divine pasta, rude servers — one number cannot hold both.
    val entryId = placeEntry(place("Hotel Brannock"))
    updateEntry(entryId) {
      it.copy(
        foodRating = Rating.LIFE_CHANGING,
        serviceRating = Rating.BAD,
        revisitIntent = RevisitIntent.WAIT,
      )
    }

    val updated = db.placeEntryDao().byId(entryId)!!

    assertEquals(Rating.LIFE_CHANGING, updated.foodRating)
    assertEquals(Rating.BAD, updated.serviceRating)
    assertEquals(later, updated.updatedAt)
  }

  // ---- 8. A want can belong to one person rather than the whole list ------------------

  @Test
  fun dishInterest_canBeScopedToOnePerson() = runTest {
    val robin = person("Robin", household = true)
    val entry = placeEntry(place("Fifth Avenue Social"))
    // "robin wants chicken" / "Have Robin get mac and cheese as her side"
    interest(dish(entry, "chicken"), DishStatus.WANT, forPerson = robin)
    interest(dish(entry, "wedge salad"), DishStatus.WANT)

    val wants = db.dishInterestDao().observeByStatus(listId, DishStatus.WANT).first()

    assertEquals(robin, wants.single { it.forPersonId != null }.forPersonId)
    assertEquals(1, wants.count { it.forPersonId == null })
  }

  // ---- 9. A recommendation can be attributed to someone who'll never use the app ------

  @Test
  fun dishInterest_recordsWhoRecommendedIt() = runTest {
    // "Dale and Marvin agrees and recommends chicken pot pie"
    val dale = person("Dale", household = false)
    interest(
      dish(placeEntry(place("Fifth Avenue Social")), "chicken pot pie"),
      DishStatus.WANT,
      recommendedBy = dale,
    )

    val want = db.dishInterestDao().observeByStatus(listId, DishStatus.WANT).first().single()

    assertEquals(dale, want.recommendedById)
    assertEquals(false, db.personDao().byId(want.recommendedById!!)!!.isHouseholdMember)
  }

  // ---- 10. A place can carry a pricing or policy warning ------------------------------

  @Test
  fun place_carriesAWarning() = runTest {
    val placeId =
      place(
        "Aioe all in one eatery",
        warning = "\$27 per person even if you order one thing, and you pay to sit",
      )

    assertTrue(db.placeDao().byId(placeId)!!.warning!!.contains("pay to sit"))
  }

  // ---- 11. Whether to go back is recorded separately from how good it was -------------

  @Test
  fun placeEntry_recordsRevisitIntent() = runTest {
    val entryId = placeEntry(place("Hotel Brannock"))
    // "Probably wait to come back"
    updateEntry(entryId) {
      it.copy(revisitIntent = RevisitIntent.WAIT, status = PlaceStatus.VISITED)
    }

    val updated = db.placeEntryDao().byId(entryId)!!

    assertEquals(RevisitIntent.WAIT, updated.revisitIntent)
    assertEquals(PlaceStatus.VISITED, updated.status)
  }

  // ---- 12. How to order it is part of the want ----------------------------------------

  @Test
  fun dishInterest_keepsOrderingInstructions() = runTest {
    // "Order the barrel potatoes and add a Chilli bomb to it" / "Ruben (chopped)"
    val entry = placeEntry(place("Halberd"))
    interest(dish(entry, "barrel potatoes"), DishStatus.WANT, modification = "add a Chilli bomb")
    interest(dish(entry, "reuben"), DishStatus.WANT, modification = "chopped")

    val wants = db.dishInterestDao().observeByStatus(listId, DishStatus.WANT).first()

    assertEquals(
      setOf("add a Chilli bomb", "chopped"),
      wants.mapNotNull { it.modification }.toSet(),
    )
  }

  // ---- 13. Two branches of one restaurant are two places ------------------------------

  @Test
  fun place_disambiguatesBranches() = runTest {
    place("Fifth Avenue Social", branch = "Kansas")
    place("Fifth Avenue Social", branch = "Plaza")

    val found = db.placeDao().search("Fifth Avenue").first()

    assertEquals(2, found.size)
    assertEquals(setOf("Kansas", "Plaza"), found.mapNotNull { it.branchLabel }.toSet())
  }

  @Test
  fun placeSearch_treatsWildcardsAsLiteralText() = runTest {
    place("Halberd")
    place("100% Pizza")

    // A stray "%" must not behave as "match everything".
    assertEquals(
      listOf("100% Pizza"),
      db.placeDao().search(escapeLike("%")).first().map { it.name },
    )
  }

  // ---- 14. Free text survives whatever was typed, including truncation ----------------

  @Test
  fun freeText_isPreservedVerbatim() = runTest {
    // "places" carries no free text of its own (CLAUDE.md rule 6, issue #13) — a place's writing
    // lives entirely on the list's entry for it, however messy or multi-line it is.
    val messy = "- great for pizza, menu doesn't "
    val entryId = placeEntry(place("Halberd"))
    updateEntry(entryId) { it.copy(note = messy) }

    assertEquals(messy, db.placeEntryDao().byId(entryId)!!.note)

    updateEntry(entryId) { it.copy(note = "Servers are rude\nSERVICE HORRIBLE") }

    assertTrue(db.placeEntryDao().byId(entryId)!!.note.contains("\n"))
  }

  // ---- 15. A person is deduped by name, however it's spelled --------------------------

  @Test
  fun person_findOrCreateDedupesOnNormalizedName() = runTest {
    // "have Robin get mac and cheese" today and "Val recommends..." tomorrow must not turn one
    // Val into two people because someone typed a trailing space.
    val repository = PersonRepository(db.personDao(), Clock { now })

    val first = repository.findOrCreate("Val")
    val second = repository.findOrCreate("val ")

    assertEquals(first, second)
    val stored =
      db.personDao().observeAll().first().filter { it.normalizedName == normalizeDishName("Val") }
    assertEquals(1, stored.size)
  }

  // ---- Constraints. The expensive-to-change part, so it is asserted too ---------------

  @Test
  fun personCannotBeRecordedTwiceUnderOneNormalizedName() = runTest {
    // The unique index PersonRepository.findOrCreate relies on to dedupe.
    assertConstraintViolation {
      person("Robin", household = false)
      person("robin ", household = false)
    }
  }

  @Test
  fun personNormalizedNameStaysReservedAfterASoftDelete() = runTest {
    // The unique index doesn't forgive a tombstoned row: a findOrCreate/rename that only checked
    // live people would try to insert a second "Val" here and crash on this same constraint,
    // which is why PersonRepository resurrects a soft-deleted match instead.
    val valId = person("Val", household = false)
    db.personDao().update(db.personDao().byId(valId)!!.copy(deletedAt = later))

    assertConstraintViolation { person("val", household = false) }
  }

  @Test
  fun placeCannotBeAddedToTheSameListTwice() = runTest {
    val placeId = place("Halberd")
    placeEntry(placeId)

    assertConstraintViolation { placeEntry(placeId) }
  }

  @Test
  fun rowsCannotReferenceAMissingParent() = runTest {
    assertConstraintViolation {
      db
        .dishDao()
        .insert(
          DishEntity(
            placeEntryId = "no-such-entry",
            canonicalName = "ghost",
            normalizedName = "ghost",
            createdAt = now,
            updatedAt = now,
          )
        )
    }
  }

  @Test
  fun hardDeletingAPlaceEntry_cascadesToItsDishesAndOpinions() = runTest {
    val entry = placeEntry(place("Cafe Mirabel"))
    val dishId = dish(entry, "arancini")
    opinion(dishId, ana, Rating.BAD, "bad")

    db.openHelper.writableDatabase.execSQL("DELETE FROM place_entries WHERE id = '$entry'")

    assertNull(db.dishDao().byId(dishId))
    assertEquals(0, db.dishOpinionDao().observeForDish(dishId).first().size)
  }

  @Test
  fun aPersonIsNotScopedToOneList_soAnyListCanCiteThem() = runTest {
    val otherList = newId()
    db
      .placeListDao()
      .insert(PlaceListEntity(id = otherList, name = "Mine", createdAt = now, updatedAt = now))
    val entryInOtherList =
      newId().also {
        db
          .placeEntryDao()
          .insert(
            PlaceEntryEntity(
              id = it,
              placeListId = otherList,
              placeId = place("The Lamplight"),
              createdAt = now,
              updatedAt = now,
            )
          )
      }

    // `ana` was created in the context of the first list; citing her from another must work.
    val dishId = dish(entryInOtherList, "mac and cheese")
    opinion(dishId, ana, Rating.EXCELLENT, "great")

    assertEquals(ana, db.dishOpinionDao().observeForDish(dishId).first().single().authorId)
  }

  @Test
  fun listsDoNotSeeEachOthersDishes() = runTest {
    val shared = placeEntry(place("Cafe Mirabel"))
    val privateList = newId()
    db
      .placeListDao()
      .insert(PlaceListEntity(id = privateList, name = "Mine", createdAt = now, updatedAt = now))
    val privateEntry =
      newId().also {
        db
          .placeEntryDao()
          .insert(
            PlaceEntryEntity(
              id = it,
              placeListId = privateList,
              // deliberately the same restaurant, in a different list
              placeId = db.placeEntryDao().byId(shared)!!.placeId,
              createdAt = now,
              updatedAt = now,
            )
          )
      }
    dish(privateEntry, "a dish only I know about")

    assertEquals(0, db.dishDao().observeForPlaceEntry(shared).first().size)
    assertEquals(1, db.dishDao().observeForPlaceEntry(privateEntry).first().size)
  }

  @Test
  fun softDeletedRowsAreHiddenFromReads() = runTest {
    val entryId = placeEntry(place("Halberd"))
    assertNotNull(db.placeEntryDao().observeForList(listId).first().singleOrNull())

    updateEntry(entryId) { it.copy(deletedAt = later) }

    assertEquals(0, db.placeEntryDao().observeForList(listId).first().size)
    // The row itself survives, because M3 sync has to propagate the tombstone.
    assertNotNull(db.placeEntryDao().byId(entryId))
  }

  // ---- helpers -----------------------------------------------------------------------

  /**
   * Asserts the database rejects [block]. Runs it with `runBlocking` rather than `runTest`: a
   * nested `runTest` fails with IllegalStateException before SQLite is ever reached, which would
   * make these constraint tests pass for the wrong reason.
   */
  private fun assertConstraintViolation(block: suspend () -> Unit) {
    assertThrows(SQLiteConstraintException::class.java) { runBlocking { block() } }
  }

  /** Mirrors what repositories must do: mutate, then stamp [updatedAt]. */
  private suspend fun updateEntry(entryId: String, change: (PlaceEntryEntity) -> PlaceEntryEntity) {
    val current = db.placeEntryDao().byId(entryId)!!
    db.placeEntryDao().update(change(current).copy(updatedAt = later))
  }

  private suspend fun person(name: String, household: Boolean): String =
    newId().also {
      db
        .personDao()
        .insert(
          PersonEntity(
            id = it,
            name = name,
            normalizedName = normalizeDishName(name),
            isHouseholdMember = household,
            createdAt = now,
            updatedAt = now,
          )
        )
    }

  private suspend fun place(name: String, branch: String? = null, warning: String? = null): String =
    newId().also {
      db
        .placeDao()
        .insert(
          PlaceEntity(
            id = it,
            name = name,
            branchLabel = branch,
            warning = warning,
            createdAt = now,
            updatedAt = now,
          )
        )
    }

  private suspend fun placeEntry(placeId: String): String =
    newId().also {
      db
        .placeEntryDao()
        .insert(
          PlaceEntryEntity(
            id = it,
            placeListId = listId,
            placeId = placeId,
            createdAt = now,
            updatedAt = now,
          )
        )
    }

  private suspend fun visit(
    placeEntryId: String,
    epochDay: Long?,
    precision: DatePrecision,
    meal: Meal? = null,
  ): String =
    newId().also {
      db
        .visitDao()
        .insert(
          VisitEntity(
            id = it,
            placeEntryId = placeEntryId,
            dateEpochDay = epochDay,
            datePrecision = precision,
            meal = meal,
            createdAt = now,
            updatedAt = now,
          )
        )
    }

  private suspend fun attend(visitId: String, personId: String) {
    db
      .visitDao()
      .addAttendee(
        VisitAttendeeEntity(
          visitId = visitId,
          personId = personId,
          createdAt = now,
          updatedAt = now,
        )
      )
  }

  private suspend fun dish(
    placeEntryId: String,
    name: String,
    aliases: List<String> = emptyList(),
  ): String =
    newId().also { id ->
      db
        .dishDao()
        .insert(
          DishEntity(
            id = id,
            placeEntryId = placeEntryId,
            canonicalName = name,
            normalizedName = normalizeDishName(name),
            createdAt = now,
            updatedAt = now,
          )
        )
      aliases.forEach { alias ->
        db
          .dishDao()
          .insertAlias(
            DishAliasEntity(
              dishId = id,
              alias = alias,
              normalized = normalizeDishName(alias),
              createdAt = now,
              updatedAt = now,
            )
          )
      }
    }

  private suspend fun interest(
    dishId: String,
    status: DishStatus,
    forPerson: String? = null,
    recommendedBy: String? = null,
    modification: String? = null,
  ): String =
    newId().also {
      db
        .dishInterestDao()
        .insert(
          DishInterestEntity(
            id = it,
            dishId = dishId,
            status = status,
            forPersonId = forPerson,
            recommendedById = recommendedBy,
            modification = modification,
            createdAt = now,
            updatedAt = now,
          )
        )
    }

  private suspend fun opinion(
    dishId: String,
    authorId: String,
    rating: Rating,
    note: String,
  ): String =
    newId().also {
      db
        .dishOpinionDao()
        .insert(
          DishOpinionEntity(
            id = it,
            dishId = dishId,
            authorId = authorId,
            rating = rating,
            note = note,
            createdAt = now,
            updatedAt = now,
          )
        )
    }
}
