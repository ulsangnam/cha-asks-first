package ai.hackerton.chaasks.ui

/** 기록 카드에 보여 줄 요약. 저장된 요약은 그대로 두고, 토크쇼는 주제와 첫 번째 Q/A 한 쌍만 보여 준다. */
object SessionPreview {
    fun cardText(summary: String): String {
        val lines = summary.lines()
        val firstAnswer = lines.indexOfFirst { it.startsWith("A: ") }
        return if (firstAnswer < 0) summary else lines.take(firstAnswer + 1).joinToString("\n")
    }
}
