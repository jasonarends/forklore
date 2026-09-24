package com.jasonarends.forklore.ui.placedetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.data.db.DishInterestEntity
import com.jasonarends.forklore.data.db.DishStatus
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.ui.components.DishStatusChip
import com.jasonarends.forklore.ui.components.DishStatusPicker
import com.jasonarends.forklore.ui.components.InterestSummary
import com.jasonarends.forklore.ui.components.LedgerGhostButton
import com.jasonarends.forklore.ui.components.LedgerPrimaryButton
import com.jasonarends.forklore.ui.components.LedgerTextField
import com.jasonarends.forklore.ui.components.NoteField
import com.jasonarends.forklore.ui.components.PersonPicker
import com.jasonarends.forklore.ui.components.UppercaseLabel
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType

/**
 * One dish's interests — want, tried, never again — and the affordance to add another. Stateless:
 * state in, events out. Renders inside [DishesSection]'s row for the dish, so it sees only that
 * dish's [interests] and, when a form is open under it, the matching [draft].
 *
 * An interest being edited is replaced in place by the form, so the editor opens where the row was
 * rather than somewhere else on the screen. The "add" link is hidden while this dish's form is open
 * — one form at a time, as in the visits section.
 */
@Composable
internal fun DishInterests(
  interests: List<DishInterestEntity>,
  people: List<PersonEntity>,
  draft: InterestDraft?,
  onStartAdd: () -> Unit,
  onStartEdit: (DishInterestEntity) -> Unit,
  onCancelDraft: () -> Unit,
  onStatusChange: (DishStatus) -> Unit,
  onForPersonChange: (String?) -> Unit,
  onRecommendedByChange: (String?) -> Unit,
  onModificationChange: (String) -> Unit,
  onNoteChange: (String) -> Unit,
  onCreateForPerson: (String) -> Unit,
  onCreateRecommender: (String) -> Unit,
  onSave: () -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier,
) {
  fun name(id: String?) = id?.let { wanted -> people.firstOrNull { it.id == wanted }?.name }

  @Composable
  fun form(current: InterestDraft) =
    InterestForm(
      draft = current,
      people = people,
      onStatusChange = onStatusChange,
      onForPersonChange = onForPersonChange,
      onRecommendedByChange = onRecommendedByChange,
      onModificationChange = onModificationChange,
      onNoteChange = onNoteChange,
      onCreateForPerson = onCreateForPerson,
      onCreateRecommender = onCreateRecommender,
      onSave = onSave,
      onCancel = onCancelDraft,
      onRemove = onRemove,
    )

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    interests.forEach { interest ->
      if (draft?.interestId == interest.id) {
        form(draft)
      } else {
        InterestRow(
          interest = interest,
          forPersonName = name(interest.forPersonId),
          recommendedByName = name(interest.recommendedById),
          onEdit = { onStartEdit(interest) },
          modifier = Modifier.testTag("interest-row-${interest.id}"),
        )
      }
    }
    if (draft == null) {
      TextButton(onClick = onStartAdd, modifier = Modifier.testTag("interest-add")) {
        Text("+ Want / never again", color = ForkloreTheme.colors.ink2)
      }
    } else if (draft.interestId == null) {
      form(draft)
    }
  }
}

/**
 * The status chip is [DishStatusChip], so `NEVER_AGAIN` gets the same tilted rubber stamp the place
 * list uses for AVOID — shape and label carry it, not colour alone. The chip's own text ("Never
 * again" vs "Want to try") is what a screen reader announces.
 */
@Composable
private fun InterestRow(
  interest: DishInterestEntity,
  forPersonName: String?,
  recommendedByName: String?,
  onEdit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    DishStatusChip(interest.status)
    InterestSummary(
      forPersonName = forPersonName,
      recommendedByName = recommendedByName,
      modification = interest.modification,
      note = interest.note,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = "Edit",
      style = MaterialTheme.typography.labelMedium.copy(textDecoration = TextDecoration.Underline),
      color = colors.ink,
      modifier =
        Modifier.minimumInteractiveComponentSize()
          .testTag("interest-edit-${interest.id}")
          .clickable(onClick = onEdit)
          .padding(4.dp),
    )
  }
}

@Composable
private fun InterestForm(
  draft: InterestDraft,
  people: List<PersonEntity>,
  onStatusChange: (DishStatus) -> Unit,
  onForPersonChange: (String?) -> Unit,
  onRecommendedByChange: (String?) -> Unit,
  onModificationChange: (String) -> Unit,
  onNoteChange: (String) -> Unit,
  onCreateForPerson: (String) -> Unit,
  onCreateRecommender: (String) -> Unit,
  onSave: () -> Unit,
  onCancel: () -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = colors.card,
    border = BorderStroke(1.dp, colors.cardBorder),
    shape = RoundedCornerShape(3.dp),
  ) {
    Column(
      modifier = Modifier.padding(12.dp).testTag("interest-form"),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      DishStatusPicker(
        status = draft.status,
        onStatusChange = onStatusChange,
        modifier = Modifier.testTag("interest-status"),
      )
      UppercaseLabel(text = "Who's it for?", style = ForkloreType.fieldLabel, color = colors.ink2)
      PersonPicker(
        people = people,
        selected = setOfNotNull(draft.forPersonId),
        onSelectionChange = { onForPersonChange(it.firstOrNull()) },
        onCreatePerson = onCreateForPerson,
        multiSelect = false,
        modifier = Modifier.testTag("interest-for"),
      )
      UppercaseLabel(text = "Recommended by", style = ForkloreType.fieldLabel, color = colors.ink2)
      PersonPicker(
        people = people,
        selected = setOfNotNull(draft.recommendedById),
        onSelectionChange = { onRecommendedByChange(it.firstOrNull()) },
        onCreatePerson = onCreateRecommender,
        multiSelect = false,
        showEveryoneByDefault = true,
        modifier = Modifier.testTag("interest-recommended-by"),
      )
      LedgerTextField(
        value = draft.modification,
        onValueChange = onModificationChange,
        label = "How to order it",
        modifier = Modifier.testTag("interest-modification"),
      )
      NoteField(
        value = draft.note,
        onValueChange = onNoteChange,
        modifier = Modifier.testTag("interest-note"),
      )
      draft.error?.let {
        Text(it, color = colors.stamp, modifier = Modifier.testTag("interest-error"))
      }
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LedgerGhostButton(
          text = "Cancel",
          onClick = onCancel,
          modifier = Modifier.testTag("interest-cancel"),
        )
        LedgerPrimaryButton(
          text = "Save",
          onClick = onSave,
          enabled = !draft.saving,
          modifier = Modifier.testTag("interest-save"),
        )
        if (draft.interestId != null) {
          LedgerGhostButton(
            text = "Remove",
            onClick = onRemove,
            modifier = Modifier.testTag("interest-remove"),
          )
        }
      }
    }
  }
}

@PreviewLightDark
@Composable
private fun DishInterestsPreview() {
  val robin =
    PersonEntity(
      name = "Robin",
      normalizedName = "robin",
      isHouseholdMember = true,
      createdAt = 0,
      updatedAt = 0,
    )
  val dale =
    PersonEntity(
      name = "Dale",
      normalizedName = "dale",
      isHouseholdMember = false,
      createdAt = 0,
      updatedAt = 0,
    )
  ForkloreTheme {
    Surface {
      DishInterests(
        interests =
          listOf(
            DishInterestEntity(
              dishId = "d",
              status = DishStatus.WANT,
              forPersonId = robin.id,
              recommendedById = dale.id,
              modification = "add a Chilli bomb",
              createdAt = 0,
              updatedAt = 0,
            ),
            DishInterestEntity(
              dishId = "d",
              status = DishStatus.NEVER_AGAIN,
              note = "Soggy every time.",
              createdAt = 0,
              updatedAt = 0,
            ),
          ),
        people = listOf(robin, dale),
        draft = null,
        onStartAdd = {},
        onStartEdit = {},
        onCancelDraft = {},
        onStatusChange = {},
        onForPersonChange = {},
        onRecommendedByChange = {},
        onModificationChange = {},
        onNoteChange = {},
        onCreateForPerson = {},
        onCreateRecommender = {},
        onSave = {},
        onRemove = {},
        modifier = Modifier.padding(16.dp),
      )
    }
  }
}
