package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.data.db.DatePrecision
import com.jasonarends.forklore.ui.theme.ForkloreTheme

val DatePrecision.label: String
  get() =
    when (this) {
      DatePrecision.DAY -> "Exact date"
      DatePrecision.MONTH -> "Month only"
      DatePrecision.YEAR -> "Year only"
      DatePrecision.UNKNOWN -> "No date"
    }

/**
 * Issue #5: the source notes carry "July 2026", "7/21/26" and nothing at all, so this offers only
 * [DatePrecision.DAY], [DatePrecision.MONTH] and [DatePrecision.UNKNOWN] — a required day-precise
 * picker would invent precision nobody has. [DatePrecision.YEAR] has no entry point here; the enum
 * value and its [label] stay defined for whenever a year-only note shows up.
 */
private val offeredPrecisions =
  listOf(DatePrecision.DAY, DatePrecision.MONTH, DatePrecision.UNKNOWN)

@Composable
fun DatePrecisionPicker(
  precision: DatePrecision,
  onPrecisionChange: (DatePrecision) -> Unit,
  modifier: Modifier = Modifier,
) {
  ChoiceChips(
    options = offeredPrecisions,
    selected = precision,
    onSelect = onPrecisionChange,
    label = { it.label },
    modifier = modifier,
  )
}

@PreviewLightDark
@Composable
private fun DatePrecisionPickerPreview() {
  ForkloreTheme {
    Surface {
      DatePrecisionPicker(
        precision = DatePrecision.MONTH,
        onPrecisionChange = {},
        modifier = Modifier.padding(16.dp),
      )
    }
  }
}
