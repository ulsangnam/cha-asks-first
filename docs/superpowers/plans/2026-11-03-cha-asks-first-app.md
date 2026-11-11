# 차가 먼저 묻는다 — 1차 앱 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 에뮬레이터에서 토크쇼 1회분, 페이싱, 도착 일기, "그만" 종료, 정차 중 기록 열람을 재현하는 앱을 만든다.

**Architecture:** 순수 Kotlin 코어(`core`, `pacing`, `conversation`)를 테스트 먼저 만들고, Pleos SDK 어댑터(`event`, `voice`), 암호화 저장소(`storage`), 앱 범위 `Orchestrator`, Compose 화면을 그 위에 얹는다. 설계: `docs/superpowers/specs/2026-11-03-cha-asks-first-app-design.md`.

**Tech Stack:** Kotlin 2.0.21, AGP 8.9.1, Gradle 8.11.1, Compose BOM 2024.10.01, Room 2.6.1 + KSP 2.0.21-1.0.27, SQLCipher `net.zetetic:sqlcipher-android:4.6.1`, kotlinx-serialization-json 1.7.3, kotlinx-coroutines 1.8.1, JUnit 4.13.2, Pleos SDK (STT/TTS 3.3.1, NaviHelper 3.0.5, Vehicle 2.3.2).

**설계 문서와 다른 점:** (1) 포그라운드 서비스 대신 `MainActivity`가 시작하는 앱 범위 `Orchestrator`를 쓴다. Android 14 포그라운드 서비스 유형 선언과 심사 요건을 피하고, 1차 시연은 화면이 켜진 상태라 충분하다. (2) 세션 종료는 `onAnswer`의 `null` 대신 `Utterance.expectsAnswer == false`로 판단한다.

**Git:** 저장소가 아직 없어 커밋 단계는 생략한다. 사용자가 요청하면 그때 `git init`과 커밋을 한다.

**공통 명령:** 모든 Gradle 명령은 `HMC_CAR_APP/`에서 `JAVA_HOME`을 JDK 17로 두고 실행한다.
`./gradlew --no-daemon --console=plain :app:testDebugUnitTest` (단위 테스트), `./gradlew --no-daemon --console=plain :app:assembleDebug` (빌드).

---

## 파일 구조

```
HMC_CAR_APP/
  settings.gradle.kts, build.gradle.kts, gradle.properties, local.properties, .gitignore
  gradle/wrapper/*, gradlew, gradlew.bat          (spikes/sdk-probe에서 복사)
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/assets/question_bank.json
  app/src/main/java/ai/hackerton/chaasks/
    core/DrivingEvent.kt          DrivingEvent, Gear, Load
    pacing/PacingStateMachine.kt
    conversation/Model.kt         Mode, Speaker, Turn, Utterance, UtteranceKind, SessionPlan
    conversation/QuestionBank.kt  JSON 모델 + parse
    conversation/SessionPlanner.kt
    conversation/ConversationEngine.kt
    conversation/ScriptedEngine.kt
    event/DrivingSignalSource.kt  인터페이스
    event/PleosVehicleSource.kt
    event/PleosNaviSource.kt
    event/ScenarioBus.kt          디버그 주입용 버스 (main, 수신자는 debug)
    voice/VoiceIO.kt
    voice/PleosVoiceIO.kt
    storage/Entities.kt, storage/ConversationDao.kt, storage/AppDatabase.kt
    storage/DbPassphrase.kt, storage/ConversationRepository.kt, storage/ConsentStore.kt
    app/ChaAsksApp.kt             Application, 의존성 조립
    app/Orchestrator.kt
    ui/MainActivity.kt, ui/Screens.kt
  app/src/debug/AndroidManifest.xml
  app/src/debug/java/ai/hackerton/chaasks/debug/ScenarioReceiver.kt
  app/src/test/java/ai/hackerton/chaasks/
    pacing/PacingStateMachineTest.kt
    conversation/QuestionBankTest.kt
    conversation/SessionPlannerTest.kt
    conversation/ScriptedEngineTest.kt
    conversation/TestBank.kt
```

---

### Task 1: 프로젝트 골격

**Files:** Create `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`, `local.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`; copy wrapper from `spikes/sdk-probe`.

- [ ] Step 1: wrapper 복사, `local.properties`에 `sdk.dir=C\:/Users/<user>/AppData/Local/Android/Sdk`
- [ ] Step 2: 루트 빌드 파일 작성. 플러그인: `com.android.application 8.9.1`, `org.jetbrains.kotlin.android 2.0.21`, `org.jetbrains.kotlin.plugin.compose 2.0.21`, `org.jetbrains.kotlin.plugin.serialization 2.0.21`, `com.google.devtools.ksp 2.0.21-1.0.27`. 저장소에 Pleos Nexus 추가.
- [ ] Step 3: `app/build.gradle.kts` — namespace/applicationId `ai.hackerton.chaasks`, minSdk 28, compile/target 34, `buildFeatures.compose = true`, 의존성(Tech Stack), `testImplementation("junit:junit:4.13.2")`.
- [ ] Step 4: 매니페스트 — 퍼미션 `STT_SERVICE`, `TTS_SERVICE`, `NAVI_ROUTE`, `CAR_LIGHTS`, `CAR_DRIVE`(모두 `pleos.car.permission.*`), `application android:name=".app.ChaAsksApp"`, `MainActivity`에 `<meta-data android:name="distractionOptimized" android:value="true"/>`.
- [ ] Step 5: `assembleDebug` 성공 확인. Expected: `BUILD SUCCESSFUL`.

### Task 2: 코어 타입

**Files:** Create `core/DrivingEvent.kt`

```kotlin
package ai.hackerton.chaasks.core

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

enum class Load { HIGH, NORMAL, STOPPED }

sealed interface DrivingEvent {
    data class TurnSignal(val on: Boolean) : DrivingEvent
    data class SteeringAngle(val degrees: Float) : DrivingEvent
    data class BrakePedal(val pressed: Boolean) : DrivingEvent
    data class GearChanged(val gear: Gear) : DrivingEvent
    data class RouteStarted(val destinationName: String, val remainMinutes: Int) : DrivingEvent
    data class RemainTime(val minutes: Int) : DrivingEvent
    data object Arrived : DrivingEvent
    data object RouteCancelled : DrivingEvent
}
```

- [ ] Step 1: 파일 작성, 컴파일 확인.

### Task 3: 페이싱 상태 머신 (TDD)

**Files:** Create `pacing/PacingStateMachine.kt`, Test `pacing/PacingStateMachineTest.kt`

규칙: 기어 P면 `STOPPED`(다른 원인보다 우선). 아니면 방향지시등 켜짐 / |조향각| ≥ 90 / 브레이크 중 하나라도 있으면 `HIGH`. 원인이 모두 사라져도 `HIGH`였다면 1500ms가 지나야 `NORMAL`. 해소 대기 중 새 원인이 오면 대기를 초기화한다. API: `onEvent(event, nowMillis): Load`, `tick(nowMillis): Load`, `val load`.

- [ ] Step 1: 실패하는 테스트 작성

```kotlin
package ai.hackerton.chaasks.pacing

import ai.hackerton.chaasks.core.DrivingEvent.BrakePedal
import ai.hackerton.chaasks.core.DrivingEvent.GearChanged
import ai.hackerton.chaasks.core.DrivingEvent.SteeringAngle
import ai.hackerton.chaasks.core.DrivingEvent.TurnSignal
import ai.hackerton.chaasks.core.Gear
import ai.hackerton.chaasks.core.Load
import org.junit.Assert.assertEquals
import org.junit.Test

class PacingStateMachineTest {
    private val pacing = PacingStateMachine()

    @Test fun startsNormal() = assertEquals(Load.NORMAL, pacing.load)

    @Test fun turnSignalMakesHigh() =
        assertEquals(Load.HIGH, pacing.onEvent(TurnSignal(true), 0))

    @Test fun staysHighUntilReleaseDelayPasses() {
        pacing.onEvent(TurnSignal(true), 0)
        assertEquals(Load.HIGH, pacing.onEvent(TurnSignal(false), 1_000))
        assertEquals(Load.HIGH, pacing.tick(2_499))
        assertEquals(Load.NORMAL, pacing.tick(2_500))
    }

    @Test fun largeSteeringEitherDirectionIsHigh() {
        assertEquals(Load.HIGH, pacing.onEvent(SteeringAngle(-95f), 0))
        pacing.onEvent(SteeringAngle(0f), 10)
        assertEquals(Load.NORMAL, pacing.tick(1_510))
        assertEquals(Load.NORMAL, pacing.onEvent(SteeringAngle(89f), 1_600))
    }

    @Test fun parkOverridesBrake() {
        pacing.onEvent(BrakePedal(true), 0)
        assertEquals(Load.STOPPED, pacing.onEvent(GearChanged(Gear.PARK), 10))
    }

    @Test fun remainingCauseKeepsHigh() {
        pacing.onEvent(TurnSignal(true), 0)
        pacing.onEvent(BrakePedal(true), 10)
        pacing.onEvent(TurnSignal(false), 20)
        assertEquals(Load.HIGH, pacing.tick(5_000))
        pacing.onEvent(BrakePedal(false), 5_000)
        assertEquals(Load.NORMAL, pacing.tick(6_500))
    }

    @Test fun newCauseDuringReleaseResetsDelay() {
        pacing.onEvent(TurnSignal(true), 0)
        pacing.onEvent(TurnSignal(false), 100)
        pacing.onEvent(BrakePedal(true), 1_000)
        pacing.onEvent(BrakePedal(false), 1_200)
        assertEquals(Load.HIGH, pacing.tick(2_600))
        assertEquals(Load.NORMAL, pacing.tick(2_700))
    }

    @Test fun leavingParkReturnsNormal() {
        pacing.onEvent(GearChanged(Gear.PARK), 0)
        assertEquals(Load.NORMAL, pacing.onEvent(GearChanged(Gear.DRIVE), 10))
    }
}
```

- [ ] Step 2: `:app:testDebugUnitTest --tests '*PacingStateMachineTest'` 실행 → 컴파일 실패 확인
- [ ] Step 3: 구현 (상수 `STEERING_HIGH_DEGREES = 90f`, `RELEASE_DELAY_MS = 1_500L`)
- [ ] Step 4: 테스트 통과 확인

### Task 4: 대화 모델과 질문 은행 (TDD)

**Files:** Create `conversation/Model.kt`, `conversation/QuestionBank.kt`, Test `conversation/TestBank.kt`, `conversation/QuestionBankTest.kt`

```kotlin
// Model.kt
enum class Mode { TALK_SHOW, DAILY_LOG }
enum class Speaker { HOST, DRIVER }
enum class UtteranceKind { OPENING, QUESTION, FOLLOW_UP, REPROMPT, CLOSING, SIGN_OFF, DAILY_LOG, STOP }
data class Utterance(val text: String, val kind: UtteranceKind, val expectsAnswer: Boolean)
data class Turn(val speaker: Speaker, val text: String)
data class SessionPlan(val mode: Mode, val topic: Topic?, val questionCount: Int)
```

```kotlin
// QuestionBank.kt (@Serializable)
data class QuestionBank(val topics: List<Topic>, val dailyLog: DailyLog)
data class Topic(val id: String, val title: String, val opening: String, val questions: List<Question>, val closing: String)
data class Question(val id: String, val text: String, val depth: Depth, val followUps: List<FollowUp> = emptyList())
enum class Depth { light, deep }
data class FollowUp(val keywords: List<String>, val text: String)
data class DailyLog(val arrival: String)
// companion: fun parse(json: String): QuestionBank (ignoreUnknownKeys = true)
```

`TestBank.kt`:

```kotlin
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
```

- [ ] Step 1: `QuestionBankTest` 작성

```kotlin
class QuestionBankTest {
    @Test fun parsesTopicsQuestionsAndFollowUps() {
        val bank = TestBank.bank
        assertEquals(2, bank.topics.size)
        val q1 = bank.topics[0].questions[0]
        assertEquals(Depth.light, q1.depth)
        assertEquals(listOf("중고", "아버지"), q1.followUps[0].keywords)
        assertEquals("도착했어요. 오늘 하루 한 줄로 남겨볼까요?", bank.dailyLog.arrival)
    }

    @Test fun missingFollowUpsDefaultsToEmpty() =
        assertTrue(TestBank.bank.topics[0].questions[1].followUps.isEmpty())
}
```

- [ ] Step 2: 실패 확인 → Step 3: 구현 → Step 4: 통과 확인

### Task 5: 세션 구성 (TDD)

**Files:** Create `conversation/SessionPlanner.kt`, Test `conversation/SessionPlannerTest.kt`

API: `object SessionPlanner { const val TALK_SHOW_MIN_MINUTES = 30; fun modeFor(remainMinutes: Int): Mode; fun questionCount(remainMinutes: Int): Int; fun shouldClose(remainMinutes: Int): Boolean; fun plan(remainMinutes: Int, bank: QuestionBank, lastTopicId: String?): SessionPlan }`. 질문 수는 `clamp((T - 6) / 8, 3, 5)`이되 주제의 질문 수를 넘지 않는다.

```kotlin
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
```

- [ ] Step 1~4: 테스트 → 실패 → 구현 → 통과

### Task 6: 대본 엔진 (TDD)

**Files:** Create `conversation/ConversationEngine.kt`, `conversation/ScriptedEngine.kt`, Test `conversation/ScriptedEngineTest.kt`

```kotlin
interface ConversationEngine {
    fun start(plan: SessionPlan): Utterance
    fun onAnswer(answer: String, load: Load): Utterance
    fun close(): Utterance?          // 클로징 앞당김. 이미 클로징 중이거나 끝났으면 null
    fun summarize(): String
}
```

규칙:
- 토크쇼: OPENING(답 기대) → QUESTION ×N (+ 질문당 최대 1개 FOLLOW_UP) → CLOSING(주제 closing 문장, 답 기대) → SIGN_OFF("고마워요. 오늘 이야기는 저장해 둘게요.", 답 기대 안 함)
- 하루 기록: DAILY_LOG(dailyLog.arrival, 답 기대) → SIGN_OFF("기록해 둘게요. 수고 많았어요.")
- 종료 명령(공백 제거 후 "그만", "그만해", "종료", "끝") → STOP("알겠어요, 여기까지 할게요.", 답 기대 안 함)
- 빈 답: 같은 단계에서 한 번만 REPROMPT("잘 못 들었어요. " + 직전 발화), 두 번째 빈 답은 다음 단계로
- 다음 질문 선택: STOPPED면 deep 우선, 그 외는 light 우선, 없으면 남은 아무 질문
- 요약: 토크쇼는 `"{title}\nQ: {질문}\nA: {답 첫 문장}"`(본 질문만, 답 없으면 생략), 하루 기록은 `"한 줄 일기: {답}"`. 첫 문장은 `.?!` 앞까지, 최대 80자.

```kotlin
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
        e.onAnswer("좋아요", Load.NORMAL)      // -> q1
        e.onAnswer("소나타", Load.NORMAL)      // -> q2
        e.onAnswer("바다", Load.NORMAL)        // -> q5
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
```

- [ ] Step 1~4: 테스트 → 실패 → 구현 → 통과

### Task 7: 질문 은행 자산

**Files:** Create `app/src/main/assets/question_bank.json` (주제 3개 × 질문 5개, light/deep 혼합, 꼬리 질문 키워드 포함, `dailyLog.arrival`).

- [ ] Step 1: 작성
- [ ] Step 2: `QuestionBankTest`에 실제 자산 파싱 테스트 추가 (`File("src/main/assets/question_bank.json").readText()`로 읽어 주제 3개, 주제마다 질문 5개, 모든 질문 텍스트가 비어 있지 않음을 단언) → 통과 확인

### Task 8: 이벤트 어댑터

**Files:** Create `event/DrivingSignalSource.kt`, `event/PleosVehicleSource.kt`, `event/PleosNaviSource.kt`, `event/ScenarioBus.kt`, `src/debug/AndroidManifest.xml`, `src/debug/java/.../debug/ScenarioReceiver.kt`

- `interface DrivingSignalSource { val events: Flow<DrivingEvent>; fun start(); fun stop() }`
- Vehicle: `Vehicle(context).initialize()` 후 2초 뒤 capability 확인(`checkTurnSignalStateCapabilities`, `checkSteeringWheelAngleCapabilities`, `checkBrakePedalStatusCapabilities`) 성공한 신호만 리스너 등록. 기어는 capability API가 없어 바로 등록. `null` 값은 버린다. `TurnSignalValue.name != "OFF"`면 켜짐. `VehicleGear.name`에 `PARK/REVERSE/NEUTRAL/DRIVE`가 들어 있으면 매핑, 아니면 `UNKNOWN`. 리스너 `onFailed(e: Exception)`는 로그만.
- Navi: `NaviHelper(context).initialize(onSuccess, onFail)`. `onRouteStarted(info)` → `RouteStarted(info.name, toMinutes(info.duration))`, `onDestinationInfo(info)`에서 `remainTime > 0`이면 `RemainTime`, `onDestinationArrived` → `Arrived`, `onRouteCancelled` → `RouteCancelled`. 시간 단위는 미확인이라 `toMinutes`는 초로 가정하고 원값을 로그로 남긴다.
- `object ScenarioBus { val events = MutableSharedFlow<DrivingEvent>(extraBufferCapacity = 16); val answers = MutableSharedFlow<String>(extraBufferCapacity = 4) }`
- `ScenarioReceiver`(debug만): `cmd` = `route`(`dest`, `min`), `remain`(`min`), `arrive`, `cancel`, `gear`(`gear`), `signal`(`on`), `brake`(`on`), `steer`(`deg`), `answer`(`text`). 사용: `adb shell am broadcast -n ai.hackerton.chaasks/.debug.ScenarioReceiver --es cmd route --es dest 부산 --ei min 90`
- [ ] Step 1: 작성 후 `assembleDebug` 성공 확인

### Task 9: 음성 어댑터

**Files:** Create `voice/VoiceIO.kt`, `voice/PleosVoiceIO.kt`

```kotlin
enum class SpeakResult { COMPLETED, INTERRUPTED, FAILED }
interface VoiceIO {
    suspend fun init(): Boolean
    suspend fun speak(text: String): SpeakResult
    fun stopSpeaking()
    suspend fun listen(timeoutMillis: Long): String?
    fun stopListening()
    fun release()
}
```

`PleosVoiceIO`: TTS/STT 모두 `Mode.ON_DEVICE`, `Locale.KOREA`. 이벤트 리스너는 초기화 때 한 번 등록하고, 진행 중인 `CompletableDeferred`에 결과를 넘긴다. TTS `onDone` → COMPLETED, `onStop` → INTERRUPTED, `onError` → FAILED. STT `onUpdated(text, true)` → 결과, `onError` → `null`, 시간 초과 → `stop()` 후 `null`.

- [ ] Step 1: 작성 후 `assembleDebug` 성공 확인

### Task 10: 저장소

**Files:** Create `storage/Entities.kt`, `ConversationDao.kt`, `AppDatabase.kt`, `DbPassphrase.kt`, `ConversationRepository.kt`, `ConsentStore.kt`

- `SessionEntity(id: String, mode: String, topicId: String?, startedAt: Long, endedAt: Long, destinationLabel: String, summary: String)`, `TurnEntity(id: Long autoGenerate, sessionId: String, speaker: String, text: String, createdAt: Long)`
- DAO: `insertSession`, `insertTurns`, `observeSessions(): Flow<List<SessionEntity>>` (최신순)
- `DbPassphrase.getOrCreate(context): ByteArray` — Keystore AES/GCM 키(`chaasks_db_key`)로 무작위 32바이트를 암호화해 prefs에 보관. `delete(context)`로 키·prefs 삭제.
- `AppDatabase.open(context)` — `System.loadLibrary("sqlcipher")`, `SupportOpenHelperFactory(passphrase)`
- `ConversationRepository.save(...)`, `sessions()`, `wipe()`(DB 닫기 → `deleteDatabase` → 키 삭제)
- `ConsentStore`: `isGranted`, `grant()`, `revoke()`
- [ ] Step 1: 작성 후 `assembleDebug` 성공 확인

### Task 11: 오케스트레이터와 앱 조립

**Files:** Create `app/ChaAsksApp.kt`, `app/Orchestrator.kt`

- `Orchestrator(scope, signals: Flow<DrivingEvent>, voice, bank, repository, now: () -> Long)`; `state: StateFlow<UiState>` (`mode`, `load`, `gear`, `currentLine`, `sessionActive`, `remainMinutes`)
- 이벤트 처리: 페이싱 갱신(250ms마다 `tick`), `HIGH` 진입 시 `stopSpeaking`/`stopListening`. `RouteStarted`: 세션이 없으면 토크쇼는 즉시 시작, 하루 기록은 도착 대기. `RemainTime`: `shouldClose`면 클로징 요청. `Arrived`: 하루 기록 대기 중이면 시작, 토크쇼 중이면 클로징 요청. `RouteCancelled`: 대기 해제.
- 세션 루프: 발화 → (답 기대 시) 듣기 → `onAnswer`/`close` 반복. 말하기·듣기 전에는 `load != HIGH`가 될 때까지 기다리고, `INTERRUPTED`면 같은 문장을 다시 말한다. `FAILED`는 한 번 재시도 후 세션 종료. 듣기는 `voice.listen(8_000)`과 `ScenarioBus.answers` 중 먼저 오는 쪽.
- 세션 종료 시 `repository.save` (요약 포함).
- `ChaAsksApp`: 질문 은행 로딩, `merge(vehicle.events, navi.events, ScenarioBus.events)`, `startOrchestrator()`는 동의 후 한 번만.
- [ ] Step 1: 작성 후 `assembleDebug` 성공 확인

### Task 12: 화면

**Files:** Create `ui/MainActivity.kt`, `ui/Screens.kt`

- 동의 전: `ConsentScreen` — 수집 항목, 목적, 보유 기간(삭제 전까지), 거부 권리. 버튼은 기어 P일 때만 활성, 아니면 "정차 후 진행해 주세요".
- 동의 후, 기어 P: `RecordsScreen` — 세션 목록(모드, 시각, 요약), "동의 철회 및 기록 삭제" 버튼.
- 동의 후, 기어 P 아님: `DrivingScreen` — 모드, 페이싱 상태, 현재 발화. 터치 요소 없음.
- [ ] Step 1: 작성 후 `assembleDebug` 성공 확인

### Task 13: 에뮬레이터 검증

- [ ] Step 1: 설치 후 `gear PARK` 주입 → 동의
- [ ] Step 2: `gear DRIVE`, `route 부산 90` → 오프닝 발화 확인
- [ ] Step 3: `answer` 주입으로 꼬리 질문·다음 질문 진행 확인
- [ ] Step 4: `signal on` → 발화 중지, `signal off` → 1.5초 뒤 같은 문장 재개 확인
- [ ] Step 5: `answer 그만` → STOP 후 저장 확인
- [ ] Step 6: `route 회사 15` → `arrive` → 한 줄 일기 질문 → `answer` → 저장 확인
- [ ] Step 7: `gear PARK` → 기록 목록에 세션 2개 확인
- [ ] Step 8: 결과와 미확인 사항을 README "현재 한계"에 반영
