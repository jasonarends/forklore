package com.jasonarends.forklore.ui.placelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jasonarends.forklore.ForkloreApp
import com.jasonarends.forklore.data.db.PlaceEntryWithPlace
import com.jasonarends.forklore.data.repository.PlaceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Reference implementation of the ViewModel pattern described in CLAUDE.md. Every other ViewModel
 * in this project should look like this one: repository in through the constructor, a sealed
 * UiState out through a StateFlow, and a Factory companion that is the only way a composable
 * reaches the AppContainer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaceListViewModel(placeRepository: PlaceRepository, placeListId: StateFlow<String?>) :
  ViewModel() {

  val uiState: StateFlow<PlaceListUiState> =
    placeListId
      .flatMapLatest { id ->
        if (id == null) MutableStateFlow(PlaceListUiState.Loading)
        else
          placeRepository
            .observeList(id)
            .map<List<PlaceEntryWithPlace>, PlaceListUiState>(PlaceListUiState::Success)
      }
      .catch { emit(PlaceListUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceListUiState.Loading)

  companion object {
    val Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY] as ForkloreApp
        PlaceListViewModel(app.container.placeRepository, app.container.currentPlaceListId)
      }
    }
  }
}

sealed interface PlaceListUiState {
  data object Loading : PlaceListUiState

  data class Error(val throwable: Throwable) : PlaceListUiState

  data class Success(val entries: List<PlaceEntryWithPlace>) : PlaceListUiState
}
