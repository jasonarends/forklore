package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType

/**
 * Sticky ledger-style header used by every screen: paper background, a 2px ink rule underneath, a
 * Zilla Slab title with a Caveat subtitle in the app's voice. [onBack], when given, renders a bare
 * back-chevron action; [action] overrides it for the one screen (place list) with a real action
 * instead (the "People" link) — a screen never has both.
 */
@Composable
fun LedgerTopBar(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  action: @Composable (() -> Unit)? = null,
) {
  val colors = ForkloreTheme.colors
  Column(modifier = modifier.fillMaxWidth().background(colors.paper).statusBarsPadding()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top,
    ) {
      Column {
        Text(text = title, style = ForkloreType.topBarTitle, color = colors.ink)
        Text(text = subtitle, style = ForkloreType.topBarSubtitle, color = colors.ink2)
      }
      when {
        action != null -> action()
        onBack != null ->
          Surface(
            onClick = onBack,
            shape = RoundedCornerShape(3.dp),
            color = Color.Transparent,
            contentColor = colors.ink,
            border = BorderStroke(1.5.dp, colors.ink),
          ) {
            LedgerIcon(LedgerGlyph.Back, tint = colors.ink, modifier = Modifier.padding(8.dp))
          }
      }
    }
    HorizontalDivider(color = colors.ink, thickness = 2.dp)
  }
}
