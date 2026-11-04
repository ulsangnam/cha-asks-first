package ai.hackerton.chaasks.conversation

object TestBank {
    const val JSON = """
    {
      "topics": [
        {
          "id": "first-car", "title": "처음 차 샀을 때",
          "opening": "오늘은 처음 차 샀을 때 얘기 어때요?",
          "questions": [
            {"id": "q1", "text": "첫 차는 어떤 차였어요?", "depth": "light",
             "followUps": [{"keywords": ["중고", "아버지"], "text": "그 차에 얽힌 사연이 있었나요?"}]},
            {"id": "q2", "text": "첫 차로 처음 간 곳은 어디였어요?", "depth": "light"},
            {"id": "q3", "text": "그때의 나에게 한마디 한다면요?", "depth": "deep"},
            {"id": "q4", "text": "첫 차를 떠나보낼 때 기분은 어땠어요?", "depth": "deep"},
            {"id": "q5", "text": "첫 차에서 제일 많이 들은 노래는요?", "depth": "light"}
          ],
          "closing": "오늘 이야기 한 줄로 정리하면요?"
        },
        {
          "id": "work", "title": "요즘 일",
          "opening": "오늘은 요즘 일 얘기 해볼까요?",
          "questions": [
            {"id": "w1", "text": "요즘 제일 바쁜 일은 뭐예요?", "depth": "light"},
            {"id": "w2", "text": "일하면서 뿌듯했던 순간은요?", "depth": "deep"},
            {"id": "w3", "text": "출근길에 주로 무슨 생각 해요?", "depth": "light"}
          ],
          "closing": "오늘 대화 한 줄로 남긴다면요?"
        }
      ],
      "dailyLog": {"arrival": "도착했어요. 오늘 하루 한 줄로 남겨볼까요?"}
    }
    """

    val bank: QuestionBank get() = QuestionBank.parse(JSON)
}
