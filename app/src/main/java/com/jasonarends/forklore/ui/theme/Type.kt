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
 * Body copy and field input — always weight 400, never italic (grepped across `ui/` to confirm
 * before trimming this down from the five-weight/italic set the mockup's CSS requested; nothing
 * here pairs Source Serif with [FontStyle.Italic] or a non-Normal weight). google/fonts ships
 * Source Serif 4 only as a variable font (opsz, wght axes); since only one instance (wght 400,
 * opsz 16) is ever used, `fontTools.varLib.instancer` pinned it down to that single static instance
 * at build time rather than bundling the full variable font just to read one point on it — same
 * visual result, a third of the file size, and no [FontVariation.Settings] needed at the call site.
 */
val SourceSerif = FontFamily(Font(R.font.source_serif, FontWeight.Normal))

/**
 * Handwritten voice: notes, subtitles, branch/address. A variable font (wght axis); instanced down
 * to the 500-700 sub-range actually referenced below (the family's own default sits at 400, unused
 * here), rather than bundling the full 400-700 range.
 */
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
 * Named text roles straight from issue #15's typography table, for the spots [ForkloreTypography]'s
 * Material roles don't cover (subtitle, place/dish names, opinion cards, inline rating). Where a
 * role here would be byte-for-byte identical to a Material role (the old `topBarTitle`,
 * `sectionLabel`, `stamp`, `chip`), use
 * `MaterialTheme.typography.titleLarge`/`titleSmall`/`labelSmall`/`labelMedium` directly instead of
 * duplicating it here. Section labels, field labels, chips and the stamp render uppercase via
 * `ui.components.UppercaseLabel`; see that composable for why the semantics tree still carries
 * natural case.
 */
object ForkloreType {
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
  val button =
    TextStyle(fontFamily = ZillaSlab, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
  val inlineRating =
    TextStyle(fontFamily = ZillaSlab, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
}
