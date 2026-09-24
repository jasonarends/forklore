package com.jasonarends.forklore.ui.placedetail

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jasonarends.forklore.ForkloreApp
import com.jasonarends.forklore.data.db.DishOpinionEntity
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.data.db.TemperatureRating
import com.jasonarends.forklore.data.db.VisitWithAttendees
import com.jasonarends.forklore.data.repository.DishRepository
import com.jasonarends.forklore.data.repository.PersonRepository
import com.jasonarends.forklore.data.repository.VisitOutsidePlaceEntryException
import com.jasonarends.forklore.data.repository.VisitRepository
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
 * Every author's opinion of every dish at one place entry, plus whichever add/edit form is open.
 * Its own ViewModel, not more fields on [DishesViewModel]: opinions come from three repositories
 * (dishes, people, visits) and carry their own draft, and the dish list stays usable while this one
 * loads or fails.
 *
 * [draft] is a plain [MutableStateFlow] rather than folded into [uiState], for the same reason as
 * [VisitsViewModel.draft]: it holds a text field someone is typing into.
 */
class DishOpinionsViewModel(
  private val dishRepository: DishRepository,
  private val personRepository: PersonRepository,
  visitRepository: VisitRepository,
  placeEntryId: String,
) : ViewModel() {

  val uiState: StateFlow<DishOpinionsUiState> =
    combine<
        List<DishOpinionEntity>,
        List<PersonEntity>,
        List<VisitWithAttendees>,
        DishOpinionsUiState,
      >(
        dishRepository.observeOpinionsForPlaceEntry(placeEntryId),
        personRepository.observeAll(),
        visitRepository.observeForPlaceEntry(placeEntryId),
      ) { opinions, people, visits ->
        DishOpinionsUiState.Success(
          byDish = buildDishOpinions(opinions, people),
          people = people,
          visits = visits,
        )
      }
      .catch { emit(DishOpinionsUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DishOpinionsUiState.Loading)

  private val _draft = MutableStateFlow<OpinionDraft?>(null)
  val draft: StateFlow<OpinionDraft?> = _draft.asStateFlow()

  fun startAdd(dishId: String) {
    _draft.value = OpinionDraft(dishId = dishId)
  }

  /**
   * A link to a visit that has since been deleted is kept as-is, not dropped: the picker simply
   * shows nothing selected, saving leaves the link alone ([DishRepository.updateOpinion] doesn't
   * re-validate an unchanged one), and choosing a live visit replaces it.
   */
  fun startEdit(opinion: DishOpinionEntity) {
    _draft.value =
      OpinionDraft(
        dishId = opinion.dishId,
        opinionId = opinion.id,
        authorId = opinion.authorId,
        rating = opinion.rating,
        temperature = opinion.temperature,
        note = opinion.note,
        visitId = opinion.visitId,
      )
  }

  /** Ignored while a save or delete is in flight, so its result can't land on a different draft. */
  fun cancelDraft() {
    _draft.update { if (it?.saving == true) it else null }
  }

  fun onAuthorChange(selection: Set<String>) = updateDraft {
    it.copy(authorId = selection.firstOrNull(), error = null)
  }

  fun onRatingChange(rating: Rating?) = updateDraft { it.copy(rating = rating, error = null) }

  fun onTemperatureChange(temperature: TemperatureRating?) = updateDraft {
    it.copy(temperature = temperature, error = null)
  }

  fun onNoteChange(note: String) = updateDraft { it.copy(note = note, error = null) }

  fun onVisitChange(visitId: String?) = updateDraft { it.copy(visitId = visitId, error = null) }

  /** Same shape as [VisitsViewModel.onCreatePerson]: the new person becomes the draft's author. */
  fun onCreatePerson(name: String) {
    viewModelScope.launch {
      try {
        val id = personRepository.findOrCreate(name)
        updateDraft { it.copy(authorId = id, error = null) }
      } catch (_: SQLiteException) {
        _draft.update { it?.copy(error = "Couldn't add that person. Try again.") }
      }
    }
  }

  /** Edits are ignored while saving: what was typed after Save was tapped would be lost anyway. */
  private fun updateDraft(change: (OpinionDraft) -> OpinionDraft) {
    _draft.update { if (it == null || it.saving) it else change(it) }
  }

  /** Clears the draft only if it is still the one being saved; see [cancelDraft]. */
  private fun finishDraft(saved: OpinionDraft) {
    _draft.update {
      if (it?.dishId == saved.dishId && it.opinionId == saved.opinionId) null else it
    }
  }

  /** No-op on a draft that can't be saved yet or is already saving; see [OpinionDraft.canSave]. */
  fun save() {
    val current = _draft.value ?: return
    if (current.saving || !current.canSave) return
    val authorId = current.authorId ?: return
    _draft.update { it?.copy(saving = true, error = null) }
    viewModelScope.launch {
      try {
        if (current.opinionId == null) {
          dishRepository.recordOpinion(
            dishId = current.dishId,
            authorId = authorId,
            rating = current.rating,
            note = current.note,
            visitId = current.visitId,
            temperature = current.temperature,
          )
        } else {
          dishRepository.updateOpinion(
            opinionId = current.opinionId,
            authorId = authorId,
            rating = current.rating,
            temperature = current.temperature,
            note = current.note,
            visitId = current.visitId,
          )
        }
        finishDraft(current)
      } catch (_: SQLiteException) {
        _draft.update { it?.copy(saving = false, error = "Couldn't save this opinion. Try again.") }
      } catch (_: VisitOutsidePlaceEntryException) {
        _draft.update {
          it?.copy(saving = false, error = "That visit isn't part of this place. Pick another.")
        }
      }
    }
  }

  /** Only meaningful while editing an existing opinion; a new draft has nothing to delete. */
  fun delete() {
    val current = _draft.value ?: return
    val opinionId = current.opinionId ?: return
    if (current.saving) return
    _draft.update { it?.copy(saving = true, error = null) }
    viewModelScope.launch {
      try {
        dishRepository.deleteOpinion(opinionId)
        finishDraft(current)
      } catch (_: SQLiteException) {
        _draft.update {
          it?.copy(saving = false, error = "Couldn't delete this opinion. Try again.")
        }
      }
    }
  }

  companion object {
    fun factory(placeEntryId: String): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY] as ForkloreApp
        DishOpinionsViewModel(
          app.container.dishRepository,
          app.container.personRepository,
          app.container.visitRepository,
          placeEntryId,
        )
      }
    }
  }
}

sealed interface DishOpinionsUiState {
  data object Loading : DishOpinionsUiState

  data class Error(val throwable: Throwable) : DishOpinionsUiState

  /**
   * [byDish] has an entry only for dishes that have at least one opinion; [people] and [visits]
   * feed the editor's author and visit pickers.
   */
  data class Success(
    val byDish: Map<String, DishOpinions>,
    val people: List<PersonEntity>,
    val visits: List<VisitWithAttendees>,
  ) : DishOpinionsUiState
}

/**
 * The colour slot an author's card is drawn in. Issue #15 defines two person colours, so a third
 * author needs a rule: only the first two people (household members first, then oldest) get one,
 * everyone else is [Neutral]. Derived from the people list alone, never from who happened to author
 * this dish, so a person keeps their colour on every dish.
 */
enum class AuthorTone {
  First,
  Second,
  Neutral,
}

data class OpinionCard(
  val opinion: DishOpinionEntity,
  val authorName: String,
  val tone: AuthorTone,
)

/**
 * One dish's opinions, ready to draw. "Disagree" is deliberately blunt: two *different authors*
 * gave *different* values. There is no threshold — the source note's own example is Bad against
 * "fine", one step apart, and that is exactly the case worth surfacing. Ratings and temperatures
 * are judged separately, and an opinion that left a field blank takes no side on it. It compares
 * every opinion, not each author's latest, so an author with an old "Bad" and a newer "Good" still
 * disagrees with someone who says "Good"; one author contradicting only themselves, with nobody
 * else weighing in, is not a disagreement.
 */
data class DishOpinions(
  val cards: List<OpinionCard>,
  val ratingsDisagree: Boolean,
  val temperaturesDisagree: Boolean,
  /** Whether any card recorded a temperature, i.e. whether cards without one need a placeholder. */
  val anyTemperature: Boolean,
)

internal fun buildDishOpinions(
  opinions: List<DishOpinionEntity>,
  people: List<PersonEntity>,
): Map<String, DishOpinions> {
  val tones = authorTones(people)
  val names = people.associate { it.id to it.name }
  return opinions
    .groupBy { it.dishId }
    .mapValues { (_, dishOpinions) ->
      DishOpinions(
        cards =
          dishOpinions.map {
            OpinionCard(
              opinion = it,
              authorName = names[it.authorId] ?: "Someone",
              tone = tones[it.authorId] ?: AuthorTone.Neutral,
            )
          },
        ratingsDisagree = authorsDisagree(dishOpinions) { it.rating },
        temperaturesDisagree = authorsDisagree(dishOpinions) { it.temperature },
        anyTemperature = dishOpinions.any { it.temperature != null },
      )
    }
}

/**
 * True when two different authors gave different values. Holds iff there are at least two distinct
 * authors *and* at least two distinct values among the opinions that gave one: given two values,
 * either two authors hold them separately, or one author holds both and another author's value
 * differs from at least one of them.
 */
private fun authorsDisagree(
  opinions: List<DishOpinionEntity>,
  value: (DishOpinionEntity) -> Any?,
): Boolean {
  val given = opinions.filter { value(it) != null }
  return given.map { it.authorId }.distinct().size >= 2 && given.map(value).distinct().size >= 2
}

private fun authorTones(people: List<PersonEntity>): Map<String, AuthorTone> =
  people
    .sortedWith(compareBy({ !it.isHouseholdMember }, { it.createdAt }, { it.id }))
    .take(2)
    .mapIndexed { index, person ->
      person.id to if (index == 0) AuthorTone.First else AuthorTone.Second
    }
    .toMap()

/**
 * The add/edit form's own state. [opinionId] is null for a new opinion. Saving needs an author (an
 * opinion is someone's) and *something* said, but no particular field: rating, temperature and note
 * are each optional, and a note alone is a complete opinion.
 */
data class OpinionDraft(
  val dishId: String,
  val opinionId: String? = null,
  val authorId: String? = null,
  val rating: Rating? = null,
  val temperature: TemperatureRating? = null,
  val note: String = "",
  val visitId: String? = null,
  val saving: Boolean = false,
  val error: String? = null,
) {
  val canSave: Boolean
    get() = authorId != null && (rating != null || temperature != null || note.isNotBlank())
}
