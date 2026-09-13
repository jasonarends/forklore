package com.jasonarends.forklore.ui.people

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.jasonarends.forklore.data.db.PersonEntity
import com.jasonarends.forklore.data.db.normalizeDishName
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PeopleContentTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun addingAPersonDefaultsToHouseholdMember() {
    var added: Pair<String, Boolean>? = null
    compose.setContent {
      PeopleContent(
        people = emptyList(),
        renameError = null,
        onAddPerson = { name, household -> added = name to household },
        onToggleHousehold = { _, _ -> },
        onRename = { _, _ -> },
        onDismissRenameError = {},
      )
    }

    compose.onNodeWithTag("people-add-name").performTextInput("  Robin  ")
    compose.onNodeWithText("Add").performClick()

    assertEquals("Robin" to true, added)
  }

  @Test
  fun uncheckingHouseholdBeforeAddingRecordsAnOutsider() {
    var added: Pair<String, Boolean>? = null
    compose.setContent {
      PeopleContent(
        people = emptyList(),
        renameError = null,
        onAddPerson = { name, household -> added = name to household },
        onToggleHousehold = { _, _ -> },
        onRename = { _, _ -> },
        onDismissRenameError = {},
      )
    }

    compose.onNodeWithTag("people-add-household").performClick()
    compose.onNodeWithTag("people-add-name").performTextInput("Dale")
    compose.onNodeWithText("Add").performClick()

    assertEquals("Dale" to false, added)
  }

  @Test
  fun tappingTheHouseholdSwitchTogglesMembership() {
    val robin = person("Robin", household = false)
    var toggled: Pair<String, Boolean>? = null
    compose.setContent {
      PeopleContent(
        people = listOf(robin),
        renameError = null,
        onAddPerson = { _, _ -> },
        onToggleHousehold = { id, isHousehold -> toggled = id to isHousehold },
        onRename = { _, _ -> },
        onDismissRenameError = {},
      )
    }

    compose.onNodeWithTag("person-household-switch-${robin.id}").performClick()

    assertEquals(robin.id to true, toggled)
  }

  @Test
  fun aRenameConflictStaysOnlyOnTheAffectedRow() {
    val dale = person("Dale", household = false)
    val robin = person("Robin", household = false)
    var renameError by mutableStateOf<RenameError?>(null)

    compose.setContent {
      PeopleContent(
        people = listOf(dale, robin),
        renameError = renameError,
        onAddPerson = { _, _ -> },
        onToggleHousehold = { _, _ -> },
        onRename = { id, name ->
          renameError = RenameError(id, "Someone is already named \"$name\".")
        },
        onDismissRenameError = { renameError = null },
      )
    }

    compose.onNodeWithTag("person-rename-button-${dale.id}").performClick()
    compose.onNodeWithTag("person-rename-field-${dale.id}").performTextReplacement("Robin")
    compose.onNodeWithText("Save").performClick()

    // The row stays open with the error visible, rather than closing over a rename that failed...
    compose.onNodeWithTag("person-rename-field-${dale.id}").assertExists()
    compose.onNodeWithTag("person-rename-error-${dale.id}").assertExists()
    // ...and Robin's row, unaffected, shows no error of its own.
    compose.onNodeWithTag("person-rename-button-${robin.id}").assertExists()
  }

  @Test
  fun aSuccessfulRenameClosesTheRowOnceThePersonNameCatchesUp() {
    val dale = person("Dale", household = false)
    var people by mutableStateOf(listOf(dale))
    var renameError by mutableStateOf<RenameError?>(null)

    compose.setContent {
      PeopleContent(
        people = people,
        renameError = renameError,
        onAddPerson = { _, _ -> },
        onToggleHousehold = { _, _ -> },
        onRename = { id, name ->
          // Simulates Room's Flow re-emitting the renamed person once the rename lands.
          people = people.map { if (it.id == id) it.copy(name = name) else it }
          renameError = null
        },
        onDismissRenameError = { renameError = null },
      )
    }

    compose.onNodeWithTag("person-rename-button-${dale.id}").performClick()
    compose.onNodeWithTag("person-rename-field-${dale.id}").performTextReplacement("Dale R.")
    compose.onNodeWithText("Save").performClick()

    compose.onNodeWithText("Dale R.").assertExists()
    compose.onNodeWithTag("person-rename-field-${dale.id}").assertDoesNotExist()
  }

  @Test
  fun cancellingARenameDismissesItsError() {
    val dale = person("Dale", household = false)
    var dismissed = false
    compose.setContent {
      PeopleContent(
        people = listOf(dale),
        renameError = RenameError(dale.id, "Someone is already named \"Robin\"."),
        onAddPerson = { _, _ -> },
        onToggleHousehold = { _, _ -> },
        onRename = { _, _ -> },
        onDismissRenameError = { dismissed = true },
      )
    }

    compose.onNodeWithTag("person-rename-button-${dale.id}").performClick()
    compose.onNodeWithText("Cancel").performClick()

    assertEquals(true, dismissed)
  }

  @Test
  fun emptyStateInvitesAddingTheFirstPerson() {
    compose.setContent {
      PeopleContent(
        people = emptyList(),
        renameError = null,
        onAddPerson = { _, _ -> },
        onToggleHousehold = { _, _ -> },
        onRename = { _, _ -> },
        onDismissRenameError = {},
      )
    }

    compose.onNodeWithText("No one yet. Add a person below.").assertExists()
  }

  private fun person(name: String, household: Boolean): PersonEntity =
    PersonEntity(
      name = name,
      normalizedName = normalizeDishName(name),
      isHouseholdMember = household,
      createdAt = 0,
      updatedAt = 0,
    )
}
