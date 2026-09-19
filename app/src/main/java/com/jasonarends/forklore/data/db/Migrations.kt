package com.jasonarends.forklore.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Drops the global `places.note` column (CLAUDE.md rule 6: list-specific writing belongs on
 * `place_entries`, never on `places` itself — issue #13). Nothing in the app writes `places.note`
 * anymore, but an existing install could still hold text there, so before the column disappears it
 * is appended after every live entry's own note for that place, never discarding one for the other.
 * The one case that does lose the text is a place with no entries at all (or only soft-deleted
 * ones) — there is nowhere left to put it. That's expected to be empty in practice: nothing in the
 * app has ever created a `Place` without also adding it to a list in the same transaction (see
 * `PlaceRepository.addPlaceToList`).
 *
 * `minSdk = 30` ships an SQLite older than 3.35, which is when `ALTER TABLE ... DROP COLUMN`
 * arrived, so the column is dropped by recreating the table rather than altering it in place.
 *
 * The `DROP TABLE places` below relies on running with foreign keys off, or SQLite would
 * cascade-delete every `place_entries` row referencing it (CLAUDE.md rule 7: `ON DELETE CASCADE` is
 * a hard-purge net, not a path this migration is allowed to take). Room only turns `PRAGMA
 * foreign_keys` on in `onOpen`, which runs after this migration's transaction commits, so this
 * holds today — but it means this migration must keep running inside Room's own migration
 * transaction, never as a one-off script against a live, already-open connection.
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
        WHERE deletedAt IS NULL
          AND placeId IN (SELECT id FROM places WHERE note != '')
        """
          .trimIndent()
      )

      db.execSQL(
        """
        CREATE TABLE `places_new` (
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

/**
 * Adds the `active_visit_attendees` view backing [ActiveVisitAttendee] (issue #5): purely additive,
 * no existing table changes, so unlike [MIGRATION_1_2] there is nothing to move or recreate.
 */
val MIGRATION_2_3: Migration =
  object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        "CREATE VIEW `active_visit_attendees` AS SELECT id, visitId, personId FROM visit_attendees WHERE deletedAt IS NULL"
      )
    }
  }

/**
 * Adds `places.dogPolicy` and `dish_opinions.temperature` (issue #21). Both are nullable `ALTER
 * TABLE ... ADD COLUMN`, which every SQLite version this app supports handles directly — unlike
 * [MIGRATION_1_2]'s column drop, there is no table recreate here.
 */
val MIGRATION_3_4: Migration =
  object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE `places` ADD COLUMN `dogPolicy` TEXT DEFAULT NULL")
      db.execSQL("ALTER TABLE `dish_opinions` ADD COLUMN `temperature` TEXT DEFAULT NULL")
    }
  }
