package com.jasonarends.forklore

import android.app.Application
import com.jasonarends.forklore.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ForkloreApp : Application() {
  lateinit var container: AppContainer
    private set

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
    appScope.launch {
      // First run has no list; the app must be usable immediately and without an account,
      // so one is created locally rather than waiting on sign-in.
      val existing = container.placeListRepository.observeAll().first()
      val id = existing.firstOrNull()?.id ?: container.placeListRepository.create(DEFAULT_LIST_NAME)
      container.setCurrentPlaceList(id)
    }
  }

  private companion object {
    const val DEFAULT_LIST_NAME = "Our list"
  }
}
