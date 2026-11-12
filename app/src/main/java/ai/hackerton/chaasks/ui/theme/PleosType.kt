package ai.hackerton.chaasks.ui.theme

import ai.hackerton.chaasks.R
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Pleos Connect 시스템 UI 글꼴(Asta Sans, 42dot 제작 가변 폰트). */
val AstaSans = FontFamily(
    weighted(FontWeight.Normal),
    weighted(FontWeight.SemiBold),
    weighted(FontWeight.Bold),
    weighted(FontWeight.ExtraBold),
)

@OptIn(ExperimentalTextApi::class)
private fun weighted(weight: FontWeight) =
    Font(R.font.asta_sans, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

private fun style(size: Int, lineHeight: Int, weight: FontWeight) =
    TextStyle(fontFamily = AstaSans, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp)

/**
 * Foundations > Typography의 위계와 크기(size / line height).
 * Headline은 Extra Bold·Bold, Title은 Bold, Body는 Strong(SemiBold)·Normal을 쓴다.
 */
object PleosType {
    val headlineSmall = style(48, 64, FontWeight.ExtraBold)
    val titleLarge = style(40, 52, FontWeight.Bold)
    val titleMedium = style(36, 44, FontWeight.Bold)
    val titleSmall = style(32, 40, FontWeight.Bold)
    val bodyLarge = style(30, 38, FontWeight.Normal)
    val bodyLargeStrong = style(30, 38, FontWeight.SemiBold)
    val bodyMedium = style(28, 36, FontWeight.Normal)
    val bodySmall = style(26, 34, FontWeight.Normal)
    val bodySmallStrong = style(26, 34, FontWeight.SemiBold)
    val labelMedium = style(24, 28, FontWeight.Normal)
    val labelSmall = style(20, 24, FontWeight.Normal)
}
