package com.jasonarends.forklore

import android.os.Looper
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * End-to-end coverage for the real Navigation3 wiring (the real [ForkloreApp], not a fake
 * container) — the level the entryDecorators bug in issue #1's review actually lives at. A
 * ViewModel- or repository-level test can't see it: it's specifically about whether NavDisplay
 * gives each back-stack entry its own ViewModelStore.
 */
@RunWith(RobolectricTestRunner::class)
class MainNavigationTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun addPlaceScreen_isUsableMoreThanOncePerAppLaunch() {
    composeTestRule.setContent { MainNavigation() }

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
