package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
  Text(
    text = title,
    modifier = modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
  )
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
  Text(
    text = message,
    modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
