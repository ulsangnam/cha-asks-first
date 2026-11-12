package ai.hackerton.chaasks.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

private val LocalPleosColors = staticCompositionLocalOf { PleosLightColors }

object PleosTheme {
    val colors: PleosColors
        @Composable get() = LocalPleosColors.current
}

/** Pleos 색상 토큰과 Asta Sans 글자 위계를 적용한다. 기본은 시스템 라이트·다크 설정을 따른다. */
@Composable
fun PleosTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) PleosDarkColors else PleosLightColors
    val materialColors = if (colors.isDark) {
        darkColorScheme(
            background = colors.backgroundPrimary,
            surface = colors.surface,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            primary = colors.buttonFilled,
            onPrimary = colors.onButtonFilled,
            error = colors.informativeNegative,
        )
    } else {
        lightColorScheme(
            background = colors.backgroundPrimary,
            surface = colors.surface,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            primary = colors.buttonFilled,
            onPrimary = colors.onButtonFilled,
            error = colors.informativeNegative,
        )
    }
    val typography = Typography(
        headlineSmall = PleosType.titleMedium,
        titleLarge = PleosType.titleSmall,
        bodyLarge = PleosType.bodyMedium,
        bodyMedium = PleosType.bodySmall,
        labelLarge = PleosType.bodySmallStrong,
    )
    CompositionLocalProvider(LocalPleosColors provides colors) {
        MaterialTheme(colorScheme = materialColors, typography = typography, content = content)
    }
}

/** Components > Basic button. 둥근 사각형 채움 버튼, 누르면 pressed 토큰으로 바뀐다. */
@Composable
fun PleosButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false) {
    val colors = PleosTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container = when {
        filled && pressed -> colors.buttonFilledPressed
        filled -> colors.buttonFilled
        pressed -> colors.buttonBasicPressed
        else -> colors.buttonBasic
    }
    Box(
        modifier = modifier
            .heightIn(min = BUTTON_HEIGHT)
            .clip(RoundedCornerShape(BUTTON_RADIUS))
            .background(container)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = PleosType.bodyLargeStrong, color = if (filled) colors.onButtonFilled else colors.textPrimary)
    }
}

// 가이드 Basic button Medium 크기(약 80 높이)를 따른다. 주행 환경이라 큰 터치 영역을 쓴다.
private val BUTTON_HEIGHT = 80.dp
private val BUTTON_RADIUS = 24.dp
