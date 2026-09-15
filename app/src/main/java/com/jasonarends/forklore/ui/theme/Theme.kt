package com.jasonarends.forklore.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

// error(), not a color fallback: a composable rendered outside ForkloreTheme should fail loudly
// rather than silently painting light-mode paper on a dark device.
private val LocalForkloreColors =
  staticCompositionLocalOf<ForkloreColors> { error("ForkloreTheme not applied") }

// 2px cards, 3px buttons/chips/fields, 4px the stamp (see issue #15, "Shape, borders, elevation").
private val ForkloreShapes =
  Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(3.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
  )

/**
 * Direction A, "Handwritten ledger" (issue #15). Implemented as an object with an `invoke` operator
 * — the same trick Material3's own `MaterialTheme` uses — so `ForkloreTheme { ... }` keeps working
 * at every existing call site while `ForkloreTheme.colors` reaches the palette roles Material has
 * no slot for (`stamp`, `rust`, `rule`, `cardShadow`, `person1`/`person2`).
 */
object ForkloreTheme {
  val colors: ForkloreColors
    @Composable get() = LocalForkloreColors.current

  /**
   * Dynamic color is deliberately absent: this paper-and-ink palette *is* the app's identity, and
   * Material You's wallpaper-derived scheme on Android 12+ would silently replace it with
   * whatever's behind the user's home screen wallpaper — the "different color on every phone"
   * problem issue #15's palette exists to fix. This is the explicit decision the issue asked for,
   * not an oversight.
   */
  @Composable
  operator fun invoke(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ledgerColors = if (darkTheme) DarkForkloreColors else LightForkloreColors
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    val colorScheme =
      base.copy(
        background = ledgerColors.paper,
        surface = ledgerColors.card,
        onBackground = ledgerColors.ink,
        onSurface = ledgerColors.ink,
        primary = ledgerColors.ink,
        onPrimary = ledgerColors.card,
        onSurfaceVariant = ledgerColors.ink2,
        outline = ledgerColors.cardBorder,
        outlineVariant = ledgerColors.rule,
        error = ledgerColors.stamp,
        onError = ledgerColors.card,
        tertiary = ledgerColors.rust,
        onTertiary = ledgerColors.card,
      )

    CompositionLocalProvider(LocalForkloreColors provides ledgerColors) {
      MaterialTheme(
        colorScheme = colorScheme,
        typography = ForkloreTypography,
        shapes = ForkloreShapes,
        content = content,
      )
    }
  }
}
