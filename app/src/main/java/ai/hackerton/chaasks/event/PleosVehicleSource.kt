package ai.hackerton.chaasks.event

import ai.hackerton.chaasks.core.DrivingEvent
import ai.hackerton.chaasks.core.Gear
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Pleos Vehicle SDK의 방향지시등·조향각·브레이크·기어를 이벤트로 바꾼다. capability 체크에 실패한 신호는 등록하지 않는다. */
class PleosVehicleSource(context: Context) : DrivingSignalSource {
    private val vehicle = Vehicle(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private val _events = MutableSharedFlow<DrivingEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<DrivingEvent> = _events.asSharedFlow()

    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
            value?.let { emit(DrivingEvent.TurnSignal(on = it.toString() != "OFF")) }
        }

        override fun onFailed(e: Exception) = logFailure("TurnSignal", e)
    }

    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(value: Float?) {
            value?.let { emit(DrivingEvent.SteeringAngle(it)) }
        }

        override fun onFailed(e: Exception) = logFailure("SteeringWheel", e)
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(value: Boolean?) {
            value?.let { emit(DrivingEvent.BrakePedal(it)) }
        }

        override fun onFailed(e: Exception) = logFailure("Brake", e)
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(value: VehicleGear?) {
            value?.let { emit(DrivingEvent.GearChanged(it.toGear())) }
        }

        override fun onFailed(e: Exception) = logFailure("Gear", e)
    }

    override fun start() {
        try {
            vehicle.initialize()
        } catch (e: Exception) {
            Log.w(TAG, "vehicle initialize failed", e)
            return
        }
        // initialize에 완료 콜백이 없어 서비스 연결 시간을 두고 등록한다.
        handler.postDelayed(::registerListeners, SERVICE_CONNECT_DELAY_MS)
    }

    override fun stop() {
        handler.removeCallbacksAndMessages(null)
        runCatching {
            vehicle.getTurnSignal().unregisterTurnSignalState(turnSignalListener)
            vehicle.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener)
            vehicle.getBrake().unregisterBrakePedalStatus(brakeListener)
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.release()
        }.onFailure { Log.w(TAG, "vehicle release failed", it) }
    }

    private fun registerListeners() {
        try {
            vehicle.getGear().registerGearSelection(gearListener)
            vehicle.getTurnSignal().checkTurnSignalStateCapabilities(
                { vehicle.getTurnSignal().registerTurnSignalState(turnSignalListener) },
                unsupported("TurnSignal"),
            )
            vehicle.getSteeringWheel().checkSteeringWheelAngleCapabilities(
                { _, _ -> vehicle.getSteeringWheel().registerSteeringWheelAngle(steeringListener) },
                unsupported("SteeringWheel"),
            )
            vehicle.getBrake().checkBrakePedalStatusCapabilities(
                { vehicle.getBrake().registerBrakePedalStatus(brakeListener) },
                unsupported("Brake"),
            )
        } catch (e: Exception) {
            Log.w(TAG, "vehicle listener registration failed", e)
        }
    }

    private fun emit(event: DrivingEvent) {
        _events.tryEmit(event)
    }

    private fun unsupported(signal: String): (Exception) -> Unit = { Log.i(TAG, "$signal not supported: ${it.message}") }

    private fun logFailure(signal: String, e: Exception) {
        Log.w(TAG, "$signal listener failed", e)
    }

    private fun VehicleGear.toGear(): Gear {
        val name = toString()
        return when {
            "PARK" in name -> Gear.PARK
            "REVERSE" in name -> Gear.REVERSE
            "NEUTRAL" in name -> Gear.NEUTRAL
            "DRIVE" in name -> Gear.DRIVE
            else -> Gear.UNKNOWN
        }
    }

    private companion object {
        const val TAG = "ChaAsks.Vehicle"
        const val SERVICE_CONNECT_DELAY_MS = 2_000L
    }
}
