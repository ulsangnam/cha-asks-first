package ai.hackerton.chaasks.voice

enum class SpeakResult { COMPLETED, INTERRUPTED, FAILED }

/** 진행자 말하기와 운전자 답 듣기. 주행 부하가 높아지면 [stopSpeaking]·[stopListening]으로 즉시 끊는다. */
interface VoiceIO {
    suspend fun init(): Boolean

    suspend fun speak(text: String): SpeakResult

    fun stopSpeaking()

    /** 인식된 문장을 돌려준다. 시간이 지나거나 끊기거나 오류가 나면 null. */
    suspend fun listen(timeoutMillis: Long): String?

    fun stopListening()

    fun release()
}
