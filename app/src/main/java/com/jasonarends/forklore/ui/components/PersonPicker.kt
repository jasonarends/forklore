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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.ui.theme.ForkloreTheme

/**
 * Picks people from [people]: household members by default, with a way to reveal outside
 * recommenders who'd otherwise clutter "who was there". Shared by every screen that cites a person
 * — visit attendees, a person-scoped want, an opinion's author — so it supports both single and
 * multi select rather than each caller reimplementing selection.
 *
 * [selected] and [onSelectionChange] are hoisted, per CLAUDE.md: Room is the source of truth for
 * who exists, and the caller (a ViewModel) owns what's selected. Which of [people] is currently
 * *visible* is not persisted anywhere and is not part of the screen it's picking for, so it stays
 * local composable state, the same way a "show password" toggle would.
 *
 * Creating a new person is the one write this component causes, and even that goes through the
 * caller: [onCreatePerson] is required to call `PersonRepository.findOrCreate` — which returns the
 * existing id on a dedupe, not always a fresh one — and then add that id to [selected] itself
 * (replacing the current selection for single select). Without that second step a freshly created
 * person who isn't a household member matches neither the household filter nor [selected] and
 * silently disappears behind "Show everyone"; this component has no way to select it for you, since
 * it only learns the new person exists once [people] re-emits from the caller's `Flow`.
 */
@Composable
fun PersonPicker(
  people: List<PersonEntity>,
  selected: Set<String>,
  onSelectionChange: (Set<String>) -> Unit,
  onCreatePerson: (String) -> Unit,
  modifier: Modifier = Modifier,
  multiSelect: Boolean = true,
) {
  var showAll by remember { mutableStateOf(false) }
  var newName by remember { mutableStateOf("") }

  // A person already selected stays visible even when hidden by the household filter: a
  // recommender picked while "everyone" was showing must not silently vanish from view when the
  // list collapses back to household-only, which would look like their selection was lost.
  val visible = if (showAll) people else people.filter { it.isHouseholdMember || it.id in selected }
  val hiddenCount = people.size - visible.size

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      visible.forEach { person ->
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
        )
      }
    }
    if (!showAll && hiddenCount > 0) {
      TextButton(onClick = { showAll = true }) { Text("Show everyone ($hiddenCount more)") }
    } else if (showAll && people.any { !it.isHouseholdMember }) {
      TextButton(onClick = { showAll = false }) { Text("Household only") }
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = newName,
        onValueChange = { newName = it },
        modifier = Modifier.testTag("person-picker-new-name"),
        label = { Text("Add a person") },
        singleLine = true,
      )
      TextButton(
        enabled = newName.isNotBlank(),
        onClick = {
          val trimmed = newName.trim()
          if (trimmed.isNotEmpty()) {
            onCreatePerson(trimmed)
            newName = ""
          }
        },
      ) {
        Text("Add")
      }
    }
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
        onCreatePerson = {},
        modifier = Modifier.padding(16.dp),
      )
    }
  }
}
