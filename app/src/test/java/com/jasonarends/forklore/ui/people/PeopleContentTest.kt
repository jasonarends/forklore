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
        editing = null,
        onAddPerson = { name, household -> added = name to household },
        onToggleHousehold = { _, _ -> },
        onStartRename = {},
        onRename = { _, _ -> },
        onCancelRename = {},
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
        editing = null,
        onAddPerson = { name, household -> added = name to household },
        onToggleHousehold = { _, _ -> },
        onStartRename = {},
        onRename = { _, _ -> },
        onCancelRename = {},
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
        editing = null,
        onAddPerson = { _, _ -> },
        onToggleHousehold = { id, isHousehold -> toggled = id to isHousehold },
        onStartRename = {},
        onRename = { _, _ -> },
        onCancelRename = {},
      )
    }

    compose.onNodeWithTag("person-household-switch-${robin.id}").performClick()

    assertEquals(robin.id to true, toggled)
  }

  @Test
  fun tappingRenameStartsEditingThatPerson() {
    val dale = person("Dale", household = false)
    var started: String? = null
    // Declared outside setContent, not inside it: a `var ... by mutableStateOf` written directly
    // in a composable body (no `remember`) is recreated from scratch on every recomposition, which
    // would silently reset `editing` back to null right after the click sets it.
    var editing by mutableStateOf<RenameEdit?>(null)

    compose.setContent {
      PeopleContent(
        people = listOf(dale),
        editing = editing,
        onAddPerson = { _, _ -> },
        onToggleHousehold = { _, _ -> },
        onStartRename = { id ->
          started = id
          editing = RenameEdit(id)
        },
        onRename = { _, _ -> },
        onCancelRename = { editing = null },
      )
    }

    compose.onNodeWithTag("person-rename-button-${dale.id}").performClick()

    assertEquals(dale.id, started)
    compose.onNodeWithTag("person-rename-field-${dale.id}").assertExists()
  }

  @Test
  fun savingARenameInvokesTheCallbackWithTheTypedText() {
    val dale = person("Dale", household = false)
    var renamed: Pair<String, String>? = null
    compose.setContent {
      PeopleContent(
        people = listOf(dale),
        editing = RenameEdit(dale.id),
        onAddPerson = { _, _ -> },
        onToggleHousehold = { _, _ -> },
        onStartRename = {},
        onRename = { id, name -> renamed = id to name },
        onCancelRename = {},
      )
    }

    compose.onNodeWithTag("person-rename-field-${dale.id}").performTextReplacement("Dale R.")
    compose.onNodeWithText("Save").performClick()

    assertEquals(dale.id to "Dale R.", renamed)
  }

  @Test
  fun cancellingARenameInvokesTheCallback() {
    val dale = person("Dale", household = false)
    var cancelled = false
    compose.setContent {
      PeopleContent(
        people = listOf(dale),
        editing = RenameEdit(dale.id),
        onAddPerson = { _, _ -> },
        onToggleHousehold = { _, _ -> },
        onStartRename = {},
        onRename = { _, _ -> },
        onCancelRename = { cancelled = true },
      )
    }

    compose.onNodeWithText("Cancel").performClick()

    assertEquals(true, cancelled)
  }

  @Test
  fun aRenameConflictShowsOnlyOnTheAffectedRow() {
    val dale = person("Dale", household = false)
    val robin = person("Robin", household = false)
    compose.setContent {
      PeopleContent(
        people = listOf(dale, robin),
        editing = RenameEdit(dale.id, "Someone is already named \"Robin\"."),
        onAddPerson = { _, _ -> },
        onToggleHousehold = { _, _ -> },
        onStartRename = {},
        onRename = { _, _ -> },
        onCancelRename = {},
      )
    }

    // Dale is mid-edit with the conflict visible...
    compose.onNodeWithTag("person-rename-field-${dale.id}").assertExists()
    compose.onNodeWithTag("person-rename-error-${dale.id}").assertExists()
    // ...while Robin, uninvolved, still shows its plain "Rename" button, not an error of its own.
    compose.onNodeWithTag("person-rename-button-${robin.id}").assertExists()
  }

  @Test
  fun emptyStateInvitesAddingTheFirstPerson() {
    compose.setContent {
      PeopleContent(
        people = emptyList(),
        editing = null,
        onAddPerson = { _, _ -> },
        onToggleHousehold = { _, _ -> },
        onStartRename = {},
        onRename = { _, _ -> },
        onCancelRename = {},
      )
    }

    compose.onNodeWithText("No one yet. Add someone above.").assertExists()
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
