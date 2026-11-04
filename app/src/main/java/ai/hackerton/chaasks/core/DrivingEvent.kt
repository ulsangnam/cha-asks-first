package ai.hackerton.chaasks.core

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** 주행 부하. HIGH면 말을 멈추고, STOPPED(기어 P)면 깊은 질문과 화면 조작을 연다. */
enum class Load { HIGH, NORMAL, STOPPED }

sealed interface DrivingEvent {
    data class TurnSignal(val on: Boolean) : DrivingEvent
    data class SteeringAngle(val degrees: Float) : DrivingEvent
    data class BrakePedal(val pressed: Boolean) : DrivingEvent
    data class GearChanged(val gear: Gear) : DrivingEvent
    data class RouteStarted(val destinationName: String, val remainMinutes: Int) : DrivingEvent
    data class RemainTime(val minutes: Int) : DrivingEvent
    data object Arrived : DrivingEvent
    data object RouteCancelled : DrivingEvent
}
