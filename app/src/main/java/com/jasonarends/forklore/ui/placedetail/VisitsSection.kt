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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.data.db.DatePrecision
import com.jasonarends.forklore.data.db.Meal
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.data.db.VisitEntity
import com.jasonarends.forklore.data.db.VisitWithAttendees
import com.jasonarends.forklore.ui.components.DatePrecisionPicker
import com.jasonarends.forklore.ui.components.EmptyState
import com.jasonarends.forklore.ui.components.LedgerGhostButton
import com.jasonarends.forklore.ui.components.LedgerPrimaryButton
import com.jasonarends.forklore.ui.components.LedgerTextField
import com.jasonarends.forklore.ui.components.MealPicker
import com.jasonarends.forklore.ui.components.NoteField
import com.jasonarends.forklore.ui.components.PersonPicker
import com.jasonarends.forklore.ui.components.UppercaseLabel
import com.jasonarends.forklore.ui.components.label
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Stateless by design: state in, events out. Renders the visit list newest-first, undated last —
 * ordering [VisitDao.observeForPlaceEntry] already handles, never re-sorted here — and either the
 * "Add a visit" affordance or the one open [draft], never both: only one visit is ever being edited
 * at a time (see [VisitsViewModel]). The "Visits" section header lives in the caller
 * ([com.jasonarends.forklore.ui.placedetail.PlaceDetail]), matching how the sibling Dishes section
 * is headered, not rendered here.
 */
@Composable
internal fun VisitsSection(
  visits: List<VisitWithAttendees>,
  people: List<PersonEntity>,
  draft: VisitDraft?,
  onStartAdd: () -> Unit,
  onStartEdit: (VisitWithAttendees) -> Unit,
  onCancelDraft: () -> Unit,
  onPrecisionChange: (DatePrecision) -> Unit,
  onYearChange: (String) -> Unit,
  onMonthChange: (String) -> Unit,
  onDayChange: (String) -> Unit,
  onMealChange: (Meal?) -> Unit,
  onNoteChange: (String) -> Unit,
  onAttendeesChange: (Set<String>) -> Unit,
  onCreatePerson: (String) -> Unit,
  onSaveVisit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    if (draft == null) {
      if (visits.isEmpty()) {
        EmptyState("No visits yet.")
      } else {
        Column(
          verticalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier.padding(top = 8.dp),
        ) {
          visits.forEach { visit -> VisitRow(visit = visit, onEdit = { onStartEdit(visit) }) }
        }
      }
      LedgerGhostButton(
        text = "Add a visit",
        onClick = onStartAdd,
        modifier = Modifier.padding(top = 12.dp).testTag("visits-add-button"),
      )
    } else {
      VisitForm(
        draft = draft,
        people = people,
        onPrecisionChange = onPrecisionChange,
        onYearChange = onYearChange,
        onMonthChange = onMonthChange,
        onDayChange = onDayChange,
        onMealChange = onMealChange,
        onNoteChange = onNoteChange,
        onAttendeesChange = onAttendeesChange,
        onCreatePerson = onCreatePerson,
        onSave = onSaveVisit,
        onCancel = onCancelDraft,
        modifier = Modifier.padding(top = 8.dp),
      )
    }
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
  Column(modifier = modifier.fillMaxWidth()) {
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

@Composable
private fun VisitForm(
  draft: VisitDraft,
  people: List<PersonEntity>,
  onPrecisionChange: (DatePrecision) -> Unit,
  onYearChange: (String) -> Unit,
  onMonthChange: (String) -> Unit,
  onDayChange: (String) -> Unit,
  onMealChange: (Meal?) -> Unit,
  onNoteChange: (String) -> Unit,
  onAttendeesChange: (Set<String>) -> Unit,
  onCreatePerson: (String) -> Unit,
  onSave: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    UppercaseLabel(text = "Date", style = ForkloreType.fieldLabel, color = colors.ink2)
    DatePrecisionPicker(
      precision = draft.precision,
      onPrecisionChange = onPrecisionChange,
      modifier = Modifier.testTag("visit-date-precision"),
    )
    when (draft.precision) {
      DatePrecision.DAY ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          LedgerTextField(
            value = draft.month,
            onValueChange = onMonthChange,
            label = "Month",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f).testTag("visit-date-month"),
          )
          LedgerTextField(
            value = draft.day,
            onValueChange = onDayChange,
            label = "Day",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f).testTag("visit-date-day"),
          )
          LedgerTextField(
            value = draft.year,
            onValueChange = onYearChange,
            label = "Year",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f).testTag("visit-date-year"),
          )
        }
      DatePrecision.MONTH ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          LedgerTextField(
            value = draft.month,
            onValueChange = onMonthChange,
            label = "Month",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f).testTag("visit-date-month"),
          )
          LedgerTextField(
            value = draft.year,
            onValueChange = onYearChange,
            label = "Year",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f).testTag("visit-date-year"),
          )
        }
      // DatePrecision.YEAR has no entry point in DatePrecisionPicker (see its KDoc) — a draft
      // can only be in this state via VisitDraft.from on a row this UI never wrote, and rendering
      // a year field here would look editable while no chip above shows it selected. Nothing to
      // render until issue #5's YEAR support gets a picker entry, per that same KDoc.
      DatePrecision.YEAR,
      DatePrecision.UNKNOWN -> Unit
    }
    UppercaseLabel(text = "Meal", style = ForkloreType.fieldLabel, color = colors.ink2)
    MealPicker(
      meal = draft.meal,
      onMealChange = onMealChange,
      modifier = Modifier.testTag("visit-meal"),
    )
    UppercaseLabel(text = "Who was there", style = ForkloreType.fieldLabel, color = colors.ink2)
    PersonPicker(
      people = people,
      selected = draft.attendees,
      onSelectionChange = onAttendeesChange,
      onCreatePerson = onCreatePerson,
    )
    NoteField(
      value = draft.note,
      onValueChange = onNoteChange,
      modifier = Modifier.testTag("visit-note"),
    )
    draft.error?.let { Text(it, color = colors.stamp, modifier = Modifier.testTag("visit-error")) }
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(top = 4.dp),
    ) {
      LedgerGhostButton(
        text = "Cancel",
        onClick = onCancel,
        modifier = Modifier.testTag("visit-cancel"),
      )
      LedgerPrimaryButton(
        text = "Save",
        onClick = onSave,
        enabled = !draft.saving,
        modifier = Modifier.testTag("visit-save"),
      )
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
        people = emptyList(),
        draft = VisitDraft(precision = DatePrecision.MONTH),
        onStartAdd = {},
        onStartEdit = {},
        onCancelDraft = {},
        onPrecisionChange = {},
        onYearChange = {},
        onMonthChange = {},
        onDayChange = {},
        onMealChange = {},
        onNoteChange = {},
        onAttendeesChange = {},
        onCreatePerson = {},
        onSaveVisit = {},
        modifier = Modifier.padding(16.dp),
      )
    }
  }
}
