package ai.hackerton.chaasks.voice

import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/** [primary]를 먼저 초기화하고, 실패하거나 응답이 없으면 [fallback]으로 진행한다. */
class FallbackVoiceIO(private val primary: VoiceIO, private val fallback: VoiceIO) : VoiceIO {
    @Volatile
    private var active: VoiceIO = fallback

    /** primary를 쓰게 되면 true. */
    override suspend fun init(): Boolean {
        val primaryReady = try {
            withTimeoutOrNull(INIT_TIMEOUT_MS) { primary.init() } == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "primary voice init threw", e)
            false
        }
        active = if (primaryReady) primary else fallback.also { it.init() }
        Log.i(TAG, "voice engine=${if (primaryReady) "primary" else "fallback"}")
        return primaryReady
    }

    override suspend fun speak(text: String): SpeakResult = active.speak(text)

    override fun stopSpeaking() = active.stopSpeaking()

    override suspend fun listen(timeoutMillis: Long): String? = active.listen(timeoutMillis)

    override fun stopListening() = active.stopListening()

    override fun release() {
        primary.release()
        fallback.release()
    }

    private companion object {
        const val TAG = "ChaAsks.Voice"
        const val INIT_TIMEOUT_MS = 10_000L
    }
}
