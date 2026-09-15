package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
  val colors = ForkloreTheme.colors
  Column(modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
    Text(
      text = title,
      modifier = Modifier.semantics { heading() },
      style = ForkloreType.sectionLabel,
      color = colors.ink2,
    )
    HorizontalDivider(
      color = colors.rule,
      thickness = 1.dp,
      modifier = Modifier.padding(top = 4.dp),
    )
  }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
  Text(
    text = message,
    modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
    style = ForkloreType.noteText,
    color = ForkloreTheme.colors.ink2,
    textAlign = TextAlign.Center,
  )
}

@PreviewLightDark
@Composable
private fun SectionsPreview() {
  ForkloreTheme {
    Surface {
      Column(Modifier.padding(16.dp)) {
        SectionHeader("Dishes")
        EmptyState("No dishes yet.")
      }
    }
  }
}
