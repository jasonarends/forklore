package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.data.db.RevisitIntent
import com.jasonarends.forklore.ui.theme.ForkloreTheme

val RevisitIntent.label: String
  get() =
    when (this) {
      RevisitIntent.EAGER -> "Eager to go back"
      RevisitIntent.MAYBE -> "Maybe"
      RevisitIntent.WAIT -> "Wait a while"
      RevisitIntent.NEVER -> "Never again"
    }

/**
 * Picks whether we'd go back, or leaves it unset. Tapping the current answer clears it — a place
 * with no visits yet has no revisit intent, and forcing one would invent an opinion nobody has.
 */
@Composable
fun RevisitIntentPicker(
  intent: RevisitIntent?,
  onIntentChange: (RevisitIntent?) -> Unit,
  modifier: Modifier = Modifier,
) {
  ChoiceChips(
    options = RevisitIntent.entries,
    selected = intent,
    onSelect = { picked -> onIntentChange(picked.takeUnless { it == intent }) },
    label = { it.label },
    modifier = modifier,
  )
}

@PreviewLightDark
@Composable
private fun RevisitIntentPickerPreview() {
  ForkloreTheme {
    Surface {
      var intent by remember { mutableStateOf<RevisitIntent?>(RevisitIntent.EAGER) }
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RevisitIntentPicker(intent = intent, onIntentChange = { intent = it })
      }
    }
  }
}
