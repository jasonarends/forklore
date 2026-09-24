package com.jasonarends.forklore.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jasonarends.forklore.data.db.DogPolicy
import com.jasonarends.forklore.ui.theme.ForkloreTheme

val DogPolicy.label: String
  get() =
    when (this) {
      DogPolicy.PATIO -> "Dog patio"
      DogPolicy.INSIDE -> "Dogs inside"
      DogPolicy.NO -> "No dogs"
    }

internal const val DOG_POLICY_NOT_RECORDED = "Not recorded"

/** Whether the list row shows a paw: only where dogs are actually welcome. */
internal val DogPolicy?.allowsDogs: Boolean
  get() = this == DogPolicy.PATIO || this == DogPolicy.INSIDE

/**
 * Three answers plus an explicit "Not recorded" chip: nobody having asked is different from the
 * restaurant saying no ([DogPolicy.NO]), so the absence of an answer has to stay a pickable state
 * rather than only being reachable by un-tapping. Tapping "Not recorded" is how a set value is
 * cleared.
 */
@Composable
fun DogPolicyPicker(
  dogPolicy: DogPolicy?,
  onDogPolicyChange: (DogPolicy?) -> Unit,
  modifier: Modifier = Modifier,
) {
  ChoiceChips(
    options = listOf(null) + DogPolicy.entries,
    selected = dogPolicy,
    onSelect = onDogPolicyChange,
    label = { it?.label ?: DOG_POLICY_NOT_RECORDED },
    modifier = modifier,
  )
}

@PreviewLightDark
@Composable
private fun DogPolicyPickerPreview() {
  ForkloreTheme {
    Surface {
      DogPolicyPicker(
        dogPolicy = DogPolicy.PATIO,
        onDogPolicyChange = {},
        modifier = Modifier.padding(16.dp),
      )
    }
  }
}
