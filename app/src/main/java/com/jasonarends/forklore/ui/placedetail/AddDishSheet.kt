package com.jasonarends.forklore.ui.placedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.data.db.DishWithAliases
import com.jasonarends.forklore.ui.components.LedgerChip
import com.jasonarends.forklore.ui.components.LedgerPrimaryButton
import com.jasonarends.forklore.ui.components.LedgerTextField
import com.jasonarends.forklore.ui.theme.ForkloreTheme

/**
 * The "add a dish" surface: a bottom sheet over place detail rather than a field inline under the
 * dish list. Typing offers matching [suggestions] as chips (see [DishesViewModel.suggestions]) so a
 * dish already recorded under a different spelling is picked rather than re-typed into a duplicate;
 * tapping one submits it exactly as [onSubmit] would. The button submits whatever was typed
 * regardless — `DishRepository.findOrCreateDish` is what actually guards against a duplicate
 * landing in Room; this only makes the existing option visible.
 *
 * A sheet rather than a screen: it is one field and a row of chips. It opens full height
 * (`skipPartiallyExpanded`) so the suggestions stay above the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddDishSheet(
  query: String,
  suggestions: List<DishWithAliases>,
  onQueryChange: (String) -> Unit,
  onSubmit: (String) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = ForkloreTheme.colors.paper,
    modifier = modifier.testTag("add-dish-sheet"),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .imePadding()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(bottom = 20.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        LedgerTextField(
          value = query,
          onValueChange = onQueryChange,
          label = "Add a dish",
          capitalization = KeyboardCapitalization.Words,
          modifier = Modifier.weight(1f).testTag("dish-query-field"),
        )
        LedgerPrimaryButton(
          text = "Add",
          onClick = { onSubmit(query) },
          enabled = query.isNotBlank(),
          modifier = Modifier.testTag("dish-submit"),
        )
      }
      if (suggestions.isNotEmpty()) {
        FlowRow(
          modifier = Modifier.padding(top = 6.dp).testTag("dish-suggestions"),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          suggestions.forEach { suggestion ->
            LedgerChip(
              label = suggestion.dish.canonicalName,
              selected = false,
              onClick = { onSubmit(suggestion.dish.canonicalName) },
              modifier = Modifier.testTag("dish-suggestion-${suggestion.dish.id}"),
            )
          }
        }
      }
    }
  }
}
