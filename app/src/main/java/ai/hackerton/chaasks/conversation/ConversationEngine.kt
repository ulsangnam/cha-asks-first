package ai.hackerton.chaasks.conversation

import ai.hackerton.chaasks.core.Load

/** 대화 진행 규칙. 1차는 [ScriptedEngine], Gleo LLM 승인 후 같은 인터페이스로 교체한다. */
interface ConversationEngine {
    fun start(plan: SessionPlan): Utterance

    fun onAnswer(answer: String, load: Load): Utterance

    /** 클로징을 앞당긴다. 이미 클로징 중이거나 끝났으면 null. */
    fun close(): Utterance?

    fun summarize(): String
}
