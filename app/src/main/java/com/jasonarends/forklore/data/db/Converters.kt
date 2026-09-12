package com.jasonarends.forklore.data.db

import androidx.room.TypeConverter

/**
 * Enums are stored by name rather than ordinal so that reordering or inserting a constant can't
 * silently reinterpret existing rows.
 */
class Converters {
  @TypeConverter fun ratingToName(value: Rating?): String? = value?.name

  @TypeConverter fun nameToRating(value: String?): Rating? = value?.let(Rating::valueOf)

  @TypeConverter fun placeStatusToName(value: PlaceStatus?): String? = value?.name

  @TypeConverter
  fun nameToPlaceStatus(value: String?): PlaceStatus? = value?.let(PlaceStatus::valueOf)

  @TypeConverter fun revisitIntentToName(value: RevisitIntent?): String? = value?.name

  @TypeConverter
  fun nameToRevisitIntent(value: String?): RevisitIntent? = value?.let(RevisitIntent::valueOf)

  @TypeConverter fun dishStatusToName(value: DishStatus?): String? = value?.name

  @TypeConverter fun nameToDishStatus(value: String?): DishStatus? = value?.let(DishStatus::valueOf)

  @TypeConverter fun datePrecisionToName(value: DatePrecision?): String? = value?.name

  @TypeConverter
  fun nameToDatePrecision(value: String?): DatePrecision? = value?.let(DatePrecision::valueOf)

  @TypeConverter fun mealToName(value: Meal?): String? = value?.name

  @TypeConverter fun nameToMeal(value: String?): Meal? = value?.let(Meal::valueOf)
}
