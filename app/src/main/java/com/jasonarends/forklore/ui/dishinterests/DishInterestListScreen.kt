package com.jasonarends.forklore.ui.dishinterests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jasonarends.forklore.data.db.DishInterestEntity
import com.jasonarends.forklore.data.db.DishStatus
import com.jasonarends.forklore.data.db.ListedDishInterest
import com.jasonarends.forklore.ui.components.DishStatusChip
import com.jasonarends.forklore.ui.components.EmptyState
import com.jasonarends.forklore.ui.components.InterestSummary
import com.jasonarends.forklore.ui.components.LedgerTopBar
import com.jasonarends.forklore.ui.components.SectionHeader
import com.jasonarends.forklore.ui.components.label
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishInterestListScreen(
  onBack: () -> Unit,
  onPlaceClick: (placeEntryId: String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: DishInterestListViewModel = viewModel(factory = DishInterestListViewModel.Factory),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  Scaffold(
    modifier = modifier,
    topBar = {
      LedgerTopBar(title = "Dishes", subtitle = "what to order, what to skip", onBack = onBack)
    },
    containerColor = ForkloreTheme.colors.paper,
  ) { innerPadding ->
    when (val current = state) {
      DishInterestListUiState.Loading -> Unit
      is DishInterestListUiState.Error ->
        Text(
          "Couldn't load your dishes: ${current.throwable.message}",
          color = ForkloreTheme.colors.stamp,
          modifier = Modifier.padding(innerPadding).padding(20.dp),
        )
      is DishInterestListUiState.Success ->
        DishInterestList(
          want = current.want,
          neverAgain = current.neverAgain,
          onPlaceClick = onPlaceClick,
          modifier = Modifier.padding(innerPadding),
        )
    }
  }
}

/**
 * Stateless: two fixed groups, each row tapping through to the place entry it belongs to. Want
 * first, then never-again; every row carries its own [DishStatusChip] so a never-again dish reads
 * as the tilted stamp even if the row is seen out of its section, and so the status is text, not
 * just position.
 */
@Composable
internal fun DishInterestList(
  want: List<ListedDishInterest>,
  neverAgain: List<ListedDishInterest>,
  onPlaceClick: (placeEntryId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier.background(ForkloreTheme.colors.paper).padding(horizontal = 20.dp).testTag("dish-list")
  ) {
    item { SectionHeader(DishStatus.WANT.label) }
    if (want.isEmpty()) {
      item { EmptyState("Nothing on the want list yet.") }
    } else {
      items(want, key = { it.interest.id }) { ListedRow(it, onPlaceClick) }
    }
    item { SectionHeader(DishStatus.NEVER_AGAIN.label) }
    if (neverAgain.isEmpty()) {
      item { EmptyState("Nothing to skip. Yet.") }
    } else {
      items(neverAgain, key = { it.interest.id }) { ListedRow(it, onPlaceClick) }
    }
  }
}

@Composable
private fun ListedRow(
  listed: ListedDishInterest,
  onPlaceClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  Column(modifier = modifier.testTag("listed-interest-${listed.interest.id}")) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clickable { onPlaceClick(listed.placeEntryId) }
          .padding(vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(text = listed.dishName, style = ForkloreType.dishName, color = colors.ink)
        Text(
          text = listOfNotNull(listed.placeName, listed.branchLabel).joinToString(" · "),
          style = ForkloreType.branchLabel,
          color = colors.ink2,
        )
        InterestSummary(
          forPersonName = listed.forPersonName,
          recommendedByName = listed.recommendedByName,
          modification = listed.interest.modification,
          note = listed.interest.note,
          modifier = Modifier.padding(top = 2.dp),
        )
      }
      DishStatusChip(listed.interest.status)
    }
    HorizontalDivider(color = colors.rule, thickness = 1.dp)
  }
}

@PreviewLightDark
@Composable
private fun DishInterestListPreview() {
  fun listed(dish: String, place: String, status: DishStatus, modification: String? = null) =
    ListedDishInterest(
      interest =
        DishInterestEntity(
          dishId = "d",
          status = status,
          modification = modification,
          createdAt = 0,
          updatedAt = 0,
        ),
      dishName = dish,
      placeEntryId = "e",
      placeName = place,
      branchLabel = null,
      forPersonName = null,
      recommendedByName = null,
    )
  ForkloreTheme {
    Surface {
      DishInterestList(
        want = listOf(listed("Barrel Potatoes", "Halberd", DishStatus.WANT, "add a Chilli bomb")),
        neverAgain = listOf(listed("Arancini", "Cafe Mirabel", DishStatus.NEVER_AGAIN)),
        onPlaceClick = {},
      )
    }
  }
}
