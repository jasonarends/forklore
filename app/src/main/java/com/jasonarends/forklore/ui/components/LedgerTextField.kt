package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType
import com.jasonarends.forklore.ui.theme.accessibleUppercase

/**
 * A single-line ledger field: `card` fill with an `ink` bottom rule instead of a boxed outline —
 * issue #15 calls for no Material outlined box anywhere in this direction. Built on Material3's
 * filled [TextField] (recolored) rather than a hand-rolled `BasicTextField`: a hand-rolled field
 * with a separately merged label lost the `RequestFocus`/text-input semantics actions Compose's
 * test framework needs for `performTextInput` — [TextField] already gets that right, and every
 * existing test that types into a field by querying its label text depends on it.
 */
@Composable
fun LedgerTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
  val colors = ForkloreTheme.colors
  TextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.fillMaxWidth(),
    label = {
      Text(
        text = label.uppercase(),
        modifier = Modifier.accessibleUppercase(label),
        style = ForkloreType.fieldLabel,
      )
    },
    textStyle = ForkloreType.fieldInput,
    singleLine = true,
    keyboardOptions = KeyboardOptions(capitalization = capitalization),
    colors =
      TextFieldDefaults.colors(
        focusedContainerColor = colors.card,
        unfocusedContainerColor = colors.card,
        disabledContainerColor = colors.card,
        focusedIndicatorColor = colors.ink,
        unfocusedIndicatorColor = colors.ink,
        cursorColor = colors.ink,
        focusedTextColor = colors.ink,
        unfocusedTextColor = colors.ink,
        focusedLabelColor = colors.ink2,
        unfocusedLabelColor = colors.ink2,
      ),
  )
}
