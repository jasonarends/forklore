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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PeopleViewModel(private val personRepository: PersonRepository) : ViewModel() {
  val uiState: StateFlow<PeopleUiState> =
    personRepository
      .observeAll()
      .map<List<PersonEntity>, PeopleUiState>(PeopleUiState::Success)
      .catch { emit(PeopleUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUiState.Loading)

  private val _renameError = MutableStateFlow<String?>(null)

  /** Set when [rename] collides with someone else's name; cleared on the next successful rename. */
  val renameError: StateFlow<String?> = _renameError.asStateFlow()

  fun addPerson(name: String, isHouseholdMember: Boolean = false) {
    if (name.isBlank()) return
    viewModelScope.launch { personRepository.findOrCreate(name, isHouseholdMember) }
  }

  fun setHouseholdMember(id: String, isHouseholdMember: Boolean) {
    viewModelScope.launch { personRepository.setHouseholdMember(id, isHouseholdMember) }
  }

  fun rename(id: String, name: String) {
    if (name.isBlank()) return
    viewModelScope.launch {
      _renameError.value =
        when (personRepository.rename(id, name)) {
          PersonRepository.RenameResult.Success -> null
          PersonRepository.RenameResult.NameTaken -> "Someone is already named \"${name.trim()}\"."
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

sealed interface PeopleUiState {
  data object Loading : PeopleUiState

  data class Error(val throwable: Throwable) : PeopleUiState

  data class Success(val people: List<PersonEntity>) : PeopleUiState
}
