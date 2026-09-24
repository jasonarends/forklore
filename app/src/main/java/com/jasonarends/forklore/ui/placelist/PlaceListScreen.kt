package com.jasonarends.forklore.ui.placelist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jasonarends.forklore.data.db.PlaceEntryWithPlace
import com.jasonarends.forklore.ui.components.EmptyState
import com.jasonarends.forklore.ui.components.LedgerGlyph
import com.jasonarends.forklore.ui.components.LedgerIcon
import com.jasonarends.forklore.ui.components.LedgerOutlinedFullWidthButton
import com.jasonarends.forklore.ui.components.LedgerTopBar
import com.jasonarends.forklore.ui.components.PlaceStatusChip
import com.jasonarends.forklore.ui.components.RatingLabel
import com.jasonarends.forklore.ui.components.UppercaseLabel
import com.jasonarends.forklore.ui.components.allowsDogs
import com.jasonarends.forklore.ui.components.label
import com.jasonarends.forklore.ui.theme.Caveat
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceListScreen(
  onAddPlace: () -> Unit,
  onPlaceClick: (String) -> Unit,
  onPeopleClick: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: PlaceListViewModel = viewModel(factory = PlaceListViewModel.Factory),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  Scaffold(
    modifier = modifier,
    topBar = {
      LedgerTopBar(
        title = "Forklore",
        subtitle = "the whole sordid history",
        action = { PeopleAction(onClick = onPeopleClick) },
      )
    },
    containerColor = ForkloreTheme.colors.paper,
  ) { innerPadding ->
    when (val current = state) {
      PlaceListUiState.Loading ->
        PlaceList(
          entries = emptyList(),
          onAddPlace = onAddPlace,
          onPlaceClick = onPlaceClick,
          modifier = Modifier.padding(innerPadding),
        )
      is PlaceListUiState.Success ->
        PlaceList(
          entries = current.entries,
          onAddPlace = onAddPlace,
          onPlaceClick = onPlaceClick,
          modifier = Modifier.padding(innerPadding),
        )
      is PlaceListUiState.Error ->
        Text(
          "Couldn't load your list: ${current.throwable.message}",
          modifier = Modifier.padding(innerPadding),
        )
    }
  }
}

@Composable
private fun PeopleAction(onClick: () -> Unit) {
  val colors = ForkloreTheme.colors
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(3.dp),
    color = Color.Transparent,
    contentColor = colors.ink,
    border = BorderStroke(1.5.dp, colors.ink),
  ) {
    Text(
      "People",
      style = ForkloreType.button,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
    )
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
  val colors = ForkloreTheme.colors
  // LazyColumn, not Column+forEach: once people can add places the list has to scroll rather
  // than overflow. The add button rides along as a header item rather than living outside the
  // scrollable area.
  LazyColumn(modifier.background(colors.paper).padding(horizontal = 20.dp)) {
    item {
      LedgerOutlinedFullWidthButton(
        text = "Add a place",
        onClick = onAddPlace,
        modifier = Modifier.padding(vertical = 14.dp),
      )
    }
    if (entries.isEmpty()) {
      item { EmptyState("Nothing here yet — add the first place you don't want to forget.") }
    } else {
      items(entries, key = { it.entry.id }) { entry ->
        Column {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable { onPlaceClick(entry.entry.id) }
                .padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            // The paw sits with the name, not among the verdicts on the right: dog policy is a
            // fact about the restaurant. fill = false lets a long name wrap without pushing the
            // paw off the row.
            Row(
              modifier = Modifier.weight(1f),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text =
                  buildAnnotatedString {
                    append(entry.place.name)
                    entry.place.branchLabel?.let { branch ->
                      withStyle(SpanStyle(fontFamily = Caveat, color = colors.ink2)) {
                        append(" · $branch")
                      }
                    }
                  },
                style = ForkloreType.placeNameList,
                color = colors.ink,
                modifier = Modifier.weight(1f, fill = false),
              )
              if (entry.place.dogPolicy.allowsDogs) {
                LedgerIcon(
                  LedgerGlyph.Paw,
                  tint = colors.ink2,
                  size = 18.dp,
                  contentDescription = entry.place.dogPolicy?.label,
                )
              }
            }
            // Food and service are rated apart; a bare rating word would read as an overall
            // verdict, so the list row keeps the "Food:" label even though the mockup's inline
            // rating doesn't show one.
            entry.entry.foodRating?.let {
              UppercaseLabel(
                text = "Food:",
                style = MaterialTheme.typography.labelMedium,
                color = colors.ink2,
              )
              RatingLabel(it)
            }
            PlaceStatusChip(entry.entry.status)
          }
          HorizontalDivider(color = colors.rule, thickness = 1.dp)
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
