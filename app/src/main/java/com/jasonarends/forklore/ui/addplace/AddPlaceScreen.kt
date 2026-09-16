package com.jasonarends.forklore.ui.addplace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jasonarends.forklore.ui.components.LedgerGhostButton
import com.jasonarends.forklore.ui.components.LedgerPrimaryButton
import com.jasonarends.forklore.ui.components.LedgerTextField
import com.jasonarends.forklore.ui.components.LedgerTopBar
import com.jasonarends.forklore.ui.components.NoteField
import com.jasonarends.forklore.ui.theme.ForkloreTheme

/**
 * Manual entry: name, branch and address as single-line fields (there is no lookup here, so there's
 * nothing to autocomplete against), note and warning through the shared [NoteField] (see CLAUDE.md,
 * "Free text is first-class").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaceScreen(
  onSaved: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: AddPlaceViewModel = viewModel(factory = AddPlaceViewModel.Factory),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val placeListReady by viewModel.placeListReady.collectAsStateWithLifecycle()

  LaunchedEffect(state.saved) {
    if (state.saved) onSaved()
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      LedgerTopBar(title = "New place", subtitle = "don't lose this one", onBack = onCancel)
    },
    containerColor = ForkloreTheme.colors.paper,
  ) { innerPadding ->
    AddPlaceForm(
      state = state,
      placeListReady = placeListReady,
      onNameChange = viewModel::onNameChange,
      onBranchLabelChange = viewModel::onBranchLabelChange,
      onAddressChange = viewModel::onAddressChange,
      onNoteChange = viewModel::onNoteChange,
      onWarningChange = viewModel::onWarningChange,
      onSave = viewModel::save,
      onCancel = onCancel,
      modifier = Modifier.padding(innerPadding),
    )
  }
}

/** Stateless by design: state in, events out. Only the screen-level composable sees a ViewModel. */
@Composable
internal fun AddPlaceForm(
  state: AddPlaceUiState,
  placeListReady: Boolean,
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
    modifier =
      modifier
        .background(ForkloreTheme.colors.paper)
        .padding(horizontal = 20.dp, vertical = 14.dp)
        .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    LedgerTextField(
      value = state.name,
      onValueChange = onNameChange,
      label = "Name",
      capitalization = KeyboardCapitalization.Words,
    )
    LedgerTextField(
      value = state.branchLabel,
      onValueChange = onBranchLabelChange,
      label = "Branch",
      capitalization = KeyboardCapitalization.Words,
    )
    LedgerTextField(value = state.address, onValueChange = onAddressChange, label = "Address")
    NoteField(value = state.note, onValueChange = onNoteChange, label = "Note")
    NoteField(
      value = state.warning,
      onValueChange = onWarningChange,
      label = "Warning",
      warning = true,
    )
    state.error?.let { Text(it, color = ForkloreTheme.colors.stamp) }
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(vertical = 12.dp),
    ) {
      LedgerGhostButton(text = "Cancel", onClick = onCancel)
      LedgerPrimaryButton(
        text = "Save",
        onClick = onSave,
        enabled = state.name.isNotBlank() && placeListReady && !state.saving,
      )
    }
  }
}

@PreviewLightDark
@Composable
private fun AddPlaceFormPreview() {
  ForkloreTheme {
    AddPlaceForm(
      state = AddPlaceUiState(name = "Halberd", branchLabel = "Westport"),
      placeListReady = true,
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
