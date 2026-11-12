package ai.hackerton.chaasks.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Pleos Connect UI 디자인 가이드의 색상 토큰 중 이 앱이 쓰는 것.
 * 값은 Foundations > Colors 표(Grayscale, Static, Informative, Gleo AI)에서 옮겼다.
 */
@Immutable
data class PleosColors(
    val backgroundPrimary: Color,
    val backgroundSecondary: Color,
    val backgroundPopup: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textQuaternary: Color,
    val buttonBasic: Color,
    val buttonBasicPressed: Color,
    val buttonFilled: Color,
    val buttonFilledPressed: Color,
    val onButtonFilled: Color,
    val informativeActive: Color,
    val informativePositive: Color,
    val informativeNegative: Color,
    val gleoBubble: Color,
    val gleoSpeaker: Color,
    val isDark: Boolean,
)

private val StaticDark = Color(0xFF131417)
private val StaticLight = Color(0xFFFFFFFF)

// basic_* 팔레트는 테마에 따라 값이 뒤집힌다(라이트 basic_00 = #FFFFFF, 다크 basic_00 = #131417).
val PleosLightColors = PleosColors(
    backgroundPrimary = Color(0xFFF7F8FA), // basic_50
    backgroundSecondary = Color(0xFFFFFFFF), // basic_00
    backgroundPopup = Color(0xFFFFFFFF), // basic_00
    surface = Color(0xFFFFFFFF), // basic_00
    textPrimary = StaticDark, // static_dark_100
    textSecondary = StaticDark.copy(alpha = 0.84f), // static_dark_200
    textTertiary = StaticDark.copy(alpha = 0.64f), // static_dark_300
    textQuaternary = StaticDark.copy(alpha = 0.32f), // static_dark_400
    // alpha_dark_50/100은 가이드에 투명도 값이 없어 견본 색에 맞춘 추정값이다.
    buttonBasic = StaticDark.copy(alpha = 0.05f),
    buttonBasicPressed = StaticDark.copy(alpha = 0.10f),
    buttonFilled = Color(0xFF44464E), // basic_600
    buttonFilledPressed = Color(0xFF313236), // basic_700
    onButtonFilled = StaticLight,
    informativeActive = Color(0xFF02C265),
    informativePositive = Color(0xFF0064FF),
    informativeNegative = Color(0xFFFE3D16),
    gleoBubble = Color(0xFF9BA8E2), // gleo_bubble_primary
    gleoSpeaker = Color(0xFF3F4B81), // gleo_bubble_speaker
    isDark = false,
)

val PleosDarkColors = PleosColors(
    backgroundPrimary = Color(0xFF1D1E21), // basic_50
    backgroundSecondary = Color(0xFF131417), // basic_00
    backgroundPopup = Color(0xFF313236), // basic_100
    surface = Color(0xFF313236), // basic_100
    textPrimary = StaticLight, // static_light_100
    textSecondary = StaticLight.copy(alpha = 0.84f),
    textTertiary = StaticLight.copy(alpha = 0.64f),
    textQuaternary = StaticLight.copy(alpha = 0.32f),
    buttonBasic = StaticLight.copy(alpha = 0.08f),
    buttonBasicPressed = StaticLight.copy(alpha = 0.16f),
    buttonFilled = Color(0xFFE0E1E6), // basic_600
    buttonFilledPressed = Color(0xFFC7C9CE), // basic_500
    onButtonFilled = StaticDark,
    informativeActive = Color(0xFF00E074),
    informativePositive = Color(0xFF0082FF),
    informativeNegative = Color(0xFFFF4C28),
    gleoBubble = Color(0xFF545F93),
    gleoSpeaker = Color(0xFFB6C1F3),
    isDark = true,
)
