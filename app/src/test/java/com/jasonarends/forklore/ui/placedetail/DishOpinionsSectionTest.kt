package com.jasonarends.forklore.ui.placedetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.jasonarends.forklore.data.db.VisitWithAttendees
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.DishRepository
import com.jasonarends.forklore.data.repository.PersonRepository
import com.jasonarends.forklore.data.repository.VisitRepository
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Tall on purpose: the form is longer than Robolectric's default screen, and a tap on a node below
// the fold injects nothing rather than scrolling to it.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h4000dp")
class DishOpinionsSectionTest {
  @get:Rule val compose = createComposeRule()

  private var db: ForkloreDatabase? = null

  @After fun tearDown() = db?.close() ?: Unit

  private val ana =
    PersonEntity(
      id = "ana",
      name = "Ana",
      normalizedName = "ana",
      isHouseholdMember = true,
      createdAt = 1,
      updatedAt = 1,
    )
  private val sam =
    PersonEntity(
      id = "sam",
      name = "Sam",
      normalizedName = "sam",
      isHouseholdMember = true,
      createdAt = 2,
      updatedAt = 2,
    )

  private fun opinion(
    author: PersonEntity,
    rating: Rating? = null,
    temperature: TemperatureRating? = null,
    note: String = "",
    visitId: String? = null,
  ) =
    DishOpinionEntity(
      dishId = "dish",
      authorId = author.id,
      rating = rating,
      temperature = temperature,
      note = note,
      visitId = visitId,
      createdAt = 0,
      updatedAt = 0,
    )

  private fun success(
    opinions: List<DishOpinionEntity>,
    visits: List<VisitWithAttendees> = emptyList(),
  ) =
    DishOpinionsUiState.Success(
      byDish = buildDishOpinions(opinions, listOf(ana, sam)),
      people = listOf(ana, sam),
      visits = visits,
    )

  private fun setBlock(
    state: DishOpinionsUiState,
    draft: OpinionDraft? = null,
    onStartAdd: (String) -> Unit = {},
    onStartEdit: (DishOpinionEntity) -> Unit = {},
    onAuthorChange: (Set<String>) -> Unit = {},
    onRatingChange: (Rating?) -> Unit = {},
    onTemperatureChange: (TemperatureRating?) -> Unit = {},
    onNoteChange: (String) -> Unit = {},
    onVisitChange: (String?) -> Unit = {},
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
  ) {
    compose.setContent {
      ForkloreTheme {
        DishOpinionsBlock(
          dishId = "dish",
          state = state,
          draft = draft,
          onStartAdd = onStartAdd,
          onStartEdit = onStartEdit,
          onCancelDraft = {},
          onAuthorChange = onAuthorChange,
          onRatingChange = onRatingChange,
          onTemperatureChange = onTemperatureChange,
          onNoteChange = onNoteChange,
          onVisitChange = onVisitChange,
          onCreatePerson = {},
          onSave = onSave,
          onDelete = onDelete,
        )
      }
    }
  }

  @Test
  fun twoAuthorsWhoDisagree_bothCardsShow_attributed_andTheDisagreementIsSaid() {
    val anas = opinion(ana, Rating.BAD, note = "Skip these.")
    val sams = opinion(sam, Rating.MID, note = "They were fine, honestly.")
    setBlock(success(listOf(anas, sams)))

    compose.onNodeWithTag("opinion-card-${anas.id}").assertExists()
    compose.onNodeWithTag("opinion-card-${sams.id}").assertExists()
    compose
      .onNode(hasText("Ana") and hasAnyAncestor(hasTestTag("opinion-card-${anas.id}")))
      .assertExists()
    compose
      .onNode(hasText("Bad") and hasAnyAncestor(hasTestTag("opinion-card-${anas.id}")))
      .assertExists()
    compose
      .onNode(hasText("Skip these.") and hasAnyAncestor(hasTestTag("opinion-card-${anas.id}")))
      .assertExists()
    compose
      .onNode(hasText("Sam") and hasAnyAncestor(hasTestTag("opinion-card-${sams.id}")))
      .assertExists()
    compose
      .onNode(hasText("Mid") and hasAnyAncestor(hasTestTag("opinion-card-${sams.id}")))
      .assertExists()
    compose.onNodeWithText("They were fine, honestly.").assertExists()
    compose.onNodeWithText("They disagree on rating").assertExists()
  }

  @Test
  fun twoAuthorsWhoAgree_showBothCards_withoutClaimingADisagreement() {
    setBlock(success(listOf(opinion(ana, Rating.GOOD), opinion(sam, Rating.GOOD))))

    compose.onAllNodesWithText("Good").assertCountEquals(2)
    compose.onNodeWithTag("opinions-disagree-dish").assertDoesNotExist()
  }

  @Test
  fun temperaturesThatDiffer_bothShow_andAreNamedAsTheDisagreement() {
    val hot = opinion(ana, Rating.GOOD, TemperatureRating.PHENOMENAL)
    val cold = opinion(sam, Rating.GOOD, TemperatureRating.LACKING)
    setBlock(success(listOf(hot, cold)))

    compose
      .onNode(hasText("Phenomenal") and hasAnyAncestor(hasTestTag("opinion-card-${hot.id}")))
      .assertExists()
    compose
      .onNode(hasText("Lacking") and hasAnyAncestor(hasTestTag("opinion-card-${cold.id}")))
      .assertExists()
    compose.onNodeWithText("They disagree on temperature").assertExists()
  }

  @Test
  fun aCardWithNoTemperature_saysSo_onlyOnADishWhereSomeoneRecordedOne() {
    val withTemp = opinion(ana, Rating.GOOD, TemperatureRating.ADEQUATE)
    val without = opinion(sam, Rating.GOOD)
    setBlock(success(listOf(withTemp, without)))

    compose
      .onNode(hasText("not said") and hasAnyAncestor(hasTestTag("opinion-card-${without.id}")))
      .assertExists()
    compose.onAllNodesWithText("Temp").assertCountEquals(2)
  }

  @Test
  fun aDishNobodyRecordedATemperatureFor_hasNoTemperatureRowAtAll() {
    setBlock(success(listOf(opinion(ana, Rating.GOOD), opinion(sam, Rating.GOOD))))

    compose.onAllNodesWithText("Temp").assertCountEquals(0)
    compose.onAllNodesWithText("not said").assertCountEquals(0)
  }

  @Test
  fun aNoteOnlyOpinion_readsAsDeliberatelyUnrated_notBroken() {
    val noteOnly = opinion(sam, note = "Too greasy for us.")
    setBlock(success(listOf(noteOnly)))

    compose
      .onNode(hasText("Not rated") and hasAnyAncestor(hasTestTag("opinion-card-${noteOnly.id}")))
      .assertExists()
    compose.onNodeWithText("Too greasy for us.").assertExists()
  }

  @Test
  fun anOpinionLinkedToAVisit_namesIt() {
    val visit =
      VisitWithAttendees(
        visit =
          VisitEntity(
            id = "v1",
            placeEntryId = "entry",
            dateEpochDay = 20_625L,
            datePrecision = com.jasonarends.forklore.data.db.DatePrecision.DAY,
            meal = com.jasonarends.forklore.data.db.Meal.DINNER,
            createdAt = 0,
            updatedAt = 0,
          ),
        attendees = emptyList(),
      )
    setBlock(success(listOf(opinion(ana, Rating.GOOD, visitId = "v1")), listOf(visit)))

    compose.onNodeWithText("from 6/21/26 · Dinner").assertExists()
  }

  @Test
  fun tappingACard_startsEditingThatOpinion() {
    val anas = opinion(ana, Rating.BAD)
    var edited: DishOpinionEntity? = null
    setBlock(success(listOf(anas, opinion(sam, Rating.MID))), onStartEdit = { edited = it })

    compose.onNodeWithTag("opinion-card-${anas.id}").performClick()

    assertEquals(anas.id, edited?.id)
  }

  @Test
  fun addAnOpinion_isOfferedOnlyWhereNoOtherDraftIsOpen() {
    var started: String? = null
    setBlock(success(emptyList()), onStartAdd = { started = it })

    compose.onNodeWithTag("opinion-add-dish").performClick()

    assertEquals("dish", started)
  }

  @Test
  fun whileADraftIsOpenOnAnotherDish_thisDishOffersNeitherAddNorEdit() {
    val anas = opinion(ana, Rating.BAD)
    var edited: DishOpinionEntity? = null
    setBlock(
      success(listOf(anas)),
      draft = OpinionDraft(dishId = "some-other-dish"),
      onStartEdit = { edited = it },
    )

    compose.onNodeWithTag("opinion-add-dish").assertDoesNotExist()
    compose.onNodeWithTag("opinion-card-${anas.id}").performClick()
    assertNull(edited)
  }

  @Test
  fun aFailedOpinionsLoad_showsTheFailure_notAnEmptyDish() {
    setBlock(DishOpinionsUiState.Error(RuntimeException("disk full")))

    compose.onNodeWithText("Couldn't load opinions: disk full").assertExists()
  }

  // ---- The form -------------------------------------------------------------------------

  @Test
  fun theForm_reportsEachChoice_withRatingAndTemperaturePickersToldApart() {
    var author: Set<String>? = null
    var rating: Rating? = null
    var temperature: TemperatureRating? = null
    var note = ""
    setBlock(
      success(emptyList()),
      draft = OpinionDraft(dishId = "dish"),
      onAuthorChange = { author = it },
      onRatingChange = { rating = it },
      onTemperatureChange = { temperature = it },
      onNoteChange = { note = it },
    )

    compose.onNodeWithTag("person-picker-chip-sam").performClick()
    // "Mid" appears in both scales; the testTag says which picker was tapped.
    compose
      .onNode(hasText("Mid") and hasAnyAncestor(hasTestTag("opinion-temperature")))
      .performClick()
    compose.onNode(hasText("Good") and hasAnyAncestor(hasTestTag("opinion-rating"))).performClick()
    compose
      .onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("opinion-note")))
      .performTextInput("Crisp outside")

    assertEquals(setOf("sam"), author)
    assertEquals(TemperatureRating.MID, temperature)
    assertEquals(Rating.GOOD, rating)
    assertEquals("Crisp outside", note)
  }

  @Test
  fun theForm_savesOnlyOnceThereIsAnAuthorAndSomethingSaid() {
    setBlock(success(emptyList()), draft = OpinionDraft(dishId = "dish"))
    compose.onNodeWithTag("opinion-save").assertIsNotEnabled()
    compose.onNodeWithTag("opinion-hint").assertExists()
  }

  @Test
  fun theForm_withAnAuthorAndANote_canSave_andReportsIt() {
    var saved = false
    setBlock(
      success(emptyList()),
      draft = OpinionDraft(dishId = "dish", authorId = "ana", note = "Great crust."),
      onSave = { saved = true },
    )

    compose.onNodeWithTag("opinion-save").assertIsEnabled().performClick()

    assertEquals(true, saved)
  }

  @Test
  fun theForm_showsTheDraftsCurrentChoices() {
    setBlock(
      success(emptyList()),
      draft =
        OpinionDraft(
          dishId = "dish",
          authorId = "sam",
          rating = Rating.FINE,
          temperature = TemperatureRating.LACKING,
        ),
    )

    compose.onNodeWithTag("person-picker-chip-sam").assertIsSelected()
    compose
      .onNode(hasText("Fine") and hasAnyAncestor(hasTestTag("opinion-rating")))
      .assertIsSelected()
    compose
      .onNode(hasText("Lacking") and hasAnyAncestor(hasTestTag("opinion-temperature")))
      .assertIsSelected()
  }

  @Test
  fun theForm_onlyOffersThisEntrysVisits_andTappingOneLinksIt() {
    val visit =
      VisitWithAttendees(
        visit = VisitEntity(id = "v1", placeEntryId = "entry", createdAt = 0, updatedAt = 0),
        attendees = emptyList(),
      )
    var linked: String? = "unset"
    setBlock(
      success(emptyList(), listOf(visit)),
      draft = OpinionDraft(dishId = "dish", authorId = "ana", note = "x"),
      onVisitChange = { linked = it },
    )

    compose.onNodeWithTag("opinion-visit-v1").performClick()

    assertEquals("v1", linked)
  }

  @Test
  fun theForm_tappingTheLinkedVisitAgain_unlinksIt() {
    val visit =
      VisitWithAttendees(
        visit = VisitEntity(id = "v1", placeEntryId = "entry", createdAt = 0, updatedAt = 0),
        attendees = emptyList(),
      )
    var linked: String? = "unset"
    setBlock(
      success(emptyList(), listOf(visit)),
      draft = OpinionDraft(dishId = "dish", authorId = "ana", note = "x", visitId = "v1"),
      onVisitChange = { linked = it },
    )

    compose.onNodeWithTag("opinion-visit-v1").performClick()

    assertNull(linked)
  }

  @Test
  fun theForm_offersNoVisitPicker_whenThereAreNoVisits() {
    setBlock(success(emptyList()), draft = OpinionDraft(dishId = "dish"))

    compose.onNodeWithText("From which visit?").assertDoesNotExist()
  }

  @Test
  fun deleteIsOnlyOfferedWhenEditingAnExistingOpinion() {
    var deleted = false
    setBlock(
      success(emptyList()),
      draft = OpinionDraft(dishId = "dish", opinionId = "o1", authorId = "ana", note = "x"),
      onDelete = { deleted = true },
    )

    compose.onNodeWithTag("opinion-delete").performClick()

    assertEquals(true, deleted)
  }

  @Test
  fun aNewOpinionsForm_hasNoDelete() {
    setBlock(success(emptyList()), draft = OpinionDraft(dishId = "dish", authorId = "ana"))

    compose.onNodeWithTag("opinion-delete").assertDoesNotExist()
  }

  // ---- The primary path, end to end -----------------------------------------------------

  /**
   * Issue #8's "done when", through the real ViewModel, repository and a real in-memory database:
   * two people rate one dish, and after both saves both cards are on screen with neither having
   * overwritten the other.
   */
  @Test
  fun twoAuthorsRateOneDish_andBothCardsShow() {
    val database =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          ForkloreDatabase::class.java,
        )
        .allowMainThreadQueries()
        .setQueryExecutor { it.run() }
        .setTransactionExecutor { it.run() }
        .build()
    db = database
    val clock = Clock { 0L }
    val dishRepository =
      DishRepository(
        database.dishDao(),
        database.dishInterestDao(),
        database.dishOpinionDao(),
        clock,
      )
    val dishId = runBlocking {
      database
        .placeListDao()
        .insert(PlaceListEntity(id = "list", name = "Ours", createdAt = 0, updatedAt = 0))
      database
        .placeDao()
        .insert(PlaceEntity(id = "place", name = "Halberd", createdAt = 0, updatedAt = 0))
      database
        .placeEntryDao()
        .insert(
          PlaceEntryEntity(
            id = "entry",
            placeListId = "list",
            placeId = "place",
            createdAt = 0,
            updatedAt = 0,
          )
        )
      database.personDao().insert(ana)
      database.personDao().insert(sam)
      dishRepository.findOrCreateDish("entry", "Arancini")
    }
    val viewModel =
      DishOpinionsViewModel(
        dishRepository,
        PersonRepository(database.personDao(), clock),
        VisitRepository(database, database.visitDao(), clock),
        "entry",
      )

    compose.setContent {
      ForkloreTheme { WiredOpinions(dishId = dishId, viewModel = viewModel) }
    }

    fun record(authorId: String, rating: String, note: String) {
      compose.waitUntil {
        compose.onAllNodesWithTag("opinion-add-$dishId").fetchSemanticsNodes().isNotEmpty()
      }
      compose.onNodeWithTag("opinion-add-$dishId").performClick()
      compose.onNodeWithTag("person-picker-chip-$authorId").performClick()
      compose
        .onNode(hasText(rating) and hasAnyAncestor(hasTestTag("opinion-rating")))
        .performClick()
      compose
        .onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("opinion-note")))
        .performTextInput(note)
      compose.onNodeWithTag("opinion-save").performClick()
      compose.waitForIdle()
    }
    record("ana", "Bad", "Skip these.")
    record("sam", "Mid", "They were fine, honestly.")

    compose.onNodeWithText("Skip these.").assertExists()
    compose.onNodeWithText("They were fine, honestly.").assertExists()
    compose.onNodeWithText("They disagree on rating").assertExists()
    val stored = runBlocking { database.dishOpinionDao().observeForDish(dishId).first() }
    assertEquals(setOf("ana", "sam"), stored.map { it.authorId }.toSet())
    assertEquals(setOf(Rating.BAD, Rating.MID), stored.map { it.rating }.toSet())
  }

  @Composable
  private fun WiredOpinions(dishId: String, viewModel: DishOpinionsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    DishOpinionsBlock(
      dishId = dishId,
      state = state,
      draft = draft,
      onStartAdd = viewModel::startAdd,
      onStartEdit = viewModel::startEdit,
      onCancelDraft = viewModel::cancelDraft,
      onAuthorChange = viewModel::onAuthorChange,
      onRatingChange = viewModel::onRatingChange,
      onTemperatureChange = viewModel::onTemperatureChange,
      onNoteChange = viewModel::onNoteChange,
      onVisitChange = viewModel::onVisitChange,
      onCreatePerson = viewModel::onCreatePerson,
      onSave = viewModel::save,
      onDelete = viewModel::delete,
    )
  }
}
