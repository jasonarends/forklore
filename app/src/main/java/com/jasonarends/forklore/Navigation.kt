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
import com.jasonarends.forklore.ui.main.MainScreen
import com.jasonarends.forklore.ui.placedetail.PlaceDetailScreen

/**
 * [backStack] defaults to a fresh one for real use; tests hoist their own so they can push and pop
 * it directly, exercising the same [NavDisplay] wiring production uses.
 */
@Composable
fun MainNavigation(backStack: NavBackStack<NavKey> = rememberNavBackStack(Main)) {
  NavDisplay(
    backStack = backStack,
    // Without these, every entry's `viewModel(factory = ...)` resolves against one shared
    // ViewModelStore keyed only by class, so navigating from one PlaceEntry to another reuses the
    // first entry's PlaceDetailViewModel instead of creating a fresh one for the new id.
    entryDecorators =
      listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
      ),
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
            onItemClick = { navKey -> backStack.add(navKey) },
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
          )
        }
        entry<PlaceDetail> { key ->
          PlaceDetailScreen(
            placeEntryId = key.placeEntryId,
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
          )
        }
      },
  )
}
