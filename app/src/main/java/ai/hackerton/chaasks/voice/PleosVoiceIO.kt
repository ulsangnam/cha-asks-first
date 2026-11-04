package ai.hackerton.chaasks.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.tts.TextToSpeech
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.stt.listener.ResultListener as SttResultListener
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener as TtsEventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener

/** Gleo TTS/STT 온디바이스 모드. 앱 등록 없이 동작하고 음성이 차 밖으로 나가지 않는다. */
class PleosVoiceIO(context: Context) : VoiceIO {
    private val tts = TextToSpeech(context.applicationContext, TtsMode.ON_DEVICE)
    private val stt = SpeechToText(context.applicationContext, SttMode.ON_DEVICE)
    private var speaking: CompletableDeferred<SpeakResult>? = null
    private var listening: CompletableDeferred<String?>? = null

    private val ttsEvents = object : TtsEventListener {
        override fun onStart() = Unit

        override fun onReady() = Unit

        override fun onDone() {
            speaking?.complete(SpeakResult.COMPLETED)
        }

        override fun onError(error: TtsError) {
            Log.w(TAG, "tts error=$error")
            speaking?.complete(SpeakResult.FAILED)
        }

        override fun onStop() {
            speaking?.complete(SpeakResult.INTERRUPTED)
        }

        override fun onUpdatedRms(rms: Double) = Unit
    }

    private val sttResults = object : SttResultListener {
        override fun onUpdated(text: String, completed: Boolean) {
            if (completed) listening?.complete(text)
        }

        override fun onUpdatedEpdData(start: Long, end: Long) = Unit

        override fun onStartedRecognition() = Unit

        override fun onEndedRecognition() = Unit

        override fun onError(error: SttError) {
            Log.w(TAG, "stt error=$error")
            listening?.complete(null)
        }

        override fun onReady() = Unit

        override fun onUpdatedRms(rms: Double) = Unit
    }

    override suspend fun init(): Boolean = initTts() && initStt()

    private suspend fun initTts(): Boolean = suspendCancellableCoroutine { cont ->
        tts.initialize(object : TtsInitListener {
            override fun onSuccess() {
                tts.addEventListener(ttsEvents)
                // Locale.KOREAN은 "ko-"로 전달돼 엔진 오류가 난다. 국가 코드까지 있는 ko-KR을 쓴다.
                tts.setLocale(Locale.KOREA)
                if (cont.isActive) cont.resume(true)
            }

            override fun onFailure(error: TtsError) {
                Log.w(TAG, "tts init failed=$error")
                if (cont.isActive) cont.resume(false)
            }
        })
    }

    private suspend fun initStt(): Boolean = suspendCancellableCoroutine { cont ->
        stt.initialize(object : SttInitListener {
            override fun onSuccess() {
                stt.addListener(sttResults)
                stt.setLocale(Locale.KOREA)
                if (cont.isActive) cont.resume(true)
            }

            override fun onFailure(error: SttError) {
                Log.w(TAG, "stt init failed=$error")
                if (cont.isActive) cont.resume(false)
            }
        })
    }

    override suspend fun speak(text: String): SpeakResult {
        val pending = CompletableDeferred<SpeakResult>().also { speaking = it }
        tts.speak(text, false)
        // 완료 콜백이 오지 않는 경우의 상한. 넘기면 말한 것으로 보고 진행한다.
        return withTimeoutOrNull(maxSpeakMillis(text)) { pending.await() }
            ?: SpeakResult.COMPLETED.also { Log.w(TAG, "tts done callback missing") }
    }

    override fun stopSpeaking() {
        runCatching { tts.stop() }
        speaking?.complete(SpeakResult.INTERRUPTED)
    }

    override suspend fun listen(timeoutMillis: Long): String? {
        val pending = CompletableDeferred<String?>().also { listening = it }
        stt.request()
        try {
            return withTimeoutOrNull(timeoutMillis) { pending.await() }
        } finally {
            if (!pending.isCompleted) runCatching { stt.stop() }
        }
    }

    override fun stopListening() {
        runCatching { stt.stop() }
        listening?.complete(null)
    }

    override fun release() {
        runCatching { tts.release() }
        runCatching { stt.release() }
    }

    private fun maxSpeakMillis(text: String): Long = SPEAK_BASE_MS + text.length * SPEAK_PER_CHAR_MS

    private companion object {
        const val TAG = "ChaAsks.Voice"
        const val SPEAK_BASE_MS = 4_000L
        const val SPEAK_PER_CHAR_MS = 250L
    }
}
