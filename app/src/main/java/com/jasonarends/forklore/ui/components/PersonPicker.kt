package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType

/**
 * Picks people from [people]. Everyone is always shown — household members first, then everyone
 * else after them (under an "Others" label when both exist) — so a person added from one picker is
 * never hidden from another. Shared by every screen that cites a person — visit attendees, a
 * person-scoped want, a recommender, an opinion's author — so it supports both single and multi
 * select rather than each caller reimplementing selection.
 *
 * [selected] and [onSelectionChange] are hoisted, per CLAUDE.md: Room is the source of truth for
 * who exists, and the caller (a ViewModel) owns what's selected.
 *
 * Creating a new person is the one write this component causes, and even that goes through the
 * caller: [onCreatePerson] receives the trimmed name and whether the user ticked "Household". It is
 * required to call `PersonRepository.findOrCreate` — which returns the existing id on a dedupe, not
 * always a fresh one — and then add that id to [selected] itself (replacing the current selection
 * for single select), since this component only learns the new person exists once [people] re-emits
 * from the caller's `Flow`. Household membership is the user's explicit choice here, never inferred
 * from which picker was used; it defaults to off because the people typed into a picker are mostly
 * one-off recommenders, and the household is small and set up on the People screen.
 */
@Composable
fun PersonPicker(
  people: List<PersonEntity>,
  selected: Set<String>,
  onSelectionChange: (Set<String>) -> Unit,
  onCreatePerson: (name: String, isHouseholdMember: Boolean) -> Unit,
  modifier: Modifier = Modifier,
  multiSelect: Boolean = true,
) {
  var newName by remember { mutableStateOf("") }
  var newIsHousehold by remember { mutableStateOf(false) }
  val colors = ForkloreTheme.colors

  val (household, others) = people.partition { it.isHouseholdMember }

  @Composable
  fun Chips(group: List<PersonEntity>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      group.forEach { person ->
        val isSelected = person.id in selected
        LedgerChip(
          label = person.name,
          selected = isSelected,
          onClick = {
            val next =
              when {
                multiSelect && isSelected -> selected - person.id
                multiSelect -> selected + person.id
                isSelected -> emptySet()
                else -> setOf(person.id)
              }
            onSelectionChange(next)
          },
          modifier = Modifier.testTag("person-picker-chip-${person.id}"),
          role = if (multiSelect) Role.Checkbox else Role.RadioButton,
        )
      }
    }
  }

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (household.isNotEmpty()) Chips(household)
    if (household.isNotEmpty() && others.isNotEmpty()) {
      UppercaseLabel(text = "Others", style = ForkloreType.fieldLabel, color = colors.ink2)
    }
    if (others.isNotEmpty()) Chips(others)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = newName,
        onValueChange = { newName = it },
        modifier = Modifier.weight(1f).testTag("person-picker-new-name"),
        label = { Text("Add a person") },
        singleLine = true,
      )
      TextButton(
        enabled = newName.isNotBlank(),
        onClick = {
          val trimmed = newName.trim()
          if (trimmed.isNotEmpty()) {
            onCreatePerson(trimmed, newIsHousehold)
            newName = ""
            newIsHousehold = false
          }
        },
      ) {
        Text("Add")
      }
    }
    HouseholdCheckbox(
      checked = newIsHousehold,
      onCheckedChange = { newIsHousehold = it },
      modifier = Modifier.testTag("person-picker-new-household"),
    )
  }
}

@PreviewLightDark
@Composable
private fun PersonPickerPreview() {
  ForkloreTheme {
    Surface {
      var selected by remember { mutableStateOf(setOf<String>()) }
      PersonPicker(
        people =
          listOf(
            PersonEntity(
              name = "Ana",
              normalizedName = "ana",
              isHouseholdMember = true,
              createdAt = 0,
              updatedAt = 0,
            ),
            PersonEntity(
              name = "Dale",
              normalizedName = "dale",
              isHouseholdMember = false,
              createdAt = 0,
              updatedAt = 0,
            ),
          ),
        selected = selected,
        onSelectionChange = { selected = it },
        onCreatePerson = { _, _ -> },
        modifier = Modifier.padding(16.dp),
      )
    }
  }
}
