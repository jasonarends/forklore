package com.jasonarends.forklore

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.jasonarends.forklore.ui.main.MainScreen
import com.jasonarends.forklore.ui.people.PeopleScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
            onItemClick = { navKey -> backStack.add(navKey) },
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
          )
        }
        entry<People> { PeopleScreen(modifier = Modifier.safeDrawingPadding().padding(16.dp)) }
      },
  )
}
