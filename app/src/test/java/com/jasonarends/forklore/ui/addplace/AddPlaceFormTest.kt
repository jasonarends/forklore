package com.jasonarends.forklore.ui.addplace

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AddPlaceFormTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun save_isDisabledUntilANameIsEntered() {
    composeTestRule.setContent {
      ForkloreTheme {
        AddPlaceForm(
          state = AddPlaceUiState(),
          placeListReady = true,
          onNameChange = {},
          onBranchLabelChange = {},
          onAddressChange = {},
          onNoteChange = {},
          onWarningChange = {},
          onSave = {},
          onCancel = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun typingAName_enablesSaveAndReportsIt() {
    var typed = ""
    composeTestRule.setContent {
      ForkloreTheme {
        AddPlaceForm(
          state = AddPlaceUiState(name = typed),
          placeListReady = true,
          onNameChange = { typed = it },
          onBranchLabelChange = {},
          onAddressChange = {},
          onNoteChange = {},
          onWarningChange = {},
          onSave = {},
          onCancel = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Name").performTextInput("Halberd")

    assertEquals("Halberd", typed)
  }

  @Test
  fun tappingSave_invokesTheCallback_whenAPlaceHasAName() {
    var saved = false
    composeTestRule.setContent {
      ForkloreTheme {
        AddPlaceForm(
          state = AddPlaceUiState(name = "Halberd"),
          placeListReady = true,
          onNameChange = {},
          onBranchLabelChange = {},
          onAddressChange = {},
          onNoteChange = {},
          onWarningChange = {},
          onSave = { saved = true },
          onCancel = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Save").performScrollTo().assertIsEnabled().performClick()

    assertEquals(true, saved)
  }

  @Test
  fun save_isDisabledWhileTheDefaultListIsStillLoading() {
    composeTestRule.setContent {
      ForkloreTheme {
        AddPlaceForm(
          state = AddPlaceUiState(name = "Halberd"),
          placeListReady = false,
          onNameChange = {},
          onBranchLabelChange = {},
          onAddressChange = {},
          onNoteChange = {},
          onWarningChange = {},
          onSave = {},
          onCancel = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun save_isDisabledWhileASaveIsAlreadyInFlight() {
    composeTestRule.setContent {
      ForkloreTheme {
        AddPlaceForm(
          state = AddPlaceUiState(name = "Halberd", saving = true),
          placeListReady = true,
          onNameChange = {},
          onBranchLabelChange = {},
          onAddressChange = {},
          onNoteChange = {},
          onWarningChange = {},
          onSave = {},
          onCancel = {},
        )
      }
    }

    composeTestRule.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun tappingCancel_invokesTheCallback() {
    var cancelled = false
    composeTestRule.setContent {
      ForkloreTheme {
        AddPlaceForm(
          state = AddPlaceUiState(),
          placeListReady = true,
          onNameChange = {},
          onBranchLabelChange = {},
          onAddressChange = {},
          onNoteChange = {},
          onWarningChange = {},
          onSave = {},
          onCancel = { cancelled = true },
        )
      }
    }

    composeTestRule.onNodeWithText("Cancel").performScrollTo().performClick()

    assertEquals(true, cancelled)
  }
}
