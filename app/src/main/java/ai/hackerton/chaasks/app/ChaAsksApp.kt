package ai.hackerton.chaasks.app

import ai.hackerton.chaasks.conversation.QuestionBank
import ai.hackerton.chaasks.event.PleosNaviSource
import ai.hackerton.chaasks.event.PleosVehicleSource
import ai.hackerton.chaasks.event.ScenarioBus
import ai.hackerton.chaasks.storage.ConsentStore
import ai.hackerton.chaasks.storage.ConversationRepository
import ai.hackerton.chaasks.voice.FallbackVoiceIO
import ai.hackerton.chaasks.voice.PleosVoiceIO
import ai.hackerton.chaasks.voice.TextOnlyVoiceIO
import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 앱 범위 의존성 조립. 오케스트레이터는 첫 화면이 뜰 때 한 번 만들어 계속 돌린다. */
class ChaAsksApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var consent: ConsentStore
        private set
    lateinit var repository: ConversationRepository
        private set

    val orchestrator: Orchestrator by lazy { createOrchestrator() }

    override fun onCreate() {
        super.onCreate()
        consent = ConsentStore(this)
        repository = ConversationRepository(this)
    }

    /** 동의를 철회하면 저장된 기록과 암호 키를 즉시 지운다. */
    fun revokeConsent() {
        consent.revoke()
        appScope.launch { repository.wipe() }
    }

    private fun createOrchestrator(): Orchestrator {
        val bank = QuestionBank.parse(assets.open(QUESTION_BANK_ASSET).bufferedReader().use { it.readText() })
        val vehicle = PleosVehicleSource(this).also { it.start() }
        val navi = PleosNaviSource(this).also { it.start() }
        return Orchestrator(
            scope = appScope,
            signals = merge(vehicle.events, navi.events, ScenarioBus.events),
            voice = FallbackVoiceIO(PleosVoiceIO(this), TextOnlyVoiceIO()),
            bank = bank,
            repository = repository,
            injectedAnswers = ScenarioBus.answers.receiveAsFlow(),
            canRecord = { consent.granted.value },
        )
    }

    private companion object {
        const val QUESTION_BANK_ASSET = "question_bank.json"
    }
}
