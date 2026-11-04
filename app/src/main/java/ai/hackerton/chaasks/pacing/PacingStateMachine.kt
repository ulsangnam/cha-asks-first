package ai.hackerton.chaasks.pacing

import ai.hackerton.chaasks.core.DrivingEvent
import ai.hackerton.chaasks.core.Gear
import ai.hackerton.chaasks.core.Load
import kotlin.math.abs

/**
 * 차량 신호로 주행 부하를 판단한다.
 * 기어 P면 STOPPED, 방향지시등·큰 조향·브레이크 중 하나라도 있으면 HIGH,
 * HIGH 원인이 모두 사라진 뒤 [RELEASE_DELAY_MS]가 지나면 NORMAL로 돌아간다.
 */
class PacingStateMachine {
    var load: Load = Load.NORMAL
        private set

    private var turnSignalOn = false
    private var steeringLarge = false
    private var brakePressed = false
    private var gear = Gear.UNKNOWN
    private var releasedAt: Long? = null

    fun onEvent(event: DrivingEvent, nowMillis: Long): Load {
        when (event) {
            is DrivingEvent.TurnSignal -> turnSignalOn = event.on
            is DrivingEvent.SteeringAngle -> steeringLarge = abs(event.degrees) >= STEERING_HIGH_DEGREES
            is DrivingEvent.BrakePedal -> brakePressed = event.pressed
            is DrivingEvent.GearChanged -> gear = event.gear
            else -> return load
        }
        return evaluate(nowMillis)
    }

    fun tick(nowMillis: Long): Load = evaluate(nowMillis)

    private fun evaluate(nowMillis: Long): Load {
        load = when {
            gear == Gear.PARK -> {
                releasedAt = null
                Load.STOPPED
            }
            turnSignalOn || steeringLarge || brakePressed -> {
                releasedAt = null
                Load.HIGH
            }
            load == Load.HIGH -> {
                val since = releasedAt ?: nowMillis.also { releasedAt = it }
                if (nowMillis - since >= RELEASE_DELAY_MS) {
                    releasedAt = null
                    Load.NORMAL
                } else {
                    Load.HIGH
                }
            }
            else -> Load.NORMAL
        }
        return load
    }

    companion object {
        const val STEERING_HIGH_DEGREES = 90f
        const val RELEASE_DELAY_MS = 1_500L
    }
}
