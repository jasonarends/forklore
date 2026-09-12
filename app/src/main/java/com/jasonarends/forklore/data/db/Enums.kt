package com.jasonarends.forklore.data.db

/**
 * How good something was, in the vocabulary people actually use. Deliberately not a star rating:
 * the source notes range from "mid" through "excellent" to "changed our lives", and a numeric scale
 * flattens the top end that users care most about.
 */
enum class Rating {
  BAD,
  MID,
  FINE,
  GOOD,
  EXCELLENT,
  PHENOMENAL,
  LIFE_CHANGING,
}

/** Where a place stands for one [PlaceListEntity]. */
enum class PlaceStatus {
  WANT,
  VISITED,
  AVOID,
}

/** Whether the people on a list intend to go back. */
enum class RevisitIntent {
  EAGER,
  MAYBE,
  WAIT,
  NEVER,
}

/**
 * A list's stance on a dish. [NEVER_AGAIN] is distinct from a bad [Rating]: it is an instruction to
 * the future ("skip the bread"), not just a record of an opinion.
 */
enum class DishStatus {
  WANT,
  TRIED,
  NEVER_AGAIN,
}

/**
 * How precisely a visit's date is known. Source notes carry "July 2026", "7/21/26", and nothing at
 * all, so a plain nullable date would silently invent precision it doesn't have.
 */
enum class DatePrecision {
  DAY,
  MONTH,
  YEAR,
  UNKNOWN,
}

enum class Meal {
  BREAKFAST,
  BRUNCH,
  LUNCH,
  DINNER,
  DESSERT,
  DRINKS,
}
