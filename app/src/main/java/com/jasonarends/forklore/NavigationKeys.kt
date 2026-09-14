package com.jasonarends.forklore

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object AddPlace : NavKey

/**
 * A stub destination: this issue only needs somewhere for a tapped place to go. #2 fills the screen
 * in; the navigation key carrying [placeEntryId] is the contract between the two.
 */
@Serializable data class PlaceDetail(val placeEntryId: String) : NavKey

@Serializable data object People : NavKey
