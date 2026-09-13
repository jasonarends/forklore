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

  private val _uiState =
    MutableStateFlow(AddPlaceUiState(placeListReady = placeListId.value != null))
  val uiState: StateFlow<AddPlaceUiState> = _uiState.asStateFlow()

  init {
    // The default list is created asynchronously at app startup (see ForkloreApp), so it may
    // still be null when this screen first opens. Save stays disabled until it resolves rather
    // than silently no-opping on a tap.
    viewModelScope.launch {
      placeListId.collect { id -> _uiState.update { it.copy(placeListReady = id != null) } }
    }
  }

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

  /**
   * No-op on a blank name, a not-yet-ready list, or a save already in flight — the Save button is
   * disabled for all three, but a double tap can still land two calls here before the first
   * recomposition, so `saving` is set synchronously (not inside the launched coroutine) to make the
   * second call see it.
   */
  fun save() {
    val state = _uiState.value
    val name = state.name.trim()
    val listId = placeListId.value
    if (name.isEmpty() || listId == null || state.saving) return

    _uiState.update { it.copy(saving = true) }
    viewModelScope.launch {
      placeRepository.addPlaceToList(
        placeListId = listId,
        name = name,
        branchLabel = state.branchLabel.trim().ifBlank { null },
        address = state.address.trim().ifBlank { null },
        note = state.note,
        warning = state.warning.trim().ifBlank { null },
      )
      _uiState.update { it.copy(saving = false, saved = true) }
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
  val placeListReady: Boolean = false,
  val saving: Boolean = false,
  val saved: Boolean = false,
)
