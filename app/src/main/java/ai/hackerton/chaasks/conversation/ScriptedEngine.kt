package ai.hackerton.chaasks.conversation

import ai.hackerton.chaasks.core.Load

/** 질문 은행과 키워드 규칙으로 진행하는 대본 엔진. 네트워크 없이 차 안에서만 동작한다. */
class ScriptedEngine(private val bank: QuestionBank) : ConversationEngine {
    private enum class Stage { OPENING, QUESTION, FOLLOW_UP, CLOSING, DAILY_LOG, DONE }

    private lateinit var plan: SessionPlan
    private var stage = Stage.DONE
    private var lastPrompt = ""
    private var reprompted = false
    private var current: Question? = null
    private val asked = mutableListOf<Question>()
    private val answers = linkedMapOf<Question, String>()
    private var dailyAnswer = ""

    override fun start(plan: SessionPlan): Utterance {
        this.plan = plan
        asked.clear()
        answers.clear()
        dailyAnswer = ""
        reprompted = false
        current = null
        return when (plan.mode) {
            Mode.TALK_SHOW -> {
                stage = Stage.OPENING
                say(requireTopic().opening, UtteranceKind.OPENING)
            }
            Mode.DAILY_LOG -> {
                stage = Stage.DAILY_LOG
                say(bank.dailyLog.arrival, UtteranceKind.DAILY_LOG)
            }
        }
    }

    override fun onAnswer(answer: String, load: Load): Utterance {
        check(stage != Stage.DONE) { "session already ended" }
        if (isStopCommand(answer)) {
            stage = Stage.DONE
            return Utterance(STOP_LINE, UtteranceKind.STOP, expectsAnswer = false)
        }
        if (answer.isBlank() && !reprompted) {
            reprompted = true
            return Utterance("$REPROMPT_PREFIX$lastPrompt", UtteranceKind.REPROMPT, expectsAnswer = true)
        }
        reprompted = false
        val text = answer.trim()
        return when (stage) {
            Stage.OPENING, Stage.FOLLOW_UP -> nextQuestionOrClosing(load)
            Stage.QUESTION -> afterQuestion(text, load)
            Stage.CLOSING -> signOff(TALK_SHOW_SIGN_OFF)
            Stage.DAILY_LOG -> {
                dailyAnswer = text
                signOff(DAILY_LOG_SIGN_OFF)
            }
            Stage.DONE -> error("unreachable")
        }
    }

    override fun close(): Utterance? = when (stage) {
        Stage.OPENING, Stage.QUESTION, Stage.FOLLOW_UP -> closing()
        else -> null
    }

    override fun summarize(): String = when (plan.mode) {
        Mode.DAILY_LOG -> "한 줄 일기: $dailyAnswer"
        Mode.TALK_SHOW -> buildList {
            add(requireTopic().title)
            answers.forEach { (question, answer) -> add("Q: ${question.text}\nA: ${firstSentence(answer)}") }
        }.joinToString("\n")
    }

    private fun afterQuestion(text: String, load: Load): Utterance {
        val question = checkNotNull(current)
        if (text.isEmpty()) return nextQuestionOrClosing(load)
        answers[question] = text
        val followUp = question.followUps.firstOrNull { f -> f.keywords.any { text.contains(it) } }
            ?: return nextQuestionOrClosing(load)
        stage = Stage.FOLLOW_UP
        return say(followUp.text, UtteranceKind.FOLLOW_UP)
    }

    private fun nextQuestionOrClosing(load: Load): Utterance {
        if (asked.size >= plan.questionCount) return closing()
        val remaining = requireTopic().questions.filterNot { it in asked }
        val preferred = if (load == Load.STOPPED) Depth.DEEP else Depth.LIGHT
        val next = remaining.firstOrNull { it.depth == preferred } ?: remaining.firstOrNull() ?: return closing()
        asked += next
        current = next
        stage = Stage.QUESTION
        return say(next.text, UtteranceKind.QUESTION)
    }

    private fun closing(): Utterance {
        stage = Stage.CLOSING
        return say(requireTopic().closing, UtteranceKind.CLOSING)
    }

    private fun signOff(text: String): Utterance {
        stage = Stage.DONE
        return Utterance(text, UtteranceKind.SIGN_OFF, expectsAnswer = false)
    }

    private fun say(text: String, kind: UtteranceKind): Utterance {
        lastPrompt = text
        return Utterance(text, kind, expectsAnswer = true)
    }

    private fun requireTopic(): Topic = checkNotNull(plan.topic) { "talk show needs a topic" }

    private fun isStopCommand(answer: String): Boolean = answer.filterNot { it.isWhitespace() } in STOP_COMMANDS

    private fun firstSentence(text: String): String =
        text.split('.', '?', '!').first().trim().take(MAX_SUMMARY_CHARS)

    companion object {
        private val STOP_COMMANDS = setOf("그만", "그만해", "종료", "끝")
        private const val STOP_LINE = "알겠어요, 여기까지 할게요."
        private const val REPROMPT_PREFIX = "잘 못 들었어요. "
        private const val TALK_SHOW_SIGN_OFF = "고마워요. 오늘 이야기는 저장해 둘게요."
        private const val DAILY_LOG_SIGN_OFF = "기록해 둘게요. 수고 많았어요."
        private const val MAX_SUMMARY_CHARS = 80
    }
}
