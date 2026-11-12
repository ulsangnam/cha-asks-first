package ai.hackerton.chaasks.ui.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class EffectMathTest {
    @Test fun envelopeIsZeroAtEdgesAndOneInMiddle() {
        assertEquals(0f, EffectMath.waveEnvelope(0f), 1e-4f)
        assertEquals(0f, EffectMath.waveEnvelope(1f), 1e-4f)
        assertEquals(1f, EffectMath.waveEnvelope(0.5f), 1e-4f)
        assertTrue(EffectMath.waveEnvelope(0.25f) < 1f)
    }

    @Test fun waveSampleStaysInRangeAndVanishesAtEdges() {
        for (layer in 0 until EffectMath.WAVE_LAYERS) {
            assertEquals(0f, EffectMath.waveSample(0f, 1.1f, layer), 1e-4f)
            assertEquals(0f, EffectMath.waveSample(1f, 1.1f, layer), 1e-4f)
            for (i in 0..100) {
                assertTrue(abs(EffectMath.waveSample(i / 100f, 2.3f, layer)) <= 1f)
            }
        }
    }

    @Test fun phaseCompletesOneTurnPerPeriod() {
        assertEquals((2 * PI).toFloat(), EffectMath.wavePhase(EffectMath.WAVE_PERIOD_MS), 1e-3f)
    }

    @Test fun rippleGrowsAndFadesOverItsLife() {
        val born = EffectMath.ripple(0L, 0)!!
        assertEquals(0f, born.radius, 1e-4f)
        assertEquals(1f, born.alpha, 1e-4f)
        val half = EffectMath.ripple(EffectMath.RIPPLE_LIFE_MS / 2, 0)!!
        assertEquals(0.5f, half.radius, 1e-3f)
        assertEquals(0.5f, half.alpha, 1e-3f)
    }

    @Test fun laterRingsWaitForTheirTurn() {
        assertNull(EffectMath.ripple(EffectMath.RIPPLE_SPAWN_MS - 1, 1))
        assertNotNull(EffectMath.ripple(EffectMath.RIPPLE_SPAWN_MS, 1))
    }

    @Test fun ringRestartsAfterItsLife() {
        val reborn = EffectMath.ripple(EffectMath.RIPPLE_LIFE_MS, 0)!!
        assertEquals(0f, reborn.radius, 1e-4f)
    }

    @Test fun revealCountIsClampedToText() {
        assertEquals(0, EffectMath.revealCount(-5L, 10, 45L))
        assertEquals(0, EffectMath.revealCount(0L, 10, 45L))
        assertEquals(3, EffectMath.revealCount(135L, 10, 45L))
        assertEquals(10, EffectMath.revealCount(10_000L, 10, 45L))
    }
}
