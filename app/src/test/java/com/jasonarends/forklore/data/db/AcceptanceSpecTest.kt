package com.jasonarends.forklore.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The acceptance spec for M1, derived from the real notes this app replaces.
 *
 * Each test corresponds to one numbered capability those notes demand. This is the definition of
 * done for the data layer: if a note in fixtures/notes-sample.txt cannot be represented by these
 * tables, a test here fails. Add to this file before adding a feature, not after.
 */
@RunWith(RobolectricTestRunner::class)
class AcceptanceSpecTest {
  private lateinit var db: ForkloreDatabase
  private lateinit var listId: String
  private lateinit var holly: String
  private lateinit var jason: String

  private val now = 1_757_000_000_000L

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
    holly = person("Holly", household = true)
    jason = person("Jason", household = true)
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

    val visits = db.visitDao().observeForPlaceEntry(entry).first()

    assertEquals(3, visits.size)
    assertEquals(
      setOf(DatePrecision.DAY, DatePrecision.MONTH, DatePrecision.UNKNOWN),
      visits.map { it.datePrecision }.toSet(),
    )
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

    val visits = db.visitDao().observeForPlaceEntry(entry).first()

    assertEquals(3, visits.size)
    assertEquals(listOf(20_675L, 20_619L, 20_284L), visits.map { it.dateEpochDay })
  }

  // ---- 3. One dish spelled three ways is still one dish -------------------------------

  @Test
  fun dish_resolvesFromAnyRecordedSpelling() = runTest {
    val placeId = place("Halberd")
    val dishId =
      dish(placeId, "barrel potatoes", aliases = listOf("potatoe barrels", "barrel tots"))

    val byCanonical = db.dishDao().findByAnyName(placeId, "barrel potatoes")
    val byTypo = db.dishDao().findByAnyName(placeId, "potatoe barrels")
    val byNickname = db.dishDao().findByAnyName(placeId, "barrel tots")

    assertEquals(dishId, byCanonical?.id)
    assertEquals(dishId, byTypo?.id)
    assertEquals(dishId, byNickname?.id)
    assertEquals(1, db.dishDao().observeForPlace(placeId).first().size)
  }

  // ---- 4. Two people can disagree about the same dish ---------------------------------

  @Test
  fun dish_keepsBothAuthorsOpinions_whenTheyDisagree() = runTest {
    // 'Arancini (bad) ((Sam says they were "fine"))'
    val dishId = dish(place("Cafe Mirabel"), "arancini")
    opinion(dishId, holly, Rating.BAD, "bad")
    opinion(dishId, jason, Rating.FINE, "they were fine")

    val opinions = db.dishOpinionDao().observeForDish(dishId).first()

    assertEquals(2, opinions.size)
    assertEquals(setOf(Rating.BAD, Rating.FINE), opinions.mapNotNull { it.rating }.toSet())
    assertEquals(setOf(holly, jason), opinions.map { it.authorId }.toSet())
  }

  // ---- 5. "Never again" is not the same as "not tried yet" ----------------------------

  @Test
  fun dishInterest_distinguishesNeverAgain_fromWant_andTried() = runTest {
    val placeId = place("Cafe Mirabel")
    interest(dish(placeId, "arancini"), DishStatus.NEVER_AGAIN) // "Skip bread and arancini"
    interest(dish(placeId, "burrata"), DishStatus.WANT) // "What we want- burrata"
    interest(dish(placeId, "lamb meatballs"), DishStatus.TRIED)

    val neverAgain = db.dishInterestDao().observeByStatus(listId, DishStatus.NEVER_AGAIN).first()
    val want = db.dishInterestDao().observeByStatus(listId, DishStatus.WANT).first()

    assertEquals(1, neverAgain.size)
    assertEquals(1, want.size)
    assertEquals(3, db.dishInterestDao().observeForPlace(listId, placeId).first().size)
  }

  // ---- 6. Ratings use the vocabulary people actually wrote ----------------------------

  @Test
  fun rating_spansMid_toLifeChanging() = runTest {
    val placeId = place("Fifth Avenue Social")
    opinion(dish(placeId, "fries"), holly, Rating.MID, "Fries mid")
    opinion(dish(placeId, "mac and chicken"), holly, Rating.PHENOMENAL, "PHENOMENAL")
    opinion(dish(placeId, "carbonara"), holly, Rating.LIFE_CHANGING, "changed our lives")

    val ordered = Rating.entries.toList()

    assertTrue(ordered.indexOf(Rating.MID) < ordered.indexOf(Rating.PHENOMENAL))
    assertTrue(ordered.indexOf(Rating.PHENOMENAL) < ordered.indexOf(Rating.LIFE_CHANGING))
  }

  // ---- 7. Food and service are judged separately --------------------------------------

  @Test
  fun placeEntry_ratesFoodAndServiceIndependently() = runTest {
    // Divine pasta, rude servers — one number cannot hold both.
    val entryId = placeEntry(place("Hotel Brannock"))
    val entry = db.placeEntryDao().byId(entryId)!!
    db
      .placeEntryDao()
      .update(
        entry.copy(
          foodRating = Rating.LIFE_CHANGING,
          serviceRating = Rating.BAD,
          revisitIntent = RevisitIntent.WAIT,
        )
      )

    val updated = db.placeEntryDao().byId(entryId)!!

    assertEquals(Rating.LIFE_CHANGING, updated.foodRating)
    assertEquals(Rating.BAD, updated.serviceRating)
  }

  // ---- 8. A want can belong to one person rather than the whole list ------------------

  @Test
  fun dishInterest_canBeScopedToOnePerson() = runTest {
    val robin = person("Robin", household = true)
    // "robin wants chicken" / "Have Robin get mac and cheese as her side"
    val placeId = place("Fifth Avenue Social")
    interest(dish(placeId, "chicken"), DishStatus.WANT, forPerson = robin)
    interest(dish(placeId, "wedge salad"), DishStatus.WANT)

    val wants = db.dishInterestDao().observeByStatus(listId, DishStatus.WANT).first()

    assertEquals(1, wants.count { it.forPersonId == robin })
    assertEquals(1, wants.count { it.forPersonId == null })
  }

  // ---- 9. A recommendation can be attributed to someone who'll never use the app ------

  @Test
  fun dishInterest_recordsWhoRecommendedIt() = runTest {
    // "Dale and Marvin agrees and recommends chicken pot pie"
    val dale = person("Dale", household = false)
    val dishId = dish(place("Fifth Avenue Social"), "chicken pot pie")
    interest(dishId, DishStatus.WANT, recommendedBy = dale)

    val wants = db.dishInterestDao().observeByStatus(listId, DishStatus.WANT).first()

    assertEquals(dale, wants.single().recommendedById)
    assertEquals(false, db.personDao().byId(dale)!!.isHouseholdMember)
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
    val entry = db.placeEntryDao().byId(entryId)!!
    // "Probably wait to come back"
    db
      .placeEntryDao()
      .update(entry.copy(revisitIntent = RevisitIntent.WAIT, status = PlaceStatus.VISITED))

    assertEquals(RevisitIntent.WAIT, db.placeEntryDao().byId(entryId)!!.revisitIntent)
  }

  // ---- 12. How to order it is part of the want ----------------------------------------

  @Test
  fun dishInterest_keepsOrderingInstructions() = runTest {
    // "Order the barrel potatoes and add a Chilli bomb to it" / "Ruben (chopped)"
    val placeId = place("Halberd")
    interest(dish(placeId, "barrel potatoes"), DishStatus.WANT, modification = "add a Chilli bomb")
    interest(dish(placeId, "reuben"), DishStatus.WANT, modification = "chopped")

    val wants = db.dishInterestDao().observeByStatus(listId, DishStatus.WANT).first()

    assertEquals(
      setOf("add a Chilli bomb", "chopped"),
      wants.mapNotNull { it.modification }.toSet(),
    )
  }

  // ---- 13. Two branches of one restaurant are two places ------------------------------

  @Test
  fun place_disambiguatesBranches() = runTest {
    val kansas = place("Fifth Avenue Social", branch = "Kansas")
    val other = place("Fifth Avenue Social", branch = "Plaza")

    val found = db.placeDao().search("Fifth Avenue").first()

    assertEquals(2, found.size)
    assertEquals(setOf("Kansas", "Plaza"), found.mapNotNull { it.branchLabel }.toSet())
    assertTrue(kansas != other)
  }

  // ---- 14. Free text survives whatever was typed, including truncation ----------------

  @Test
  fun freeText_isPreservedVerbatim() = runTest {
    val messy = "- great for pizza, menu doesn't "
    val placeId = place("Halberd", note = messy)
    val entryId = placeEntry(placeId)
    val entry = db.placeEntryDao().byId(entryId)!!
    db.placeEntryDao().update(entry.copy(note = "Servers are rude\nSERVICE HORRIBLE"))

    assertEquals(messy, db.placeDao().byId(placeId)!!.note)
    assertTrue(db.placeEntryDao().byId(entryId)!!.note.contains("\n"))
  }

  // ---- helpers -----------------------------------------------------------------------

  private suspend fun person(name: String, household: Boolean): String =
    newId().also {
      db
        .personDao()
        .insert(
          PersonEntity(
            id = it,
            placeListId = listId,
            name = name,
            isHouseholdMember = household,
            createdAt = now,
            updatedAt = now,
          )
        )
    }

  private suspend fun place(
    name: String,
    branch: String? = null,
    warning: String? = null,
    note: String = "",
  ): String =
    newId().also {
      db
        .placeDao()
        .insert(
          PlaceEntity(
            id = it,
            name = name,
            branchLabel = branch,
            warning = warning,
            note = note,
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

  private suspend fun dish(
    placeId: String,
    name: String,
    aliases: List<String> = emptyList(),
  ): String =
    newId().also { id ->
      db
        .dishDao()
        .insert(
          DishEntity(
            id = id,
            placeId = placeId,
            canonicalName = name,
            createdAt = now,
            updatedAt = now,
          )
        )
      aliases.forEach { alias ->
        db
          .dishDao()
          .insertAlias(
            DishAliasEntity(dishId = id, alias = alias, normalized = alias.lowercase().trim())
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
            placeListId = listId,
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
