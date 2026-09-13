package com.jasonarends.forklore.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jasonarends.forklore.ForkloreApp
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.data.repository.PersonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PeopleViewModel(private val personRepository: PersonRepository) : ViewModel() {
  private val _renameError = MutableStateFlow<RenameError?>(null)

  val uiState: StateFlow<PeopleUiState> =
    combine(personRepository.observeAll(), _renameError) { people, renameError ->
        PeopleUiState.Success(people, renameError) as PeopleUiState
      }
      .catch { emit(PeopleUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUiState.Loading)

  fun addPerson(name: String, isHouseholdMember: Boolean = false) {
    if (name.isBlank()) return
    viewModelScope.launch { personRepository.findOrCreate(name, isHouseholdMember) }
  }

  fun setHouseholdMember(id: String, isHouseholdMember: Boolean) {
    viewModelScope.launch { personRepository.setHouseholdMember(id, isHouseholdMember) }
  }

  /** The affected row owns showing/clearing its own error: see [RenameError.personId]. */
  fun rename(id: String, name: String) {
    if (name.isBlank()) return
    viewModelScope.launch {
      _renameError.value =
        when (personRepository.rename(id, name)) {
          PersonRepository.RenameResult.Success -> null
          PersonRepository.RenameResult.NameTaken ->
            RenameError(id, "Someone is already named \"${name.trim()}\".")
          PersonRepository.RenameResult.NotFound -> null
        }
    }
  }

  fun dismissRenameError() {
    _renameError.value = null
  }

  companion object {
    val Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY] as ForkloreApp
        PeopleViewModel(app.container.personRepository)
      }
    }
  }
}

/** [personId] is who the error belongs to, so only that row shows it. */
data class RenameError(val personId: String, val message: String)

sealed interface PeopleUiState {
  data object Loading : PeopleUiState

  data class Error(val throwable: Throwable) : PeopleUiState

  data class Success(val people: List<PersonEntity>, val renameError: RenameError? = null) :
    PeopleUiState
}
