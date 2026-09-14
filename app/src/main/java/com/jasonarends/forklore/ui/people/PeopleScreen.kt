package com.jasonarends.forklore.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.ui.components.EmptyState
import com.jasonarends.forklore.ui.components.SectionHeader

@Composable
fun PeopleScreen(
  modifier: Modifier = Modifier,
  viewModel: PeopleViewModel = viewModel(factory = PeopleViewModel.Factory),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  when (val current = state) {
    PeopleUiState.Loading ->
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
        modifier = modifier,
      )
    is PeopleUiState.Error ->
      Text("Couldn't load people: ${current.throwable.message}", modifier = modifier)
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
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item { SectionHeader("People") }
    item { AddPersonRow(onAddPerson = onAddPerson) }
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
  val isEditing = editing != null
  // Re-seeded from `person.name` every time editing starts, rather than hoisted: the ViewModel
  // owns *whether* this row is editing (see PeopleViewModel.RenameEdit), but the in-progress text
  // is exactly the transient, not-yet-committed kind of state CLAUDE.md carves out for a composable
  // to hold locally. `rememberSaveable` rather than `remember` so a rotation mid-edit doesn't throw
  // away what was typed.
  var text by rememberSaveable(person.id, isEditing) { mutableStateOf(person.name) }

  Column(modifier = modifier.fillMaxWidth()) {
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
        Text(person.name, modifier = Modifier.weight(1f))
        Switch(
          modifier = Modifier.testTag("person-household-switch-${person.id}"),
          checked = person.isHouseholdMember,
          onCheckedChange = onToggleHousehold,
        )
        TextButton(
          modifier = Modifier.testTag("person-rename-button-${person.id}"),
          onClick = onStartRename,
        ) {
          Text("Rename")
        }
      }
    }
    if (editing?.error != null) {
      Text(
        editing.error,
        color = MaterialTheme.colorScheme.error,
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
      TextButton(
        enabled = name.isNotBlank(),
        onClick = {
          val trimmed = name.trim()
          if (trimmed.isNotEmpty()) {
            onAddPerson(trimmed, isHouseholdMember)
            name = ""
          }
        },
      ) {
        Text("Add")
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Checkbox(
        modifier = Modifier.testTag("people-add-household"),
        checked = isHouseholdMember,
        onCheckedChange = { isHouseholdMember = it },
      )
      Text("Household member")
    }
  }
}
