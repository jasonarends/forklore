package com.jasonarends.forklore.ui.visiteditor

import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.jasonarends.forklore.data.db.ForkloreDatabase
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceListEntity
import com.jasonarends.forklore.data.db.VisitDao
import com.jasonarends.forklore.data.db.VisitEntity
import com.jasonarends.forklore.data.repository.Clock
import com.jasonarends.forklore.data.repository.PersonRepository
import com.jasonarends.forklore.data.repository.VisitRepository
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The screen on its own, against a real database whose visit insert can be held open. Room keeps
 * its own executors here: with the direct ones other tests use, a gate held inside
 * `withTransaction` would block the test thread itself.
 */
@RunWith(RobolectricTestRunner::class)
class VisitEditorScreenTest {
  @get:Rule val compose = createComposeRule()

  private val db: ForkloreDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        ForkloreDatabase::class.java,
      )
      .allowMainThreadQueries()
      .build()

  @After fun tearDown() = db.close()

  @Test
  fun backDuringAnInFlightSave_doesNothing_thenPopsOnceTheSaveLands() {
    val gate = CompletableDeferred<Unit>()
    runBlocking {
      db
        .placeListDao()
        .insert(PlaceListEntity(id = "list", name = "Ours", createdAt = 0, updatedAt = 0))
      db
        .placeDao()
        .insert(PlaceEntity(id = "place", name = "Halberd", createdAt = 0, updatedAt = 0))
      db
        .placeEntryDao()
        .insert(
          PlaceEntryEntity(
            id = "entry",
            placeListId = "list",
            placeId = "place",
            createdAt = 0,
            updatedAt = 0,
          )
        )
    }
    val gatedDao =
      object : VisitDao by db.visitDao() {
        override suspend fun insert(visit: VisitEntity) {
          gate.await()
          db.visitDao().insert(visit)
        }
      }
    val viewModel =
      VisitEditorViewModel(
        VisitRepository(db, gatedDao, Clock { 0L }),
        PersonRepository(db.personDao(), Clock { 0L }),
        "entry",
        null,
        Clock { 1_790_992_800_000L },
      )
    var done = 0
    compose.setContent {
      ForkloreTheme {
        // Stands in for NavDisplay's own pop, which handles back whenever the screen doesn't.
        BackHandler { done++ }
        VisitEditorScreen(
          placeEntryId = "entry",
          visitId = null,
          onDone = { done++ },
          viewModel = viewModel,
        )
      }
    }
    idle()
    compose
      .onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("visit-note")))
      .performScrollTo()
      .performTextInput("Verano")
    idle()

    compose.onNodeWithTag("visit-save").performScrollTo().performClick()
    idle()

    Espresso.pressBack()
    idle()
    compose.onNodeWithText("Discard changes?").assertDoesNotExist()
    compose.onNodeWithTag("visit-cancel").performScrollTo().performClick()
    compose.onNodeWithContentDescription("Back").performClick()
    idle()
    compose.onNodeWithText("Discard changes?").assertDoesNotExist()
    assertEquals(0, done)
    compose.onNodeWithTag("visit-save").assertExists()

    gate.complete(Unit)
    val deadline = System.currentTimeMillis() + 5_000
    while (done == 0) {
      check(System.currentTimeMillis() < deadline) { "save never landed" }
      idle()
      Thread.sleep(5)
    }
    assertEquals(1, done)
  }

  private fun idle() {
    shadowOf(Looper.getMainLooper()).idle()
    compose.waitForIdle()
  }
}
