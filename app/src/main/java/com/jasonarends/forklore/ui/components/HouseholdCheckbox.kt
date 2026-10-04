package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType

/**
 * The "Household member" toggle shared by every add-a-person affordance. The whole row is the
 * toggle target, so the label is tappable and TalkBack sees a single checkbox node rather than a
 * box plus loose text.
 */
@Composable
internal fun HouseholdCheckbox(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  Row(
    modifier =
      modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(
      checked = checked,
      onCheckedChange = null,
      colors =
        CheckboxDefaults.colors(
          checkedColor = colors.ink,
          checkmarkColor = colors.card,
          uncheckedColor = colors.ink2,
        ),
    )
    Text("Household member", style = ForkloreType.topBarSubtitle, color = colors.ink2)
  }
}
