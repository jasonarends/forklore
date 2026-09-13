package com.jasonarends.forklore.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.jasonarends.forklore.People
import com.jasonarends.forklore.data.db.PlaceEntryWithPlace
import com.jasonarends.forklore.ui.components.EmptyState
import com.jasonarends.forklore.ui.components.PlaceStatusChip
import com.jasonarends.forklore.ui.components.RatingLabel
import com.jasonarends.forklore.ui.theme.ForkloreTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel(factory = MainScreenViewModel.Factory),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text("Forklore") },
        actions = { TextButton(onClick = { onItemClick(People) }) { Text("People") } },
      )
    },
  ) { innerPadding ->
    when (val current = state) {
      MainScreenUiState.Loading ->
        PlaceList(entries = emptyList(), modifier = Modifier.padding(innerPadding))
      is MainScreenUiState.Success ->
        PlaceList(entries = current.entries, modifier = Modifier.padding(innerPadding))
      is MainScreenUiState.Error ->
        Text(
          "Couldn't load your list: ${current.throwable.message}",
          modifier = Modifier.padding(innerPadding),
        )
    }
  }
}

/** Stateless by design: state in, events out. Only the screen-level composable sees a ViewModel. */
@Composable
internal fun PlaceList(entries: List<PlaceEntryWithPlace>, modifier: Modifier = Modifier) {
  Column(modifier) {
    if (entries.isEmpty()) {
      EmptyState("Nothing here yet.")
    } else {
      entries.forEach { entry ->
        Row(
          modifier = Modifier.padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = listOfNotNull(entry.place.name, entry.place.branchLabel).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
          )
          // Food and service are rated apart; a bare rating word would read as an overall verdict.
          entry.entry.foodRating?.let {
            Text(
              "Food:",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RatingLabel(it)
          }
          PlaceStatusChip(entry.entry.status)
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun PlaceListEmptyPreview() {
  ForkloreTheme { PlaceList(entries = emptyList()) }
}
