package ai.hackerton.chaasks.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QuestionBankTest {
    @Test fun parsesTopicsQuestionsAndFollowUps() {
        val bank = TestBank.bank
        assertEquals(2, bank.topics.size)
        val q1 = bank.topics[0].questions[0]
        assertEquals(Depth.LIGHT, q1.depth)
        assertEquals(listOf("중고", "아버지"), q1.followUps[0].keywords)
        assertEquals("도착했어요. 오늘 하루 한 줄로 남겨볼까요?", bank.dailyLog.arrival)
    }

    @Test fun missingFollowUpsDefaultsToEmpty() =
        assertTrue(TestBank.bank.topics[0].questions[1].followUps.isEmpty())

    @Test fun shippedAssetHasThreeTopicsWithFiveQuestionsEach() {
        val bank = QuestionBank.parse(File("src/main/assets/question_bank.json").readText())
        assertEquals(3, bank.topics.size)
        bank.topics.forEach { topic ->
            assertEquals(topic.id, 5, topic.questions.size)
            assertTrue(topic.id, topic.questions.all { it.text.isNotBlank() })
            assertTrue(topic.id, topic.questions.any { it.depth == Depth.DEEP })
            assertTrue(topic.id, topic.questions.any { it.depth == Depth.LIGHT })
        }
        assertTrue(bank.dailyLog.arrival.isNotBlank())
    }
}
