package com.jasonarends.forklore

import android.os.Looper
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import kotlinx.coroutines.test.runTest
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
