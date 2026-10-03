package com.jasonarends.forklore

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.jasonarends.forklore.ui.addplace.AddPlaceScreen
import com.jasonarends.forklore.ui.dishinterests.DishInterestListScreen
import com.jasonarends.forklore.ui.people.PeopleScreen
import com.jasonarends.forklore.ui.placedetail.PlaceDetailScreen
import com.jasonarends.forklore.ui.placelist.PlaceListScreen

/**
 * [backStack] defaults to a fresh one for real use; tests hoist their own so they can push and pop
 * it directly, exercising the same [NavDisplay] wiring production uses.
 */
@Composable
fun MainNavigation(backStack: NavBackStack<NavKey> = rememberNavBackStack(Main)) {
  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    // Without these, every entry's `viewModel(factory = ...)` resolves against one shared
    // ViewModelStore keyed only by class: opening a second PlaceEntry reuses the first one's
    // PlaceDetailViewModel, and AddPlace visited twice gets back its already-saved ViewModel.
    entryDecorators =
      listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
      ),
    entryProvider =
      entryProvider {
        entry<Main> {
          PlaceListScreen(
            onAddPlace = { backStack.add(AddPlace) },
            onPlaceClick = { entryId -> backStack.add(PlaceDetail(entryId)) },
            onPeopleClick = { backStack.add(People) },
            onDishInterestsClick = { backStack.add(DishInterestList) },
            // Every screen now owns its own Scaffold + LedgerTopBar, which needs to render
            // edge-to-edge (paper background, full-width rule) rather than inset by a blanket
            // margin — LedgerTopBar handles the status-bar inset itself.
            modifier = Modifier.fillMaxSize(),
          )
        }
        entry<AddPlace> {
          AddPlaceScreen(
            onSaved = { backStack.removeLastOrNull() },
            onCancel = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize(),
          )
        }
        entry<PlaceDetail> { key ->
          PlaceDetailScreen(
            placeEntryId = key.placeEntryId,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize(),
          )
        }
        entry<DishInterestList> {
          DishInterestListScreen(
            onBack = { backStack.removeLastOrNull() },
            onPlaceClick = { entryId -> backStack.add(PlaceDetail(entryId)) },
            modifier = Modifier.fillMaxSize(),
          )
        }
        entry<People> {
          PeopleScreen(onBack = { backStack.removeLastOrNull() }, modifier = Modifier.fillMaxSize())
        }
      },
  )
}
