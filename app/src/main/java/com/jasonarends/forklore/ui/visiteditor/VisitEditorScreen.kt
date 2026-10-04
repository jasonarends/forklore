package com.jasonarends.forklore.ui.visiteditor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jasonarends.forklore.data.db.DatePrecision
import com.jasonarends.forklore.data.db.Meal
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.ui.components.DatePrecisionPicker
import com.jasonarends.forklore.ui.components.EmptyState
import com.jasonarends.forklore.ui.components.LedgerChip
import com.jasonarends.forklore.ui.components.LedgerGhostButton
import com.jasonarends.forklore.ui.components.LedgerPrimaryButton
import com.jasonarends.forklore.ui.components.LedgerTextField
import com.jasonarends.forklore.ui.components.LedgerTopBar
import com.jasonarends.forklore.ui.components.MealPicker
import com.jasonarends.forklore.ui.components.NoteField
import com.jasonarends.forklore.ui.components.PersonPicker
import com.jasonarends.forklore.ui.components.UppercaseLabel
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType

/**
 * Add ([visitId] null) or edit one visit. Save and Cancel both call [onDone] — the caller pops —
 * and so does the system back gesture and the top bar's back button, except when the form has
 * unsaved changes: then all of them ask first, because a visit's date, attendees and note are
 * expensive to retype and the person may have swiped back by accident. Nothing here ever discards
 * or saves on its own.
 *
 * Draft survival matches the inline editors this replaced: the draft lives in the ViewModel, so it
 * survives rotation but not process death.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitEditorScreen(
  placeEntryId: String,
  visitId: String?,
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: VisitEditorViewModel =
    viewModel(factory = VisitEditorViewModel.factory(placeEntryId, visitId)),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val draft by viewModel.draft.collectAsStateWithLifecycle()
  val saved by viewModel.saved.collectAsStateWithLifecycle()
  val dirty by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
  var confirmingDiscard by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(saved) {
    if (saved) onDone()
  }

  val saving = draft?.saving == true
  // Mid-save the write may still land, so leaving would neither discard nor save reliably:
  // back, Cancel and the top bar do nothing until it resolves (success pops via `saved`).
  val requestExit = {
    when {
      saving -> Unit
      dirty -> confirmingDiscard = true
      else -> onDone()
    }
  }
  // Disabled when clean so the back gesture falls through to NavDisplay's own pop. Enabled while
  // saving purely to consume the event.
  BackHandler(enabled = dirty || saving) { if (!saving) confirmingDiscard = true }

  Scaffold(
    modifier = modifier,
    topBar = {
      LedgerTopBar(
        title = if (visitId == null) "New visit" else "Edit visit",
        subtitle = "when, and who was there",
        onBack = requestExit,
      )
    },
    containerColor = ForkloreTheme.colors.paper,
  ) { innerPadding ->
    val current = state
    val currentDraft = draft
    when {
      current is VisitEditorUiState.NotFound ->
        EmptyState("This visit couldn't be found.", Modifier.padding(innerPadding))
      current is VisitEditorUiState.Error ->
        Text(
          "Couldn't load this visit: ${current.throwable.message}",
          color = ForkloreTheme.colors.stamp,
          modifier = Modifier.padding(innerPadding),
        )
      current is VisitEditorUiState.Ready && currentDraft != null ->
        VisitEditorForm(
          draft = currentDraft,
          people = current.people,
          onPrecisionChange = viewModel::onPrecisionChange,
          onQuickDate = viewModel::onQuickDate,
          onYearChange = viewModel::onYearChange,
          onMonthChange = viewModel::onMonthChange,
          onDayChange = viewModel::onDayChange,
          onMealChange = viewModel::onMealChange,
          onNoteChange = viewModel::onNoteChange,
          onAttendeesChange = viewModel::onAttendeesChange,
          onCreatePerson = viewModel::onCreatePerson,
          onSave = viewModel::save,
          onCancel = requestExit,
          modifier = Modifier.padding(innerPadding),
        )
      else -> Unit
    }
  }

  if (confirmingDiscard) {
    DiscardChangesDialog(
      onDiscard = {
        confirmingDiscard = false
        onDone()
      },
      onKeepEditing = { confirmingDiscard = false },
    )
  }
}

@Composable
internal fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
  AlertDialog(
    onDismissRequest = onKeepEditing,
    title = { Text("Discard changes?") },
    text = { Text("This visit has changes that haven't been saved.") },
    confirmButton = {
      TextButton(onClick = onDiscard, modifier = Modifier.testTag("visit-discard")) {
        Text("Discard")
      }
    },
    dismissButton = {
      TextButton(onClick = onKeepEditing, modifier = Modifier.testTag("visit-keep-editing")) {
        Text("Keep editing")
      }
    },
  )
}

/**
 * Stateless by design: state in, events out. A screen of its own rather than a section: #33 adds
 * per-attendee dishes and opinions below the attendee picker, so the column scrolls and each block
 * is headed by an [UppercaseLabel] that a later block can follow.
 */
@Composable
internal fun VisitEditorForm(
  draft: VisitDraft,
  people: List<PersonEntity>,
  onPrecisionChange: (DatePrecision) -> Unit,
  onQuickDate: (QuickDate) -> Unit,
  onYearChange: (String) -> Unit,
  onMonthChange: (String) -> Unit,
  onDayChange: (String) -> Unit,
  onMealChange: (Meal?) -> Unit,
  onNoteChange: (String) -> Unit,
  onAttendeesChange: (Set<String>) -> Unit,
  onCreatePerson: (String, Boolean) -> Unit,
  onSave: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(colors.paper)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    UppercaseLabel(text = "Date", style = ForkloreType.fieldLabel, color = colors.ink2)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      QuickDate.entries.forEach { quickDate ->
        LedgerChip(
          label = quickDate.label,
          selected = false,
          onClick = { onQuickDate(quickDate) },
          role = Role.Button,
          modifier = Modifier.testTag("visit-date-${quickDate.name.lowercase()}"),
        )
      }
    }
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
private fun VisitEditorFormPreview() {
  ForkloreTheme {
    Surface {
      VisitEditorForm(
        draft = VisitDraft(precision = DatePrecision.MONTH),
        people = emptyList(),
        onPrecisionChange = {},
        onQuickDate = {},
        onYearChange = {},
        onMonthChange = {},
        onDayChange = {},
        onMealChange = {},
        onNoteChange = {},
        onAttendeesChange = {},
        onCreatePerson = { _, _ -> },
        onSave = {},
        onCancel = {},
      )
    }
  }
}
