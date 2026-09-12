package com.jasonarends.forklore

import android.app.Application
import com.jasonarends.forklore.di.AppContainer

class ForkloreApp : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
  }
}
