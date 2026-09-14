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
import kotlinx.coroutines.flow.combine
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

  // The form fields this screen actually owns. placeListReady is deliberately not stored here: it
  // is derived below from placeListId, so there's exactly one source of truth for it rather than a
  // seeded initial value plus a collector that has to keep it in sync.
  private val _formState = MutableStateFlow(AddPlaceUiState())

  val uiState: StateFlow<AddPlaceUiState> =
    combine(_formState, placeListId) { form, listId -> form.copy(placeListReady = listId != null) }
      .stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AddPlaceUiState(placeListReady = placeListId.value != null),
      )

  fun onNameChange(value: String) {
    _formState.update { it.copy(name = value) }
  }

  fun onBranchLabelChange(value: String) {
    _formState.update { it.copy(branchLabel = value) }
  }

  fun onAddressChange(value: String) {
    _formState.update { it.copy(address = value) }
  }

  fun onNoteChange(value: String) {
    _formState.update { it.copy(note = value) }
  }

  fun onWarningChange(value: String) {
    _formState.update { it.copy(warning = value) }
  }

  /**
   * No-op on a blank name, a not-yet-ready list, or a save already in flight — the Save button is
   * disabled for all three, but a double tap can still land two calls here before the first
   * recomposition, so `saving` is set synchronously (not inside the launched coroutine) to make the
   * second call see it.
   */
  fun save() {
    val state = _formState.value
    val name = state.name.trim()
    val listId = placeListId.value
    if (name.isEmpty() || listId == null || state.saving) return

    _formState.update { it.copy(saving = true, error = null) }
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
        _formState.update { it.copy(saving = false, saved = true) }
      } catch (_: SQLiteException) {
        // A write that fails (a full disk, a constraint violation) must not crash the app or
        // leave Save stuck disabled forever — the person just needs to be able to try again.
        _formState.update {
          it.copy(saving = false, error = "Couldn't save this place. Try again.")
        }
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
  val placeListReady: Boolean = false,
  val saving: Boolean = false,
  val saved: Boolean = false,
  val error: String? = null,
)
