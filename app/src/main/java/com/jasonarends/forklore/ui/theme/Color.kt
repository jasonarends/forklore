package com.jasonarends.forklore.ui.theme

import androidx.compose.ui.graphics.Color

// Direction A — "Handwritten ledger" (issue #15). Every value below is the chosen mockup's own
// hex, mapped 1:1 across both palettes; dark `stamp` is the one deliberate exception (see below).
internal val PaperLight = Color(0xFFF5EEDC)
internal val CardLight = Color(0xFFFBF6E9)
internal val InkLight = Color(0xFF2E2414)
internal val Ink2Light = Color(0xFF6E5D44)
internal val RuleLight = Color(0xFFD9C9A6)
internal val CardBorderLight = Color(0xFFCDBB94)
internal val StampLight = Color(0xFF9C2020)
internal val RustLight = Color(0xFFA93A1B)
internal val CardShadowLight = Color(0x292E2414) // rgba(46,36,20,.16)
internal val Person1Light = Color(0xFF8A3B2B)
internal val Person2Light = Color(0xFF2C4A7C)

internal val PaperDark = Color(0xFF221A11)
internal val CardDark = Color(0xFF2B2116)
internal val InkDark = Color(0xFFF1E6D2)
internal val Ink2Dark = Color(0xFFB9A88A)
internal val RuleDark = Color(0xFF4A3B26)
internal val CardBorderDark = Color(0xFF5A492E)

// Deviation (issue #15, "Deviations" #1): the mockup's dark stamp #E0574B is 4.23:1 on dark
// `card`, short of the 4.5:1 an 11px label needs. #E26156 keeps the hue/saturation and clears AA
// (4.56:1 on card, 4.97:1 on paper). Light `stamp` stays the mockup's own #9C2020 (7.37:1).
internal val StampDark = Color(0xFFE26156)
internal val RustDark = Color(0xFFE08A4B)
internal val CardShadowDark = Color(0x80000000) // rgba(0,0,0,.5)
internal val Person1Dark = Color(0xFFE0876B)
internal val Person2Dark = Color(0xFF7FA8D9)

/**
 * Palette roles Material's [androidx.compose.material3.ColorScheme] has no slot for. `stamp` also
 * maps to Material's `error` role; it's repeated here so components can reach it without naming a
 * color role that's semantically about ink, not errors.
 */
data class ForkloreColors(
  val paper: Color,
  val card: Color,
  val ink: Color,
  val ink2: Color,
  val rule: Color,
  val cardBorder: Color,
  val stamp: Color,
  val rust: Color,
  val cardShadow: Color,
  val person1: Color,
  val person2: Color,
)

internal val LightForkloreColors =
  ForkloreColors(
    paper = PaperLight,
    card = CardLight,
    ink = InkLight,
    ink2 = Ink2Light,
    rule = RuleLight,
    cardBorder = CardBorderLight,
    stamp = StampLight,
    rust = RustLight,
    cardShadow = CardShadowLight,
    person1 = Person1Light,
    person2 = Person2Light,
  )

internal val DarkForkloreColors =
  ForkloreColors(
    paper = PaperDark,
    card = CardDark,
    ink = InkDark,
    ink2 = Ink2Dark,
    rule = RuleDark,
    cardBorder = CardBorderDark,
    stamp = StampDark,
    rust = RustDark,
    cardShadow = CardShadowDark,
    person1 = Person1Dark,
    person2 = Person2Dark,
  )
