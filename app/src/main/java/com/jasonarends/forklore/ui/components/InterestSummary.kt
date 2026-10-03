package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType

/**
 * Who a dish interest is for, who suggested it, how to order it, and the free-text note — the part
 * of an interest that isn't its status. Shared by the dish screen's row and the list-wide view so
 * the two can't word "for Robin" or "recommended by Dale" differently. Each line only renders when
 * it has something to say; a bare interest with no dimensions set renders nothing here.
 */
@Composable
internal fun InterestSummary(
  forPersonName: String?,
  recommendedByName: String?,
  modification: String?,
  note: String,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  val people =
    listOfNotNull(forPersonName?.let { "for $it" }, recommendedByName?.let { "recommended by $it" })
  Column(modifier = modifier) {
    if (people.isNotEmpty()) {
      Text(
        text = people.joinToString(" · "),
        style = ForkloreType.branchLabel,
        color = colors.ink2,
      )
    }
    if (!modification.isNullOrBlank()) {
      Text(
        text = modification,
        style = ForkloreType.noteText,
        color = colors.ink,
        modifier = Modifier.padding(top = 2.dp),
      )
    }
    if (note.isNotBlank()) {
      Text(
        text = note,
        style = ForkloreType.opinionNote,
        color = colors.ink2,
        modifier = Modifier.padding(top = 2.dp),
      )
    }
  }
}
