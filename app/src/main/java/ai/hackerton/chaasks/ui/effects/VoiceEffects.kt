package ai.hackerton.chaasks.ui.effects

import ai.hackerton.chaasks.app.VoiceActivity
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** 무대 배경과 효과에 쓰는 앱 전용 색. Pleos 가이드 토큰에는 없는 값이다. */
object StageColors {
    val Top = Color(0xFF141B33)
    val Bottom = Color(0xFF0B1020)
    val Waves = listOf(Color(0xFF8896D3), Color(0xFF9BA8E2), Color(0xFF6FA8FF))
    val Ripple = Color(0xFF4FC3D9)
    val Frozen = Color(0x52FFFFFF)
}

/** 진행자 상태에 맞는 배경 효과. frozen이면 지금 모양 그대로 멈추고 회색이 된다. */
@Composable
fun VoiceBackdrop(voice: VoiceActivity, frozen: Boolean, modifier: Modifier = Modifier) {
    when (voice) {
        VoiceActivity.SPEAKING -> SpeakingWave(frozen, modifier)
        VoiceActivity.LISTENING -> ListeningRipples(frozen, modifier)
        VoiceActivity.IDLE -> IdleLine(modifier)
    }
}

/** 프레임마다 흐르는 경과 시간. 멈췄다 다시 움직이면 멈춘 지점부터 이어 간다. */
@Composable
private fun rememberEffectClock(frozen: Boolean): Long {
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(frozen) {
        if (frozen) return@LaunchedEffect
        val start = withFrameMillis { it } - elapsed
        while (true) {
            elapsed = withFrameMillis { it } - start
        }
    }
    return elapsed
}

@Composable
fun SpeakingWave(frozen: Boolean, modifier: Modifier = Modifier) {
    val elapsed = rememberEffectClock(frozen)
    Canvas(modifier) {
        val phase = EffectMath.wavePhase(elapsed)
        val mid = size.height / 2
        for (layer in 0 until EffectMath.WAVE_LAYERS) {
            val amplitude = mid * (0.9f - layer * 0.22f)
            val path = Path()
            var x = 0f
            while (x <= size.width) {
                val y = mid + EffectMath.waveSample(x / size.width, phase, layer) * amplitude
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += WAVE_STEP_PX
            }
            val color = if (frozen) StageColors.Frozen else StageColors.Waves[layer]
            // 굵고 옅은 선 위에 가늘고 진한 선을 겹쳐 빛이 번지는 느낌을 낸다.
            drawPath(path, color.copy(alpha = 0.12f), style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, color.copy(alpha = 0.40f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
fun ListeningRipples(frozen: Boolean, modifier: Modifier = Modifier) {
    val elapsed = rememberEffectClock(frozen)
    Canvas(modifier) {
        val maxRadius = size.minDimension / 2
        for (ring in 0 until EffectMath.RIPPLE_RINGS) {
            val ripple = EffectMath.ripple(elapsed, ring) ?: continue
            val color = if (frozen) StageColors.Frozen else StageColors.Ripple
            drawCircle(
                color = color.copy(alpha = 0.45f * ripple.alpha),
                radius = maxRadius * (0.15f + 0.85f * ripple.radius),
                style = Stroke(width = 4.dp.toPx()),
            )
        }
    }
}

@Composable
fun IdleLine(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawLine(
            color = StageColors.Waves[1].copy(alpha = 0.25f),
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

private const val WAVE_STEP_PX = 6f
