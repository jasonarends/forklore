package com.jasonarends.forklore.ui.placedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jasonarends.forklore.ForkloreApp
import com.jasonarends.forklore.data.db.DatePrecision
import com.jasonarends.forklore.data.db.Meal
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.data.db.VisitWithAttendees
import com.jasonarends.forklore.data.repository.PersonRepository
import com.jasonarends.forklore.data.repository.VisitRepository
import java.time.LocalDate
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
 * Visits for one place entry, plus whatever add/edit form is currently open.
 *
 * [draft] is deliberately its own plain [MutableStateFlow], not folded into [uiState] via `combine`
 * — see [com.jasonarends.forklore.ui.addplace.AddPlaceViewModel]'s KDoc for why a `combine()`
 * sitting in the path of a field someone is actively typing into risks a stale value winning a
 * recomposition race. [uiState]'s own `combine` is safe: nothing in it is a text field someone
 * types into directly.
 */
class VisitsViewModel(
  private val visitRepository: VisitRepository,
  private val personRepository: PersonRepository,
  private val placeEntryId: String,
) : ViewModel() {

  val uiState: StateFlow<VisitsUiState> =
    combine(visitRepository.observeForPlaceEntry(placeEntryId), personRepository.observeAll()) {
        visits,
        people ->
        VisitsUiState.Success(visits, people) as VisitsUiState
      }
      .catch { emit(VisitsUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisitsUiState.Loading)

  private val _draft = MutableStateFlow<VisitDraft?>(null)
  val draft: StateFlow<VisitDraft?> = _draft.asStateFlow()

  fun startAdd() {
    _draft.value = VisitDraft()
  }

  fun startEdit(visit: VisitWithAttendees) {
    _draft.value = VisitDraft.from(visit)
  }

  fun cancelDraft() {
    _draft.value = null
  }

  fun onPrecisionChange(precision: DatePrecision) = updateDraft {
    it.copy(precision = precision, error = null)
  }

  fun onYearChange(value: String) = updateDraft { it.copy(year = value, error = null) }

  fun onMonthChange(value: String) = updateDraft { it.copy(month = value, error = null) }

  fun onDayChange(value: String) = updateDraft { it.copy(day = value, error = null) }

  fun onMealChange(meal: Meal?) = updateDraft { it.copy(meal = meal) }

  fun onNoteChange(note: String) = updateDraft { it.copy(note = note) }

  fun onAttendeesChange(attendees: Set<String>) = updateDraft { it.copy(attendees = attendees) }

  /**
   * The one write [com.jasonarends.forklore.ui.components.PersonPicker] causes directly. Per its
   * KDoc, the new id is folded into the draft's own selection here rather than left for the picker
   * to infer once [uiState] eventually replays the newly created person.
   */
  fun onCreatePerson(name: String) {
    viewModelScope.launch {
      val id = personRepository.findOrCreate(name)
      updateDraft { it.copy(attendees = it.attendees + id) }
    }
  }

  private fun updateDraft(change: (VisitDraft) -> VisitDraft) {
    _draft.update { it?.let(change) }
  }

  /**
   * No-op on an already-saving draft or one whose date can't be resolved — see
   * [VisitDraft.resolveDate].
   */
  fun save() {
    val current = _draft.value ?: return
    if (current.saving) return
    val resolution = current.resolveDate()
    if (resolution is DateResolution.Invalid) {
      _draft.update { it?.copy(error = "Enter a valid date, or choose \"No date\".") }
      return
    }
    val epochDay = (resolution as DateResolution.Ready).epochDay
    _draft.update { it?.copy(saving = true, error = null) }
    viewModelScope.launch {
      val visitId = current.visitId
      if (visitId == null) {
        visitRepository.record(
          placeEntryId = placeEntryId,
          dateEpochDay = epochDay,
          datePrecision = current.precision,
          meal = current.meal,
          note = current.note,
          attendees = current.attendees.toList(),
        )
      } else {
        visitRepository.update(visitId) { visit ->
          visit.copy(
            dateEpochDay = epochDay,
            datePrecision = current.precision,
            meal = current.meal,
            note = current.note,
          )
        }
        visitRepository.setAttendees(visitId, current.attendees)
      }
      _draft.value = null
    }
  }

  companion object {
    fun factory(placeEntryId: String): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY] as ForkloreApp
        VisitsViewModel(app.container.visitRepository, app.container.personRepository, placeEntryId)
      }
    }
  }
}

sealed interface VisitsUiState {
  data object Loading : VisitsUiState

  data class Error(val throwable: Throwable) : VisitsUiState

  data class Success(val visits: List<VisitWithAttendees>, val people: List<PersonEntity>) :
    VisitsUiState
}

/**
 * The add/edit form's own state. [year], [month] and [day] stay plain strings rather than `Int?`: a
 * text field passes through "not yet a valid number" and "not yet typed" on every keystroke, and
 * forcing either into `Int?` this early would either reject a still-being-typed "2" or invent a
 * default nobody chose. [visitId] is null for a new visit and set for an edit in progress.
 */
data class VisitDraft(
  val visitId: String? = null,
  val precision: DatePrecision = DatePrecision.UNKNOWN,
  val year: String = "",
  val month: String = "",
  val day: String = "",
  val meal: Meal? = null,
  val note: String = "",
  val attendees: Set<String> = emptySet(),
  val saving: Boolean = false,
  val error: String? = null,
) {
  /**
   * [DatePrecision.UNKNOWN] always resolves to no date — no fields needed. The other precisions
   * resolve to [DateResolution.Invalid] on anything from a still-in-progress edit (a blank field)
   * to a real calendar impossibility (April 31st); [DatePrecision.MONTH] and [DatePrecision.YEAR]
   * fill in the day/month Room doesn't ask them to know, per [VisitRepository.record]'s contract
   * that a caller who only knows the month passes the first of it, never a fabricated day.
   */
  fun resolveDate(): DateResolution =
    when (precision) {
      DatePrecision.UNKNOWN -> DateResolution.Ready(null)
      DatePrecision.DAY ->
        runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()).toEpochDay() }
          .fold({ DateResolution.Ready(it) }, { DateResolution.Invalid })
      DatePrecision.MONTH ->
        runCatching { LocalDate.of(year.toInt(), month.toInt(), 1).toEpochDay() }
          .fold({ DateResolution.Ready(it) }, { DateResolution.Invalid })
      DatePrecision.YEAR ->
        runCatching { LocalDate.of(year.toInt(), 1, 1).toEpochDay() }
          .fold({ DateResolution.Ready(it) }, { DateResolution.Invalid })
    }

  companion object {
    fun from(visit: VisitWithAttendees): VisitDraft {
      val entity = visit.visit
      val date = entity.dateEpochDay?.let(LocalDate::ofEpochDay)
      return VisitDraft(
        visitId = entity.id,
        precision = entity.datePrecision,
        year = date?.year?.toString() ?: "",
        month = date?.monthValue?.toString() ?: "",
        day =
          if (entity.datePrecision == DatePrecision.DAY) date?.dayOfMonth?.toString() ?: "" else "",
        meal = entity.meal,
        note = entity.note,
        attendees = visit.attendees.map { it.id }.toSet(),
      )
    }
  }
}

sealed interface DateResolution {
  data class Ready(val epochDay: Long?) : DateResolution

  data object Invalid : DateResolution
}
