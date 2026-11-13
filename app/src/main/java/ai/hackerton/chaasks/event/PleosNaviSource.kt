package ai.hackerton.chaasks.event

import ai.hackerton.chaasks.core.DrivingEvent
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** NaviHelper의 경로 시작·남은 시간·도착·취소를 이벤트로 바꾼다. */
class PleosNaviSource(context: Context) : DrivingSignalSource {
    private val navi = NaviHelper(context.applicationContext)
    private val _events = MutableSharedFlow<DrivingEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<DrivingEvent> = _events.asSharedFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) {
            Log.i(TAG, "route started name=${info.name} duration(raw)=${info.duration}")
            emit(DrivingEvent.RouteStarted(info.name, secondsToMinutes(info.duration)))
        }

        override fun onDestinationInfo(info: DestinationInfo) {
            Log.i(TAG, "destination remainTime(raw)=${info.remainTime}")
            if (info.remainTime > 0) emit(DrivingEvent.RemainTime(secondsToMinutes(info.remainTime)))
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            Log.i(TAG, "destination arrived")
            emit(DrivingEvent.Arrived)
        }

        override fun onRouteCancelled() {
            Log.i(TAG, "route cancelled")
            emit(DrivingEvent.RouteCancelled)
        }

        override fun onError(code: NaviErrorCode) {
            Log.w(TAG, "navi error=$code")
        }
    }

    override fun start() {
        try {
            navi.initialize(
                {
                    navi.addListener(listener)
                    navi.getDestinationInfo()
                },
                { Log.w(TAG, "navi initialize failed", it) },
            )
        } catch (e: Exception) {
            Log.w(TAG, "navi initialize threw", e)
        }
    }

    override fun stop() {
        runCatching {
            navi.removeListener(listener)
            navi.release()
        }.onFailure { Log.w(TAG, "navi release failed", it) }
    }

    private fun emit(event: DrivingEvent) {
        _events.tryEmit(event)
    }

    // 시간 단위는 문서에 없다. 에뮬레이터 실제 경로에서 원값 1920이 내비 표시 32분과 맞아 초로 확인했다.
    private fun secondsToMinutes(seconds: Int): Int = (seconds + 59) / 60

    private companion object {
        const val TAG = "ChaAsks.Navi"
    }
}
