package com.jasonarends.forklore.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Covers issue #13: dropping `places.note` must not silently discard a place's free text. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
  @get:Rule
  val helper: MigrationTestHelper =
    MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ForkloreDatabase::class.java)

  private val now = 1_757_000_000_000L

  @Test
  fun migrate1To2_movesPlaceNoteOntoItsEntries_andDropsTheColumn() {
    helper.createDatabase(TEST_DB, 1).apply {
      execSQL(
        "INSERT INTO place_lists (id, name, createdAt, updatedAt) VALUES ('list-1', 'Ours', $now, $now)"
      )
      execSQL(
        "INSERT INTO place_lists (id, name, createdAt, updatedAt) VALUES ('list-2', 'Shared', $now, $now)"
      )
      // A place with a note and one entry with no note of its own: the place note becomes the
      // entry note.
      execSQL(
        "INSERT INTO places (id, name, note, createdAt, updatedAt) " +
          "VALUES ('place-1', 'Halberd', '- great for pizza, menu doesn''t ', $now, $now)"
      )
      execSQL(
        "INSERT INTO place_entries (id, placeListId, placeId, status, note, createdAt, updatedAt) " +
          "VALUES ('entry-1', 'list-1', 'place-1', 'WANT', '', $now, $now)"
      )
      // A second entry for the same place that already has its own note: both must survive.
      execSQL(
        "INSERT INTO place_entries (id, placeListId, placeId, status, note, createdAt, updatedAt) " +
          "VALUES ('entry-2', 'list-2', 'place-1', 'WANT', 'Already had a note', $now, $now)"
      )
      // A place with no note must migrate with nothing appended anywhere.
      execSQL(
        "INSERT INTO places (id, name, note, createdAt, updatedAt) " +
          "VALUES ('place-2', 'Verano', '', $now, $now)"
      )
      execSQL(
        "INSERT INTO place_entries (id, placeListId, placeId, status, note, createdAt, updatedAt) " +
          "VALUES ('entry-3', 'list-1', 'place-2', 'WANT', '', $now, $now)"
      )
      close()
    }

    val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

    db.query("SELECT note FROM place_entries WHERE id = 'entry-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("- great for pizza, menu doesn't ", cursor.getString(0))
    }
    db.query("SELECT note FROM place_entries WHERE id = 'entry-2'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      val note = cursor.getString(0)
      assertTrue(note.contains("Already had a note"))
      assertTrue(note.contains("- great for pizza, menu doesn't "))
    }
    db.query("SELECT note FROM place_entries WHERE id = 'entry-3'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("", cursor.getString(0))
    }
    db.query("PRAGMA table_info(places)").use { cursor ->
      val columnNames = generateSequence { if (cursor.moveToNext()) cursor.getString(1) else null }
      assertFalse(columnNames.toList().contains("note"))
    }
  }

  companion object {
    private const val TEST_DB = "migration-test"
  }
}
