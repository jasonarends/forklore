package com.jasonarends.forklore

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.jasonarends.forklore.ui.addplace.AddPlaceScreen
import com.jasonarends.forklore.ui.placedetail.PlaceDetailScreen
import com.jasonarends.forklore.ui.placelist.PlaceListScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          PlaceListScreen(
            onAddPlace = { backStack.add(AddPlace) },
            onPlaceClick = { entryId -> backStack.add(PlaceDetail(entryId)) },
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
      },
  )
}
