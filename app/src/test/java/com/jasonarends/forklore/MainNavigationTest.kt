package com.jasonarends.forklore

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.jasonarends.forklore.data.db.DatePrecision
import com.jasonarends.forklore.data.db.DishStatus
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * End-to-end coverage for the real Navigation3 wiring (the real [ForkloreApp], not a fake
 * container). The entryDecorators regression lives at this level and nowhere below it: without
 * per-entry ViewModelStores, the same destination visited twice gets back its stale ViewModel, and
 * two different PlaceDetail entries share one — so edits land on the wrong row.
 */
@RunWith(RobolectricTestRunner::class)
class MainNavigationTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun addPlaceScreen_isUsableMoreThanOncePerAppLaunch() {
    composeTestRule.setContent { ForkloreTheme { MainNavigation() } }

    addAPlace("Halberd")

    // Visit AddPlace a second time in the same app launch.
    composeTestRule.onNodeWithText("Add a place").performClick()

    // Bug (before entryDecorators): the ViewModel from the first visit survives — scoped to the
    // Activity/host rather than this nav entry — so its stale `saved = true` pops this screen
    // straight back to the list instead of showing an empty form.
    composeTestRule.onNodeWithText("Name").assertExists()
    // A fixed pop-avoidance bug alone wouldn't be enough: the same stale ViewModel would also
    // still be holding the first place's typed-in fields.
    composeTestRule.onNode(hasText("Halberd")).assertDoesNotExist()

    finishAddingAPlace("Fifth Avenue Social")

    waitForListEntry("Halberd")
    waitForListEntry("Fifth Avenue Social")
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun navigatingFromOnePlaceEntryToAnother_showsTheSecondEntrysOwnData() = runTest {
    val app = ApplicationProvider.getApplicationContext<ForkloreApp>()
    val repository = app.container.placeRepository
    val listId = app.container.placeListRepository.create("Test list")
    val entryA = repository.addToList(listId, repository.addPlace("Halberd"))
    val entryB = repository.addToList(listId, repository.addPlace("Cafe Mirabel"))
    repository.updateEntry(entryA) { it.copy(note = "Note about Halberd") }
    repository.updateEntry(entryB) { it.copy(note = "Note about Mirabel") }

    val backStack = NavBackStack<NavKey>(Main)
    composeTestRule.setContent { ForkloreTheme { MainNavigation(backStack = backStack) } }

    composeTestRule.runOnIdle { backStack.add(PlaceDetail(entryA)) }
    composeTestRule.waitUntilExactlyOneExists(hasText("Note about Halberd"))

    // Back to the list, then into a different place entry.
    composeTestRule.runOnIdle {
      backStack.removeLastOrNull()
      backStack.add(PlaceDetail(entryB))
    }

    // Real Room background threads mean Compose idling alone doesn't guarantee the second
    // screen's query has landed, so wait for whichever note actually shows rather than asserting
    // immediately after runOnIdle. Either note appearing resolves this quickly — the regression is
    // which one it is, not how long it takes to show up.
    composeTestRule.waitUntilExactlyOneExists(
      hasText("Note about Mirabel") or hasText("Note about Halberd")
    )

    // Checked in this order deliberately: under the bug, this first assertion is the one that
    // fails — and it fails by finding "Note about Halberd" still there, not by timing out.
    composeTestRule.onNodeWithText("Note about Halberd").assertDoesNotExist()
    composeTestRule.onNodeWithText("Note about Mirabel").assertExists()
  }

  @Test
  fun peopleAction_opensThePeopleScreen() {
    composeTestRule.setContent { ForkloreTheme { MainNavigation() } }

    composeTestRule.onNodeWithText("People").performClick()

    // "People" itself can't be the assertion: the top-bar action and PeopleScreen's section header
    // both show it. The empty state is only on PeopleScreen, and a fresh app has no people yet.
    waitUntilIdlingTheMainLooper(timeoutMillis = 5_000) {
      composeTestRule
        .onAllNodesWithText("No one yet. Add someone above.")
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    composeTestRule.onNodeWithText("Add a place").assertDoesNotExist()
  }

  @Test
  fun dishesLink_listsTheCurrentListsInterests_andATapOpensThatPlace() {
    val app = ApplicationProvider.getApplicationContext<ForkloreApp>()
    composeTestRule.setContent { ForkloreTheme { MainNavigation() } }
    waitUntilIdlingTheMainLooper(timeoutMillis = 5_000) {
      app.container.currentPlaceListId.value != null
    }
    val listId = app.container.currentPlaceListId.value!!
    runBlocking {
      val places = app.container.placeRepository
      val dishes = app.container.dishRepository
      val entry = places.addToList(listId, places.addPlace("Halberd"))
      val dish = dishes.findOrCreateDish(entry, "Barrel Potatoes")
      dishes.setInterest(dish, DishStatus.NEVER_AGAIN, modification = "no bread")
    }

    composeTestRule.onNodeWithText("Dishes: want & never again").performClick()
    waitUntilIdlingTheMainLooper(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Barrel Potatoes").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("no bread").assertExists()
    composeTestRule.onNodeWithText("Add a place").assertDoesNotExist()

    composeTestRule.onNodeWithText("Barrel Potatoes").performClick()

    // Only PlaceDetailScreen's top bar carries this subtitle.
    waitUntilIdlingTheMainLooper(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("the receipts").fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun addVisit_opensTheEditorOnItsOwn_withoutTheDishesList() {
    val entry = seedEntry(dish = "Barrel Potatoes")
    val backStack = NavBackStack<NavKey>(Main, PlaceDetail(entry))
    composeTestRule.setContent { ForkloreTheme { MainNavigation(backStack = backStack) } }
    waitForText("Barrel Potatoes")

    composeTestRule.onNodeWithTag("visits-add-button").performScrollTo().performClick()

    waitForText("New visit")
    // The motivating bug: the dishes sat directly under the visit form and read as its menu.
    composeTestRule.onNodeWithText("Barrel Potatoes").assertDoesNotExist()
    composeTestRule.onNodeWithTag("dish-add-button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("visit-save").assertExists()
  }

  @Test
  fun savingAVisit_returnsToPlaceDetail_whereItIsListed() {
    val entry = seedEntry()
    val backStack = NavBackStack<NavKey>(Main, PlaceDetail(entry))
    composeTestRule.setContent { ForkloreTheme { MainNavigation(backStack = backStack) } }
    waitForText("No visits yet.")
    composeTestRule.onNodeWithTag("visits-add-button").performScrollTo().performClick()
    waitForText("New visit")

    typeVisitNote("Verano")
    composeTestRule.onNodeWithTag("visit-save").performScrollTo().performClick()

    waitUntilIdlingTheMainLooper(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("New visit").fetchSemanticsNodes().isEmpty()
    }
    waitForText("Verano")
    composeTestRule.onNodeWithText("No visits yet.").assertDoesNotExist()
    composeTestRule.runOnIdle {
      assertEquals(listOf<NavKey>(Main, PlaceDetail(entry)), backStack.toList())
    }
  }

  @Test
  fun tappingAVisit_opensItForEdit_withItsData() {
    val entry = seedEntry()
    val visit = seedVisit(entry, note = "Loud but good")
    val backStack = NavBackStack<NavKey>(Main, PlaceDetail(entry))
    composeTestRule.setContent { ForkloreTheme { MainNavigation(backStack = backStack) } }
    waitForText("Loud but good")

    composeTestRule.onNodeWithTag("visit-row-$visit").performScrollTo().performClick()

    waitForText("Edit visit")
    composeTestRule.onNodeWithTag("visit-note").assertExists()
    composeTestRule
      .onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("visit-note")))
      .assertTextContains("Loud but good")
    composeTestRule.runOnIdle { assertEquals(VisitEditor(entry, visit), backStack.last()) }
  }

  @Test
  fun leavingTheEditorTwiceDuringItsExitAnimation_neverPopsPlaceDetailToo() {
    val entry = seedEntry()
    val backStack = NavBackStack<NavKey>(Main, PlaceDetail(entry), VisitEditor(entry))
    composeTestRule.setContent { ForkloreTheme { MainNavigation(backStack = backStack) } }
    waitForText("New visit")
    waitUntilIdlingTheMainLooper(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("visit-cancel").fetchSemanticsNodes().isNotEmpty()
    }

    // Two taps landing before the popped editor has left composition (its exit animation): fire the
    // same click action twice in one frame, the way a double-tap can.
    val cancel = composeTestRule.onNodeWithTag("visit-cancel").performScrollTo()
    composeTestRule.runOnUiThread {
      val click = cancel.fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
      click()
      click()
    }
    composeTestRule.waitForIdle()

    composeTestRule.runOnIdle {
      assertEquals(listOf<NavKey>(Main, PlaceDetail(entry)), backStack.toList())
    }
  }

  @Test
  fun doubleTappingAddAVisit_opensOneEditor_notTwoStacked() {
    val entry = seedEntry()
    val backStack = NavBackStack<NavKey>(Main, PlaceDetail(entry))
    composeTestRule.setContent { ForkloreTheme { MainNavigation(backStack = backStack) } }
    waitUntilIdlingTheMainLooper(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("visits-add-button").fetchSemanticsNodes().isNotEmpty()
    }

    // Same-frame double invoke, as above: the second tap lands before PlaceDetail leaves the top.
    val add = composeTestRule.onNodeWithTag("visits-add-button").performScrollTo()
    composeTestRule.runOnUiThread {
      val click = add.fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
      click()
      click()
    }
    composeTestRule.waitForIdle()

    composeTestRule.runOnIdle {
      assertEquals(listOf(Main, PlaceDetail(entry), VisitEditor(entry)), backStack.toList())
    }
  }

  @Test
  fun backWithNoChanges_justPops() {
    val entry = seedEntry()
    val backStack = NavBackStack<NavKey>(Main, PlaceDetail(entry), VisitEditor(entry))
    composeTestRule.setContent { ForkloreTheme { MainNavigation(backStack = backStack) } }
    waitForText("New visit")

    pressBack()

    composeTestRule.runOnIdle { assertEquals(PlaceDetail(entry), backStack.last()) }
    composeTestRule.onNodeWithText("Discard changes?").assertDoesNotExist()
  }

  @Test
  fun backWithUnsavedChanges_asksFirst_andKeepEditingKeepsTheDraft() {
    val entry = seedEntry()
    val backStack = NavBackStack<NavKey>(Main, PlaceDetail(entry), VisitEditor(entry))
    composeTestRule.setContent { ForkloreTheme { MainNavigation(backStack = backStack) } }
    waitForText("New visit")
    typeVisitNote("Verano")

    pressBack()

    composeTestRule.onNodeWithText("Discard changes?").assertExists()
    composeTestRule.runOnIdle { assertEquals(VisitEditor(entry), backStack.last()) }

    composeTestRule.onNodeWithTag("visit-keep-editing").performClick()

    composeTestRule.onNodeWithText("Discard changes?").assertDoesNotExist()
    composeTestRule.runOnIdle { assertEquals(VisitEditor(entry), backStack.last()) }
    composeTestRule.onNodeWithText("Verano").assertExists()
  }

  @Test
  fun backWithUnsavedChanges_thenDiscard_popsWithoutSaving() {
    val entry = seedEntry()
    val backStack = NavBackStack<NavKey>(Main, PlaceDetail(entry), VisitEditor(entry))
    composeTestRule.setContent { ForkloreTheme { MainNavigation(backStack = backStack) } }
    waitForText("New visit")
    typeVisitNote("Verano")

    pressBack()
    composeTestRule.onNodeWithTag("visit-discard").performClick()

    composeTestRule.runOnIdle { assertEquals(PlaceDetail(entry), backStack.last()) }
    waitForText("No visits yet.")
    composeTestRule.onNodeWithText("Verano").assertDoesNotExist()
  }

  @Test
  fun theTopBarBackAndCancel_askTheSameQuestion_whenThereAreUnsavedChanges() {
    val entry = seedEntry()
    val backStack = NavBackStack<NavKey>(Main, PlaceDetail(entry), VisitEditor(entry))
    composeTestRule.setContent { ForkloreTheme { MainNavigation(backStack = backStack) } }
    waitForText("New visit")
    typeVisitNote("Verano")

    composeTestRule.onNodeWithContentDescription("Back").performClick()
    composeTestRule.onNodeWithText("Discard changes?").assertExists()
    composeTestRule.onNodeWithTag("visit-keep-editing").performClick()

    composeTestRule.onNodeWithTag("visit-cancel").performScrollTo().performClick()
    composeTestRule.onNodeWithText("Discard changes?").assertExists()
    composeTestRule.runOnIdle { assertEquals(VisitEditor(entry), backStack.last()) }
  }

  @Test
  fun editingOneVisitAfterAbandoningAnother_neverShowsTheFirstsDraft() {
    val entry = seedEntry()
    val a = seedVisit(entry, note = "Note A")
    val b = seedVisit(entry, note = "Note B")
    val backStack = NavBackStack<NavKey>(Main, PlaceDetail(entry), VisitEditor(entry, a))
    composeTestRule.setContent { ForkloreTheme { MainNavigation(backStack = backStack) } }
    waitForText("Note A")
    typeVisitNote("Note A edited", replace = true)
    composeTestRule.onNodeWithText("Note A edited").assertExists()

    composeTestRule.runOnIdle {
      backStack.removeLastOrNull()
      backStack.add(VisitEditor(entry, b))
    }

    waitForText("Note B")
    composeTestRule.onNodeWithText("Note A edited").assertDoesNotExist()
    composeTestRule.onNodeWithText("Note A").assertDoesNotExist()
  }

  @Test
  fun anAddAfterAnAbandonedEdit_opensFresh_notWithTheEditsDraft() {
    val entry = seedEntry()
    val a = seedVisit(entry, note = "Note A")
    val backStack = NavBackStack<NavKey>(Main, PlaceDetail(entry), VisitEditor(entry, a))
    composeTestRule.setContent { ForkloreTheme { MainNavigation(backStack = backStack) } }
    waitForText("Note A")
    typeVisitNote("Note A edited", replace = true)

    composeTestRule.runOnIdle {
      backStack.removeLastOrNull()
      backStack.add(VisitEditor(entry))
    }

    waitForText("New visit")
    composeTestRule.onNodeWithText("Note A edited").assertDoesNotExist()
    composeTestRule.onNodeWithTag("visit-save").assertExists()
  }

  @Test
  fun anEditorOnTopOfAnotherEditor_keepsEachEntrysOwnDraft() {
    val entryA = seedEntry()
    val entryB = seedEntry(name = "Cafe Mirabel")
    val visitA = seedVisit(entryA, note = "Note A")
    val visitB = seedVisit(entryB, note = "Note B")
    val backStack = NavBackStack<NavKey>(Main, VisitEditor(entryA, visitA))
    composeTestRule.setContent { ForkloreTheme { MainNavigation(backStack = backStack) } }
    waitForText("Note A")
    typeVisitNote("Note A edited", replace = true)

    composeTestRule.runOnIdle { backStack.add(VisitEditor(entryB, visitB)) }
    waitForText("Note B")
    composeTestRule.onNodeWithText("Note A edited").assertDoesNotExist()

    composeTestRule.runOnIdle { backStack.removeLastOrNull() }
    waitForText("Note A edited")
  }

  @Test
  fun addingADish_opensASheet_andAddingClosesItAndListsTheDish() {
    val entry = seedEntry()
    val backStack = NavBackStack<NavKey>(Main, PlaceDetail(entry))
    composeTestRule.setContent { ForkloreTheme { MainNavigation(backStack = backStack) } }
    waitForText("No dishes yet.")
    composeTestRule.onNodeWithTag("dish-query-field").assertDoesNotExist()

    composeTestRule.onNodeWithTag("dish-add-button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("dish-query-field").performTextInput("Burnt Ends")
    composeTestRule.onNodeWithTag("dish-submit").performClick()

    waitUntilIdlingTheMainLooper(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Burnt Ends").fetchSemanticsNodes().isNotEmpty() &&
        composeTestRule.onAllNodesWithTag("dish-query-field").fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithText("Burnt Ends").assertExists()
  }

  private fun seedEntry(name: String = "Halberd", dish: String? = null): String = runBlocking {
    val app = ApplicationProvider.getApplicationContext<ForkloreApp>()
    val places = app.container.placeRepository
    val listId = app.container.placeListRepository.create("Test list")
    val entry = places.addToList(listId, places.addPlace(name))
    if (dish != null) app.container.dishRepository.findOrCreateDish(entry, dish)
    entry
  }

  private fun seedVisit(entry: String, note: String): String = runBlocking {
    val app = ApplicationProvider.getApplicationContext<ForkloreApp>()
    app.container.visitRepository.record(
      placeEntryId = entry,
      dateEpochDay = 20_625,
      datePrecision = DatePrecision.DAY,
      note = note,
    )
  }

  private fun typeVisitNote(text: String, replace: Boolean = false) {
    val note = hasSetTextAction() and hasAnyAncestor(hasTestTag("visit-note"))
    // The title shows before the draft has loaded; the note field only exists once it has.
    waitUntilIdlingTheMainLooper(timeoutMillis = 5_000) {
      composeTestRule.onAllNodes(note).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNode(note).performScrollTo()
    // Typing into a prefilled field lands at the cursor, which starts at the front.
    if (replace) composeTestRule.onNode(note).performTextReplacement(text)
    else composeTestRule.onNode(note).performTextInput(text)
    // The keystroke reaches the ViewModel, whose StateFlow replies on the (paused) main looper.
    shadowOf(Looper.getMainLooper()).idle()
    composeTestRule.waitForIdle()
  }

  private fun waitForText(text: String) {
    waitUntilIdlingTheMainLooper(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun pressBack() {
    shadowOf(Looper.getMainLooper()).idle()
    Espresso.pressBack()
    shadowOf(Looper.getMainLooper()).idle()
    composeTestRule.waitForIdle()
  }

  private fun addAPlace(name: String) {
    composeTestRule.onNodeWithText("Add a place").performClick()
    finishAddingAPlace(name)
  }

  private fun finishAddingAPlace(name: String) {
    composeTestRule.onNodeWithText("Name").performTextInput(name)
    // Save starts disabled until the default list (created asynchronously at app startup)
    // resolves.
    waitUntilIdlingTheMainLooper(timeoutMillis = 5_000) {
      composeTestRule.onAllNodes(hasText("Save") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
    }
    // The form is taller than the test window, so Save is scrolled out of the clipped viewport;
    // an unscrolled performClick() lands off-screen and silently does nothing (see the identical
    // trap in AddPlaceFormTest).
    composeTestRule.onNodeWithText("Save").performScrollTo().performClick()
    // Wait for the pop back to the list screen, not for `name` to appear: while this screen is
    // still up, the Name field's own (matching) editable text would satisfy that immediately.
    waitUntilIdlingTheMainLooper(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Add a place").fetchSemanticsNodes().isNotEmpty()
    }
  }

  /**
   * Robolectric's main looper is paused by default: a coroutine resumed via Dispatchers.Main (e.g.
   * the continuation after a suspend Room call returns on its own executor thread) won't actually
   * run until something pumps that looper. [ComposeContentTestRule.waitUntil] doesn't do this on
   * its own, so real async work that hops back onto Main can hang it forever instead of timing out.
   */
  private fun waitUntilIdlingTheMainLooper(timeoutMillis: Long, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (!condition()) {
      check(System.currentTimeMillis() < deadline) {
        "Condition not satisfied after ${timeoutMillis}ms"
      }
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(5)
    }
  }

  /**
   * Room's invalidation tracking re-queries [PlaceListViewModel]'s Flow on its own background
   * thread, a second hop of real async on top of the save itself, so this can lag the pop back to
   * the list screen by a beat.
   */
  private fun waitForListEntry(name: String) {
    waitUntilIdlingTheMainLooper(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText(name).assertExists()
  }
}
