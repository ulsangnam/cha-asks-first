package ai.hackerton.chaasks.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 음성 엔진을 쓸 수 없을 때의 화면 표시 모드.
 * 발화는 읽는 데 걸리는 시간만큼 화면에 두고, 듣기는 하지 않는다(시연 주입 답만 받는다).
 */
class TextOnlyVoiceIO : VoiceIO {
    private var speaking: CompletableDeferred<SpeakResult>? = null
    private var listening: CompletableDeferred<String?>? = null

    override suspend fun init(): Boolean = true

    override suspend fun speak(text: String): SpeakResult {
        val pending = CompletableDeferred<SpeakResult>().also { speaking = it }
        return withTimeoutOrNull(readingMillis(text)) { pending.await() } ?: SpeakResult.COMPLETED
    }

    override fun stopSpeaking() {
        speaking?.complete(SpeakResult.INTERRUPTED)
    }

    override suspend fun listen(timeoutMillis: Long): String? {
        val pending = CompletableDeferred<String?>().also { listening = it }
        return withTimeoutOrNull(timeoutMillis) { pending.await() }
    }

    override fun stopListening() {
        listening?.complete(null)
    }

    override fun release() = Unit

    private fun readingMillis(text: String): Long = READ_BASE_MS + text.length * READ_PER_CHAR_MS

    private companion object {
        const val READ_BASE_MS = 1_500L
        const val READ_PER_CHAR_MS = 120L
    }
}
