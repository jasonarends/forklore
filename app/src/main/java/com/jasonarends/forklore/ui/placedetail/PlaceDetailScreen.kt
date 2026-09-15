package com.jasonarends.forklore.ui.placedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceEntryWithPlace
import com.jasonarends.forklore.data.db.PlaceStatus
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.data.db.RevisitIntent
import com.jasonarends.forklore.ui.components.EmptyState
import com.jasonarends.forklore.ui.components.LedgerGlyph
import com.jasonarends.forklore.ui.components.LedgerIcon
import com.jasonarends.forklore.ui.components.LedgerTopBar
import com.jasonarends.forklore.ui.components.NoteField
import com.jasonarends.forklore.ui.components.PlaceStatusPicker
import com.jasonarends.forklore.ui.components.RatingPicker
import com.jasonarends.forklore.ui.components.RevisitIntentPicker
import com.jasonarends.forklore.ui.components.SectionHeader
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType
import com.jasonarends.forklore.ui.theme.dashedBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
  placeEntryId: String,
  modifier: Modifier = Modifier,
  onBack: () -> Unit = {},
  viewModel: PlaceDetailViewModel = viewModel(factory = PlaceDetailViewModel.factory(placeEntryId)),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  // The top bar repeats the same name/branch the body heading shows (see PlaceDetail below), so
  // it needs the name before the rest of the screen is ready to render.
  val title =
    (state as? PlaceDetailUiState.Success)?.entry?.let {
      listOfNotNull(it.place.name, it.place.branchLabel).joinToString(" · ")
    } ?: "Place"
  Scaffold(
    modifier = modifier,
    topBar = { LedgerTopBar(title = title, subtitle = "the receipts", onBack = onBack) },
    containerColor = ForkloreTheme.colors.paper,
  ) { innerPadding ->
    when (val current = state) {
      PlaceDetailUiState.Loading -> Unit
      PlaceDetailUiState.NotFound ->
        EmptyState("This place couldn't be found.", Modifier.padding(innerPadding))
      is PlaceDetailUiState.Error ->
        Text(
          "Couldn't load this place: ${current.throwable.message}",
          Modifier.padding(innerPadding),
        )
      is PlaceDetailUiState.Success ->
        PlaceDetail(
          entry = current.entry,
          onStatusChange = viewModel::updateStatus,
          onFoodRatingChange = viewModel::updateFoodRating,
          onServiceRatingChange = viewModel::updateServiceRating,
          onRevisitIntentChange = viewModel::updateRevisitIntent,
          onNoteChange = viewModel::updateNote,
          modifier = Modifier.padding(innerPadding),
        )
    }
  }
}

/**
 * Stateless by design: state in, events out. Only the screen-level composable sees a ViewModel.
 *
 * The large heading repeats the name/branch already shown in the top bar (see [PlaceDetailScreen])
 * — a deliberate "ledger repeats its own header" touch straight from the frozen mockup, not an
 * oversight. It only reads as duplication when both render in the same semantics tree, which
 * happens in the full [PlaceDetailScreen] (never asserted on by exact place name in this codebase's
 * tests) but not when this composable is exercised directly, as most of this file's tests do.
 */
@Composable
internal fun PlaceDetail(
  entry: PlaceEntryWithPlace,
  onStatusChange: (PlaceStatus) -> Unit,
  onFoodRatingChange: (Rating?) -> Unit,
  onServiceRatingChange: (Rating?) -> Unit,
  onRevisitIntentChange: (RevisitIntent?) -> Unit,
  onNoteChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(colors.paper)
        .padding(horizontal = 20.dp)
        .verticalScroll(rememberScrollState())
  ) {
    Text(
      text = listOfNotNull(entry.place.name, entry.place.branchLabel).joinToString(" · "),
      style = ForkloreType.placeNameDetail,
      color = colors.ink,
      modifier = Modifier.padding(top = 14.dp),
    )
    entry.place.address?.let {
      Text(
        text = it,
        style = ForkloreType.branchLabel,
        color = colors.ink2,
        modifier = Modifier.padding(top = 4.dp),
      )
    }
    entry.place.warning?.let { warning -> WarningCard(warning) }

    SectionHeader("Status")
    PlaceStatusPicker(status = entry.entry.status, onStatusChange = onStatusChange)

    // Food and service get their own headers, not a shared "Rating" section: divine pasta and
    // rude servers are two different verdicts and must never read as one. testTag lets tests tell
    // the two pickers apart — their rating labels are otherwise identical text.
    SectionHeader("Food")
    RatingPicker(
      rating = entry.entry.foodRating,
      onRatingChange = onFoodRatingChange,
      modifier = Modifier.testTag("food"),
    )

    SectionHeader("Service")
    RatingPicker(
      rating = entry.entry.serviceRating,
      onRatingChange = onServiceRatingChange,
      modifier = Modifier.testTag("service"),
    )

    SectionHeader("Would we go back?")
    RevisitIntentPicker(intent = entry.entry.revisitIntent, onIntentChange = onRevisitIntentChange)

    // No SectionHeader here: NoteField already carries its own "Note" label, and a second one
    // above it would just be the same word twice.
    NoteField(
      value = entry.entry.note,
      onValueChange = onNoteChange,
      modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
    )
  }
}

/**
 * The dashed-stamp warning card, per issue #15: 2px dashed `stamp` border with a circle-slash icon.
 * The mockup bolds the opening clause via a one-off fixture; a real generic bold-first- clause
 * parser isn't warranted for arbitrary free text, so this renders the warning plainly.
 */
@Composable
private fun WarningCard(warning: String) {
  val colors = ForkloreTheme.colors
  Surface(
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).dashedBorder(colors.stamp, 2.dp, 3.dp),
    shape = RoundedCornerShape(3.dp),
    color = colors.card,
    contentColor = colors.stamp,
  ) {
    Row(modifier = Modifier.padding(11.dp)) {
      LedgerIcon(
        LedgerGlyph.CircleSlash,
        tint = colors.stamp,
        modifier = Modifier.padding(end = 8.dp),
      )
      Text(text = warning, style = ForkloreType.fieldInput, color = colors.stamp)
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun PlaceDetailPopulatedPreview() {
  val place =
    PlaceEntity(
      name = "Hotel Brannock",
      address = "123 Main St",
      warning = "\$27 per person even if you order one thing",
      createdAt = 0,
      updatedAt = 0,
    )
  ForkloreTheme {
    Surface {
      PlaceDetail(
        entry =
          PlaceEntryWithPlace(
            entry =
              PlaceEntryEntity(
                placeListId = "list",
                placeId = place.id,
                status = PlaceStatus.VISITED,
                foodRating = Rating.LIFE_CHANGING,
                serviceRating = Rating.BAD,
                revisitIntent = RevisitIntent.WAIT,
                note = "Servers are rude, food was incredible.",
                createdAt = 0,
                updatedAt = 0,
              ),
            place = place,
          ),
        onStatusChange = {},
        onFoodRatingChange = {},
        onServiceRatingChange = {},
        onRevisitIntentChange = {},
        onNoteChange = {},
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun PlaceDetailEmptyPreview() {
  val place = PlaceEntity(name = "Halberd", createdAt = 0, updatedAt = 0)
  ForkloreTheme {
    Surface {
      PlaceDetail(
        entry =
          PlaceEntryWithPlace(
            entry =
              PlaceEntryEntity(
                placeListId = "list",
                placeId = place.id,
                createdAt = 0,
                updatedAt = 0,
              ),
            place = place,
          ),
        onStatusChange = {},
        onFoodRatingChange = {},
        onServiceRatingChange = {},
        onRevisitIntentChange = {},
        onNoteChange = {},
      )
    }
  }
}
