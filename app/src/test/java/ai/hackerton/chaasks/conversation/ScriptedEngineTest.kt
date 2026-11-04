package ai.hackerton.chaasks.conversation

import ai.hackerton.chaasks.core.Load
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptedEngineTest {
    private val bank = TestBank.bank

    private fun talkShow(count: Int = 3) = ScriptedEngine(bank).also {
        it.start(SessionPlan(Mode.TALK_SHOW, bank.topics[0], count))
    }

    @Test fun startsWithOpening() {
        val u = ScriptedEngine(bank).start(SessionPlan(Mode.TALK_SHOW, bank.topics[0], 3))
        assertEquals(UtteranceKind.OPENING, u.kind)
        assertEquals("오늘은 처음 차 샀을 때 얘기 어때요?", u.text)
        assertTrue(u.expectsAnswer)
    }

    @Test fun normalLoadAsksLightQuestionFirst() {
        val u = talkShow().onAnswer("좋아요", Load.NORMAL)
        assertEquals(UtteranceKind.QUESTION, u.kind)
        assertEquals("첫 차는 어떤 차였어요?", u.text)
    }

    @Test fun stoppedLoadPrefersDeepQuestion() =
        assertEquals("그때의 나에게 한마디 한다면요?", talkShow().onAnswer("좋아요", Load.STOPPED).text)

    @Test fun keywordTriggersOneFollowUp() {
        val e = talkShow()
        e.onAnswer("좋아요", Load.NORMAL)
        val follow = e.onAnswer("아버지가 타던 중고차였어요", Load.NORMAL)
        assertEquals(UtteranceKind.FOLLOW_UP, follow.kind)
        assertEquals("그 차에 얽힌 사연이 있었나요?", follow.text)
        assertEquals(UtteranceKind.QUESTION, e.onAnswer("아버지랑 바다 갔어요", Load.NORMAL).kind)
    }

    @Test fun blankAnswerRepromptsOnceThenMovesOn() {
        val e = talkShow()
        e.onAnswer("좋아요", Load.NORMAL)
        val re = e.onAnswer("  ", Load.NORMAL)
        assertEquals(UtteranceKind.REPROMPT, re.kind)
        assertEquals("잘 못 들었어요. 첫 차는 어떤 차였어요?", re.text)
        assertEquals("첫 차로 처음 간 곳은 어디였어요?", e.onAnswer("", Load.NORMAL).text)
    }

    @Test fun stopCommandEndsImmediately() {
        val u = talkShow().onAnswer(" 그 만 ", Load.NORMAL)
        assertEquals(UtteranceKind.STOP, u.kind)
        assertFalse(u.expectsAnswer)
    }

    @Test fun runsClosingThenSignOffAfterPlannedQuestions() {
        val e = talkShow(count = 3)
        e.onAnswer("좋아요", Load.NORMAL) // -> q1
        e.onAnswer("소나타", Load.NORMAL) // -> q2
        e.onAnswer("바다", Load.NORMAL) // -> q5
        val closing = e.onAnswer("노래는 발라드", Load.NORMAL)
        assertEquals(UtteranceKind.CLOSING, closing.kind)
        assertEquals("오늘 이야기 한 줄로 정리하면요?", closing.text)
        val signOff = e.onAnswer("즐거웠다", Load.NORMAL)
        assertEquals(UtteranceKind.SIGN_OFF, signOff.kind)
        assertFalse(signOff.expectsAnswer)
    }

    @Test fun closeJumpsToClosingOnce() {
        val e = talkShow()
        e.onAnswer("좋아요", Load.NORMAL)
        assertEquals(UtteranceKind.CLOSING, e.close()?.kind)
        assertNull(e.close())
    }

    @Test fun summaryListsAnsweredQuestionsWithFirstSentence() {
        val e = talkShow()
        e.onAnswer("좋아요", Load.NORMAL)
        e.onAnswer("소나타였어요. 하얀색이었죠.", Load.NORMAL)
        assertEquals("처음 차 샀을 때\nQ: 첫 차는 어떤 차였어요?\nA: 소나타였어요", e.summarize())
    }

    @Test fun dailyLogAsksArrivalThenSignsOff() {
        val e = ScriptedEngine(bank)
        val first = e.start(SessionPlan(Mode.DAILY_LOG, null, 0))
        assertEquals(UtteranceKind.DAILY_LOG, first.kind)
        assertEquals(UtteranceKind.SIGN_OFF, e.onAnswer("회의가 길었다", Load.NORMAL).kind)
        assertEquals("한 줄 일기: 회의가 길었다", e.summarize())
    }
}
