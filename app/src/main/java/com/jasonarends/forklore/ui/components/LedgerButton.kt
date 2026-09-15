package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType
import com.jasonarends.forklore.ui.theme.hardShadow

/** Ink fill, card text, rust offset shadow — the primary action on every screen. */
@Composable
fun LedgerPrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val colors = ForkloreTheme.colors
  val alpha = if (enabled) 1f else 0.4f
  Surface(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.hardShadow(3.dp, 3.dp, colors.rust.copy(alpha = alpha), 3.dp),
    shape = RoundedCornerShape(3.dp),
    color = colors.ink.copy(alpha = alpha),
    contentColor = colors.card,
  ) {
    Text(
      text = text,
      style = ForkloreType.button,
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
    )
  }
}

/** A 1.5px ink2 outline, no fill — Cancel and other secondary actions. */
@Composable
fun LedgerGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = ForkloreTheme.colors
  Surface(
    onClick = onClick,
    modifier = modifier,
    shape = RoundedCornerShape(3.dp),
    color = Color.Transparent,
    contentColor = colors.ink2,
    border = BorderStroke(1.5.dp, colors.ink2),
  ) {
    Text(
      text = text,
      style = ForkloreType.button,
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
    )
  }
}

/** Card fill, 2px ink border, ink offset shadow — the full-width "Add a place" call to action. */
@Composable
fun LedgerOutlinedFullWidthButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  Surface(
    onClick = onClick,
    modifier = modifier.fillMaxWidth().hardShadow(3.dp, 3.dp, colors.ink, 3.dp),
    shape = RoundedCornerShape(3.dp),
    color = colors.card,
    contentColor = colors.ink,
    border = BorderStroke(2.dp, colors.ink),
  ) {
    Text(
      text = text,
      style = ForkloreType.button,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
    )
  }
}
