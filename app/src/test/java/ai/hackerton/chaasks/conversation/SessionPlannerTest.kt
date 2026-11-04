package ai.hackerton.chaasks.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPlannerTest {
    @Test fun modeSwitchesAtThirtyMinutes() {
        assertEquals(Mode.DAILY_LOG, SessionPlanner.modeFor(29))
        assertEquals(Mode.TALK_SHOW, SessionPlanner.modeFor(30))
    }

    @Test fun questionCountIsClampedBetweenThreeAndFive() {
        assertEquals(3, SessionPlanner.questionCount(30))
        assertEquals(4, SessionPlanner.questionCount(38))
        assertEquals(5, SessionPlanner.questionCount(120))
    }

    @Test fun closesAtFiveMinutesLeft() {
        assertFalse(SessionPlanner.shouldClose(6))
        assertTrue(SessionPlanner.shouldClose(5))
    }

    @Test fun talkShowPlanAvoidsLastTopic() {
        val plan = SessionPlanner.plan(60, TestBank.bank, lastTopicId = "first-car")
        assertEquals(Mode.TALK_SHOW, plan.mode)
        assertEquals("work", plan.topic?.id)
    }

    @Test fun questionCountNeverExceedsTopicSize() =
        assertEquals(3, SessionPlanner.plan(120, TestBank.bank, "first-car").questionCount)

    @Test fun dailyLogPlanHasNoTopic() {
        val plan = SessionPlanner.plan(15, TestBank.bank, null)
        assertEquals(Mode.DAILY_LOG, plan.mode)
        assertNull(plan.topic)
    }
}
