package com.jasonarends.forklore.data.db

import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** Client-generated so rows can be created offline without coordinating ids. */
fun newId(): String = UUID.randomUUID().toString()

/**
 * Scoping rule, applied uniformly: a [PlaceEntryEntity] is the boundary of a list's private
 * writing. Places are global — one restaurant, one row, shared by every list that includes it — but
 * everything a list *says* about a place (visits, dishes, interests, opinions) hangs off that
 * list's entry for it. The same restaurant in a personal list and a shared list therefore keeps two
 * independent sets of notes, and nothing written privately can surface in a shared list. The cost
 * is that a dish recorded in one list is not visible in the other, which is the right trade:
 * duplicated typing is recoverable, a leaked private opinion is not.
 *
 * Deletion, equally uniform: rows are soft-deleted by stamping [deletedAt], which is what M3 sync
 * propagates. The ON DELETE CASCADE rules below are a referential-integrity net for a genuine hard
 * purge, not the app's delete path. Repositories own cascading a soft delete to children, in one
 * transaction — see CLAUDE.md.
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
 * Someone whose preferences or recommendations are tracked: a household member whose usual order
 * matters, or an outsider who recommended something and will never use the app.
 *
 * Deliberately *not* scoped to a list. A person is a person, and scoping them per-list means an
 * opinion could be authored by someone from a different list. List membership arrives in M3 as its
 * own table.
 */
@Entity(tableName = "people", indices = [Index("normalizedName", unique = true)])
data class PersonEntity(
  @PrimaryKey val id: String = newId(),
  val name: String,
  /** Match key, so "Val" and "val " don't become two people. */
  val normalizedName: String,
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
@Entity(tableName = "places", indices = [Index(value = ["provider", "providerId"])])
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
  /** Pricing or policy gotchas: "$27 per person even if you order one thing". */
  val warning: String? = null,
  val createdAt: Long,
  val updatedAt: Long,
  val deletedAt: Long? = null,
)

/** A place's membership in one list, and the root of everything that list writes about it. */
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
      ForeignKey(PersonEntity::class, ["id"], ["authorId"], onDelete = SET_NULL),
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

/** Who was there. Carries its own id and tombstone so "Sam wasn't there" can sync. */
@Entity(
  tableName = "visit_attendees",
  foreignKeys =
    [
      ForeignKey(VisitEntity::class, ["id"], ["visitId"], onDelete = CASCADE),
      ForeignKey(PersonEntity::class, ["id"], ["personId"], onDelete = CASCADE),
    ],
  indices =
    [Index("visitId"), Index("personId"), Index(value = ["visitId", "personId"], unique = true)],
)
data class VisitAttendeeEntity(
  @PrimaryKey val id: String = newId(),
  val visitId: String,
  val personId: String,
  val createdAt: Long,
  val updatedAt: Long,
  val deletedAt: Long? = null,
)

/**
 * The non-deleted half of [VisitAttendeeEntity], for [VisitWithAttendees] to join through. A
 * `@Relation`'s `Junction` can't carry a `WHERE` clause of its own, so without this view a removed
 * attendee's tombstoned row would still join in — the read-side half of CLAUDE.md's "every read
 * filters `deletedAt IS NULL`" would silently stop holding for this one table the moment a visit's
 * attendee list changed on the same day #5 first started writing tombstones there.
 */
@DatabaseView(
  viewName = "active_visit_attendees",
  value = "SELECT id, visitId, personId FROM visit_attendees WHERE deletedAt IS NULL",
)
data class ActiveVisitAttendee(val id: String, val visitId: String, val personId: String)

/**
 * A named menu item, scoped to one list's entry for a place. [normalizedName] is the match key and
 * is uniquely indexed per entry, so the same dish cannot be recorded twice however it is spelled.
 */
@Entity(
  tableName = "dishes",
  foreignKeys = [ForeignKey(PlaceEntryEntity::class, ["id"], ["placeEntryId"], onDelete = CASCADE)],
  indices =
    [Index("placeEntryId"), Index(value = ["placeEntryId", "normalizedName"], unique = true)],
)
data class DishEntity(
  @PrimaryKey val id: String = newId(),
  val placeEntryId: String,
  val canonicalName: String,
  /** Always [normalizeDishName] of [canonicalName]; repositories must keep these in step. */
  val normalizedName: String,
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
  /** [normalizeDishName] of [alias]. */
  val normalized: String,
  val createdAt: Long,
  val updatedAt: Long,
  val deletedAt: Long? = null,
)

/**
 * A stance on a dish. The owning list is reached through the dish's place entry rather than stored
 * again here — one source of truth for scoping.
 */
@Entity(
  tableName = "dish_interests",
  foreignKeys =
    [
      ForeignKey(DishEntity::class, ["id"], ["dishId"], onDelete = CASCADE),
      ForeignKey(PersonEntity::class, ["id"], ["forPersonId"], onDelete = SET_NULL),
      ForeignKey(PersonEntity::class, ["id"], ["recommendedById"], onDelete = SET_NULL),
    ],
  indices = [Index("dishId"), Index("forPersonId"), Index("recommendedById")],
)
data class DishInterestEntity(
  @PrimaryKey val id: String = newId(),
  val dishId: String,
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
      ForeignKey(VisitEntity::class, ["id"], ["visitId"], onDelete = SET_NULL),
    ],
  indices =
    [Index("dishId"), Index("authorId"), Index("visitId"), Index(value = ["dishId", "authorId"])],
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
