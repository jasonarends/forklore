package com.jasonarends.forklore.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.ui.components.EmptyState
import com.jasonarends.forklore.ui.components.LedgerPrimaryButton
import com.jasonarends.forklore.ui.components.LedgerTopBar
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: PeopleViewModel = viewModel(factory = PeopleViewModel.Factory),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
    modifier = modifier,
    topBar = { LedgerTopBar(title = "People", subtitle = "who's eating", onBack = onBack) },
    containerColor = ForkloreTheme.colors.paper,
  ) { innerPadding ->
    when (val current = state) {
      PeopleUiState.Loading ->
        Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      is PeopleUiState.Success ->
        PeopleContent(
          people = current.people,
          editing = current.editing,
          onAddPerson = viewModel::addPerson,
          onToggleHousehold = viewModel::setHouseholdMember,
          onStartRename = viewModel::startRename,
          onRename = viewModel::rename,
          onCancelRename = viewModel::cancelRename,
          modifier = Modifier.padding(innerPadding),
        )
      is PeopleUiState.Error ->
        Text(
          "Couldn't load people: ${current.throwable.message}",
          modifier = Modifier.padding(innerPadding),
        )
    }
  }
}

/**
 * Stateless by design: state in, events out. One row per person — name, a household [Switch] and a
 * rename affordance — rather than routing household membership through
 * [com.jasonarends.forklore.ui.components.PersonPicker]: a picker's selection is "who did you
 * pick", and forcing "is a household member" through that same shape only worked by relying on
 * `initiallyShowAll`/`allowReveal` escape hatches that existed for no other caller. `PersonPicker`
 * stays in `ui.components` for #5 (visit attendees), its first real caller, to use as intended.
 *
 * A `LazyColumn` rather than a `Column` in a scrollable modifier: this list has no natural cap, and
 * `items(people, key = { it.id })` keeps each row's local text field tied to the person rather than
 * to list position when a rename reorders the (name-sorted) list mid-edit.
 */
@Composable
internal fun PeopleContent(
  people: List<PersonEntity>,
  editing: RenameEdit?,
  onAddPerson: (String, Boolean) -> Unit,
  onToggleHousehold: (String, Boolean) -> Unit,
  onStartRename: (String) -> Unit,
  onRename: (String, String) -> Unit,
  onCancelRename: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier =
      modifier.fillMaxWidth().background(ForkloreTheme.colors.paper).padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item { AddPersonRow(onAddPerson = onAddPerson, modifier = Modifier.padding(top = 14.dp)) }
    if (people.isEmpty()) {
      item { EmptyState("No one yet. Add someone above.") }
    }
    items(people, key = { it.id }) { person ->
      PersonRow(
        person = person,
        editing = editing?.takeIf { it.personId == person.id },
        onToggleHousehold = { isHouseholdMember ->
          onToggleHousehold(person.id, isHouseholdMember)
        },
        onStartRename = { onStartRename(person.id) },
        onRename = { name -> onRename(person.id, name) },
        onCancelRename = onCancelRename,
      )
    }
  }
}

@Composable
private fun PersonRow(
  person: PersonEntity,
  editing: RenameEdit?,
  onToggleHousehold: (Boolean) -> Unit,
  onStartRename: () -> Unit,
  onRename: (String) -> Unit,
  onCancelRename: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  val isEditing = editing != null
  // Re-seeded from `person.name` every time editing starts, rather than hoisted: the ViewModel
  // owns *whether* this row is editing (see PeopleViewModel.RenameEdit), but the in-progress text
  // is exactly the transient, not-yet-committed kind of state CLAUDE.md carves out for a composable
  // to hold locally. `rememberSaveable` rather than `remember` so a rotation mid-edit doesn't throw
  // away what was typed.
  var text by rememberSaveable(person.id, isEditing) { mutableStateOf(person.name) }

  Column(modifier = modifier.fillMaxWidth().padding(vertical = 10.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (isEditing) {
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          modifier = Modifier.weight(1f).testTag("person-rename-field-${person.id}"),
          singleLine = true,
        )
        TextButton(onClick = { onRename(text) }) { Text("Save") }
        TextButton(onClick = onCancelRename) { Text("Cancel") }
      } else {
        Text(
          person.name,
          style = ForkloreType.placeNameList,
          color = colors.ink,
          modifier = Modifier.weight(1f),
        )
        Switch(
          modifier = Modifier.testTag("person-household-switch-${person.id}"),
          checked = person.isHouseholdMember,
          onCheckedChange = onToggleHousehold,
          colors =
            SwitchDefaults.colors(
              checkedThumbColor = colors.card,
              checkedTrackColor = colors.ink,
              checkedBorderColor = colors.ink,
              uncheckedThumbColor = colors.ink,
              uncheckedTrackColor = Color.Transparent,
              uncheckedBorderColor = colors.ink,
            ),
        )
        Text(
          text = "Rename",
          style =
            MaterialTheme.typography.labelMedium.copy(textDecoration = TextDecoration.Underline),
          color = colors.ink,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("person-rename-button-${person.id}")
              .clickable(onClick = onStartRename)
              .padding(4.dp),
        )
      }
    }
    if (editing?.error != null) {
      Text(
        editing.error,
        color = colors.stamp,
        modifier = Modifier.testTag("person-rename-error-${person.id}"),
      )
    }
  }
}

@Composable
private fun AddPersonRow(onAddPerson: (String, Boolean) -> Unit, modifier: Modifier = Modifier) {
  // rememberSaveable, not remember: this row is a LazyColumn item, so scrolling it off-screen and
  // back (or a rotation) can recreate the composable, and half-typed input shouldn't vanish either
  // way.
  var name by rememberSaveable { mutableStateOf("") }
  var isHouseholdMember by rememberSaveable { mutableStateOf(true) }
  val colors = ForkloreTheme.colors

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        modifier = Modifier.weight(1f).testTag("people-add-name"),
        label = { Text("Add a person") },
        singleLine = true,
      )
      LedgerPrimaryButton(
        text = "Add",
        enabled = name.isNotBlank(),
        onClick = {
          val trimmed = name.trim()
          if (trimmed.isNotEmpty()) {
            onAddPerson(trimmed, isHouseholdMember)
            name = ""
          }
        },
      )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Checkbox(
        modifier = Modifier.testTag("people-add-household"),
        checked = isHouseholdMember,
        onCheckedChange = { isHouseholdMember = it },
        colors =
          CheckboxDefaults.colors(
            checkedColor = colors.ink,
            checkmarkColor = colors.card,
            uncheckedColor = colors.ink2,
          ),
      )
      Text("Household member", style = ForkloreType.topBarSubtitle, color = colors.ink2)
    }
  }
}
