package com.jasonarends.forklore.data.repository

/** Wall clock, injected so tests can pin time instead of racing it. */
fun interface Clock {
  fun nowMillis(): Long

  companion object {
    val System = Clock { java.lang.System.currentTimeMillis() }
  }
}
