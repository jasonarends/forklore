package com.jasonarends.forklore.ui.placedetail

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jasonarends.forklore.ForkloreApp
import com.jasonarends.forklore.data.db.DishInterestEntity
import com.jasonarends.forklore.data.db.DishStatus
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.data.repository.DishRepository
import com.jasonarends.forklore.data.repository.PersonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Every dish interest at one place entry, plus the add/edit form currently open. A separate
 * ViewModel from [DishesViewModel] for the same reason that one is separate from
 * [PlaceDetailViewModel]: interests come from their own flow and carry their own form state, and
 * keeping them apart lets the dish list and the interest editor evolve without sharing a state
 * class.
 *
 * [draft] is its own plain [MutableStateFlow] rather than folded into [uiState], per
 * [VisitsViewModel]: a `combine()` in the path of a field being typed into risks a stale value
 * winning a recomposition race.
 *
 * A dish can have several interests ("Robin wants chicken", "Holly: never again"), so the form
 * edits one row at a time, addressed by [InterestDraft.interestId] (null while adding).
 */
class DishInterestsViewModel(
  private val dishRepository: DishRepository,
  private val personRepository: PersonRepository,
  private val placeEntryId: String,
) : ViewModel() {

  val uiState: StateFlow<DishInterestsUiState> =
    combine(dishRepository.observeInterests(placeEntryId), personRepository.observeAll()) {
        interests,
        people ->
        DishInterestsUiState.Success(interests, people) as DishInterestsUiState
      }
      .catch { emit(DishInterestsUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DishInterestsUiState.Loading)

  private val _draft = MutableStateFlow<InterestDraft?>(null)
  val draft: StateFlow<InterestDraft?> = _draft.asStateFlow()

  /** Opens a blank form for [dishId], discarding any other open form: one edit at a time. */
  fun startAdd(dishId: String) {
    _draft.value = InterestDraft(dishId = dishId)
  }

  fun startEdit(interest: DishInterestEntity) {
    _draft.value = InterestDraft.from(interest)
  }

  fun cancelDraft() {
    _draft.value = null
  }

  fun onStatusChange(status: DishStatus) = updateDraft { it.copy(status = status, error = null) }

  fun onForPersonChange(personId: String?) = updateDraft {
    it.copy(forPersonId = personId, error = null)
  }

  fun onRecommendedByChange(personId: String?) = updateDraft {
    it.copy(recommendedById = personId, error = null)
  }

  fun onModificationChange(text: String) = updateDraft {
    it.copy(modification = text, error = null)
  }

  fun onNoteChange(text: String) = updateDraft { it.copy(note = text, error = null) }

  /**
   * The write [com.jasonarends.forklore.ui.components.PersonPicker] causes for the "for" field. Per
   * that component's KDoc the created (or already existing) id is selected here, since the picker
   * only learns the person exists once [uiState] re-emits.
   */
  fun onCreateForPerson(name: String) = createPerson(name) { id -> onForPersonChange(id) }

  fun onCreateRecommender(name: String) = createPerson(name) { id -> onRecommendedByChange(id) }

  private fun createPerson(name: String, select: (String) -> Unit) {
    viewModelScope.launch {
      try {
        select(personRepository.findOrCreate(name))
      } catch (_: SQLiteException) {
        _draft.update { it?.copy(error = "Couldn't add that person. Try again.") }
      }
    }
  }

  private fun updateDraft(change: (InterestDraft) -> InterestDraft) {
    _draft.update { it?.let(change) }
  }

  /** No-op with no open form or one already saving, so a double tap can't insert twice. */
  fun save() {
    val current = _draft.value ?: return
    if (current.saving) return
    _draft.update { it?.copy(saving = true, error = null) }
    viewModelScope.launch {
      try {
        val interestId = current.interestId
        if (interestId == null) {
          dishRepository.setInterest(
            dishId = current.dishId,
            status = current.status,
            forPersonId = current.forPersonId,
            recommendedById = current.recommendedById,
            modification = current.modification,
            note = current.note,
          )
        } else {
          dishRepository.updateInterest(
            interestId = interestId,
            status = current.status,
            forPersonId = current.forPersonId,
            recommendedById = current.recommendedById,
            modification = current.modification,
            note = current.note,
          )
        }
        _draft.value = null
      } catch (_: SQLiteException) {
        _draft.update { it?.copy(saving = false, error = "Couldn't save this. Try again.") }
      }
    }
  }

  /** Removes the interest being edited. Nothing to remove while adding, so that's a no-op. */
  fun remove() {
    val current = _draft.value ?: return
    val interestId = current.interestId ?: return
    if (current.saving) return
    _draft.update { it?.copy(saving = true, error = null) }
    viewModelScope.launch {
      try {
        dishRepository.removeInterest(interestId)
        _draft.value = null
      } catch (_: SQLiteException) {
        _draft.update { it?.copy(saving = false, error = "Couldn't remove this. Try again.") }
      }
    }
  }

  companion object {
    fun factory(placeEntryId: String): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY] as ForkloreApp
        DishInterestsViewModel(
          app.container.dishRepository,
          app.container.personRepository,
          placeEntryId,
        )
      }
    }
  }
}

sealed interface DishInterestsUiState {
  data object Loading : DishInterestsUiState

  data class Error(val throwable: Throwable) : DishInterestsUiState

  data class Success(val interests: List<DishInterestEntity>, val people: List<PersonEntity>) :
    DishInterestsUiState {
    fun forDish(dishId: String): List<DishInterestEntity> = interests.filter { it.dishId == dishId }
  }
}

/**
 * The add/edit form's own state. [interestId] is null for a new interest and set for an edit in
 * progress; [dishId] says which dish's row the form opens under.
 */
data class InterestDraft(
  val interestId: String? = null,
  val dishId: String,
  val status: DishStatus = DishStatus.WANT,
  val forPersonId: String? = null,
  val recommendedById: String? = null,
  val modification: String = "",
  val note: String = "",
  val saving: Boolean = false,
  val error: String? = null,
) {
  companion object {
    fun from(interest: DishInterestEntity) =
      InterestDraft(
        interestId = interest.id,
        dishId = interest.dishId,
        status = interest.status,
        forPersonId = interest.forPersonId,
        recommendedById = interest.recommendedById,
        modification = interest.modification ?: "",
        note = interest.note,
      )
  }
}
