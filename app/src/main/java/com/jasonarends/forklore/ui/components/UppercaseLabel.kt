package com.jasonarends.forklore.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle

/**
 * Issue #18: displays [text] uppercase (section labels, field labels, chips, the stamp) but pins
 * the semantics tree's text back to [text] as written, so `onNodeWithText("Food")` still matches
 * the real label rather than the display transform, and so TalkBack reads the word itself instead
 * of a short all-caps run some TTS engines spell out letter by letter. One composable taking one
 * string, rather than a modifier the caller pairs with a separately-uppercased `text =` by hand:
 * two expressions that have to agree can silently drift (wrong `.uppercase()` target, or a label
 * that only gets one of the two treatments).
 *
 * `clearAndSetSemantics` also drops this node's own `GetTextLayoutResult` action, not just the
 * merged-up `text` value it doesn't need to be re-derived — fine for the one-to-three-word labels
 * this is used for. [extraSemantics] carries anything else the node needs (e.g. `SectionHeader`'s
 * `heading()`), since `clearAndSetSemantics` replaces a node's whole semantics config and can't be
 * layered with a second `Modifier.semantics {}` in the chain.
 */
@Composable
internal fun UppercaseLabel(
  text: String,
  style: TextStyle,
  modifier: Modifier = Modifier,
  color: Color = Color.Unspecified,
  extraSemantics: SemanticsPropertyReceiver.() -> Unit = {},
) {
  Text(
    text = text.uppercase(),
    modifier =
      modifier.clearAndSetSemantics {
        this.text = AnnotatedString(text)
        extraSemantics()
      },
    style = style,
    color = color,
  )
}
