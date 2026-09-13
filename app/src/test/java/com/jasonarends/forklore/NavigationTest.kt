package com.jasonarends.forklore

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression coverage for entries sharing a ViewModel: [MainNavigation] must scope each
 * [com.jasonarends.forklore.ui.placedetail.PlaceDetailViewModel] to its own back stack entry, or
 * navigating from one place to another reuses the first place's ViewModel — and its edits land on
 * the wrong row.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationTest {
  @get:Rule val compose = createComposeRule()

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
    compose.setContent { MainNavigation(backStack = backStack) }

    compose.runOnIdle { backStack.add(PlaceDetail(entryA)) }
    compose.onNodeWithText("Note about Halberd").assertExists()

    // Back to the list, then into a different place entry.
    compose.runOnIdle {
      backStack.removeLastOrNull()
      backStack.add(PlaceDetail(entryB))
    }

    // The regression: without per-entry ViewModel scoping, entry A's cached ViewModel is reused
    // and this still shows "Note about Halberd".
    compose.onNodeWithText("Note about Mirabel").assertExists()
  }
}
