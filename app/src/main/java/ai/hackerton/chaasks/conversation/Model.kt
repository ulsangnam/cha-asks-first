package ai.hackerton.chaasks.conversation

enum class Mode { TALK_SHOW, DAILY_LOG }

enum class Speaker { HOST, DRIVER }

enum class UtteranceKind { OPENING, QUESTION, FOLLOW_UP, REPROMPT, CLOSING, SIGN_OFF, DAILY_LOG, STOP }

/** 진행자 발화. [expectsAnswer]가 false면 이 발화로 세션이 끝난다. */
data class Utterance(val text: String, val kind: UtteranceKind, val expectsAnswer: Boolean)

data class Turn(val speaker: Speaker, val text: String)

data class SessionPlan(val mode: Mode, val topic: Topic?, val questionCount: Int)
