package com.jasonarends.forklore.data.repository

import com.jasonarends.forklore.data.db.DatePrecision
import com.jasonarends.forklore.data.db.Meal
import com.jasonarends.forklore.data.db.VisitAttendeeEntity
import com.jasonarends.forklore.data.db.VisitDao
import com.jasonarends.forklore.data.db.VisitEntity
import com.jasonarends.forklore.data.db.VisitWithAttendees
import kotlinx.coroutines.flow.Flow

/** Occasions at a place, with whoever was there. */
class VisitRepository(private val visitDao: VisitDao, private val clock: Clock = Clock.System) {
  fun observeForPlaceEntry(placeEntryId: String): Flow<List<VisitWithAttendees>> =
    visitDao.observeForPlaceEntry(placeEntryId)

  /**
   * [dateEpochDay] and [datePrecision] travel together: a caller that knows only the month passes
   * the first of that month with [DatePrecision.MONTH], never a fabricated day.
   */
  suspend fun record(
    placeEntryId: String,
    dateEpochDay: Long?,
    datePrecision: DatePrecision,
    meal: Meal? = null,
    note: String = "",
    authorId: String? = null,
    attendees: List<String> = emptyList(),
  ): String {
    val now = clock.nowMillis()
    val visit =
      VisitEntity(
        placeEntryId = placeEntryId,
        dateEpochDay = dateEpochDay,
        datePrecision = datePrecision,
        meal = meal,
        note = note,
        authorId = authorId,
        createdAt = now,
        updatedAt = now,
      )
    visitDao.insert(visit)
    attendees.forEach {
      visitDao.addAttendee(
        VisitAttendeeEntity(visitId = visit.id, personId = it, createdAt = now, updatedAt = now)
      )
    }
    return visit.id
  }

  suspend fun update(visitId: String, change: (VisitEntity) -> VisitEntity) {
    val current = visitDao.byId(visitId) ?: return
    visitDao.update(change(current).copy(updatedAt = clock.nowMillis()))
  }
}
