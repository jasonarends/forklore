package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType
import com.jasonarends.forklore.ui.theme.hardShadow
import com.jasonarends.forklore.ui.theme.ruledBackground

/**
 * The one way free text is captured. Multiline, no length limit, no validation and no error state:
 * a note is never the reason a save fails (see CLAUDE.md, "Free text is first-class"). Styled as an
 * index card — ruled lines, a hard offset shadow, Caveat handwriting — per issue #15; [warning]
 * swaps the border/text to `stamp` red for the add-place "Warning" field.
 * `semantics(mergeDescendants = true)` on the outer column mirrors what
 * [androidx.compose.material3.OutlinedTextField] did before — the label and the field merge into
 * one queryable node.
 */
@Composable
fun NoteField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  label: String = "Note",
  warning: Boolean = false,
) {
  val colors = ForkloreTheme.colors
  val borderColor = if (warning) colors.stamp else colors.cardBorder
  val textColor = if (warning) colors.stamp else colors.ink
  Column(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
    UppercaseLabel(text = label, style = ForkloreType.fieldLabel, color = colors.ink2)
    Surface(
      modifier =
        Modifier.fillMaxWidth().padding(top = 5.dp).hardShadow(2.dp, 3.dp, colors.cardShadow, 2.dp),
      shape = RoundedCornerShape(2.dp),
      color = colors.card,
      border = BorderStroke(1.5.dp, borderColor),
    ) {
      BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(14.dp).ruledBackground(colors.rule, 23.dp),
        textStyle = ForkloreType.noteText.copy(color = textColor),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        minLines = 3,
        cursorBrush = SolidColor(textColor),
      )
    }
  }
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

@PreviewLightDark
@Composable
private fun NoteFieldWarningPreview() {
  ForkloreTheme {
    Surface {
      NoteField(
        value = "No walk-ins after 7 — call ahead.",
        onValueChange = {},
        label = "Warning",
        warning = true,
        modifier = Modifier.padding(16.dp),
      )
    }
  }
}
