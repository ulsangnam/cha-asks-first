package ai.hackerton.chaasks.pacing

import ai.hackerton.chaasks.core.DrivingEvent.BrakePedal
import ai.hackerton.chaasks.core.DrivingEvent.GearChanged
import ai.hackerton.chaasks.core.DrivingEvent.SteeringAngle
import ai.hackerton.chaasks.core.DrivingEvent.TurnSignal
import ai.hackerton.chaasks.core.Gear
import ai.hackerton.chaasks.core.Load
import org.junit.Assert.assertEquals
import org.junit.Test

class PacingStateMachineTest {
    private val pacing = PacingStateMachine()

    @Test fun startsNormal() = assertEquals(Load.NORMAL, pacing.load)

    @Test fun turnSignalMakesHigh() =
        assertEquals(Load.HIGH, pacing.onEvent(TurnSignal(true), 0))

    @Test fun staysHighUntilReleaseDelayPasses() {
        pacing.onEvent(TurnSignal(true), 0)
        assertEquals(Load.HIGH, pacing.onEvent(TurnSignal(false), 1_000))
        assertEquals(Load.HIGH, pacing.tick(2_499))
        assertEquals(Load.NORMAL, pacing.tick(2_500))
    }

    @Test fun largeSteeringEitherDirectionIsHigh() {
        assertEquals(Load.HIGH, pacing.onEvent(SteeringAngle(-95f), 0))
        pacing.onEvent(SteeringAngle(0f), 10)
        assertEquals(Load.NORMAL, pacing.tick(1_510))
        assertEquals(Load.NORMAL, pacing.onEvent(SteeringAngle(89f), 1_600))
    }

    @Test fun parkOverridesBrake() {
        pacing.onEvent(BrakePedal(true), 0)
        assertEquals(Load.STOPPED, pacing.onEvent(GearChanged(Gear.PARK), 10))
    }

    @Test fun remainingCauseKeepsHigh() {
        pacing.onEvent(TurnSignal(true), 0)
        pacing.onEvent(BrakePedal(true), 10)
        pacing.onEvent(TurnSignal(false), 20)
        assertEquals(Load.HIGH, pacing.tick(5_000))
        pacing.onEvent(BrakePedal(false), 5_000)
        assertEquals(Load.NORMAL, pacing.tick(6_500))
    }

    @Test fun newCauseDuringReleaseResetsDelay() {
        pacing.onEvent(TurnSignal(true), 0)
        pacing.onEvent(TurnSignal(false), 100)
        pacing.onEvent(BrakePedal(true), 1_000)
        pacing.onEvent(BrakePedal(false), 1_200)
        assertEquals(Load.HIGH, pacing.tick(2_600))
        assertEquals(Load.NORMAL, pacing.tick(2_700))
    }

    @Test fun leavingParkReturnsNormal() {
        pacing.onEvent(GearChanged(Gear.PARK), 0)
        assertEquals(Load.NORMAL, pacing.onEvent(GearChanged(Gear.DRIVE), 10))
    }
}
