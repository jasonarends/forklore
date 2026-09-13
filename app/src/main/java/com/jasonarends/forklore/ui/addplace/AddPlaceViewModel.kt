package com.jasonarends.forklore.ui.addplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jasonarends.forklore.ForkloreApp
import com.jasonarends.forklore.data.repository.PlaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manual entry only (see CLAUDE.md, "External place data is a cache" — provider lookup is M2).
 * Every field but [AddPlaceUiState.name] is optional, which is why saving never validates them.
 */
class AddPlaceViewModel(
  private val placeRepository: PlaceRepository,
  private val placeListId: StateFlow<String?>,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AddPlaceUiState())
  val uiState: StateFlow<AddPlaceUiState> = _uiState.asStateFlow()

  fun onNameChange(value: String) {
    _uiState.update { it.copy(name = value) }
  }

  fun onBranchLabelChange(value: String) {
    _uiState.update { it.copy(branchLabel = value) }
  }

  fun onAddressChange(value: String) {
    _uiState.update { it.copy(address = value) }
  }

  fun onNoteChange(value: String) {
    _uiState.update { it.copy(note = value) }
  }

  fun onWarningChange(value: String) {
    _uiState.update { it.copy(warning = value) }
  }

  /** No-op on a blank name: the button that calls this is disabled for that case too. */
  fun save() {
    val state = _uiState.value
    val name = state.name.trim()
    val listId = placeListId.value
    if (name.isEmpty() || listId == null) return

    viewModelScope.launch {
      placeRepository.addPlaceToList(
        placeListId = listId,
        name = name,
        branchLabel = state.branchLabel.trim().ifBlank { null },
        address = state.address.trim().ifBlank { null },
        note = state.note,
        warning = state.warning.trim().ifBlank { null },
      )
      _uiState.update { it.copy(saved = true) }
    }
  }

  companion object {
    val Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY] as ForkloreApp
        AddPlaceViewModel(app.container.placeRepository, app.container.currentPlaceListId)
      }
    }
  }
}

data class AddPlaceUiState(
  val name: String = "",
  val branchLabel: String = "",
  val address: String = "",
  val note: String = "",
  val warning: String = "",
  val saved: Boolean = false,
)
