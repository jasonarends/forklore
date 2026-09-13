package com.jasonarends.forklore

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

/**
 * One restaurant in one list. Keyed by the `PlaceEntry` id, not the place id: the same place can
 * appear on two lists with two different verdicts.
 */
@Serializable data class PlaceDetail(val placeEntryId: String) : NavKey
