package com.jasonarends.forklore.data.repository

import androidx.room.withTransaction
import com.jasonarends.forklore.data.db.DatePrecision
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.Meal
import com.jasonarends.forklore.data.db.VisitAttendeeEntity
import com.jasonarends.forklore.data.db.VisitDao
import com.jasonarends.forklore.data.db.VisitEntity
import com.jasonarends.forklore.data.db.VisitWithAttendees
import kotlinx.coroutines.flow.Flow

/** Occasions at a place, with whoever was there. */
class VisitRepository(
  private val database: ForkloreDatabase,
  private val visitDao: VisitDao,
  private val clock: Clock = Clock.System,
) {
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

  /**
   * [update] and [setAttendees] as one transaction: an editor saving a visit treats the date/meal/
   * note change and the attendee change as a single action, and a crash between two separate writes
   * would otherwise leave the visit with an attendee set that is neither the old one nor the new
   * one.
   */
  suspend fun updateWithAttendees(
    visitId: String,
    personIds: Set<String>,
    change: (VisitEntity) -> VisitEntity,
  ) {
    database.withTransaction {
      update(visitId, change)
      setAttendees(visitId, personIds)
    }
  }

  /**
   * Replaces who attended with exactly [personIds]: anyone missing from the current attendee list
   * is added, anyone currently recorded but no longer in [personIds] is soft-deleted (see
   * CLAUDE.md, "Delete means soft delete") rather than removed outright. A person re-added after
   * being dropped resurrects their old tombstoned row instead of inserting a second one, the same
   * way [PersonRepository.findOrCreate] resurrects a tombstoned person — the unique index on
   * (visitId, personId) would otherwise reject the fresh insert. Runs as one transaction: a crash
   * partway through must not leave the attendee set half added, half removed.
   */
  suspend fun setAttendees(visitId: String, personIds: Set<String>) {
    val now = clock.nowMillis()
    database.withTransaction {
      val existing = visitDao.attendeesForVisitIncludingDeleted(visitId)
      val (live, tombstoned) = existing.partition { it.deletedAt == null }
      val livePersonIds = live.map { it.personId }.toSet()
      val tombstonedByPerson = tombstoned.associateBy { it.personId }

      live
        .filter { it.personId !in personIds }
        .forEach { visitDao.updateAttendee(it.copy(deletedAt = now, updatedAt = now)) }

      (personIds - livePersonIds).forEach { personId ->
        val existingTombstone = tombstonedByPerson[personId]
        if (existingTombstone != null) {
          visitDao.updateAttendee(existingTombstone.copy(deletedAt = null, updatedAt = now))
        } else {
          visitDao.addAttendee(
            VisitAttendeeEntity(
              visitId = visitId,
              personId = personId,
              createdAt = now,
              updatedAt = now,
            )
          )
        }
      }
    }
  }
}
