package com.jasonarends.forklore

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object AddPlace : NavKey

/**
 * One restaurant in one list. Keyed by the `PlaceEntry` id, not the place id: the same place can
 * appear on two lists with two different verdicts.
 */
@Serializable data class PlaceDetail(val placeEntryId: String) : NavKey

/**
 * The add/edit visit screen. Ids only: [visitId] null adds a visit to [placeEntryId], otherwise it
 * edits that visit. The editor loads the row itself, so a key restored after process death never
 * carries a stale copy of it.
 */
@Serializable data class VisitEditor(val placeEntryId: String, val visitId: String? = null) : NavKey

@Serializable data object People : NavKey

/** What the current list wants to try and must never reorder, across all its places. */
@Serializable data object DishInterestList : NavKey
