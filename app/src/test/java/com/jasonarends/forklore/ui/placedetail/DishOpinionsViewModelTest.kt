package com.jasonarends.forklore.ui.placedetail

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.data.db.DishOpinionEntity
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceListEntity
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.data.db.TemperatureRating
import com.jasonarends.forklore.data.db.VisitEntity
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.DishRepository
import com.jasonarends.forklore.data.repository.PersonRepository
import com.jasonarends.forklore.data.repository.VisitRepository
import com.jasonarends.forklore.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A real in-memory Room database, per [VisitsViewModelTest]: what reaches the screen is checked
 * against what was actually written and read back out, not against hand-built state.
 */
@RunWith(RobolectricTestRunner::class)
class DishOpinionsViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  @get:Rule val mainDispatcherRule = MainDispatcherRule(testDispatcher)

  private lateinit var db: ForkloreDatabase
  private lateinit var dishRepository: DishRepository
  private lateinit var viewModel: DishOpinionsViewModel
  private lateinit var dishId: String
  private val entryId = "entry"

  @Before
  fun setUp() =
    runTest(testDispatcher) {
      db =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ForkloreDatabase::class.java,
          )
          .allowMainThreadQueries()
          .setQueryExecutor { it.run() }
          .setTransactionExecutor { it.run() }
          .build()
      // Ticks on every read so cards, which sort by createdAt, come back in the order they were
      // written rather than tie-breaking on random ids.
      var tick = 0L
      val clock = Clock { ++tick }
      dishRepository =
        DishRepository(db.dishDao(), db.dishInterestDao(), db.dishOpinionDao(), clock)

      db
        .placeListDao()
        .insert(PlaceListEntity(id = "list", name = "Ours", createdAt = 0, updatedAt = 0))
      db
        .placeDao()
        .insert(PlaceEntity(id = "place", name = "Halberd", createdAt = 0, updatedAt = 0))
      db
        .placeEntryDao()
        .insert(
          PlaceEntryEntity(
            id = entryId,
            placeListId = "list",
            placeId = "place",
            createdAt = 0,
            updatedAt = 0,
          )
        )
      db.personDao().insert(person("ana", "Ana", household = true, createdAt = 1))
      db.personDao().insert(person("sam", "Sam", household = true, createdAt = 2))
      dishId = dishRepository.findOrCreateDish(entryId, "Arancini")

      // Built here, not in a field initializer, so viewModelScope sees MainDispatcherRule's
      // replacement Main — see VisitsViewModelTest.
      viewModel =
        DishOpinionsViewModel(
          dishRepository,
          PersonRepository(db.personDao(), clock),
          VisitRepository(db, db.visitDao(), clock),
          entryId,
        )
    }

  @After fun tearDown() = db.close()

  private fun TestScope.observing(): DishOpinionsViewModel {
    backgroundScope.launch { viewModel.uiState.collect {} }
    return viewModel
  }

  private fun DishOpinionsViewModel.success() = uiState.value as DishOpinionsUiState.Success

  private fun DishOpinionsViewModel.addOpinion(
    author: String,
    rating: Rating? = null,
    temperature: TemperatureRating? = null,
    note: String = "",
  ) {
    startAdd(dishId)
    onAuthorChange(setOf(author))
    onRatingChange(rating)
    onTemperatureChange(temperature)
    onNoteChange(note)
    save()
  }

  @Test
  fun twoAuthorsRatingOneDish_bothAppearAsCards_andTheDisagreementIsFlagged() =
    runTest(testDispatcher) {
      val vm = observing()

      vm.addOpinion("ana", Rating.BAD, note = "Skip these.")
      vm.addOpinion("sam", Rating.MID, note = "They were fine.")

      val opinions = vm.success().byDish.getValue(dishId)
      assertEquals(listOf("Ana", "Sam"), opinions.cards.map { it.authorName })
      assertEquals(listOf(Rating.BAD, Rating.MID), opinions.cards.map { it.opinion.rating })
      assertEquals(listOf(AuthorTone.First, AuthorTone.Second), opinions.cards.map { it.tone })
      assertTrue(opinions.ratingsDisagree)
      assertFalse(opinions.temperaturesDisagree)
      // What the screen shows is what Room holds, not just what the ViewModel remembers.
      assertEquals(2, db.dishOpinionDao().observeForDish(dishId).first().size)
    }

  @Test
  fun twoAuthorsWhoAgree_areNotFlagged() =
    runTest(testDispatcher) {
      val vm = observing()

      vm.addOpinion("ana", Rating.GOOD)
      vm.addOpinion("sam", Rating.GOOD, note = "Same.")

      assertFalse(vm.success().byDish.getValue(dishId).ratingsDisagree)
    }

  @Test
  fun temperaturesAreJudgedSeparatelyFromRatings() =
    runTest(testDispatcher) {
      val vm = observing()

      vm.addOpinion("ana", Rating.GOOD, TemperatureRating.PHENOMENAL)
      vm.addOpinion("sam", Rating.GOOD, TemperatureRating.LACKING)

      val opinions = vm.success().byDish.getValue(dishId)
      assertFalse(opinions.ratingsDisagree)
      assertTrue(opinions.temperaturesDisagree)
      assertTrue(opinions.anyTemperature)
    }

  @Test
  fun anAuthorContradictingThemselvesAcrossVisits_isNotADisagreement() =
    runTest(testDispatcher) {
      val vm = observing()

      vm.addOpinion("ana", Rating.BAD)
      vm.addOpinion("ana", Rating.GOOD, note = "Better the second time.")

      val opinions = vm.success().byDish.getValue(dishId)
      assertEquals(2, opinions.cards.size)
      assertFalse(opinions.ratingsDisagree)
    }

  @Test
  fun anOpinionWithNoRating_takesNoSideInTheDisagreement() =
    runTest(testDispatcher) {
      val vm = observing()

      vm.addOpinion("ana", Rating.BAD)
      vm.addOpinion("sam", note = "Didn't get to try it.")

      assertFalse(vm.success().byDish.getValue(dishId).ratingsDisagree)
    }

  @Test
  fun aDishWithNoOpinions_hasNoEntry() =
    runTest(testDispatcher) {
      val vm = observing()

      assertNull(vm.success().byDish[dishId])
    }

  @Test
  fun startAdd_opensABlankDraftThatCannotBeSavedYet() =
    runTest(testDispatcher) {
      val vm = observing()

      vm.startAdd(dishId)

      val draft = vm.draft.value!!
      assertEquals(dishId, draft.dishId)
      assertNull(draft.opinionId)
      assertFalse(draft.canSave)
      vm.save()
      assertNotNull("a blank draft must stay open", vm.draft.value)
      assertEquals(0, db.dishOpinionDao().observeForDish(dishId).first().size)
    }

  @Test
  fun aDraft_needsAnAuthor_andAtLeastOneThingSaid_butNoParticularField() =
    runTest(testDispatcher) {
      val vm = observing()
      vm.startAdd(dishId)

      vm.onRatingChange(Rating.GOOD)
      assertFalse("rating but no author", vm.draft.value!!.canSave)

      vm.onAuthorChange(setOf("ana"))
      assertTrue("author + rating", vm.draft.value!!.canSave)

      vm.onRatingChange(null)
      assertFalse("author but nothing said", vm.draft.value!!.canSave)

      vm.onNoteChange("  ")
      assertFalse("a whitespace-only note is nothing said", vm.draft.value!!.canSave)

      vm.onNoteChange("Great crust.")
      assertTrue("a note alone is a complete opinion", vm.draft.value!!.canSave)
    }

  @Test
  fun savingANoteOnlyOpinion_writesNoRatingAndNoTemperature() =
    runTest(testDispatcher) {
      val vm = observing()

      vm.addOpinion("sam", note = "Too greasy for us.")

      val stored = db.dishOpinionDao().observeForDish(dishId).first().single()
      assertNull(stored.rating)
      assertNull(stored.temperature)
      assertEquals("Too greasy for us.", stored.note)
      assertNull(vm.draft.value)
    }

  @Test
  fun editingAnOpinion_prefillsTheDraft_andSavingUpdatesTheSameRow() =
    runTest(testDispatcher) {
      val vm = observing()
      vm.addOpinion("ana", Rating.BAD, TemperatureRating.LACKING, "Skip these.")
      val original = vm.success().byDish.getValue(dishId).cards.single().opinion

      vm.startEdit(original)
      val draft = vm.draft.value!!
      assertEquals(original.id, draft.opinionId)
      assertEquals("ana", draft.authorId)
      assertEquals(Rating.BAD, draft.rating)
      assertEquals(TemperatureRating.LACKING, draft.temperature)
      assertEquals("Skip these.", draft.note)

      vm.onRatingChange(Rating.FINE)
      vm.onTemperatureChange(null)
      vm.save()

      val stored = db.dishOpinionDao().observeForDish(dishId).first().single()
      assertEquals(original.id, stored.id)
      assertEquals(Rating.FINE, stored.rating)
      assertNull(stored.temperature)
      assertEquals("Skip these.", stored.note)
    }

  @Test
  fun editingOneAuthorsOpinion_leavesTheOtherAuthorsUntouched() =
    runTest(testDispatcher) {
      val vm = observing()
      vm.addOpinion("ana", Rating.BAD)
      vm.addOpinion("sam", Rating.MID, note = "Fine.")
      val anas = vm.success().byDish.getValue(dishId).cards.first { it.authorName == "Ana" }.opinion

      vm.startEdit(anas)
      vm.onRatingChange(Rating.EXCELLENT)
      vm.save()

      val stored = db.dishOpinionDao().observeForDish(dishId).first()
      assertEquals(Rating.EXCELLENT, stored.single { it.authorId == "ana" }.rating)
      assertEquals(Rating.MID, stored.single { it.authorId == "sam" }.rating)
      assertEquals("Fine.", stored.single { it.authorId == "sam" }.note)
    }

  @Test
  fun deleting_removesTheCard_butOnlyTombstonesTheRow() =
    runTest(testDispatcher) {
      val vm = observing()
      vm.addOpinion("ana", Rating.BAD)
      vm.addOpinion("sam", Rating.MID, note = "Fine.")
      val anas = vm.success().byDish.getValue(dishId).cards.first { it.authorName == "Ana" }.opinion

      vm.startEdit(anas)
      vm.delete()

      assertNull(vm.draft.value)
      assertEquals(
        listOf("Sam"),
        vm.success().byDish.getValue(dishId).cards.map { it.authorName },
      )
      assertNotNull(db.dishOpinionDao().byId(anas.id)?.deletedAt)
    }

  @Test
  fun delete_onANewDraft_doesNothing() =
    runTest(testDispatcher) {
      val vm = observing()
      vm.startAdd(dishId)

      vm.delete()

      assertNotNull(vm.draft.value)
    }

  @Test
  fun cancelDraft_closesTheFormWithoutWriting() =
    runTest(testDispatcher) {
      val vm = observing()
      vm.startAdd(dishId)
      vm.onAuthorChange(setOf("ana"))
      vm.onNoteChange("never mind")

      vm.cancelDraft()

      assertNull(vm.draft.value)
      assertEquals(0, db.dishOpinionDao().observeForDish(dishId).first().size)
    }

  @Test
  fun aVisitAtThisPlaceEntry_canBeLinked_andShowsUpOnTheCard() =
    runTest(testDispatcher) {
      db
        .visitDao()
        .insert(VisitEntity(id = "v1", placeEntryId = entryId, createdAt = 0, updatedAt = 0))
      val vm = observing()

      vm.startAdd(dishId)
      vm.onAuthorChange(setOf("ana"))
      vm.onRatingChange(Rating.GOOD)
      vm.onVisitChange("v1")
      vm.save()

      assertEquals("v1", vm.success().byDish.getValue(dishId).cards.single().opinion.visitId)
      assertEquals(listOf("v1"), vm.success().visits.map { it.visit.id })
    }

  @Test
  fun aVisitFromAnotherPlaceEntry_isRefused_andTheDraftStaysOpenWithAnError() =
    runTest(testDispatcher) {
      db
        .placeDao()
        .insert(PlaceEntity(id = "place2", name = "Verano", createdAt = 0, updatedAt = 0))
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
      db
        .visitDao()
        .insert(
          VisitEntity(id = "elsewhere", placeEntryId = "entry2", createdAt = 0, updatedAt = 0)
        )
      val vm = observing()

      vm.startAdd(dishId)
      vm.onAuthorChange(setOf("ana"))
      vm.onRatingChange(Rating.GOOD)
      vm.onVisitChange("elsewhere")
      vm.save()

      val draft = vm.draft.value!!
      assertNotNull(draft.error)
      assertFalse("a failed save must not leave Save stuck disabled", draft.saving)
      assertEquals(0, db.dishOpinionDao().observeForDish(dishId).first().size)
    }

  @Test
  fun editingAnOpinionWhoseVisitWasDeleted_dropsTheStaleLink() =
    runTest(testDispatcher) {
      db
        .visitDao()
        .insert(VisitEntity(id = "v1", placeEntryId = entryId, createdAt = 0, updatedAt = 0))
      dishRepository.recordOpinion(dishId, "ana", Rating.GOOD, visitId = "v1")
      db.visitDao().update(db.visitDao().byId("v1")!!.copy(deletedAt = 1))
      val vm = observing()

      vm.startEdit(vm.success().byDish.getValue(dishId).cards.single().opinion)

      assertNull(vm.draft.value!!.visitId)
    }

  @Test
  fun creatingAPersonInTheEditor_makesThemTheAuthor() =
    runTest(testDispatcher) {
      val vm = observing()
      vm.startAdd(dishId)

      vm.onCreatePerson("Dale")

      val dale = db.personDao().byNormalizedNameIncludingDeleted("dale")!!
      assertEquals(dale.id, vm.draft.value!!.authorId)
    }

  // ---- buildDishOpinions: the rules, without a database -------------------------------

  private fun opinion(dish: String, author: String, rating: Rating? = null, note: String = "") =
    DishOpinionEntity(
      dishId = dish,
      authorId = author,
      rating = rating,
      note = note,
      createdAt = 0,
      updatedAt = 0,
    )

  @Test
  fun colours_goToTheFirstTwoPeople_householdFirst_andEveryoneElseIsNeutral() {
    val outsider = person("dale", "Dale", household = false, createdAt = 0)
    val sam = person("sam", "Sam", household = true, createdAt = 5)
    val ana = person("ana", "Ana", household = true, createdAt = 3)
    val third = person("val", "Val", household = true, createdAt = 9)
    val opinions = listOf("dale", "sam", "ana", "val").map { opinion("d", it, Rating.GOOD) }

    val tones =
      buildDishOpinions(opinions, listOf(outsider, sam, ana, third)).getValue("d").cards.associate {
        it.authorName to it.tone
      }

    assertEquals(AuthorTone.First, tones["Ana"])
    assertEquals(AuthorTone.Second, tones["Sam"])
    assertEquals(AuthorTone.Neutral, tones["Val"])
    assertEquals(AuthorTone.Neutral, tones["Dale"])
  }

  @Test
  fun anAuthorNoLongerInThePeopleList_isShownAsSomeone() {
    val built = buildDishOpinions(listOf(opinion("d", "ghost", Rating.GOOD)), emptyList())

    assertEquals("Someone", built.getValue("d").cards.single().authorName)
  }

  @Test
  fun aSingleOpinion_isNeverADisagreement() {
    val built = buildDishOpinions(listOf(opinion("d", "ana", Rating.BAD)), emptyList())

    assertFalse(built.getValue("d").ratingsDisagree)
  }

  @Test
  fun disagreementCaption_namesWhatTheyDisagreeAbout() {
    fun caption(ratings: Boolean, temperatures: Boolean) =
      disagreementCaption(DishOpinions(emptyList(), ratings, temperatures, false))

    assertNull(caption(ratings = false, temperatures = false))
    assertEquals("They disagree on rating", caption(ratings = true, temperatures = false))
    assertEquals("They disagree on temperature", caption(ratings = false, temperatures = true))
    assertEquals(
      "They disagree on rating and temperature",
      caption(ratings = true, temperatures = true),
    )
  }

  private fun person(id: String, name: String, household: Boolean, createdAt: Long) =
    PersonEntity(
      id = id,
      name = name,
      normalizedName = name.lowercase(),
      isHouseholdMember = household,
      createdAt = createdAt,
      updatedAt = createdAt,
    )
}
