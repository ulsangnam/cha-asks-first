package ai.hackerton.chaasks.event

import ai.hackerton.chaasks.core.DrivingEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 시연용 주입 경로. 에뮬레이터에 내비 제어가 없어 디버그 빌드의 ScenarioReceiver가 여기로 이벤트와 답변을 넣는다.
 * 릴리스 빌드에는 수신자가 없어 아무것도 흐르지 않는다.
 */
object ScenarioBus {
    val events = MutableSharedFlow<DrivingEvent>(extraBufferCapacity = 16)

    /** 진행자가 말하는 도중에 들어온 답도 잃지 않도록 버퍼에 두고, 다음 듣기에서 하나씩 꺼낸다. */
    val answers = Channel<String>(capacity = 8)
}
