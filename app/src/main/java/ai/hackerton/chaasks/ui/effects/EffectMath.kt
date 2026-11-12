package ai.hackerton.chaasks.ui.effects

import kotlin.math.PI
import kotlin.math.sin

/** 음성 효과의 모양을 정하는 순수 계산. 그리기와 분리해 단위 테스트한다. */
object EffectMath {
    const val WAVE_LAYERS = 3
    /** 파형이 한 번 흘러가는 시간. 느리게 움직여 시선을 끌지 않게 한다. */
    const val WAVE_PERIOD_MS = 3_000L
    /** 동심원이 하나씩 생기는 간격과 수명. 수명/간격 = 동시에 보이는 최대 개수(3). */
    const val RIPPLE_SPAWN_MS = 1_200L
    const val RIPPLE_LIFE_MS = 3_600L
    const val RIPPLE_RINGS = (RIPPLE_LIFE_MS / RIPPLE_SPAWN_MS).toInt()

    private val WAVE_FREQUENCIES = floatArrayOf(1.5f, 2.3f, 3.1f)

    data class Ripple(val radius: Float, val alpha: Float)

    /** 가로 위치 x(0..1)의 진폭 배율. 양 끝 0, 가운데 1. */
    fun waveEnvelope(x: Float): Float {
        val s = sin(PI * x.coerceIn(0f, 1f)).toFloat()
        return s * s
    }

    /** layer번째 파형의 x(0..1) 위치 값(-1..1). phase는 라디안. */
    fun waveSample(x: Float, phase: Float, layer: Int): Float {
        val wave = sin(2 * PI * WAVE_FREQUENCIES[layer] * x + phase + layer * 1.3f).toFloat()
        return waveEnvelope(x) * wave
    }

    /** 경과 시간을 파형 위상(라디안)으로 바꾼다. */
    fun wavePhase(elapsedMs: Long): Float = (2 * PI * elapsedMs / WAVE_PERIOD_MS).toFloat()

    /** ring번째 동심원의 상태. 처음 생기기 전이면 null. radius는 0..1 비율. */
    fun ripple(elapsedMs: Long, ring: Int): Ripple? {
        val sinceBirth = elapsedMs - ring * RIPPLE_SPAWN_MS
        if (sinceBirth < 0) return null
        val progress = (sinceBirth % RIPPLE_LIFE_MS).toFloat() / RIPPLE_LIFE_MS
        return Ripple(radius = progress, alpha = 1f - progress)
    }

    /** 타자 효과로 지금까지 보여 줄 글자 수. */
    fun revealCount(elapsedMs: Long, length: Int, perCharMs: Long): Int =
        (elapsedMs / perCharMs).toInt().coerceIn(0, length)
}
