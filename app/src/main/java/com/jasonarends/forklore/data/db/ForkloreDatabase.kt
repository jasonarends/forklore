package com.jasonarends.forklore.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
  entities =
    [
      PlaceListEntity::class,
      PersonEntity::class,
      PlaceEntity::class,
      PlaceEntryEntity::class,
      VisitEntity::class,
      VisitAttendeeEntity::class,
      DishEntity::class,
      DishAliasEntity::class,
      DishInterestEntity::class,
      DishOpinionEntity::class,
    ],
  views = [ActiveVisitAttendee::class],
  version = 3,
  exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ForkloreDatabase : RoomDatabase() {
  abstract fun placeListDao(): PlaceListDao

  abstract fun personDao(): PersonDao

  abstract fun placeDao(): PlaceDao

  abstract fun placeEntryDao(): PlaceEntryDao

  abstract fun visitDao(): VisitDao

  abstract fun dishDao(): DishDao

  abstract fun dishInterestDao(): DishInterestDao

  abstract fun dishOpinionDao(): DishOpinionDao

  companion object {
    fun build(context: Context): ForkloreDatabase =
      Room.databaseBuilder(context, ForkloreDatabase::class.java, "forklore.db")
        // No fallbackToDestructiveMigration: this database is the user's own writing and
        // there is no server copy to restore from. A missing migration must fail loudly.
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()
  }
}
