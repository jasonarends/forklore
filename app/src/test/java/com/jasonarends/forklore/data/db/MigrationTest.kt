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
    helper.createDatabase(TEST_DB, 1).use { db ->
      db.execSQL(
        "INSERT INTO place_lists (id, name, createdAt, updatedAt) VALUES ('list-1', 'Ours', $now, $now)"
      )
      db.execSQL(
        "INSERT INTO place_lists (id, name, createdAt, updatedAt) VALUES ('list-2', 'Shared', $now, $now)"
      )
      db.execSQL(
        "INSERT INTO place_lists (id, name, createdAt, updatedAt) VALUES ('list-3', 'Archived', $now, $now)"
      )
      // A place with a note and one entry with no note of its own: the place note becomes the
      // entry note.
      db.execSQL(
        "INSERT INTO places (id, name, note, createdAt, updatedAt) " +
          "VALUES ('place-1', 'Halberd', '- great for pizza, menu doesn''t ', $now, $now)"
      )
      db.execSQL(
        "INSERT INTO place_entries (id, placeListId, placeId, status, note, createdAt, updatedAt) " +
          "VALUES ('entry-1', 'list-1', 'place-1', 'WANT', '', $now, $now)"
      )
      // A second entry for the same place that already has its own note: both must survive.
      db.execSQL(
        "INSERT INTO place_entries (id, placeListId, placeId, status, note, createdAt, updatedAt) " +
          "VALUES ('entry-2', 'list-2', 'place-1', 'WANT', 'Already had a note', $now, $now)"
      )
      // A soft-deleted entry for the same place must be left alone: no note rewrite, no
      // updatedAt touch — it's a tombstone, not a row still being edited.
      db.execSQL(
        "INSERT INTO place_entries " +
          "(id, placeListId, placeId, status, note, createdAt, updatedAt, deletedAt) " +
          "VALUES ('entry-tombstone', 'list-3', 'place-1', 'WANT', '', $now, $now, $now)"
      )
      // A place with every other column populated and an empty note: nothing gets appended
      // anywhere, and every non-note column must survive the table recreate unchanged.
      db.execSQL(
        "INSERT INTO places " +
          "(id, name, branchLabel, address, latitude, longitude, provider, providerId, " +
          "providerFetchedAt, note, warning, createdAt, updatedAt, deletedAt) " +
          "VALUES ('place-2', 'Verano', 'Downtown', '456 Elm St', 39.0997, -94.5786, 'osm', " +
          "'way/123456', ${now - 1_000}, '', 'Cash only', $now, ${now + 2_000}, ${now + 3_000})"
      )
      db.execSQL(
        "INSERT INTO place_entries (id, placeListId, placeId, status, note, createdAt, updatedAt) " +
          "VALUES ('entry-3', 'list-1', 'place-2', 'WANT', '', $now, $now)"
      )
    }

    val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

    db.query("SELECT note, updatedAt FROM place_entries WHERE id = 'entry-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("- great for pizza, menu doesn't ", cursor.getString(0))
      assertTrue(cursor.getLong(1) > now)
    }
    db.query("SELECT note FROM place_entries WHERE id = 'entry-2'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(
        "Already had a note\n\n- great for pizza, menu doesn't ",
        cursor.getString(0),
      )
    }
    db.query("SELECT note, updatedAt FROM place_entries WHERE id = 'entry-tombstone'").use { cursor
      ->
      assertTrue(cursor.moveToFirst())
      assertEquals("", cursor.getString(0))
      assertEquals(now, cursor.getLong(1))
    }
    db.query("SELECT note FROM place_entries WHERE id = 'entry-3'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("", cursor.getString(0))
    }
    db
      .query(
        "SELECT branchLabel, address, latitude, longitude, provider, providerId, " +
          "providerFetchedAt, warning, createdAt, updatedAt, deletedAt " +
          "FROM places WHERE id = 'place-2'"
      )
      .use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("Downtown", cursor.getString(0))
        assertEquals("456 Elm St", cursor.getString(1))
        assertEquals(39.0997, cursor.getDouble(2), 0.0)
        assertEquals(-94.5786, cursor.getDouble(3), 0.0)
        assertEquals("osm", cursor.getString(4))
        assertEquals("way/123456", cursor.getString(5))
        assertEquals(now - 1_000, cursor.getLong(6))
        assertEquals("Cash only", cursor.getString(7))
        assertEquals(now, cursor.getLong(8))
        assertEquals(now + 2_000, cursor.getLong(9))
        assertEquals(now + 3_000, cursor.getLong(10))
      }
    db.query("PRAGMA table_info(places)").use { cursor ->
      val columnNames = generateSequence { if (cursor.moveToNext()) cursor.getString(1) else null }
      assertFalse(columnNames.any { it == "note" })
    }
  }

  companion object {
    private const val TEST_DB = "migration-test"
  }
}
