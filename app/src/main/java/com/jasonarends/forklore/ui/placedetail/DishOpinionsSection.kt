package com.jasonarends.forklore.ui.placedetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jasonarends.forklore.data.db.DishOpinionEntity
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.data.db.TemperatureRating
import com.jasonarends.forklore.data.db.VisitEntity
import com.jasonarends.forklore.data.db.VisitWithAttendees
import com.jasonarends.forklore.ui.components.LedgerChip
import com.jasonarends.forklore.ui.components.LedgerGhostButton
import com.jasonarends.forklore.ui.components.LedgerPrimaryButton
import com.jasonarends.forklore.ui.components.NoteField
import com.jasonarends.forklore.ui.components.PersonPicker
import com.jasonarends.forklore.ui.components.RatingPicker
import com.jasonarends.forklore.ui.components.TemperatureLabel
import com.jasonarends.forklore.ui.components.TemperaturePicker
import com.jasonarends.forklore.ui.components.UppercaseLabel
import com.jasonarends.forklore.ui.components.label
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType
import com.jasonarends.forklore.ui.theme.hardShadow
import com.jasonarends.forklore.ui.theme.ruledBackground
import com.jasonarends.forklore.ui.theme.tilt

/**
 * Everything one dish has to say for itself: each author's opinion as its own card, a line naming
 * any disagreement, and either the form for the open [draft] or the "Add an opinion" affordance.
 * Stateless: state in, events out. Lives in the slot [DishesSection] leaves under each dish row.
 *
 * Only one draft exists at a time (see [DishOpinionsViewModel]), so while one is open on another
 * dish this dish offers neither "Add an opinion" nor tap-to-edit — otherwise starting a second
 * would silently throw away whatever was typed into the first.
 */
@Composable
internal fun DishOpinionsBlock(
  dishId: String,
  state: DishOpinionsUiState,
  draft: OpinionDraft?,
  onStartAdd: (dishId: String) -> Unit,
  onStartEdit: (DishOpinionEntity) -> Unit,
  onCancelDraft: () -> Unit,
  onAuthorChange: (Set<String>) -> Unit,
  onRatingChange: (Rating?) -> Unit,
  onTemperatureChange: (TemperatureRating?) -> Unit,
  onNoteChange: (String) -> Unit,
  onVisitChange: (String?) -> Unit,
  onCreatePerson: (String) -> Unit,
  onSave: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  Column(modifier = modifier.fillMaxWidth()) {
    when (state) {
      DishOpinionsUiState.Loading -> Unit
      is DishOpinionsUiState.Error ->
        Text(
          "Couldn't load opinions: ${state.throwable.message}",
          style = ForkloreType.fieldInput,
          color = colors.stamp,
        )
      is DishOpinionsUiState.Success -> {
        val opinions = state.byDish[dishId]
        val editingHere = draft?.dishId == dishId
        val draftElsewhere = draft != null && !editingHere
        if (opinions != null) {
          OpinionCards(
            dishId = dishId,
            opinions = opinions,
            visits = state.visits,
            onEdit = onStartEdit.takeUnless { draft != null },
          )
        }
        if (draft != null && editingHere) {
          OpinionForm(
            draft = draft,
            people = state.people,
            visits = state.visits,
            onAuthorChange = onAuthorChange,
            onRatingChange = onRatingChange,
            onTemperatureChange = onTemperatureChange,
            onNoteChange = onNoteChange,
            onVisitChange = onVisitChange,
            onCreatePerson = onCreatePerson,
            onSave = onSave,
            onCancel = onCancelDraft,
            onDelete = onDelete,
            modifier = Modifier.padding(top = 10.dp),
          )
        } else if (!draftElsewhere) {
          LedgerGhostButton(
            text = "Add an opinion",
            onClick = { onStartAdd(dishId) },
            modifier = Modifier.padding(top = 10.dp).testTag("opinion-add-$dishId"),
          )
        }
      }
    }
  }
}

/**
 * The disagreement, made visible without hiding anyone: every card is always drawn, each author's
 * verdict in their own colour, and when two authors' ratings (or temperatures) differ a handwritten
 * line in `stamp` says so above the cards. Two cards per row, tilted opposite ways per issue #15 —
 * two columns rather than a stack because the point is comparing them, and a card's header wraps
 * (name above rating) so both verdicts still fit at half width and line up across the row. A third
 * or later card wraps onto the next row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpinionCards(
  dishId: String,
  opinions: DishOpinions,
  visits: List<VisitWithAttendees>,
  onEdit: ((DishOpinionEntity) -> Unit)?,
) {
  val colors = ForkloreTheme.colors
  disagreementCaption(opinions)?.let {
    Text(
      text = it,
      style = ForkloreType.noteText,
      color = colors.stamp,
      modifier = Modifier.padding(top = 4.dp).testTag("opinions-disagree-$dishId"),
    )
  }
  FlowRow(
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    maxItemsInEachRow = 2,
  ) {
    opinions.cards.forEachIndexed { index, card ->
      OpinionCardView(
        card = card,
        showTemperature = opinions.anyTemperature,
        visit = visits.firstOrNull { it.visit.id == card.opinion.visitId }?.visit,
        tiltDegrees = if (index % 2 == 0) -1.5f else 1.3f,
        onEdit = onEdit?.let { edit -> { edit(card.opinion) } },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

internal fun disagreementCaption(opinions: DishOpinions): String? =
  when {
    opinions.ratingsDisagree && opinions.temperaturesDisagree ->
      "They disagree on rating and temperature"
    opinions.ratingsDisagree -> "They disagree on rating"
    opinions.temperaturesDisagree -> "They disagree on temperature"
    else -> null
  }

@Composable
private fun AuthorTone.color(): Color {
  val colors = ForkloreTheme.colors
  return when (this) {
    AuthorTone.First -> colors.person1
    AuthorTone.Second -> colors.person2
    AuthorTone.Neutral -> colors.ink2
  }
}

/**
 * One author's card. A missing rating reads "Not rated" and a missing temperature "not said" — in
 * `ink2`, so an opinion that is just a note looks like a choice rather than a card that failed to
 * load. The temperature line only exists on a dish where someone recorded one; on all the others it
 * would be an empty label on every card.
 */
@Composable
private fun OpinionCardView(
  card: OpinionCard,
  showTemperature: Boolean,
  visit: VisitEntity?,
  tiltDegrees: Float,
  onEdit: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  val tone = card.tone.color()
  val opinion = card.opinion
  // Ruled-paper pitch follows the note's line height in *scaled* pixels, so the lines stay under
  // the text at large system font sizes instead of drifting off it.
  val rulePitch = with(LocalDensity.current) { 22.sp.toDp() }
  Surface(
    modifier =
      modifier
        .tilt(tiltDegrees)
        .hardShadow(2.dp, 3.dp, colors.cardShadow, 2.dp)
        .testTag("opinion-card-${opinion.id}"),
    shape = RoundedCornerShape(2.dp),
    color = colors.card,
    border = BorderStroke(1.5.dp, colors.cardBorder),
  ) {
    Column(
      modifier =
        Modifier.then(
            if (onEdit != null) {
              Modifier.clickable(
                onClickLabel = "Edit ${card.authorName}'s opinion",
                role = Role.Button,
                onClick = onEdit,
              )
            } else {
              Modifier
            }
          )
          .ruledBackground(colors.rule, rulePitch)
          .padding(13.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(initial = card.authorName.firstOrNull()?.uppercase() ?: "?", color = tone)
        Column(modifier = Modifier.padding(start = 8.dp)) {
          Text(text = card.authorName, style = ForkloreType.opinionAuthor, color = tone)
          if (opinion.rating != null) {
            Text(
              text = opinion.rating.label,
              style = ForkloreType.opinionRating,
              color = tone,
              modifier = Modifier.testTag("opinion-rating-${opinion.id}"),
            )
          } else {
            Text(
              text = "Not rated",
              style = ForkloreType.opinionRating.copy(fontStyle = null),
              color = colors.ink2,
            )
          }
        }
      }
      if (showTemperature) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          UppercaseLabel(text = "Temp", style = ForkloreType.fieldLabel, color = colors.ink2)
          if (opinion.temperature != null) {
            TemperatureLabel(
              temperature = opinion.temperature,
              modifier = Modifier.testTag("opinion-temperature-${opinion.id}"),
            )
          } else {
            Text("not said", style = ForkloreType.branchLabel, color = colors.ink2)
          }
        }
      }
      if (opinion.note.isNotBlank()) {
        Text(text = opinion.note, style = ForkloreType.opinionNote, color = colors.ink)
      }
      if (visit != null) {
        Text(
          text = "from ${visit.summaryLabel()}",
          style = ForkloreType.branchLabel,
          color = colors.ink2,
        )
      }
    }
  }
}

/** The mockup's circled initial. Decorative: the name beside it already says who this is. */
@Composable
private fun Avatar(initial: String, color: Color) {
  Box(
    modifier = Modifier.size(30.dp).border(2.dp, color, CircleShape).clearAndSetSemantics {},
    contentAlignment = Alignment.Center,
  ) {
    Text(text = initial, style = ForkloreType.opinionAuthor.copy(fontSize = 13.sp), color = color)
  }
}

/**
 * Author, rating, temperature, note, and (only if this entry has visits) which visit it came from.
 * Nothing but the author is needed, and even that only alongside *something* said — see
 * [OpinionDraft.canSave]. Temperature carries a line of explanation because "Mid" and "Lacking"
 * don't say what is being rated: it's how well the temperature suited the dish, not how hot it was.
 */
@Composable
private fun OpinionForm(
  draft: OpinionDraft,
  people: List<PersonEntity>,
  visits: List<VisitWithAttendees>,
  onAuthorChange: (Set<String>) -> Unit,
  onRatingChange: (Rating?) -> Unit,
  onTemperatureChange: (TemperatureRating?) -> Unit,
  onNoteChange: (String) -> Unit,
  onVisitChange: (String?) -> Unit,
  onCreatePerson: (String) -> Unit,
  onSave: () -> Unit,
  onCancel: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    UppercaseLabel(text = "Who said it", style = ForkloreType.fieldLabel, color = colors.ink2)
    PersonPicker(
      people = people,
      selected = setOfNotNull(draft.authorId),
      onSelectionChange = onAuthorChange,
      onCreatePerson = onCreatePerson,
      multiSelect = false,
    )
    UppercaseLabel(text = "Rating", style = ForkloreType.fieldLabel, color = colors.ink2)
    RatingPicker(
      rating = draft.rating,
      onRatingChange = onRatingChange,
      modifier = Modifier.testTag("opinion-rating"),
    )
    UppercaseLabel(text = "Temperature", style = ForkloreType.fieldLabel, color = colors.ink2)
    Text(
      text = "How well the temperature suited the dish — a cold one done right is Phenomenal.",
      style = ForkloreType.branchLabel,
      color = colors.ink2,
    )
    TemperaturePicker(
      temperature = draft.temperature,
      onTemperatureChange = onTemperatureChange,
      modifier = Modifier.testTag("opinion-temperature"),
    )
    NoteField(
      value = draft.note,
      onValueChange = onNoteChange,
      modifier = Modifier.testTag("opinion-note"),
    )
    if (visits.isNotEmpty()) {
      UppercaseLabel(
        text = "From which visit?",
        style = ForkloreType.fieldLabel,
        color = colors.ink2,
      )
      @OptIn(ExperimentalLayoutApi::class)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        visits.forEach { visit ->
          val selected = visit.visit.id == draft.visitId
          LedgerChip(
            label = visit.visit.summaryLabel(),
            selected = selected,
            // Tapping the chosen visit again unlinks it: "no particular visit" is a real answer.
            onClick = { onVisitChange(visit.visit.id.takeUnless { selected }) },
            modifier = Modifier.testTag("opinion-visit-${visit.visit.id}"),
          )
        }
      }
    }
    if (!draft.canSave) {
      Text(
        text = "Pick who said it, and add a rating, a temperature, a note — any of them.",
        style = ForkloreType.branchLabel,
        color = colors.ink2,
        modifier = Modifier.testTag("opinion-hint"),
      )
    }
    draft.error?.let {
      Text(it, color = colors.stamp, modifier = Modifier.testTag("opinion-error"))
    }
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(top = 4.dp),
    ) {
      LedgerGhostButton(
        text = "Cancel",
        onClick = onCancel,
        modifier = Modifier.testTag("opinion-cancel"),
      )
      LedgerPrimaryButton(
        text = "Save",
        onClick = onSave,
        enabled = draft.canSave && !draft.saving,
        modifier = Modifier.testTag("opinion-save"),
      )
      if (draft.opinionId != null) {
        Text(
          text = "Delete",
          style =
            MaterialTheme.typography.labelMedium.copy(textDecoration = TextDecoration.Underline),
          color = colors.stamp,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("opinion-delete")
              .clickable(enabled = !draft.saving, role = Role.Button, onClick = onDelete)
              .padding(4.dp),
        )
      }
    }
  }
}

@PreviewLightDark
@Composable
private fun DishOpinionsPreview() {
  val ana =
    PersonEntity(
      name = "Ana",
      normalizedName = "ana",
      isHouseholdMember = true,
      createdAt = 1,
      updatedAt = 1,
    )
  val sam =
    PersonEntity(
      name = "Sam",
      normalizedName = "sam",
      isHouseholdMember = true,
      createdAt = 2,
      updatedAt = 2,
    )
  val opinions =
    listOf(
      DishOpinionEntity(
        dishId = "d",
        authorId = ana.id,
        rating = Rating.BAD,
        temperature = TemperatureRating.LACKING,
        note = "Skip these.",
        createdAt = 1,
        updatedAt = 1,
      ),
      DishOpinionEntity(
        dishId = "d",
        authorId = sam.id,
        rating = Rating.MID,
        note = "They were fine, honestly.",
        createdAt = 2,
        updatedAt = 2,
      ),
    )
  ForkloreTheme {
    Surface {
      DishOpinionsBlock(
        dishId = "d",
        state =
          DishOpinionsUiState.Success(
            byDish = buildDishOpinions(opinions, listOf(ana, sam)),
            people = listOf(ana, sam),
            visits = emptyList(),
          ),
        draft = null,
        onStartAdd = {},
        onStartEdit = {},
        onCancelDraft = {},
        onAuthorChange = {},
        onRatingChange = {},
        onTemperatureChange = {},
        onNoteChange = {},
        onVisitChange = {},
        onCreatePerson = {},
        onSave = {},
        onDelete = {},
        modifier = Modifier.padding(16.dp),
      )
    }
  }
}
