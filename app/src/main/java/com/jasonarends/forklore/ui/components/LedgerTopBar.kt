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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType

/**
 * Sticky ledger-style header used by every screen: paper background, a 2px ink rule underneath, a
 * Zilla Slab title with a Caveat subtitle in the app's voice. [onBack], when given, renders a bare
 * back-chevron action; [action] is an additional trailing slot (the "People" link on place list).
 * Both can be given together — [onBack] leads, [action] trails — so a future screen needing both
 * doesn't hit a silently-discarded chevron.
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
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Text(text = subtitle, style = ForkloreType.topBarSubtitle, color = colors.ink2)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onBack != null) BackButton(onBack, colors.ink)
        action?.invoke()
      }
    }
    HorizontalDivider(color = colors.ink, thickness = 2.dp)
  }
}

@Composable
private fun BackButton(onClick: () -> Unit, tint: Color) {
  Surface(
    onClick = onClick,
    modifier = Modifier.minimumInteractiveComponentSize().semantics { contentDescription = "Back" },
    shape = RoundedCornerShape(3.dp),
    color = Color.Transparent,
    contentColor = tint,
    border = BorderStroke(1.5.dp, tint),
  ) {
    LedgerIcon(LedgerGlyph.Back, tint = tint, modifier = Modifier.padding(8.dp))
  }
}
