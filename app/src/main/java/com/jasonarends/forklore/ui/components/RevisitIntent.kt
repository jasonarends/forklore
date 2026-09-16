package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.data.db.RevisitIntent
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType
import com.jasonarends.forklore.ui.theme.wavyUnderline

val RevisitIntent.label: String
  get() =
    when (this) {
      RevisitIntent.EAGER -> "Eager to go back"
      RevisitIntent.MAYBE -> "Maybe"
      RevisitIntent.WAIT -> "Wait a while"
      RevisitIntent.NEVER -> "Never again"
    }

/**
 * Plain inline words, not chips — the wavy stamp-colored underline is what marks the answer, per
 * issue #15's "RevisitIntent" component. `FlowRow` rather than the mockup's single row, so this
 * still wraps rather than overflows at large system font scales. Tapping the current answer clears
 * it — a place with no visits yet has no revisit intent, and forcing one would invent an opinion
 * nobody has.
 */
@Composable
fun RevisitIntentPicker(
  intent: RevisitIntent?,
  onIntentChange: (RevisitIntent?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  FlowRow(
    modifier = modifier.selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    RevisitIntent.entries.forEach { option ->
      val isSelected = option == intent
      Text(
        text = option.label,
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .selectable(
              selected = isSelected,
              onClick = { onIntentChange(option.takeUnless { it == intent }) },
              role = Role.RadioButton,
            )
            .then(if (isSelected) Modifier.wavyUnderline(colors.stamp) else Modifier),
        style = ForkloreType.button,
        color = colors.ink,
      )
    }
  }
}

@PreviewLightDark
@Composable
private fun RevisitIntentPickerPreview() {
  ForkloreTheme {
    Surface {
      RevisitIntentPicker(
        intent = RevisitIntent.EAGER,
        onIntentChange = {},
        modifier = Modifier.padding(16.dp),
      )
    }
  }
}
