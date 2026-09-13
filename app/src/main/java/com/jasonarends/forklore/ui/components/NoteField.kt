package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.ui.theme.ForkloreTheme

/**
 * The one way free text is captured. Multiline, no length limit, no validation and no error state:
 * a note is never the reason a save fails (see CLAUDE.md, "Free text is first-class").
 */
@Composable
fun NoteField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  label: String = "Note",
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.fillMaxWidth(),
    label = { Text(label) },
    minLines = 3,
    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
  )
}

@PreviewLightDark
@Composable
private fun NoteFieldPreview() {
  ForkloreTheme {
    Surface {
      NoteField(
        value = "Skip the bread.\nAsk for the sauce on the side.",
        onValueChange = {},
        modifier = Modifier.padding(16.dp),
      )
    }
  }
}
