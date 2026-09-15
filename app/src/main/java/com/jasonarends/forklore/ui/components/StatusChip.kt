package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.data.db.DishStatus
import com.jasonarends.forklore.data.db.PlaceStatus
import com.jasonarends.forklore.ui.theme.ForkloreTheme

val PlaceStatus.label: String
  get() =
    when (this) {
      PlaceStatus.WANT -> "Want to go"
      PlaceStatus.VISITED -> "Been"
      PlaceStatus.AVOID -> "Avoid"
    }

val DishStatus.label: String
  get() =
    when (this) {
      DishStatus.WANT -> "Want to try"
      DishStatus.TRIED -> "Tried"
      DishStatus.NEVER_AGAIN -> "Never again"
    }

/**
 * How a status reads at a glance. [Warning] is an instruction to the future ("don't order this"),
 * so it must never look like a faded version of [Pending] — it gets the rubber-stamp treatment
 * ([LedgerStamp]), not just a different chip color.
 */
internal enum class StatusTone {
  Pending,
  Done,
  Warning,
}

internal val PlaceStatus.tone: StatusTone
  get() =
    when (this) {
      PlaceStatus.WANT -> StatusTone.Pending
      PlaceStatus.VISITED -> StatusTone.Done
      PlaceStatus.AVOID -> StatusTone.Warning
    }

internal val DishStatus.tone: StatusTone
  get() =
    when (this) {
      DishStatus.WANT -> StatusTone.Pending
      DishStatus.TRIED -> StatusTone.Done
      DishStatus.NEVER_AGAIN -> StatusTone.Warning
    }

@Composable
fun PlaceStatusChip(status: PlaceStatus, modifier: Modifier = Modifier) {
  StatusBadge(status.label, status.tone, modifier)
}

@Composable
fun DishStatusChip(status: DishStatus, modifier: Modifier = Modifier) {
  StatusBadge(status.label, status.tone, modifier)
}

@Composable
fun PlaceStatusPicker(
  status: PlaceStatus,
  onStatusChange: (PlaceStatus) -> Unit,
  modifier: Modifier = Modifier,
) {
  ChoiceChips(
    options = PlaceStatus.entries,
    selected = status,
    onSelect = onStatusChange,
    label = { it.label },
    modifier = modifier,
  )
}

@Composable
fun DishStatusPicker(
  status: DishStatus,
  onStatusChange: (DishStatus) -> Unit,
  modifier: Modifier = Modifier,
) {
  ChoiceChips(
    options = DishStatus.entries,
    selected = status,
    onSelect = onStatusChange,
    label = { it.label },
    modifier = modifier,
  )
}

/**
 * The committed, read-only status badge — [StatusTone.Warning] is a stamp, the others are plain
 * chips (pending outlined with a bookmark, done filled with a check).
 */
@Composable
private fun StatusBadge(label: String, tone: StatusTone, modifier: Modifier) {
  when (tone) {
    StatusTone.Warning -> LedgerStamp(label, modifier)
    StatusTone.Pending ->
      LedgerChip(
        label,
        selected = false,
        onClick = null,
        modifier = modifier,
        icon = LedgerGlyph.Bookmark,
      )
    StatusTone.Done ->
      LedgerChip(
        label,
        selected = true,
        onClick = null,
        modifier = modifier,
        icon = LedgerGlyph.Check,
      )
  }
}

@PreviewLightDark
@Composable
private fun StatusChipPreview() {
  ForkloreTheme {
    Surface {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          PlaceStatus.entries.forEach { PlaceStatusChip(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DishStatus.entries.forEach { DishStatusChip(it) }
        }
        PlaceStatusPicker(status = PlaceStatus.AVOID, onStatusChange = {})
        DishStatusPicker(status = DishStatus.NEVER_AGAIN, onStatusChange = {})
      }
    }
  }
}
