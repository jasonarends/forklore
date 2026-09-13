package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
 * so it must never look like a faded version of [Pending].
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
  StatusChip(status.label, status.tone, modifier)
}

@Composable
fun DishStatusChip(status: DishStatus, modifier: Modifier = Modifier) {
  StatusChip(status.label, status.tone, modifier)
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
    colors = { toneChipColors(it.tone) },
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
    colors = { toneChipColors(it.tone) },
  )
}

@Composable
private fun StatusChip(label: String, tone: StatusTone, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  // Pending is outlined and Warning is filled, so they differ in shape as well as colour and stay
  // distinguishable to someone who can't tell the colours apart.
  val (container, content) =
    when (tone) {
      StatusTone.Pending -> Color.Transparent to colors.onSurfaceVariant
      StatusTone.Done -> colors.secondaryContainer to colors.onSecondaryContainer
      StatusTone.Warning -> colors.errorContainer to colors.onErrorContainer
    }
  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.small,
    color = container,
    contentColor = content,
    border = if (tone == StatusTone.Pending) BorderStroke(1.dp, colors.outline) else null,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = if (tone == StatusTone.Warning) FontWeight.Bold else null,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Composable
private fun toneChipColors(tone: StatusTone): SelectableChipColors =
  if (tone == StatusTone.Warning)
    FilterChipDefaults.filterChipColors(
      selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
      selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
    )
  else FilterChipDefaults.filterChipColors()

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
