package com.jasonarends.forklore.ui.visiteditor

import android.database.sqlite.SQLiteException
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
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.PersonRepository
import com.jasonarends.forklore.data.repository.VisitRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The add/edit form for one visit: [visitId] null adds a visit to [placeEntryId], otherwise edits
 * that visit. Its own ViewModel, scoped to its own nav entry (see `MainNavigation`'s entry
 * decorators), so a draft belongs to exactly one editor and never leaks into another.
 *
 * [draft] is deliberately its own plain [MutableStateFlow], not folded into [uiState] via `combine`
 * — see [com.jasonarends.forklore.ui.addplace.AddPlaceViewModel]'s KDoc for why a `combine()`
 * sitting in the path of a field someone is actively typing into risks a stale value winning a
 * recomposition race. [uiState]'s own `combine` is safe: nothing in it is a text field someone
 * types into directly.
 *
 * "Today" is [clock] read in [zone]. [zone] is a supplier, not a value, so a device whose time zone
 * changes while this ViewModel is alive (travel) still gets the local date at the moment the editor
 * opens.
 */
class VisitEditorViewModel(
  private val visitRepository: VisitRepository,
  private val personRepository: PersonRepository,
  private val placeEntryId: String,
  private val visitId: String?,
  private val clock: Clock,
  private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

  private enum class Load {
    LOADING,
    READY,
    NOT_FOUND,
  }

  private val load = MutableStateFlow(Load.LOADING)

  val uiState: StateFlow<VisitEditorUiState> =
    combine(personRepository.observeAll(), load) { people, load ->
        when (load) {
          Load.LOADING -> VisitEditorUiState.Loading
          Load.NOT_FOUND -> VisitEditorUiState.NotFound
          Load.READY -> VisitEditorUiState.Ready(people)
        }
      }
      .catch { emit(VisitEditorUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisitEditorUiState.Loading)

  private val _draft = MutableStateFlow<VisitDraft?>(null)

  /** Null only while an edit's visit is still being read; an add has its draft from the start. */
  val draft: StateFlow<VisitDraft?> = _draft.asStateFlow()

  private val _saved = MutableStateFlow(false)

  /** True once the write has landed; the screen pops on it. */
  val saved: StateFlow<Boolean> = _saved.asStateFlow()

  private var initial: VisitDraft? = null

  /**
   * Whether the form differs from what it opened with. [VisitDraft.saving] and [VisitDraft.error]
   * are transient UI state, not something the person changed, so they never count. Opening an add
   * already carries today's date, so an untouched add is not dirty.
   */
  val hasUnsavedChanges: StateFlow<Boolean> =
    combine(_draft, _saved) { draft, saved ->
        !saved && draft != null && draft.comparable() != initial
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  init {
    if (visitId == null) {
      open(VisitDraft().withDay(today()))
    } else {
      viewModelScope.launch {
        val visit =
          try {
            visitRepository.observeForPlaceEntry(placeEntryId).first().firstOrNull {
              it.visit.id == visitId
            }
          } catch (_: SQLiteException) {
            null
          }
        if (visit == null) load.value = Load.NOT_FOUND else open(VisitDraft.from(visit))
      }
    }
  }

  private fun open(draft: VisitDraft) {
    initial = draft.comparable()
    _draft.value = draft
    load.value = Load.READY
  }

  private fun VisitDraft.comparable() = copy(saving = false, error = null)

  /**
   * Replaces whatever date the draft holds with the day [quickDate] names, at [DatePrecision.DAY].
   */
  fun onQuickDate(quickDate: QuickDate) = updateDraft {
    it.withDay(today().minusDays(quickDate.daysAgo)).copy(error = null)
  }

  private fun today(): LocalDate =
    Instant.ofEpochMilli(clock.nowMillis()).atZone(zone()).toLocalDate()

  fun onPrecisionChange(precision: DatePrecision) = updateDraft {
    it.copy(precision = precision, error = null)
  }

  fun onYearChange(value: String) = updateDraft { it.copy(year = value, error = null) }

  fun onMonthChange(value: String) = updateDraft { it.copy(month = value, error = null) }

  fun onDayChange(value: String) = updateDraft { it.copy(day = value, error = null) }

  fun onMealChange(meal: Meal?) = updateDraft { it.copy(meal = meal, error = null) }

  fun onNoteChange(note: String) = updateDraft { it.copy(note = note, error = null) }

  fun onAttendeesChange(attendees: Set<String>) = updateDraft {
    it.copy(attendees = attendees, error = null)
  }

  /**
   * The one write [com.jasonarends.forklore.ui.components.PersonPicker] causes directly. Per its
   * KDoc, the new id is folded into the draft's own selection here rather than left for the picker
   * to infer once [uiState] eventually replays the newly created person. Same crash/stuck-draft
   * risk as [save] on a write failure, so the same catch.
   */
  fun onCreatePerson(name: String, isHouseholdMember: Boolean) {
    viewModelScope.launch {
      try {
        val id = personRepository.findOrCreate(name, isHouseholdMember)
        updateDraft { it.copy(attendees = it.attendees + id) }
      } catch (_: SQLiteException) {
        _draft.update { it?.copy(error = "Couldn't add that person. Try again.") }
      }
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
    val epochDay =
      current.resolveDate().getOrElse {
        _draft.update { it?.copy(error = "Enter a valid date, or choose \"No date\".") }
        return
      }
    _draft.update { it?.copy(saving = true, error = null) }
    viewModelScope.launch {
      try {
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
          visitRepository.updateWithAttendees(visitId, current.attendees) { visit ->
            visit.copy(
              dateEpochDay = epochDay,
              datePrecision = current.precision,
              meal = current.meal,
              note = current.note,
            )
          }
        }
        _saved.value = true
      } catch (_: SQLiteException) {
        // A write that fails (a full disk, a constraint violation) must not crash the app or
        // leave Save stuck disabled forever — the person just needs to be able to try again. See
        // AddPlaceViewModel.save() for the same shape.
        _draft.update { it?.copy(saving = false, error = "Couldn't save this visit. Try again.") }
      }
    }
  }

  companion object {
    fun factory(placeEntryId: String, visitId: String?): ViewModelProvider.Factory =
      viewModelFactory {
        initializer {
          val app = this[APPLICATION_KEY] as ForkloreApp
          VisitEditorViewModel(
            app.container.visitRepository,
            app.container.personRepository,
            placeEntryId,
            visitId,
            app.container.clock,
          )
        }
      }
  }
}

sealed interface VisitEditorUiState {
  data object Loading : VisitEditorUiState

  data object NotFound : VisitEditorUiState

  data class Error(val throwable: Throwable) : VisitEditorUiState

  data class Ready(val people: List<PersonEntity>) : VisitEditorUiState
}

/** The date chips offered beside the precision picker; see [VisitsViewModel.onQuickDate]. */
enum class QuickDate(val label: String, val daysAgo: Long) {
  TODAY("Today", 0),
  YESTERDAY("Yesterday", 1),
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
   * fail on anything from a still-in-progress edit (a blank field) to a real calendar impossibility
   * (April 31st); [DatePrecision.MONTH] and [DatePrecision.YEAR] fill in the day/month Room doesn't
   * ask them to know, per [VisitRepository.record]'s contract that a caller who only knows the
   * month passes the first of it, never a fabricated day.
   */
  fun resolveDate(): Result<Long?> =
    when (precision) {
      DatePrecision.UNKNOWN -> Result.success(null)
      DatePrecision.DAY ->
        runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()).toEpochDay() }
      DatePrecision.MONTH ->
        runCatching { LocalDate.of(year.toInt(), month.toInt(), 1).toEpochDay() }
      DatePrecision.YEAR -> runCatching { LocalDate.of(year.toInt(), 1, 1).toEpochDay() }
    }

  fun withDay(date: LocalDate): VisitDraft =
    copy(
      precision = DatePrecision.DAY,
      year = date.year.toString(),
      month = date.monthValue.toString(),
      day = date.dayOfMonth.toString(),
    )

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
