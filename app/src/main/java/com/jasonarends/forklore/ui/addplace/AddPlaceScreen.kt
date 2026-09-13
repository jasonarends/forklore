package com.jasonarends.forklore.ui.addplace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jasonarends.forklore.ui.components.NoteField
import com.jasonarends.forklore.ui.theme.ForkloreTheme

/**
 * Manual entry: name, branch and address as single-line fields (there is no lookup here, so there's
 * nothing to autocomplete against), note and warning through the shared [NoteField] (see CLAUDE.md,
 * "Free text is first-class").
 */
@Composable
fun AddPlaceScreen(
  onSaved: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: AddPlaceViewModel = viewModel(factory = AddPlaceViewModel.Factory),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.saved) {
    if (state.saved) onSaved()
  }

  AddPlaceForm(
    state = state,
    onNameChange = viewModel::onNameChange,
    onBranchLabelChange = viewModel::onBranchLabelChange,
    onAddressChange = viewModel::onAddressChange,
    onNoteChange = viewModel::onNoteChange,
    onWarningChange = viewModel::onWarningChange,
    onSave = viewModel::save,
    onCancel = onCancel,
    modifier = modifier,
  )
}

/** Stateless by design: state in, events out. Only the screen-level composable sees a ViewModel. */
@Composable
internal fun AddPlaceForm(
  state: AddPlaceUiState,
  onNameChange: (String) -> Unit,
  onBranchLabelChange: (String) -> Unit,
  onAddressChange: (String) -> Unit,
  onNoteChange: (String) -> Unit,
  onWarningChange: (String) -> Unit,
  onSave: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    OutlinedTextField(
      value = state.name,
      onValueChange = onNameChange,
      label = { Text("Name") },
      modifier = Modifier.fillMaxWidth(),
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
    OutlinedTextField(
      value = state.branchLabel,
      onValueChange = onBranchLabelChange,
      label = { Text("Branch") },
      modifier = Modifier.fillMaxWidth(),
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
    OutlinedTextField(
      value = state.address,
      onValueChange = onAddressChange,
      label = { Text("Address") },
      modifier = Modifier.fillMaxWidth(),
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    NoteField(value = state.note, onValueChange = onNoteChange, label = "Note")
    NoteField(value = state.warning, onValueChange = onWarningChange, label = "Warning")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TextButton(onClick = onCancel) { Text("Cancel") }
      Button(
        onClick = onSave,
        enabled = state.name.isNotBlank() && state.placeListReady && !state.saving,
      ) {
        Text("Save")
      }
    }
  }
}

@PreviewLightDark
@Composable
private fun AddPlaceFormPreview() {
  ForkloreTheme {
    AddPlaceForm(
      state = AddPlaceUiState(name = "Halberd", branchLabel = "Westport"),
      onNameChange = {},
      onBranchLabelChange = {},
      onAddressChange = {},
      onNoteChange = {},
      onWarningChange = {},
      onSave = {},
      onCancel = {},
    )
  }
}
