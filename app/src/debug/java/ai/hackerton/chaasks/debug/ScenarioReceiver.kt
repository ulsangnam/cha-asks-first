package ai.hackerton.chaasks.debug

import ai.hackerton.chaasks.core.DrivingEvent
import ai.hackerton.chaasks.core.Gear
import ai.hackerton.chaasks.event.ScenarioBus
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 시연용 신호·답변 주입. 예:
 * adb shell am broadcast -n ai.hackerton.chaasks/.debug.ScenarioReceiver --es cmd route --es dest 부산 --ei min 90
 * cmd: route(dest, min) remain(min) arrive cancel gear(gear) signal(on) brake(on) steer(deg) answer(text)
 */
class ScenarioReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd")
        if (cmd == "answer") {
            val text = intent.getStringExtra("text").orEmpty()
            ScenarioBus.answers.trySend(text)
            Log.i(TAG, "answer injected: $text")
            return
        }
        val event = when (cmd) {
            "route" -> DrivingEvent.RouteStarted(intent.getStringExtra("dest") ?: "목적지", intent.getIntExtra("min", 60))
            "remain" -> DrivingEvent.RemainTime(intent.getIntExtra("min", 0))
            "arrive" -> DrivingEvent.Arrived
            "cancel" -> DrivingEvent.RouteCancelled
            "gear" -> DrivingEvent.GearChanged(parseGear(intent.getStringExtra("gear")))
            "signal" -> DrivingEvent.TurnSignal(intent.getBooleanExtra("on", false))
            "brake" -> DrivingEvent.BrakePedal(intent.getBooleanExtra("on", false))
            "steer" -> DrivingEvent.SteeringAngle(intent.getFloatExtra("deg", 0f))
            else -> null
        }
        if (event == null) {
            Log.w(TAG, "unknown cmd=$cmd")
            return
        }
        ScenarioBus.events.tryEmit(event)
        Log.i(TAG, "event injected: $event")
    }

    private fun parseGear(value: String?): Gear =
        Gear.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Gear.UNKNOWN

    private companion object {
        const val TAG = "ChaAsks.Scenario"
    }
}
