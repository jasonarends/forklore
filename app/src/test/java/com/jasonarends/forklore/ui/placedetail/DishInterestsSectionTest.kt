package com.jasonarends.forklore.ui.placedetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.jasonarends.forklore.data.db.DishInterestEntity
import com.jasonarends.forklore.data.db.DishStatus
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DishInterestsSectionTest {
  @get:Rule val compose = createComposeRule()

  private val robin =
    PersonEntity(
      name = "Robin",
      normalizedName = "robin",
      isHouseholdMember = true,
      createdAt = 0,
      updatedAt = 0,
    )
  private val dale =
    PersonEntity(
      name = "Dale",
      normalizedName = "dale",
      isHouseholdMember = false,
      createdAt = 0,
      updatedAt = 0,
    )

  private fun interest(status: DishStatus, dishId: String = "dish", note: String = "") =
    DishInterestEntity(dishId = dishId, status = status, note = note, createdAt = 0, updatedAt = 0)

  /**
   * The real screen scrolls; a bare composable in the test window doesn't, and a tap on a node
   * below the fold silently does nothing (see MainNavigationTest's note on the same trap).
   */
  @Composable
  private fun ScrollableHost(content: @Composable () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) { content() }
  }

  /** Every callback recorded, so a test can assert on exactly what the composable emitted. */
  private class Events {
    val log = mutableListOf<String>()
  }

  private fun setContent(
    interests: List<DishInterestEntity>,
    draft: InterestDraft? = null,
    events: Events = Events(),
  ) {
    compose.setContent {
      ForkloreTheme {
        ScrollableHost {
          DishInterests(
            interests = interests,
            people = listOf(robin, dale),
            draft = draft,
            onStartAdd = { events.log += "startAdd" },
            onStartEdit = { events.log += "startEdit ${it.id}" },
            onCancelDraft = { events.log += "cancel" },
            onStatusChange = { events.log += "status $it" },
            onForPersonChange = { events.log += "for $it" },
            onRecommendedByChange = { events.log += "recommendedBy $it" },
            onModificationChange = { events.log += "modification $it" },
            onNoteChange = { events.log += "note $it" },
            onCreateForPerson = { events.log += "createFor $it" },
            onCreateRecommender = { events.log += "createRecommender $it" },
            onSave = { events.log += "save" },
            onRemove = { events.log += "remove" },
          )
        }
      }
    }
  }

  @Test
  fun neverAgain_isLabelledDifferentlyFromWantAndTried_notJustColoured() {
    setContent(
      listOf(
        interest(DishStatus.WANT),
        interest(DishStatus.TRIED),
        interest(DishStatus.NEVER_AGAIN),
      )
    )

    // Each state is announced by its own words, so a screen reader hears the difference too.
    compose.onNodeWithText("Want to try").assertExists()
    compose.onNodeWithText("Tried").assertExists()
    compose.onNodeWithText("Never again").assertExists()
  }

  @Test
  fun anInterest_showsWhoItIsFor_whoRecommendedIt_howToOrderIt_andItsNote() {
    setContent(
      listOf(
        DishInterestEntity(
          dishId = "dish",
          status = DishStatus.WANT,
          forPersonId = robin.id,
          recommendedById = dale.id,
          modification = "add a Chilli bomb",
          note = "ask for extra napkins",
          createdAt = 0,
          updatedAt = 0,
        )
      )
    )

    compose.onNodeWithText("for Robin · recommended by Dale").assertExists()
    compose.onNodeWithText("add a Chilli bomb").assertExists()
    compose.onNodeWithText("ask for extra napkins").assertExists()
  }

  @Test
  fun aBareInterest_rendersNoStrayDetailLines() {
    setContent(listOf(interest(DishStatus.WANT)))

    compose.onNodeWithText("Want to try").assertExists()
    compose.onNodeWithText("for Robin", substring = true).assertDoesNotExist()
    compose.onNodeWithText("recommended by", substring = true).assertDoesNotExist()
  }

  @Test
  fun tappingAdd_andEdit_reportTheirTargets() {
    val existing = interest(DishStatus.WANT)
    val events = Events()
    setContent(listOf(existing), events = events)

    compose.onNodeWithTag("interest-add").performClick()
    compose.onNodeWithTag("interest-edit-${existing.id}").performClick()

    assertEquals(listOf("startAdd", "startEdit ${existing.id}"), events.log)
  }

  @Test
  fun theForm_reachesAllFourOptionalDimensions_andTheStatus() {
    var draft by mutableStateOf<InterestDraft?>(InterestDraft(dishId = "dish"))
    var saved: InterestDraft? = null
    compose.setContent {
      ForkloreTheme {
        ScrollableHost {
          DishInterests(
            interests = emptyList(),
            people = listOf(robin, dale),
            draft = draft,
            onStartAdd = {},
            onStartEdit = {},
            onCancelDraft = {},
            onStatusChange = { draft = draft?.copy(status = it) },
            onForPersonChange = { draft = draft?.copy(forPersonId = it) },
            onRecommendedByChange = { draft = draft?.copy(recommendedById = it) },
            onModificationChange = { draft = draft?.copy(modification = it) },
            onNoteChange = { draft = draft?.copy(note = it) },
            onCreateForPerson = {},
            onCreateRecommender = {},
            onSave = { saved = draft },
            onRemove = {},
          )
        }
      }
    }

    compose.onNodeWithText("Never again").performClick()
    compose
      .onNode(
        hasTestTag("person-picker-chip-${robin.id}") and hasAnyAncestor(hasTestTag("interest-for"))
      )
      .performClick()
    compose
      .onNode(
        hasTestTag("person-picker-chip-${dale.id}") and
          hasAnyAncestor(hasTestTag("interest-recommended-by"))
      )
      .performClick()
    compose.onNodeWithTag("interest-modification").performScrollTo().performTextInput("chopped")
    compose
      .onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("interest-note")))
      .performScrollTo()
      .performTextInput("skip the bread")
    compose.onNodeWithTag("interest-save").performScrollTo().performClick()

    assertEquals(
      InterestDraft(
        dishId = "dish",
        status = DishStatus.NEVER_AGAIN,
        forPersonId = robin.id,
        recommendedById = dale.id,
        modification = "chopped",
        note = "skip the bread",
      ),
      saved,
    )
  }

  @Test
  fun aRecommenderWhoIsntInTheHousehold_isVisibleWithoutDiggingForThem() {
    setContent(interests = emptyList(), draft = InterestDraft(dishId = "dish"))

    compose
      .onNode(
        hasTestTag("person-picker-chip-${dale.id}") and
          hasAnyAncestor(hasTestTag("interest-recommended-by"))
      )
      .assertExists()
    // The "for" picker keeps the household-only default; Dale isn't offered there.
    compose
      .onNode(
        hasTestTag("person-picker-chip-${dale.id}") and hasAnyAncestor(hasTestTag("interest-for"))
      )
      .assertDoesNotExist()
  }

  @Test
  fun deselectingAPerson_clearsTheDimension() {
    val events = Events()
    setContent(
      interests = emptyList(),
      draft = InterestDraft(dishId = "dish", forPersonId = robin.id),
      events = events,
    )

    compose
      .onNode(
        hasTestTag("person-picker-chip-${robin.id}") and hasAnyAncestor(hasTestTag("interest-for"))
      )
      .performClick()

    assertEquals(listOf("for null"), events.log)
  }

  @Test
  fun editingAnExistingInterest_offersRemove_butAddingDoesNot() {
    val existing = interest(DishStatus.NEVER_AGAIN)
    val events = Events()
    setContent(
      interests = listOf(existing),
      draft = InterestDraft.from(existing),
      events = events,
    )

    compose.onNodeWithTag("interest-remove").performScrollTo().performClick()
    assertEquals(listOf("remove"), events.log)
  }

  @Test
  fun addingAnInterest_hasNoRemove() {
    setContent(interests = emptyList(), draft = InterestDraft(dishId = "dish"))

    compose.onNodeWithTag("interest-remove").assertDoesNotExist()
    compose.onNodeWithTag("interest-save").assertExists()
  }

  @Test
  fun theEditor_replacesTheRowBeingEdited_inPlace() {
    val existing = interest(DishStatus.WANT)
    setContent(interests = listOf(existing), draft = InterestDraft.from(existing))

    compose.onNodeWithTag("interest-row-${existing.id}").assertDoesNotExist()
    compose.onNodeWithTag("interest-form").assertExists()
    compose.onNodeWithTag("interest-add").assertDoesNotExist()
  }

  @Test
  fun aFormFailure_isShown() {
    setContent(
      interests = emptyList(),
      draft = InterestDraft(dishId = "dish", error = "Couldn't save this. Try again."),
    )

    compose.onNodeWithTag("interest-error").assertExists()
  }

  @Test
  fun startingAdd_thenSaving_showsTheNewInterestOnceTheHostFeedsItBack() {
    var interests by mutableStateOf(emptyList<DishInterestEntity>())
    var draft by mutableStateOf<InterestDraft?>(null)
    compose.setContent {
      ForkloreTheme {
        ScrollableHost {
          DishInterests(
            interests = interests,
            people = listOf(robin, dale),
            draft = draft,
            onStartAdd = { draft = InterestDraft(dishId = "dish") },
            onStartEdit = {},
            onCancelDraft = { draft = null },
            onStatusChange = { draft = draft?.copy(status = it) },
            onForPersonChange = {},
            onRecommendedByChange = {},
            onModificationChange = { draft = draft?.copy(modification = it) },
            onNoteChange = {},
            onCreateForPerson = {},
            onCreateRecommender = {},
            onSave = {
              val d = draft!!
              interests =
                listOf(
                  DishInterestEntity(
                    dishId = d.dishId,
                    status = d.status,
                    modification = d.modification,
                    createdAt = 0,
                    updatedAt = 0,
                  )
                )
              draft = null
            },
            onRemove = {},
          )
        }
      }
    }

    compose.onNodeWithTag("interest-add").performClick()
    compose.onNodeWithText("Never again").performClick()
    compose.onNodeWithTag("interest-modification").performScrollTo().performTextInput("no bread")
    compose.onNodeWithTag("interest-save").performScrollTo().performClick()

    compose.onNodeWithText("Never again").assertExists()
    compose.onNodeWithText("no bread").assertExists()
    compose.onNodeWithTag("interest-form").assertDoesNotExist()
  }
}
