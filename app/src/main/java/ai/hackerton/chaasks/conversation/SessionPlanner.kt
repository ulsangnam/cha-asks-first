package ai.hackerton.chaasks.conversation

/** 남은 주행 시간으로 모드와 토크쇼 분량을 정한다. */
object SessionPlanner {
    const val TALK_SHOW_MIN_MINUTES = 30
    const val CLOSING_MINUTES = 5

    private const val OPENING_AND_CLOSING_MINUTES = 6
    private const val MINUTES_PER_QUESTION = 8
    private const val MIN_QUESTIONS = 3
    private const val MAX_QUESTIONS = 5

    fun modeFor(remainMinutes: Int): Mode =
        if (remainMinutes >= TALK_SHOW_MIN_MINUTES) Mode.TALK_SHOW else Mode.DAILY_LOG

    fun questionCount(remainMinutes: Int): Int =
        ((remainMinutes - OPENING_AND_CLOSING_MINUTES) / MINUTES_PER_QUESTION).coerceIn(MIN_QUESTIONS, MAX_QUESTIONS)

    fun shouldClose(remainMinutes: Int): Boolean = remainMinutes <= CLOSING_MINUTES

    fun plan(remainMinutes: Int, bank: QuestionBank, lastTopicId: String?): SessionPlan {
        if (modeFor(remainMinutes) == Mode.DAILY_LOG) return SessionPlan(Mode.DAILY_LOG, null, 0)
        val topic = bank.topics.firstOrNull { it.id != lastTopicId } ?: bank.topics.first()
        return SessionPlan(Mode.TALK_SHOW, topic, minOf(questionCount(remainMinutes), topic.questions.size))
    }
}
