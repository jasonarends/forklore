package com.jasonarends.forklore.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Drops the global `places.note` column (CLAUDE.md rule 6: list-specific writing belongs on
 * `place_entries`, never on `places` itself — issue #13). Nothing in the app writes `places.note`
 * anymore, but an existing install could still hold text there, so it is appended onto every entry
 * for that place — ahead of that entry's own note, never discarding one for the other — before the
 * column disappears.
 *
 * `minSdk = 30` ships an SQLite older than 3.35, which is when `ALTER TABLE ... DROP COLUMN`
 * arrived, so the column is dropped by recreating the table rather than altering it in place.
 */
val MIGRATION_1_2: Migration =
  object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        UPDATE place_entries
        SET note = CASE
            WHEN note = '' THEN (SELECT note FROM places WHERE places.id = place_entries.placeId)
            ELSE note || CHAR(10) || CHAR(10) ||
              (SELECT note FROM places WHERE places.id = place_entries.placeId)
          END,
          updatedAt = (CAST(strftime('%s', 'now') AS INTEGER) * 1000)
        WHERE placeId IN (SELECT id FROM places WHERE note IS NOT NULL AND note != '')
        """
          .trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `places_new` (
          `id` TEXT NOT NULL,
          `name` TEXT NOT NULL,
          `branchLabel` TEXT,
          `address` TEXT,
          `latitude` REAL,
          `longitude` REAL,
          `provider` TEXT,
          `providerId` TEXT,
          `providerFetchedAt` INTEGER,
          `warning` TEXT,
          `createdAt` INTEGER NOT NULL,
          `updatedAt` INTEGER NOT NULL,
          `deletedAt` INTEGER,
          PRIMARY KEY(`id`)
        )
        """
          .trimIndent()
      )
      db.execSQL(
        """
        INSERT INTO `places_new`
          (`id`, `name`, `branchLabel`, `address`, `latitude`, `longitude`, `provider`,
           `providerId`, `providerFetchedAt`, `warning`, `createdAt`, `updatedAt`, `deletedAt`)
        SELECT
          `id`, `name`, `branchLabel`, `address`, `latitude`, `longitude`, `provider`,
          `providerId`, `providerFetchedAt`, `warning`, `createdAt`, `updatedAt`, `deletedAt`
        FROM `places`
        """
          .trimIndent()
      )
      db.execSQL("DROP TABLE `places`")
      db.execSQL("ALTER TABLE `places_new` RENAME TO `places`")
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_places_provider_providerId` ON `places` (`provider`, `providerId`)"
      )
    }
  }
