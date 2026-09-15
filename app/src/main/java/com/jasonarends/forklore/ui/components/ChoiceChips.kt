package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Mutually exclusive chips that wrap rather than scroll, so every option is visible at once — a
 * rating scale whose top end is hidden off-screen defeats the point of having one. Renders each
 * option as a [LedgerChip]: outlined unselected, filled ("done" look) when selected — the frozen
 * markup shows even the AVOID/NEVER_AGAIN option as a plain chip inside a picker; the rubber-stamp
 * treatment is reserved for the committed, read-only status badge (see `StatusChip.kt`).
 */
@Composable
internal fun <T> ChoiceChips(
  options: List<T>,
  selected: T?,
  onSelect: (T) -> Unit,
  label: (T) -> String,
  modifier: Modifier = Modifier,
) {
  FlowRow(
    modifier = modifier.selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    options.forEach { option ->
      LedgerChip(
        label = label(option),
        selected = option == selected,
        onClick = { onSelect(option) },
      )
    }
  }
}
