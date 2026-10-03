package com.jasonarends.forklore.data.db

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * A list entry together with the restaurant it points at. The list screen needs the place's name,
 * and without this every caller would either re-query per row or hand-roll its own join — so it is
 * provided once, here.
 */
data class PlaceEntryWithPlace(
  @Embedded val entry: PlaceEntryEntity,
  @Relation(parentColumn = "placeId", entityColumn = "id") val place: PlaceEntity,
)

/**
 * A visit with everyone who was there, in one query rather than 1 + N. Joins through
 * [ActiveVisitAttendee], not [VisitAttendeeEntity] directly — see that view's KDoc for why a
 * removed attendee must not still show up here.
 */
data class VisitWithAttendees(
  @Embedded val visit: VisitEntity,
  @Relation(
    parentColumn = "id",
    entityColumn = "id",
    associateBy =
      Junction(ActiveVisitAttendee::class, parentColumn = "visitId", entityColumn = "personId"),
  )
  val attendees: List<PersonEntity>,
)

/** A dish with its alternate spellings, in one query rather than 1 + N. */
data class DishWithAliases(
  @Embedded val dish: DishEntity,
  @Relation(parentColumn = "id", entityColumn = "dishId") val aliases: List<DishAliasEntity>,
)

/** A dish with every author's verdict on it, for the screen that shows disagreement. */
data class DishWithOpinions(
  @Embedded val dish: DishEntity,
  @Relation(parentColumn = "id", entityColumn = "dishId") val opinions: List<DishOpinionEntity>,
)

/**
 * One interest with the names needed to show it outside its own dish screen: the dish, the place
 * entry to open when it's tapped, the place, and the people it cites. Names are read at query time
 * rather than copied, so a renamed person or dish shows up here with no extra write.
 */
data class ListedDishInterest(
  @Embedded val interest: DishInterestEntity,
  val dishName: String,
  val placeEntryId: String,
  val placeName: String,
  val branchLabel: String?,
  val forPersonName: String?,
  val recommendedByName: String?,
)
