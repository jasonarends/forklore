package com.jasonarends.forklore.ui.placedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jasonarends.forklore.ForkloreApp
import com.jasonarends.forklore.data.db.VisitWithAttendees
import com.jasonarends.forklore.data.repository.VisitRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The visits listed on one place entry's detail screen. Read-only: adding and editing a visit
 * happens on its own screen, see [com.jasonarends.forklore.ui.visiteditor.VisitEditorViewModel].
 */
class VisitsViewModel(visitRepository: VisitRepository, placeEntryId: String) : ViewModel() {

  val uiState: StateFlow<VisitsUiState> =
    visitRepository
      .observeForPlaceEntry(placeEntryId)
      .map<List<VisitWithAttendees>, VisitsUiState>(VisitsUiState::Success)
      .catch { emit(VisitsUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisitsUiState.Loading)

  companion object {
    fun factory(placeEntryId: String): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY] as ForkloreApp
        VisitsViewModel(app.container.visitRepository, placeEntryId)
      }
    }
  }
}

sealed interface VisitsUiState {
  data object Loading : VisitsUiState

  data class Error(val throwable: Throwable) : VisitsUiState

  data class Success(val visits: List<VisitWithAttendees>) : VisitsUiState
}
