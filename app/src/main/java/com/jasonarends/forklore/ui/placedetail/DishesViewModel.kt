package com.jasonarends.forklore.ui.placedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jasonarends.forklore.ForkloreApp
import com.jasonarends.forklore.data.db.DishWithAliases
import com.jasonarends.forklore.data.db.normalizeDishName
import com.jasonarends.forklore.data.repository.DishRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The dishes recorded for one place entry, plus the in-progress "add a dish" text. A separate
 * ViewModel from [PlaceDetailViewModel] rather than more fields on it: dishes come from their own
 * repository and carry their own transient autocomplete state, which has nothing to do with the
 * entry's status/rating/note fields.
 *
 * [query] and the [suggestions] derived from it are ViewModel-local per CLAUDE.md rule 2 — a
 * candidate the user is still typing is never written to Room. Only [addDish] and [addAlias] write
 * anything, and both always go through [DishRepository] so a spelling that already resolves to a
 * dish never creates a second one (issue #6).
 */
class DishesViewModel(
  private val dishRepository: DishRepository,
  private val placeEntryId: String,
) : ViewModel() {

  val uiState: StateFlow<DishesUiState> =
    dishRepository
      .observeForPlaceEntry(placeEntryId)
      .map<List<DishWithAliases>, DishesUiState>(DishesUiState::Success)
      .catch { emit(DishesUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DishesUiState.Loading)

  private val _query = MutableStateFlow("")
  val query: StateFlow<String> = _query.asStateFlow()

  /**
   * Dishes already recorded at this place entry whose canonical name or any alias contains [query],
   * matched via [normalizeDishName] so punctuation, accents and case never hide a match. Derived
   * entirely from [uiState] rather than a fresh database query, so a spelling recorded only as an
   * alias still surfaces the dish it belongs to instead of reading as new.
   */
  val suggestions: StateFlow<List<DishWithAliases>> =
    combine(_query, uiState) { query, state ->
        val normalized = normalizeDishName(query)
        val dishes = (state as? DishesUiState.Success)?.dishes ?: emptyList()
        if (normalized.isEmpty()) {
          emptyList()
        } else {
          dishes.filter { dish ->
            dish.dish.normalizedName.contains(normalized) ||
              dish.aliases.any { it.normalized.contains(normalized) }
          }
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun onQueryChange(text: String) {
    _query.value = text
  }

  /**
   * Always routes through [DishRepository.findOrCreateDish] — never a direct insert — which is what
   * resolves "barrel tots" back to an existing "Barrel Potatoes" row rather than creating a second
   * one. Clears [query] once the write lands, whether it created a dish or found one.
   */
  fun addDish(name: String) {
    if (name.isBlank()) return
    viewModelScope.launch {
      dishRepository.findOrCreateDish(placeEntryId, name)
      _query.value = ""
    }
  }

  /** No-ops (via [DishRepository.addAlias]) when the spelling already resolves to this dish. */
  fun addAlias(dishId: String, alias: String) {
    if (alias.isBlank()) return
    viewModelScope.launch { dishRepository.addAlias(dishId, placeEntryId, alias) }
  }

  companion object {
    fun factory(placeEntryId: String): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY] as ForkloreApp
        DishesViewModel(app.container.dishRepository, placeEntryId)
      }
    }
  }
}

sealed interface DishesUiState {
  data object Loading : DishesUiState

  data class Error(val throwable: Throwable) : DishesUiState

  data class Success(val dishes: List<DishWithAliases>) : DishesUiState
}
