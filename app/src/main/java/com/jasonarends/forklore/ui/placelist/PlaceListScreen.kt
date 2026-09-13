package com.jasonarends.forklore.ui.placelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jasonarends.forklore.data.db.PlaceEntryWithPlace
import com.jasonarends.forklore.ui.components.EmptyState
import com.jasonarends.forklore.ui.components.PlaceStatusChip
import com.jasonarends.forklore.ui.components.RatingLabel
import com.jasonarends.forklore.ui.theme.ForkloreTheme

@Composable
fun PlaceListScreen(
  onAddPlace: () -> Unit,
  onPlaceClick: (String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: PlaceListViewModel = viewModel(factory = PlaceListViewModel.Factory),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  when (val current = state) {
    PlaceListUiState.Loading ->
      PlaceList(
        entries = emptyList(),
        onAddPlace = onAddPlace,
        onPlaceClick = onPlaceClick,
        modifier = modifier,
      )
    is PlaceListUiState.Success ->
      PlaceList(
        entries = current.entries,
        onAddPlace = onAddPlace,
        onPlaceClick = onPlaceClick,
        modifier = modifier,
      )
    is PlaceListUiState.Error ->
      Text("Couldn't load your list: ${current.throwable.message}", modifier)
  }
}

/** Stateless by design: state in, events out. Only the screen-level composable sees a ViewModel. */
@Composable
internal fun PlaceList(
  entries: List<PlaceEntryWithPlace>,
  onAddPlace: () -> Unit,
  onPlaceClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    Button(onClick = onAddPlace, modifier = Modifier.fillMaxWidth()) { Text("Add a place") }
    if (entries.isEmpty()) {
      EmptyState("Nothing here yet — add the first place you don't want to forget.")
    } else {
      entries.forEach { entry ->
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clickable { onPlaceClick(entry.entry.id) }
              .padding(vertical = 4.dp),
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
  ForkloreTheme { PlaceList(entries = emptyList(), onAddPlace = {}, onPlaceClick = {}) }
}
