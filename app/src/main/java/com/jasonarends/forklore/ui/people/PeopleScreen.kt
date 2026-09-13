package com.jasonarends.forklore.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
        renameError = current.renameError,
        onAddPerson = viewModel::addPerson,
        onToggleHousehold = viewModel::setHouseholdMember,
        onRename = viewModel::rename,
        onDismissRenameError = viewModel::dismissRenameError,
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
 */
@Composable
internal fun PeopleContent(
  people: List<PersonEntity>,
  renameError: RenameError?,
  onAddPerson: (String, Boolean) -> Unit,
  onToggleHousehold: (String, Boolean) -> Unit,
  onRename: (String, String) -> Unit,
  onDismissRenameError: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionHeader("People")
    if (people.isEmpty()) {
      EmptyState("No one yet. Add a person below.")
    }
    people.forEach { person ->
      PersonRow(
        person = person,
        renameError = renameError?.takeIf { it.personId == person.id },
        onToggleHousehold = { isHouseholdMember ->
          onToggleHousehold(person.id, isHouseholdMember)
        },
        onRename = { name -> onRename(person.id, name) },
        onDismissRenameError = onDismissRenameError,
      )
    }
    AddPersonRow(onAddPerson = onAddPerson)
  }
}

@Composable
private fun PersonRow(
  person: PersonEntity,
  renameError: RenameError?,
  onToggleHousehold: (Boolean) -> Unit,
  onRename: (String) -> Unit,
  onDismissRenameError: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var editing by remember(person.id) { mutableStateOf(false) }
  var text by remember(person.id, person.name) { mutableStateOf(person.name) }
  // Tracks the name a Save is waiting on, so the row can tell "no error yet because nothing was
  // submitted" apart from "no error because the rename landed" — see the LaunchedEffect below.
  var pendingSave by remember(person.id) { mutableStateOf<String?>(null) }

  // Closes the row once the rename this row submitted actually lands in `person.name`, rather than
  // when Save is tapped: a rejected rename must leave the field open with its error visible, not
  // close over a name that was never saved.
  LaunchedEffect(person.name, renameError) {
    if (pendingSave != null && renameError == null && person.name == pendingSave) {
      editing = false
      pendingSave = null
    }
  }

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (editing) {
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          modifier = Modifier.weight(1f).testTag("person-rename-field-${person.id}"),
          singleLine = true,
        )
        TextButton(
          onClick = {
            pendingSave = text
            onRename(text)
          }
        ) {
          Text("Save")
        }
        TextButton(
          onClick = {
            text = person.name
            editing = false
            pendingSave = null
            if (renameError != null) onDismissRenameError()
          }
        ) {
          Text("Cancel")
        }
      } else {
        Text(person.name, modifier = Modifier.weight(1f))
        Switch(
          modifier = Modifier.testTag("person-household-switch-${person.id}"),
          checked = person.isHouseholdMember,
          onCheckedChange = onToggleHousehold,
        )
        TextButton(
          modifier = Modifier.testTag("person-rename-button-${person.id}"),
          onClick = { editing = true },
        ) {
          Text("Rename")
        }
      }
    }
    if (editing && renameError != null) {
      Text(
        renameError.message,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("person-rename-error-${person.id}"),
      )
    }
  }
}

@Composable
private fun AddPersonRow(onAddPerson: (String, Boolean) -> Unit, modifier: Modifier = Modifier) {
  var name by remember { mutableStateOf("") }
  var isHouseholdMember by remember { mutableStateOf(true) }

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
