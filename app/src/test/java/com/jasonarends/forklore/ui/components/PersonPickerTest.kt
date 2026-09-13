package com.jasonarends.forklore.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jasonarends.forklore.data.db.PersonEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersonPickerTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun multiSelect_accumulatesAndRemovesSelections() {
    val robin = person("Robin", household = true)
    val dale = person("Dale", household = true)
    var selected by mutableStateOf(setOf<String>())

    compose.setContent {
      PersonPicker(
        people = listOf(robin, dale),
        selected = selected,
        onSelectionChange = { selected = it },
        onCreatePerson = {},
      )
    }

    compose.onNodeWithTag("person-picker-chip-${robin.id}").performClick()
    assertEquals(setOf(robin.id), selected)

    compose.onNodeWithTag("person-picker-chip-${dale.id}").performClick()
    assertEquals(setOf(robin.id, dale.id), selected)

    compose.onNodeWithTag("person-picker-chip-${robin.id}").performClick()
    assertEquals(setOf(dale.id), selected)
  }

  @Test
  fun singleSelect_replacesRatherThanAccumulates() {
    val robin = person("Robin", household = true)
    val dale = person("Dale", household = true)
    var selected by mutableStateOf(setOf<String>())

    compose.setContent {
      PersonPicker(
        people = listOf(robin, dale),
        selected = selected,
        onSelectionChange = { selected = it },
        onCreatePerson = {},
        multiSelect = false,
      )
    }

    compose.onNodeWithTag("person-picker-chip-${robin.id}").performClick()
    assertEquals(setOf(robin.id), selected)

    compose.onNodeWithTag("person-picker-chip-${dale.id}").performClick()
    assertEquals(setOf(dale.id), selected)

    compose.onNodeWithTag("person-picker-chip-${dale.id}").performClick()
    assertEquals(emptySet<String>(), selected)
  }

  @Test
  fun householdMembersShowByDefault_outsideRecommendersAreHidden() {
    val ana = person("Ana", household = true)
    val dale = person("Dale", household = false)

    compose.setContent {
      PersonPicker(
        people = listOf(ana, dale),
        selected = emptySet(),
        onSelectionChange = {},
        onCreatePerson = {},
      )
    }

    compose.onNodeWithTag("person-picker-chip-${ana.id}").assertExists()
    compose.onNodeWithTag("person-picker-chip-${dale.id}").assertDoesNotExist()
  }

  @Test
  fun revealingShowsOutsideRecommenders() {
    val ana = person("Ana", household = true)
    val dale = person("Dale", household = false)

    compose.setContent {
      PersonPicker(
        people = listOf(ana, dale),
        selected = emptySet(),
        onSelectionChange = {},
        onCreatePerson = {},
      )
    }

    compose.onNodeWithText("Show everyone (1 more)").performClick()

    compose.onNodeWithTag("person-picker-chip-${dale.id}").assertExists()
  }

  @Test
  fun aSelectedOutsiderStaysVisibleWithoutRevealingEveryone() {
    val dale = person("Dale", household = false)

    compose.setContent {
      PersonPicker(
        people = listOf(dale),
        selected = setOf(dale.id),
        onSelectionChange = {},
        onCreatePerson = {},
      )
    }

    compose.onNodeWithTag("person-picker-chip-${dale.id}").assertExists()
  }

  @Test
  fun typingANewNameAndTappingAddCreatesAPersonAndClearsTheField() {
    var created: String? = null

    compose.setContent {
      PersonPicker(
        people = emptyList(),
        selected = emptySet(),
        onSelectionChange = {},
        onCreatePerson = { created = it },
      )
    }

    compose.onNodeWithTag("person-picker-new-name").performTextInput("  Casey  ")
    compose.onNodeWithText("Add").performClick()

    assertEquals("Casey", created)
  }

  private fun person(name: String, household: Boolean): PersonEntity =
    PersonEntity(
      name = name,
      normalizedName = name.lowercase(),
      isHouseholdMember = household,
      createdAt = 0,
      updatedAt = 0,
    )
}
