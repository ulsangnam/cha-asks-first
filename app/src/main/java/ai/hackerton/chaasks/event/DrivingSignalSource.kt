package ai.hackerton.chaasks.event

import ai.hackerton.chaasks.core.DrivingEvent
import kotlinx.coroutines.flow.Flow

/** 차량·내비 신호를 [DrivingEvent]로 바꿔 내보낸다. 지원되지 않는 신호는 조용히 빠진다. */
interface DrivingSignalSource {
    val events: Flow<DrivingEvent>

    fun start()

    fun stop()
}
