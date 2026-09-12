package com.jasonarends.forklore.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** Client-generated so rows can be created offline without coordinating ids. */
fun newId(): String = UUID.randomUUID().toString()

/**
 * A shareable list of places. A personal list is simply a list with one member, so sharing later
 * needs no migration. Named PlaceList rather than Collection to avoid shadowing
 * kotlin.collections.Collection.
 */
@Entity(tableName = "place_lists")
data class PlaceListEntity(
  @PrimaryKey val id: String = newId(),
  val name: String,
  val createdAt: Long,
  val updatedAt: Long,
  val deletedAt: Long? = null,
)

/**
 * Someone whose preferences or recommendations are tracked. Covers both household members whose
 * usual order matters ("have Robin get mac and cheese") and outside recommenders who will never use
 * the app ("Dale and Marvin recommend the pot pie").
 */
@Entity(tableName = "people", indices = [Index("placeListId")])
data class PersonEntity(
  @PrimaryKey val id: String = newId(),
  val placeListId: String,
  val name: String,
  val isHouseholdMember: Boolean = false,
  val note: String = "",
  val createdAt: Long,
  val updatedAt: Long,
  val deletedAt: Long? = null,
)

/**
 * A restaurant. Identity is ours: [providerId] is a hint for refresh and dedupe, never the source
 * of truth, and every provider field may be null for a hand-entered place.
 */
@Entity(tableName = "places")
data class PlaceEntity(
  @PrimaryKey val id: String = newId(),
  val name: String,
  /** Disambiguates branches of one restaurant: "Kansas", "kc". */
  val branchLabel: String? = null,
  val address: String? = null,
  val latitude: Double? = null,
  val longitude: Double? = null,
  val provider: String? = null,
  val providerId: String? = null,
  val providerFetchedAt: Long? = null,
  val note: String = "",
  /** Pricing or policy gotchas: "$27 per person even if you order one thing". */
  val warning: String? = null,
  val createdAt: Long,
  val updatedAt: Long,
  val deletedAt: Long? = null,
)

/** A place's membership in one list, carrying that list's stance on it. */
@Entity(
  tableName = "place_entries",
  foreignKeys =
    [
      ForeignKey(PlaceListEntity::class, ["id"], ["placeListId"], onDelete = CASCADE),
      ForeignKey(PlaceEntity::class, ["id"], ["placeId"], onDelete = CASCADE),
    ],
  indices =
    [
      Index("placeListId"),
      Index("placeId"),
      Index(value = ["placeListId", "placeId"], unique = true),
    ],
)
data class PlaceEntryEntity(
  @PrimaryKey val id: String = newId(),
  val placeListId: String,
  val placeId: String,
  val status: PlaceStatus = PlaceStatus.WANT,
  /** Food and service are rated apart: divine pasta and hostile staff is a real review. */
  val foodRating: Rating? = null,
  val serviceRating: Rating? = null,
  val revisitIntent: RevisitIntent? = null,
  val note: String = "",
  val createdAt: Long,
  val updatedAt: Long,
  val deletedAt: Long? = null,
)

/** One occasion at a place. Date is optional and carries its own precision. */
@Entity(
  tableName = "visits",
  foreignKeys =
    [
      ForeignKey(PlaceEntryEntity::class, ["id"], ["placeEntryId"], onDelete = CASCADE),
      ForeignKey(PersonEntity::class, ["id"], ["authorId"], onDelete = ForeignKey.SET_NULL),
    ],
  indices = [Index("placeEntryId"), Index("authorId")],
)
data class VisitEntity(
  @PrimaryKey val id: String = newId(),
  val placeEntryId: String,
  /** Epoch day. Null when the note recorded no date at all. */
  val dateEpochDay: Long? = null,
  val datePrecision: DatePrecision = DatePrecision.UNKNOWN,
  val meal: Meal? = null,
  val note: String = "",
  val authorId: String? = null,
  val createdAt: Long,
  val updatedAt: Long,
  val deletedAt: Long? = null,
)

/** Who was there. */
@Entity(
  tableName = "visit_attendees",
  primaryKeys = ["visitId", "personId"],
  foreignKeys =
    [
      ForeignKey(VisitEntity::class, ["id"], ["visitId"], onDelete = CASCADE),
      ForeignKey(PersonEntity::class, ["id"], ["personId"], onDelete = CASCADE),
    ],
  indices = [Index("visitId"), Index("personId")],
)
data class VisitAttendeeEntity(val visitId: String, val personId: String)

/** A named menu item at a place. */
@Entity(
  tableName = "dishes",
  foreignKeys = [ForeignKey(PlaceEntity::class, ["id"], ["placeId"], onDelete = CASCADE)],
  indices = [Index("placeId")],
)
data class DishEntity(
  @PrimaryKey val id: String = newId(),
  val placeId: String,
  val canonicalName: String,
  val note: String = "",
  val createdAt: Long,
  val updatedAt: Long,
  val deletedAt: Long? = null,
)

/**
 * Other spellings of the same dish. Users write "potatoe barrels", "barrel potatoes" and "barrel
 * tots" for one item; without aliases that becomes three rows and the want-list silently breaks.
 */
@Entity(
  tableName = "dish_aliases",
  foreignKeys = [ForeignKey(DishEntity::class, ["id"], ["dishId"], onDelete = CASCADE)],
  indices = [Index("dishId"), Index(value = ["dishId", "normalized"], unique = true)],
)
data class DishAliasEntity(
  @PrimaryKey val id: String = newId(),
  val dishId: String,
  val alias: String,
  /** Lowercased and punctuation-stripped, for matching. */
  val normalized: String,
)

/** A list's stance on a dish, optionally scoped to one person. */
@Entity(
  tableName = "dish_interests",
  foreignKeys =
    [
      ForeignKey(DishEntity::class, ["id"], ["dishId"], onDelete = CASCADE),
      ForeignKey(PlaceListEntity::class, ["id"], ["placeListId"], onDelete = CASCADE),
      ForeignKey(PersonEntity::class, ["id"], ["forPersonId"], onDelete = ForeignKey.SET_NULL),
      ForeignKey(PersonEntity::class, ["id"], ["recommendedById"], onDelete = ForeignKey.SET_NULL),
    ],
  indices = [Index("dishId"), Index("placeListId"), Index("forPersonId"), Index("recommendedById")],
)
data class DishInterestEntity(
  @PrimaryKey val id: String = newId(),
  val dishId: String,
  val placeListId: String,
  val status: DishStatus = DishStatus.WANT,
  /** "Robin wants chicken" — a want belonging to one person, not the whole list. */
  val forPersonId: String? = null,
  /** "Dale and Marvin recommend the pot pie" — attribution for an outside tip. */
  val recommendedById: String? = null,
  /** How to order it: "add a Chilli bomb", "chopped", "lettuce and tomato on the side". */
  val modification: String? = null,
  val note: String = "",
  val createdAt: Long,
  val updatedAt: Long,
  val deletedAt: Long? = null,
)

/**
 * One author's verdict on a dish. Multiple rows per dish by design — when two people disagree the
 * app shows both, because the disagreement is the interesting part.
 */
@Entity(
  tableName = "dish_opinions",
  foreignKeys =
    [
      ForeignKey(DishEntity::class, ["id"], ["dishId"], onDelete = CASCADE),
      ForeignKey(PersonEntity::class, ["id"], ["authorId"], onDelete = CASCADE),
      ForeignKey(VisitEntity::class, ["id"], ["visitId"], onDelete = ForeignKey.SET_NULL),
    ],
  indices = [Index("dishId"), Index("authorId"), Index("visitId")],
)
data class DishOpinionEntity(
  @PrimaryKey val id: String = newId(),
  val dishId: String,
  val authorId: String,
  val visitId: String? = null,
  val rating: Rating? = null,
  val note: String = "",
  val createdAt: Long,
  val updatedAt: Long,
  val deletedAt: Long? = null,
)
