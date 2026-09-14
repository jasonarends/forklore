package com.jasonarends.forklore.ui.addplace

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jasonarends.forklore.ForkloreApp
import com.jasonarends.forklore.data.repository.PlaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

  // Plain, not derived: combine()'s collector yields after each emission, and on Main.immediate
  // that's a real gap (a posted Handler message) in which a recomposition can hand the
  // String-based OutlinedTextFields a stale value — dropped keystrokes, cursor jumps, worse with
  // IME autocorrect batching. Text-field state stays on a synchronous StateFlow for exactly that
  // reason; placeListReady, which nothing types into, is fine as its own derived flow below.
  private val _uiState = MutableStateFlow(AddPlaceUiState())
  val uiState: StateFlow<AddPlaceUiState> = _uiState.asStateFlow()

  val placeListReady: StateFlow<Boolean> =
    placeListId
      .map { it != null }
      .stateIn(viewModelScope, SharingStarted.Eagerly, placeListId.value != null)

  fun onNameChange(value: String) {
    _uiState.update { it.copy(name = value, error = null) }
  }

  fun onBranchLabelChange(value: String) {
    _uiState.update { it.copy(branchLabel = value, error = null) }
  }

  fun onAddressChange(value: String) {
    _uiState.update { it.copy(address = value, error = null) }
  }

  fun onNoteChange(value: String) {
    _uiState.update { it.copy(note = value, error = null) }
  }

  fun onWarningChange(value: String) {
    _uiState.update { it.copy(warning = value, error = null) }
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

    _uiState.update { it.copy(saving = true, error = null) }
    viewModelScope.launch {
      try {
        placeRepository.addPlaceToList(
          placeListId = listId,
          name = name,
          branchLabel = state.branchLabel.trim().ifBlank { null },
          address = state.address.trim().ifBlank { null },
          note = state.note,
          warning = state.warning.trim().ifBlank { null },
        )
        _uiState.update { it.copy(saving = false, saved = true) }
      } catch (_: SQLiteException) {
        // A write that fails (a full disk, a constraint violation) must not crash the app or
        // leave Save stuck disabled forever — the person just needs to be able to try again.
        _uiState.update { it.copy(saving = false, error = "Couldn't save this place. Try again.") }
      }
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
  val saving: Boolean = false,
  val saved: Boolean = false,
  val error: String? = null,
)
