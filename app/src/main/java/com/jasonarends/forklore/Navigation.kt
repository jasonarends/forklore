package com.jasonarends.forklore

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.jasonarends.forklore.ui.addplace.AddPlaceScreen
import com.jasonarends.forklore.ui.people.PeopleScreen
import com.jasonarends.forklore.ui.placedetail.PlaceDetailScreen
import com.jasonarends.forklore.ui.placelist.PlaceListScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    // Without these, entries share the host's single ViewModelStore/saved-state holder instead
    // of getting their own: a screen popped off the back stack and pushed again (e.g. AddPlace,
    // visited twice in one app launch) gets back the *same* ViewModel instance, stale state and
    // all, rather than a fresh one.
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
