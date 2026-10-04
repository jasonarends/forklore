package com.jasonarends.forklore.ui.placedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.data.db.DatePrecision
import com.jasonarends.forklore.data.db.VisitEntity
import com.jasonarends.forklore.data.db.VisitWithAttendees
import com.jasonarends.forklore.ui.components.EmptyState
import com.jasonarends.forklore.ui.components.LedgerGhostButton
import com.jasonarends.forklore.ui.components.label
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Stateless by design: state in, events out. Renders the visit list newest-first, undated last —
 * ordering [VisitDao.observeForPlaceEntry] already handles, never re-sorted here — and the "Add a
 * visit" affordance. Adding and editing both leave this screen for the visit editor, so place
 * detail stays the read view. The "Visits" section header lives in the caller
 * ([com.jasonarends.forklore.ui.placedetail.PlaceDetail]), matching how the sibling Dishes section
 * is headered, not rendered here.
 */
@Composable
internal fun VisitsSection(
  visits: List<VisitWithAttendees>,
  onAddVisit: () -> Unit,
  onEditVisit: (visitId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    if (visits.isEmpty()) {
      EmptyState("No visits yet.")
    } else {
      Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(top = 8.dp),
      ) {
        visits.forEach { visit ->
          VisitRow(visit = visit, onEdit = { onEditVisit(visit.visit.id) })
        }
      }
    }
    LedgerGhostButton(
      text = "Add a visit",
      onClick = onAddVisit,
      modifier = Modifier.padding(top = 12.dp).testTag("visits-add-button"),
    )
  }
}

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d/yy")
private val monthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

private fun VisitEntity.dateLabel(): String {
  val epochDay = dateEpochDay ?: return "No date"
  val date = LocalDate.ofEpochDay(epochDay)
  return when (datePrecision) {
    DatePrecision.DAY -> date.format(dayFormatter)
    DatePrecision.MONTH -> date.format(monthFormatter)
    DatePrecision.YEAR -> date.year.toString()
    DatePrecision.UNKNOWN -> "No date"
  }
}

/** "7/21/26 · Dinner": how a visit is named wherever something needs to point at it. */
internal fun VisitEntity.summaryLabel(): String =
  listOfNotNull(dateLabel(), meal?.label).joinToString(" · ")

@Composable
private fun VisitRow(visit: VisitWithAttendees, onEdit: () -> Unit, modifier: Modifier = Modifier) {
  val colors = ForkloreTheme.colors
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(role = Role.Button, onClick = onEdit)
        .testTag("visit-row-${visit.visit.id}")
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = visit.visit.summaryLabel(),
        style = ForkloreType.opinionAuthor,
        color = colors.ink,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = "Edit",
        style =
          MaterialTheme.typography.labelMedium.copy(textDecoration = TextDecoration.Underline),
        color = colors.ink,
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .testTag("visit-edit-${visit.visit.id}")
            .clickable(onClick = onEdit)
            .padding(4.dp),
      )
    }
    if (visit.attendees.isNotEmpty()) {
      Text(
        text = visit.attendees.joinToString(", ") { it.name },
        style = ForkloreType.branchLabel,
        color = colors.ink2,
      )
    }
    if (visit.visit.note.isNotBlank()) {
      Text(text = visit.visit.note, style = ForkloreType.noteText, color = colors.ink)
    }
  }
}

@PreviewLightDark
@Composable
private fun VisitsSectionPreview() {
  ForkloreTheme {
    Surface {
      VisitsSection(
        visits = emptyList(),
        onAddVisit = {},
        onEditVisit = {},
        modifier = Modifier.padding(16.dp),
      )
    }
  }
}
