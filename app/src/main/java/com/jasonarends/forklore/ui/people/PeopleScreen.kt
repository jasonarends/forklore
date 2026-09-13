package com.jasonarends.forklore.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.jasonarends.forklore.ui.components.PersonPicker
import com.jasonarends.forklore.ui.components.SectionHeader

@Composable
fun PeopleScreen(
  modifier: Modifier = Modifier,
  viewModel: PeopleViewModel = viewModel(factory = PeopleViewModel.Factory),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val renameError by viewModel.renameError.collectAsStateWithLifecycle()

  when (val current = state) {
    PeopleUiState.Loading ->
      PeopleContent(people = emptyList(), renameError = null, modifier = modifier)
    is PeopleUiState.Success ->
      PeopleContent(
        people = current.people,
        renameError = renameError,
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
 * Stateless by design: state in, events out. [PersonPicker] does double duty here — its selection
 * models "who's in the household", since a boolean per person is exactly a multi-select over the
 * whole list — while renaming gets its own row-based UI below, since a picker chip has nowhere to
 * put an edit affordance.
 */
@Composable
internal fun PeopleContent(
  people: List<PersonEntity>,
  renameError: String?,
  onAddPerson: (String, Boolean) -> Unit = { _, _ -> },
  onToggleHousehold: (String, Boolean) -> Unit = { _, _ -> },
  onRename: (String, String) -> Unit = { _, _ -> },
  onDismissRenameError: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val household = people.filter { it.isHouseholdMember }.map { it.id }.toSet()

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionHeader("People")
    if (people.isEmpty()) {
      EmptyState("No one yet. Add a person below.")
    }
    PersonPicker(
      people = people,
      selected = household,
      onSelectionChange = { newHousehold ->
        people.forEach { person ->
          val shouldBeHousehold = person.id in newHousehold
          if (shouldBeHousehold != person.isHouseholdMember) {
            onToggleHousehold(person.id, shouldBeHousehold)
          }
        }
      },
      onCreatePerson = { name -> onAddPerson(name, false) },
      multiSelect = true,
      initiallyShowAll = true,
      allowReveal = false,
    )
    if (renameError != null) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(renameError, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismissRenameError) { Text("Dismiss") }
      }
    }
    if (people.isNotEmpty()) {
      SectionHeader("Rename")
      people.forEach { person -> PersonRenameRow(person = person, onRename = onRename) }
    }
  }
}

@Composable
private fun PersonRenameRow(
  person: PersonEntity,
  onRename: (String, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var editing by remember(person.id) { mutableStateOf(false) }
  var text by remember(person.id, person.name) { mutableStateOf(person.name) }

  Row(
    modifier = modifier.fillMaxWidth(),
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
          onRename(person.id, text)
          editing = false
        }
      ) {
        Text("Save")
      }
      TextButton(
        onClick = {
          text = person.name
          editing = false
        }
      ) {
        Text("Cancel")
      }
    } else {
      Text(person.name, modifier = Modifier.weight(1f))
      TextButton(
        modifier = Modifier.testTag("person-rename-button-${person.id}"),
        onClick = { editing = true },
      ) {
        Text("Rename")
      }
    }
  }
}
