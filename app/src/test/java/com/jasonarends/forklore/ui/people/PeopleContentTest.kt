package com.jasonarends.forklore.ui.people

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
  fun addingAPersonInvokesTheCallbackWithTheTrimmedName() {
    var added: Pair<String, Boolean>? = null
    compose.setContent {
      PeopleContent(
        people = emptyList(),
        renameError = null,
        onAddPerson = { name, household -> added = name to household },
      )
    }

    compose.onNodeWithTag("person-picker-new-name").performTextInput("  Robin  ")
    compose.onNodeWithText("Add").performClick()

    assertEquals("Robin" to false, added)
  }

  @Test
  fun tappingAPersonChipTogglesHouseholdMembership() {
    val robin = person("Robin", household = false)
    var toggled: Pair<String, Boolean>? = null
    compose.setContent {
      PeopleContent(
        people = listOf(robin),
        renameError = null,
        onToggleHousehold = { id, isHousehold -> toggled = id to isHousehold },
      )
    }

    compose.onNodeWithTag("person-picker-chip-${robin.id}").performClick()

    assertEquals(robin.id to true, toggled)
  }

  @Test
  fun renamingAPersonInvokesTheCallback() {
    val dale = person("Dale", household = false)
    var renamed: Pair<String, String>? = null
    compose.setContent {
      PeopleContent(
        people = listOf(dale),
        renameError = null,
        onRename = { id, name -> renamed = id to name },
      )
    }

    compose.onNodeWithTag("person-rename-button-${dale.id}").performClick()
    compose.onNodeWithTag("person-rename-field-${dale.id}").performTextReplacement("Dale R.")
    compose.onNodeWithText("Save").performClick()

    assertEquals(dale.id to "Dale R.", renamed)
  }

  @Test
  fun aRenameConflictIsShownAndCanBeDismissed() {
    var dismissed = false
    compose.setContent {
      PeopleContent(
        people = emptyList(),
        renameError = "Someone is already named \"Dale\".",
        onDismissRenameError = { dismissed = true },
      )
    }

    compose.onNodeWithText("Someone is already named \"Dale\".").assertExists()
    compose.onNodeWithText("Dismiss").performClick()

    assertEquals(true, dismissed)
  }

  @Test
  fun emptyStateInvitesAddingTheFirstPerson() {
    compose.setContent { PeopleContent(people = emptyList(), renameError = null) }

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
