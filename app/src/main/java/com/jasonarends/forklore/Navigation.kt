package com.jasonarends.forklore

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.jasonarends.forklore.ui.addplace.AddPlaceScreen
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
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
          )
        }
        entry<AddPlace> {
          AddPlaceScreen(
            onSaved = { backStack.removeLastOrNull() },
            onCancel = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
          )
        }
        entry<PlaceDetail> { key ->
          PlaceDetailScreen(
            placeEntryId = key.placeEntryId,
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
          )
        }
        entry<People> { PeopleScreen(modifier = Modifier.safeDrawingPadding().padding(16.dp)) }
      },
  )
}
