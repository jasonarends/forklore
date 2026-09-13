package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Mutually exclusive chips that wrap rather than scroll, so every option is visible at once — a
 * rating scale whose top end is hidden off-screen defeats the point of having one.
 */
@Composable
internal fun <T> ChoiceChips(
  options: List<T>,
  selected: T?,
  onSelect: (T) -> Unit,
  label: (T) -> String,
  modifier: Modifier = Modifier,
  colors: @Composable (T) -> SelectableChipColors = { FilterChipDefaults.filterChipColors() },
) {
  FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    options.forEach { option ->
      FilterChip(
        selected = option == selected,
        onClick = { onSelect(option) },
        label = { Text(label(option)) },
        colors = colors(option),
      )
    }
  }
}
