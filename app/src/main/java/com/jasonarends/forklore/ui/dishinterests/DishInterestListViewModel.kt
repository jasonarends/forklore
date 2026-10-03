package com.jasonarends.forklore.ui.dishinterests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jasonarends.forklore.ForkloreApp
import com.jasonarends.forklore.data.db.DishStatus
import com.jasonarends.forklore.data.db.ListedDishInterest
import com.jasonarends.forklore.data.repository.DishRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The current list's dish interests across every place: what it wants to try, and what it must
 * never order again. Read-only — interests are edited on the dish they belong to, and this view
 * links to it. [DishStatus.TRIED] is deliberately not surfaced: it is a record, not an instruction,
 * and belongs to the dish screen. Issue #23 generalizes this view into filtering and sorting; this
 * is the fixed two-group version it grows from.
 *
 * Scoped by the current list id, which is null until startup resolves it — [Loading] until then.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DishInterestListViewModel(dishRepository: DishRepository, placeListId: StateFlow<String?>) :
  ViewModel() {

  val uiState: StateFlow<DishInterestListUiState> =
    placeListId
      .flatMapLatest { id ->
        if (id == null) MutableStateFlow(DishInterestListUiState.Loading)
        else
          dishRepository.observeListedInterests(id).map { listed ->
            DishInterestListUiState.Success(
              want = listed.filter { it.interest.status == DishStatus.WANT },
              neverAgain = listed.filter { it.interest.status == DishStatus.NEVER_AGAIN },
            )
          }
      }
      .catch { emit(DishInterestListUiState.Error(it)) }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DishInterestListUiState.Loading,
      )

  companion object {
    val Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY] as ForkloreApp
        DishInterestListViewModel(app.container.dishRepository, app.container.currentPlaceListId)
      }
    }
  }
}

sealed interface DishInterestListUiState {
  data object Loading : DishInterestListUiState

  data class Error(val throwable: Throwable) : DishInterestListUiState

  data class Success(val want: List<ListedDishInterest>, val neverAgain: List<ListedDishInterest>) :
    DishInterestListUiState
}
