package ai.hackerton.chaasks.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionPreviewTest {
    @Test fun talkShowKeepsTopicAndFirstPairOnly() {
        val summary = "처음 차 샀을 때\nQ: 첫 차는 어떤 차였어요?\nA: 소나타였어요\nQ: 처음 멀리 간 곳은?\nA: 강릉"
        assertEquals("처음 차 샀을 때\nQ: 첫 차는 어떤 차였어요?\nA: 소나타였어요", SessionPreview.cardText(summary))
    }

    @Test fun summaryWithoutAnswersIsUnchanged() {
        assertEquals("한 줄 일기: 잘 끝났다", SessionPreview.cardText("한 줄 일기: 잘 끝났다"))
        assertEquals("요즘 나", SessionPreview.cardText("요즘 나"))
    }
}
