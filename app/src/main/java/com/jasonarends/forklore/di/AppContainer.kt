package com.jasonarends.forklore.di

import android.content.Context
import com.jasonarends.forklore.data.db.ForkloreDatabase

/**
 * The whole object graph, by hand.
 *
 * There is no DI framework here on purpose: the graph is a database and a handful of repositories,
 * and a generated one would cost build time and indirection without buying anything. Feature work
 * adds a repository property here and reaches it from a ViewModel's `Factory` companion — see
 * CLAUDE.md for the one sanctioned way a composable gets at this.
 */
open class AppContainer(context: Context) {
  open val database: ForkloreDatabase by lazy { ForkloreDatabase.build(context) }

  open val placeListDao by lazy { database.placeListDao() }
  open val personDao by lazy { database.personDao() }
  open val placeDao by lazy { database.placeDao() }
  open val placeEntryDao by lazy { database.placeEntryDao() }
  open val visitDao by lazy { database.visitDao() }
  open val dishDao by lazy { database.dishDao() }
  open val dishInterestDao by lazy { database.dishInterestDao() }
  open val dishOpinionDao by lazy { database.dishOpinionDao() }
}
