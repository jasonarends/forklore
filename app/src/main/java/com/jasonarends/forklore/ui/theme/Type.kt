package com.jasonarends.forklore.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jasonarends.forklore.R

/**
 * Slab-serif "ledger" voice: names, labels, buttons, chips, the rating scale. Zilla Slab ships real
 * static TTF instances on google/fonts, so no variable-font axes are needed here.
 */
val ZillaSlab =
  FontFamily(
    Font(R.font.zilla_slab_medium, FontWeight.Medium),
    Font(R.font.zilla_slab_semibold, FontWeight.SemiBold),
    Font(R.font.zilla_slab_bold, FontWeight.Bold),
    Font(R.font.zilla_slab_bold_italic, FontWeight.Bold, FontStyle.Italic),
  )

/**
 * Body copy and field input. google/fonts ships Source Serif 4 only as a variable font (opsz, wght
 * axes) — bundled as-is and instanced per weight via [FontVariation.Settings] rather than
 * generating static files, since this Compose BOM supports variable-font axis settings directly
 * (see issue #15's "Deviations" note on checking this before relying on it).
 */
val SourceSerif =
  FontFamily(
    Font(
      R.font.source_serif_variable,
      FontWeight.Normal,
      variationSettings =
        FontVariation.Settings(FontVariation.weight(400), FontVariation.opticalSizing(16.sp)),
    ),
    Font(
      R.font.source_serif_variable,
      FontWeight.Medium,
      variationSettings =
        FontVariation.Settings(FontVariation.weight(500), FontVariation.opticalSizing(16.sp)),
    ),
    Font(
      R.font.source_serif_variable,
      FontWeight.SemiBold,
      variationSettings =
        FontVariation.Settings(FontVariation.weight(600), FontVariation.opticalSizing(16.sp)),
    ),
    Font(
      R.font.source_serif_italic_variable,
      FontWeight.Normal,
      FontStyle.Italic,
      variationSettings =
        FontVariation.Settings(FontVariation.weight(400), FontVariation.opticalSizing(16.sp)),
    ),
    Font(
      R.font.source_serif_italic_variable,
      FontWeight.Medium,
      FontStyle.Italic,
      variationSettings =
        FontVariation.Settings(FontVariation.weight(500), FontVariation.opticalSizing(16.sp)),
    ),
  )

/** Handwritten voice: notes, subtitles, branch/address. Also a variable font (wght axis). */
val Caveat =
  FontFamily(
    Font(
      R.font.caveat_variable,
      FontWeight.Medium,
      variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
      R.font.caveat_variable,
      FontWeight.SemiBold,
      variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
      R.font.caveat_variable,
      FontWeight.Bold,
      variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
  )

// Material3 role mapping so default Text/OutlinedTextField/etc. aren't stuck on the system font.
val ForkloreTypography =
  Typography(
    headlineSmall =
      TextStyle(
        fontFamily = ZillaSlab,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = ZillaSlab,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        letterSpacing = 0.3.sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = ZillaSlab,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 23.sp,
      ),
    titleSmall =
      TextStyle(
        fontFamily = ZillaSlab,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.5.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = SourceSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = Caveat,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 22.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = ZillaSlab,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = ZillaSlab,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = ZillaSlab,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.5.sp,
      ),
  )

/**
 * Named text roles straight from issue #15's typography table, for the spots Material's generic
 * type scale doesn't cover (top bar, chips, the stamp, opinion cards). Display case is left as
 * written rather than transformed to visual-only uppercase: several existing tests match exact text
 * via Compose's semantics tree (e.g. `onNodeWithText("Food")`), and `.uppercase()`-ing a label
 * would break them without weakening the underlying assertion being an option worth taking.
 */
object ForkloreType {
  val topBarTitle =
    TextStyle(
      fontFamily = ZillaSlab,
      fontWeight = FontWeight.Bold,
      fontSize = 26.sp,
      letterSpacing = 0.3.sp,
    )
  val topBarSubtitle =
    TextStyle(fontFamily = Caveat, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
  val placeNameList =
    TextStyle(
      fontFamily = ZillaSlab,
      fontWeight = FontWeight.SemiBold,
      fontSize = 16.5.sp,
      lineHeight = 21.sp,
    )
  val placeNameDetail =
    TextStyle(fontFamily = ZillaSlab, fontWeight = FontWeight.Bold, fontSize = 24.sp)
  val dishName = TextStyle(fontFamily = ZillaSlab, fontWeight = FontWeight.Bold, fontSize = 19.sp)
  val branchLabel =
    TextStyle(
      fontFamily = Caveat,
      fontWeight = FontWeight.Medium,
      fontSize = 15.sp,
      lineHeight = 17.sp,
    )
  val sectionLabel =
    TextStyle(
      fontFamily = ZillaSlab,
      fontWeight = FontWeight.Bold,
      fontSize = 13.sp,
      letterSpacing = 1.5.sp,
    )
  val fieldLabel =
    TextStyle(
      fontFamily = ZillaSlab,
      fontWeight = FontWeight.SemiBold,
      fontSize = 12.sp,
      letterSpacing = 1.sp,
    )
  val fieldInput =
    TextStyle(fontFamily = SourceSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp)
  val noteText =
    TextStyle(
      fontFamily = Caveat,
      fontWeight = FontWeight.Medium,
      fontSize = 19.sp,
      lineHeight = 23.sp,
    )
  val opinionNote =
    TextStyle(
      fontFamily = Caveat,
      fontWeight = FontWeight.Medium,
      fontSize = 18.sp,
      lineHeight = 22.sp,
    )
  val opinionAuthor =
    TextStyle(fontFamily = ZillaSlab, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
  val opinionRating =
    TextStyle(
      fontFamily = ZillaSlab,
      fontWeight = FontWeight.Bold,
      fontStyle = FontStyle.Italic,
      fontSize = 13.5.sp,
    )
  val chip =
    TextStyle(
      fontFamily = ZillaSlab,
      fontWeight = FontWeight.SemiBold,
      fontSize = 11.5.sp,
      letterSpacing = 0.4.sp,
    )
  val stamp =
    TextStyle(
      fontFamily = ZillaSlab,
      fontWeight = FontWeight.Bold,
      fontSize = 11.sp,
      letterSpacing = 1.5.sp,
    )
  val button =
    TextStyle(fontFamily = ZillaSlab, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
  val inlineRating =
    TextStyle(fontFamily = ZillaSlab, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
}
