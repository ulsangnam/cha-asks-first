package ai.hackerton.chaasks.app

import ai.hackerton.chaasks.conversation.ConversationEngine
import ai.hackerton.chaasks.conversation.Mode
import ai.hackerton.chaasks.conversation.QuestionBank
import ai.hackerton.chaasks.conversation.ScriptedEngine
import ai.hackerton.chaasks.conversation.SessionPlan
import ai.hackerton.chaasks.conversation.SessionPlanner
import ai.hackerton.chaasks.conversation.Speaker
import ai.hackerton.chaasks.conversation.Turn
import ai.hackerton.chaasks.conversation.Utterance
import ai.hackerton.chaasks.conversation.UtteranceKind
import ai.hackerton.chaasks.core.DrivingEvent
import ai.hackerton.chaasks.core.Gear
import ai.hackerton.chaasks.core.Load
import ai.hackerton.chaasks.pacing.PacingStateMachine
import ai.hackerton.chaasks.storage.ConversationRepository
import ai.hackerton.chaasks.storage.SessionRecord
import ai.hackerton.chaasks.storage.TimedTurn
import ai.hackerton.chaasks.voice.SpeakResult
import ai.hackerton.chaasks.voice.VoiceIO
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

enum class VoiceActivity { IDLE, SPEAKING, LISTENING }

data class UiState(
    val gear: Gear = Gear.UNKNOWN,
    val load: Load = Load.NORMAL,
    val mode: Mode? = null,
    val sessionActive: Boolean = false,
    val currentLine: String = "",
    val destination: String = "",
    val remainMinutes: Int? = null,
    /** null이면 준비 중, true면 Pleos 음성 엔진, false면 화면 표시 모드. */
    val voiceEngineReady: Boolean? = null,
    val voice: VoiceActivity = VoiceActivity.IDLE,
    /** 현재 문장을 말하기 시작한 시각(epoch millis). 화면의 읽기 진행 표시에 쓴다. */
    val speakingStartedAt: Long = 0L,
    /** 운전자의 가장 최근 답. 진행자의 다음 말 위에 남겨 대화 흐름이 보이게 하고, 세션이 끝나면 비운다. */
    val driverLine: String = "",
)

/**
 * 차량 이벤트로 페이싱을 갱신하고, 경로 시작·도착에 맞춰 대화 세션을 연다.
 * 말하기·듣기는 부하가 HIGH가 아닐 때만 하고, HIGH가 되면 즉시 끊은 뒤 해소되면 같은 문장부터 다시 한다.
 */
class Orchestrator(
    private val scope: CoroutineScope,
    private val signals: Flow<DrivingEvent>,
    private val voice: VoiceIO,
    private val bank: QuestionBank,
    private val repository: ConversationRepository,
    private val injectedAnswers: Flow<String>,
    private val canRecord: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val pacing = PacingStateMachine()
    private val load = MutableStateFlow(Load.NORMAL)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var started = false
    private var session: Job? = null
    private var pendingDailyLogDestination: String? = null

    @Volatile
    private var closeRequested = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            val ready = voice.init()
            _state.update { it.copy(voiceEngineReady = ready) }
        }
        scope.launch { signals.collect { handle(it) } }
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                applyLoad(pacing.tick(now()))
            }
        }
    }

    private fun handle(event: DrivingEvent) {
        applyLoad(pacing.onEvent(event, now()))
        when (event) {
            is DrivingEvent.GearChanged -> _state.update { it.copy(gear = event.gear) }
            is DrivingEvent.RouteStarted -> onRouteStarted(event)
            is DrivingEvent.RemainTime -> {
                _state.update { it.copy(remainMinutes = event.minutes) }
                if (SessionPlanner.shouldClose(event.minutes)) closeRequested = true
            }
            DrivingEvent.Arrived -> {
                _state.update { it.copy(remainMinutes = null) }
                onArrived()
            }
            DrivingEvent.RouteCancelled -> {
                _state.update { it.copy(remainMinutes = null) }
                pendingDailyLogDestination = null
            }
            else -> Unit
        }
    }

    private fun onRouteStarted(event: DrivingEvent.RouteStarted) {
        _state.update { it.copy(destination = event.destinationName, remainMinutes = event.remainMinutes) }
        if (isSessionRunning() || !canRecord()) return
        when (SessionPlanner.modeFor(event.remainMinutes)) {
            Mode.TALK_SHOW -> session = scope.launch {
                val plan = SessionPlanner.plan(event.remainMinutes, bank, repository.lastTalkShowTopicId())
                runSession(plan, event.destinationName)
            }
            Mode.DAILY_LOG -> pendingDailyLogDestination = event.destinationName
        }
    }

    private fun onArrived() {
        val destination = pendingDailyLogDestination
        pendingDailyLogDestination = null
        when {
            isSessionRunning() -> closeRequested = true
            destination != null && canRecord() -> session = scope.launch {
                runSession(SessionPlan(Mode.DAILY_LOG, null, 0), destination)
            }
        }
    }

    private fun isSessionRunning(): Boolean = session?.isActive == true

    private fun applyLoad(next: Load) {
        if (next == load.value) return
        load.value = next
        _state.update { it.copy(load = next) }
        Log.i(TAG, "load=$next")
        if (next == Load.HIGH) {
            voice.stopSpeaking()
            voice.stopListening()
        }
    }

    private suspend fun runSession(plan: SessionPlan, destination: String) {
        closeRequested = false
        val engine: ConversationEngine = ScriptedEngine(bank)
        val turns = mutableListOf<TimedTurn>()
        val startedAt = now()
        _state.update { it.copy(mode = plan.mode, sessionActive = true) }
        try {
            var utterance = engine.start(plan)
            while (true) {
                Log.i(TAG, "host ${utterance.kind}: ${utterance.text}")
                if (!speak(utterance.text)) break
                turns += TimedTurn(Turn(Speaker.HOST, utterance.text), now())
                if (!utterance.expectsAnswer) break
                val answer = listen()
                if (answer.isNotBlank()) {
                    turns += TimedTurn(Turn(Speaker.DRIVER, answer), now())
                    _state.update { it.copy(driverLine = answer) }
                }
                utterance = nextUtterance(engine, answer)
            }
            repository.save(
                SessionRecord(plan.mode, plan.topic?.id, startedAt, now(), destination, engine.summarize(), turns),
            )
        } finally {
            _state.update {
                it.copy(mode = null, sessionActive = false, currentLine = "", voice = VoiceActivity.IDLE, driverLine = "")
            }
        }
    }

    private fun nextUtterance(engine: ConversationEngine, answer: String): Utterance {
        val next = engine.onAnswer(answer, load.value)
        if (!closeRequested || next.kind !in CLOSABLE_KINDS) return next
        closeRequested = false
        return engine.close() ?: next
    }

    private suspend fun speak(text: String): Boolean {
        var failures = 0
        while (true) {
            load.first { it != Load.HIGH }
            _state.update {
                it.copy(
                    currentLine = text,
                    voice = VoiceActivity.SPEAKING,
                    speakingStartedAt = now(),
                )
            }
            val result = voice.speak(text)
            _state.update { it.copy(voice = VoiceActivity.IDLE) }
            when (result) {
                SpeakResult.COMPLETED -> return true
                SpeakResult.INTERRUPTED -> continue
                SpeakResult.FAILED -> {
                    Log.w(TAG, "speak failed")
                    if (++failures >= MAX_SPEAK_FAILURES) return false
                }
            }
        }
    }

    /** 음성 인식과 시연 주입 중 먼저 온 답을 쓴다. HIGH로 끊긴 듣기는 해소된 뒤 다시 듣는다. */
    private suspend fun listen(): String {
        while (true) {
            load.first { it != Load.HIGH }
            _state.update { it.copy(voice = VoiceActivity.LISTENING) }
            val heard = coroutineScope {
                val fromVoice = async { voice.listen(LISTEN_TIMEOUT_MS) }
                val fromScenario = async { injectedAnswers.first() }
                select<String?> {
                    fromVoice.onAwait { it }
                    fromScenario.onAwait { it }
                }.also { coroutineContext.cancelChildren() }
            }
            _state.update { it.copy(voice = VoiceActivity.IDLE) }
            if (heard == null && load.value == Load.HIGH) continue
            return heard.orEmpty()
        }
    }

    private companion object {
        const val TAG = "ChaAsks.Orchestrator"
        const val TICK_MS = 250L
        const val LISTEN_TIMEOUT_MS = 15_000L
        const val MAX_SPEAK_FAILURES = 2
        val CLOSABLE_KINDS = setOf(UtteranceKind.QUESTION, UtteranceKind.FOLLOW_UP, UtteranceKind.REPROMPT)
    }
}
