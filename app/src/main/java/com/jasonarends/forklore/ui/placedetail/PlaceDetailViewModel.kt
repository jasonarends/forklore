package com.jasonarends.forklore.ui.placedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jasonarends.forklore.ForkloreApp
import com.jasonarends.forklore.data.db.DogPolicy
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceEntryWithPlace
import com.jasonarends.forklore.data.db.PlaceStatus
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.data.db.RevisitIntent
import com.jasonarends.forklore.data.repository.PlaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One [PlaceEntryEntity], editable with no separate "edit mode" — every field writes through
 * [PlaceRepository] as soon as it changes, except the note (see [updateNote]).
 */
class PlaceDetailViewModel(
  private val placeRepository: PlaceRepository,
  private val placeEntryId: String,
  /**
   * Outlives this ViewModel, unlike `viewModelScope`. [updateNote]'s debounce runs here so that
   * leaving the screen before it fires doesn't cancel it — see [updateNote].
   */
  private val appScope: CoroutineScope,
) : ViewModel() {

  /**
   * The note the user is currently typing, overriding whatever Room reports. Without this, each
   * keystroke would be visible in the UI for a moment and then overwritten by the previous
   * (pre-edit) value replayed from the still-unwritten database row, which reads as the app eating
   * keystrokes. Once set it is authoritative for the rest of this instance's lifetime — it never
   * needs to yield back to what Room reports.
   */
  private val pendingNote = MutableStateFlow<String?>(null)

  private var noteSaveJob: Job? = null

  init {
    // Flush on close rather than waiting out the debounce, so a reopened screen reads the edit
    // instead of the stale note; isActive skips a note whose debounce already landed.
    addCloseable {
      val job = noteSaveJob
      val note = pendingNote.value
      if (job?.isActive == true && note != null) {
        job.cancel()
        appScope.launch { saveNote(note) }
      }
    }
  }

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
   * Writes the place, not the entry: dog policy is a fact about the restaurant (see
   * [PlaceRepository.addPlace]), so every list that includes it sees the change. The place id is
   * read from Room rather than the UI state so this doesn't depend on anyone collecting [uiState].
   */
  fun updateDogPolicy(dogPolicy: DogPolicy?) {
    viewModelScope.launch {
      val placeId = placeRepository.observeEntry(placeEntryId).first()?.place?.id ?: return@launch
      placeRepository.setDogPolicy(placeId, dogPolicy)
    }
  }

  /**
   * Debounced rather than written on every keystroke, since nobody reads a note mid-keystroke. Runs
   * on [appScope] rather than `viewModelScope` and is flushed early if the ViewModel is closed
   * first — see the `init` block.
   */
  fun updateNote(note: String) {
    pendingNote.value = note
    noteSaveJob?.cancel()
    noteSaveJob = appScope.launch {
      delay(NOTE_SAVE_DEBOUNCE_MILLIS)
      saveNote(note)
    }
  }

  private suspend fun saveNote(note: String) =
    placeRepository.updateEntry(placeEntryId) { it.copy(note = note) }

  private fun update(change: (PlaceEntryEntity) -> PlaceEntryEntity) {
    viewModelScope.launch { placeRepository.updateEntry(placeEntryId, change) }
  }

  companion object {
    private const val NOTE_SAVE_DEBOUNCE_MILLIS = 500L

    /**
     * The id this observes comes from navigation rather than
     * [com.jasonarends.forklore.di.AppContainer] state, so this factory is a function of that id
     * rather than a fixed `val`.
     */
    fun factory(placeEntryId: String): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY] as ForkloreApp
        PlaceDetailViewModel(app.container.placeRepository, placeEntryId, app.container.appScope)
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
