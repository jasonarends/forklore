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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PeopleViewModel(private val personRepository: PersonRepository) : ViewModel() {
  private val _editing = MutableStateFlow<RenameEdit?>(null)

  val uiState: StateFlow<PeopleUiState> =
    combine(personRepository.observeAll(), _editing) { people, editing ->
        PeopleUiState.Success(people, editing) as PeopleUiState
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

  fun startRename(id: String) {
    _editing.value = RenameEdit(id)
  }

  fun cancelRename() {
    _editing.value = null
  }

  /**
   * Stays in [RenameEdit] until the rename actually resolves: closing on [Unit] return (the old
   * fire-and-forget shape) meant the row closed the instant Save was tapped, before there was any
   * way to know whether it had succeeded.
   *
   * Every update is guarded by `it?.personId == id`: this call is in flight for as long as
   * [personRepository]'s suspending write takes, and by the time it resolves the user may have
   * cancelled this edit, or moved on to editing someone else entirely. Without the guard, a stale
   * result either closes whatever row is now open or, worse, stamps this row's error onto it.
   */
  fun rename(id: String, name: String) {
    if (name.isBlank()) return
    viewModelScope.launch {
      when (personRepository.rename(id, name)) {
        PersonRepository.RenameResult.Success,
        PersonRepository.RenameResult.NotFound ->
          _editing.update { if (it?.personId == id) null else it }
        PersonRepository.RenameResult.NameTaken -> {
          val message = "Someone is already named \"${name.trim()}\"."
          _editing.update { if (it?.personId == id) it.copy(error = message) else it }
        }
      }
    }
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

/**
 * Which person's row is open for editing, and any error from the last attempt to [PeopleViewModel]
 * `.rename` it. Owned by the ViewModel rather than row-local state so a `NameTaken` error can only
 * ever be showing on the one row it's actually about.
 */
data class RenameEdit(val personId: String, val error: String? = null)

sealed interface PeopleUiState {
  data object Loading : PeopleUiState

  data class Error(val throwable: Throwable) : PeopleUiState

  data class Success(val people: List<PersonEntity>, val editing: RenameEdit? = null) :
    PeopleUiState
}
