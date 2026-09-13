package com.jasonarends.forklore.ui.placedetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.jasonarends.forklore.ui.components.NoteField
import com.jasonarends.forklore.ui.components.PlaceStatusPicker
import com.jasonarends.forklore.ui.components.RatingPicker
import com.jasonarends.forklore.ui.components.RevisitIntentPicker
import com.jasonarends.forklore.ui.components.SectionHeader
import com.jasonarends.forklore.ui.theme.ForkloreTheme

@Composable
fun PlaceDetailScreen(
  placeEntryId: String,
  modifier: Modifier = Modifier,
  viewModel: PlaceDetailViewModel = viewModel(factory = PlaceDetailViewModel.factory(placeEntryId)),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  when (val current = state) {
    PlaceDetailUiState.Loading -> Unit
    PlaceDetailUiState.NotFound -> EmptyState("This place couldn't be found.", modifier)
    is PlaceDetailUiState.Error ->
      Text("Couldn't load this place: ${current.throwable.message}", modifier)
    is PlaceDetailUiState.Success ->
      PlaceDetail(
        entry = current.entry,
        onStatusChange = viewModel::updateStatus,
        onFoodRatingChange = viewModel::updateFoodRating,
        onServiceRatingChange = viewModel::updateServiceRating,
        onRevisitIntentChange = viewModel::updateRevisitIntent,
        onNoteChange = viewModel::updateNote,
        modifier = modifier,
      )
  }
}

/** Stateless by design: state in, events out. Only [PlaceDetailScreen] sees a ViewModel. */
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
  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    Text(
      text = listOfNotNull(entry.place.name, entry.place.branchLabel).joinToString(" · "),
      style = MaterialTheme.typography.headlineSmall,
    )
    entry.place.address?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    entry.place.warning?.let { warning ->
      Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
      ) {
        Text(
          text = warning,
          modifier = Modifier.padding(12.dp),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }

    SectionHeader("Status")
    PlaceStatusPicker(status = entry.entry.status, onStatusChange = onStatusChange)

    // Food and service get their own headers, not a shared "Rating" section: divine pasta and
    // rude servers are two different verdicts and must never read as one.
    SectionHeader("Food")
    RatingPicker(rating = entry.entry.foodRating, onRatingChange = onFoodRatingChange)

    SectionHeader("Service")
    RatingPicker(rating = entry.entry.serviceRating, onRatingChange = onServiceRatingChange)

    SectionHeader("Would we go back?")
    RevisitIntentPicker(intent = entry.entry.revisitIntent, onIntentChange = onRevisitIntentChange)

    // No SectionHeader here: NoteField already carries its own "Note" label, and a second one
    // above it would just be the same word twice.
    NoteField(
      value = entry.entry.note,
      onValueChange = onNoteChange,
      modifier = Modifier.padding(top = 16.dp),
    )
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
