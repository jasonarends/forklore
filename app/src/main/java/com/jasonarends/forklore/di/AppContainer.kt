package com.jasonarends.forklore.di

import android.content.Context
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.DishRepository
import com.jasonarends.forklore.data.repository.PersonRepository
import com.jasonarends.forklore.data.repository.PlaceListRepository
import com.jasonarends.forklore.data.repository.PlaceRepository
import com.jasonarends.forklore.data.repository.VisitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The whole object graph, by hand.
 *
 * There is no DI framework here on purpose: the graph is a database and five repositories, and a
 * generated one would cost build time and indirection without buying anything. Feature work adds a
 * repository property here and reaches it from a ViewModel's `Factory` companion — see CLAUDE.md
 * for the one sanctioned way a composable gets at this.
 *
 * DAOs are deliberately not exposed. ViewModels depend on repositories, which own timestamp
 * stamping, soft deletes and name normalization; a ViewModel holding a DAO would quietly skip all
 * three.
 */
class AppContainer(context: Context, private val clock: Clock = Clock.System) {
  private val database: ForkloreDatabase by lazy { ForkloreDatabase.build(context) }

  val placeListRepository by lazy { PlaceListRepository(database.placeListDao(), clock) }
  val placeRepository by lazy {
    PlaceRepository(database.placeDao(), database.placeEntryDao(), clock)
  }
  val personRepository by lazy { PersonRepository(database.personDao(), clock) }
  val visitRepository by lazy { VisitRepository(database.visitDao(), clock) }
  val dishRepository by lazy {
    DishRepository(database.dishDao(), database.dishInterestDao(), database.dishOpinionDao(), clock)
  }

  private val _currentPlaceListId = MutableStateFlow<String?>(null)

  /**
   * Which list the UI is showing. Null until the default list is resolved at startup, which is why
   * screens must render a sane empty state rather than assuming a list exists.
   */
  val currentPlaceListId: StateFlow<String?> = _currentPlaceListId.asStateFlow()

  fun setCurrentPlaceList(id: String) {
    _currentPlaceListId.value = id
  }
}
