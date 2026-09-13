package com.jasonarends.forklore.ui.placedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jasonarends.forklore.ForkloreApp
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceEntryWithPlace
import com.jasonarends.forklore.data.db.PlaceStatus
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.data.db.RevisitIntent
import com.jasonarends.forklore.data.repository.PlaceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One [PlaceEntryEntity], editable with no separate "edit mode" — every field writes through
 * [PlaceRepository] as soon as it changes, except the note (see [updateNote]).
 *
 * Unlike [com.jasonarends.forklore.ui.main.MainScreenViewModel], the id this observes comes from
 * navigation rather than [com.jasonarends.forklore.di.AppContainer] state, so the `Factory` here is
 * a function of that id rather than a fixed `val`.
 */
class PlaceDetailViewModel(
  private val placeRepository: PlaceRepository,
  private val placeEntryId: String,
) : ViewModel() {

  /**
   * The note the user is currently typing, overriding whatever Room reports until the debounced
   * write below lands. Without this, each keystroke would be visible in the UI for a moment and
   * then overwritten by the previous (pre-edit) value replayed from the still-unwritten database
   * row, which reads as the app eating keystrokes.
   */
  private val pendingNote = MutableStateFlow<String?>(null)

  private var noteSaveJob: Job? = null

  val uiState: StateFlow<PlaceDetailUiState> =
    combine(placeRepository.observeEntry(placeEntryId), pendingNote) { entry, pending ->
        when {
          entry == null -> PlaceDetailUiState.NotFound
          pending == null -> PlaceDetailUiState.Success(entry)
          else -> PlaceDetailUiState.Success(entry.copy(entry = entry.entry.copy(note = pending)))
        }
      }
      .catch { emit(PlaceDetailUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceDetailUiState.Loading)

  fun updateStatus(status: PlaceStatus) = update { it.copy(status = status) }

  fun updateFoodRating(rating: Rating?) = update { it.copy(foodRating = rating) }

  fun updateServiceRating(rating: Rating?) = update { it.copy(serviceRating = rating) }

  fun updateRevisitIntent(intent: RevisitIntent?) = update { it.copy(revisitIntent = intent) }

  /**
   * Debounced rather than written on every keystroke: a note is typed a character at a time and a
   * Room write (plus the Flow re-query it triggers) per character would mean constant recomposition
   * for no benefit, since nobody reads a note mid-keystroke. [pendingNote] keeps the field
   * responsive while the write is pending; the trade-off is up to [NOTE_SAVE_DEBOUNCE_MILLIS] of a
   * pause being lost if the process dies before the debounce fires, which a short interval bounds.
   */
  fun updateNote(note: String) {
    pendingNote.value = note
    noteSaveJob?.cancel()
    noteSaveJob = viewModelScope.launch {
      delay(NOTE_SAVE_DEBOUNCE_MILLIS)
      placeRepository.updateEntry(placeEntryId) { it.copy(note = note) }
      pendingNote.value = null
    }
  }

  private fun update(change: (PlaceEntryEntity) -> PlaceEntryEntity) {
    viewModelScope.launch { placeRepository.updateEntry(placeEntryId, change) }
  }

  companion object {
    private const val NOTE_SAVE_DEBOUNCE_MILLIS = 500L

    fun factory(placeEntryId: String): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY] as ForkloreApp
        PlaceDetailViewModel(app.container.placeRepository, placeEntryId)
      }
    }
  }
}

sealed interface PlaceDetailUiState {
  data object Loading : PlaceDetailUiState

  /** The entry was removed (or never existed) since navigating here. */
  data object NotFound : PlaceDetailUiState

  data class Error(val throwable: Throwable) : PlaceDetailUiState

  data class Success(val entry: PlaceEntryWithPlace) : PlaceDetailUiState
}
