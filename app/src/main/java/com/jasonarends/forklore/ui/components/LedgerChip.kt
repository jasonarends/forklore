package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType
import com.jasonarends.forklore.ui.theme.tilt

/**
 * The generic ledger chip: outlined ([selected] = false) or filled/"done" ([selected] = true), per
 * issue #15's `.a-chip`/`.a-chip.done`. Shared by every mutually-exclusive picker ([ChoiceChips])
 * and the read-only pending/done statuses in `StatusChip.kt`.
 */
@Composable
internal fun LedgerChip(
  label: String,
  selected: Boolean,
  onClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
  icon: LedgerGlyph? = null,
) {
  val colors = ForkloreTheme.colors
  val borderColor = if (selected) colors.ink else colors.ink2
  val contentColor = if (selected) colors.ink else colors.ink2
  val fill = if (selected) colors.rule else Color.Transparent
  val base =
    if (onClick != null) {
      modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
    } else {
      modifier
    }
  Surface(
    modifier = base,
    shape = RoundedCornerShape(3.dp),
    color = fill,
    contentColor = contentColor,
    border = BorderStroke(1.5.dp, borderColor),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (icon != null) {
        LedgerIcon(icon, tint = contentColor, modifier = Modifier.padding(end = 4.dp))
      }
      Text(text = label, style = ForkloreType.chip)
    }
  }
}

/**
 * The rubber-stamp treatment for a committed AVOID/NEVER_AGAIN status: shape, rotation, border and
 * icon carry the meaning, not color alone, per issue #15's contrast requirement.
 */
@Composable
internal fun LedgerStamp(label: String, modifier: Modifier = Modifier) {
  val colors = ForkloreTheme.colors
  val stampColor = colors.stamp.copy(alpha = 0.92f)
  Surface(
    modifier = modifier.tilt(-6f),
    shape = RoundedCornerShape(4.dp),
    color = Color.Transparent,
    contentColor = stampColor,
    border = BorderStroke(2.dp, stampColor),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LedgerIcon(
        LedgerGlyph.CircleSlash,
        tint = stampColor,
        modifier = Modifier.padding(end = 4.dp),
      )
      Text(text = label, style = ForkloreType.stamp)
    }
  }
}
