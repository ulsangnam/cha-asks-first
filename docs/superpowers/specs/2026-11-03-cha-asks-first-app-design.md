# 차가 먼저 묻는다 — 앱 설계 (1차 접수 범위)

기준일 2026-11-03. 승인된 방향: 대본 엔진으로 먼저 완성하고 Gleo LLM은 승인 후 교체한다. 근거와 실측 결과는 `../../../CLAUDE.md`와 `../../../PLAN_BRIEF.md`에 있다.

## 목표

11/15 접수용으로, 에뮬레이터에서 다음 장면을 실제 앱으로 재현한다.

1. 장거리 경로가 잡히면 토크쇼 모드로 들어가 오프닝 → 본 질문 3개 → 꼬리 질문 → 클로징을 진행한다.
2. 방향지시등, 큰 조향, 브레이크가 들어오면 말을 멈췄다가 해소되면 이어간다.
3. 단거리 주행은 도착할 때 한 줄 일기 질문을 하나 하고 저장한다.
4. "그만"이라고 하면 세션이 끝난다.
5. 기어 P일 때만 기록 열람 화면이 열린다(목업).

범위 밖: 실제 LLM 호출, 주유 감지(연료량 미지원), 폰 연동, 음성 원본 저장.

## 구조

단일 Android 앱 모듈 `app`, 패키지 `ai.hackerton.chaasks`. minSdk 28, compileSdk 34, Kotlin, Jetpack Compose.

| 패키지 | 주요 타입 | 의존 |
|---|---|---|
| `core` | `DrivingEvent`, `Gear`, `Load` | 없음 (순수 Kotlin) |
| `pacing` | `PacingStateMachine` | `core` |
| `conversation` | `ConversationEngine`, `ScriptedEngine`, `SessionPlanner`, `QuestionBank`, `Turn`, `Utterance` | `core` |
| `event` | `DrivingSignalSource`, `PleosVehicleSource`, `PleosNaviSource`, `ScenarioReceiver` | Pleos Vehicle/NaviHelper SDK |
| `voice` | `VoiceIO`, `PleosVoiceIO` | Pleos TTS/STT SDK |
| `storage` | Room DB, `ConversationRepository` | Room, SQLCipher |
| `service` | `DriveCompanionService`, `Orchestrator` | 위 전부 |
| `ui` | `ConsentScreen`, `DrivingScreen`, `RecordsScreen` | Compose |

`core`, `pacing`, `conversation`은 Android 의존이 없어 JVM 단위 테스트로 검증한다.

## 데이터 흐름

```
Vehicle SDK ─┐
NaviHelper ──┼─> DrivingSignalSource ─> Flow<DrivingEvent> ─> Orchestrator
Scenario ────┘                                                  │
                         PacingStateMachine <───────────────────┤
                         ConversationEngine <───────────────────┤
                         VoiceIO (TTS 말하기 / STT 듣기) <───────┤
                         ConversationRepository <───────────────┘
```

## 핵심 타입

### DrivingEvent

```kotlin
sealed interface DrivingEvent {
    data class TurnSignal(val on: Boolean) : DrivingEvent
    data class SteeringAngle(val degrees: Float) : DrivingEvent
    data class BrakePedal(val pressed: Boolean) : DrivingEvent
    data class GearChanged(val gear: Gear) : DrivingEvent        // PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN
    data class RouteStarted(val destinationName: String, val remainMinutes: Int) : DrivingEvent
    data class RemainTime(val minutes: Int) : DrivingEvent
    data object Arrived : DrivingEvent
    data object RouteCancelled : DrivingEvent
}
```

차량 리스너 인자는 nullable이므로 `null`은 이벤트로 만들지 않고 버린다.

### 페이싱

| 상태 | 진입 조건 | 효과 |
|---|---|---|
| `HIGH` | 방향지시등 켜짐, 조향각 절댓값 ≥ 90°, 브레이크 밟음 | TTS 즉시 중지, STT 대기 |
| `NORMAL` | HIGH 원인이 모두 해소되고 1.5초 경과, 기어 P 아님 | 대화 진행 |
| `STOPPED` | 기어 P | 대화 진행 가능, 깊은 질문 허용, 화면 조작 허용 |

HIGH가 해소되면 끊긴 문장을 처음부터 다시 말한다. 임계값(90°, 1.5초)은 상수로 두고 테스트로 고정한다.

### 모드 결정

`RouteStarted.remainMinutes ≥ 30`이면 토크쇼, 아니면 하루 기록. 경로가 없으면 대화를 시작하지 않는다.

### 토크쇼 세션 구성 (`SessionPlanner`)

남은 시간 T분 기준:

- 오프닝 1개, 클로징 1개는 항상 넣는다.
- 본 질문 수 = `clamp((T - 6) / 8, 3, 5)`. 질문 하나에 답과 꼬리 질문 포함 약 8분으로 잡는다.
- 클로징은 남은 시간 5분 이하가 되면 앞당긴다.
- 주제는 질문 은행의 주제 하나를 고르고, 직전 세션과 같은 주제는 피한다.

### 대본 엔진 (`ScriptedEngine`)

- 질문 은행은 `assets/question_bank.json`에 둔다. 구조: `topics[].{id, title, opening, questions[].{id, text, depth: light|deep, followUps[].{keywords[], text}}, closing}`, 그리고 `dailyLog.{arrival}`.
- 꼬리 질문은 답변에 키워드가 하나라도 있으면 해당 꼬리 질문을 하나 낸다. 질문 하나당 꼬리 질문은 최대 1개.
- `NORMAL` 상태에서는 `light` 질문을 우선 고르고, `STOPPED` 상태에서는 `deep`을 우선 고른다.
- 답이 비었으면 한 번 다시 묻고, 그래도 없으면 다음 질문으로 넘어간다.
- 요약은 본 질문마다 한 줄씩 `"Q: {질문} / A: {답 첫 문장}"` 형식으로 만들고 맨 앞에 주제 제목을 붙인다.

### ConversationEngine 인터페이스

```kotlin
interface ConversationEngine {
    fun start(plan: SessionPlan): Utterance
    fun onAnswer(answer: String, load: Load): Utterance?   // null이면 세션 종료
    fun summarize(): String
}
```

`GleoLlmEngine`은 1차에서 만들지 않는다. 승인 후 같은 인터페이스로 추가한다.

### 종료 명령

STT 결과를 공백 제거 후 비교해 "그만", "그만해", "종료", "끝"이면 클로징 없이 "알겠어요, 여기까지 할게요."로 끝내고 저장한다.

### 저장 스키마 (Room + SQLCipher)

| 테이블 | 필드 |
|---|---|
| `session` | `id` (UUID 문자열), `mode` (TALK_SHOW / DAILY_LOG), `topicId` (nullable), `startedAt` / `endedAt` (epoch millis), `destinationLabel`, `summary` |
| `turn` | `id` (자동 증가), `sessionId`, `speaker` (HOST / DRIVER), `text`, `createdAt` (epoch millis) |

- DB 암호 문구는 무작위 32바이트를 Android Keystore AES 키로 암호화해 `SharedPreferences`에 보관한다.
- 동의 철회 시 DB 파일과 보관 키를 삭제한다.

### 시나리오 주입 (디버그 빌드 전용)

에뮬레이터에 내비 제어가 없어서 `adb shell am broadcast -a ai.hackerton.chaasks.SCENARIO --es cmd <route|remain|arrive|answer> ...`로 이벤트와 운전자 답변을 넣는다. 리시버는 `src/debug` 소스셋에만 둔다. 영상에 주입 사실을 밝힌다.

### 화면

- 첫 실행: 동의 화면. 기어 P가 아니면 "정차 후 진행" 안내만 보인다.
- 주행 중: 상태 표시(모드, 현재 발화 텍스트, 페이싱 상태). 터치 요소 없음.
- 기어 P: 기록 목록과 요약.
- 매니페스트 액티비티에 `distractionOptimized` 메타데이터를 둔다.

## 오류 처리

- SDK 초기화 실패나 capability 미지원은 해당 신호만 비활성화하고 로그를 남긴다. 앱 전체를 멈추지 않는다.
- TTS/STT 오류는 한 번 재시도하고, 그래도 실패하면 세션을 저장하고 끝낸다.
- SDK 호출은 메인 스레드를 막지 않도록 한다. 프로브에서 LLM 초기화가 메인 스레드를 막은 사례가 있다.

## 테스트

- JVM 단위 테스트: `PacingStateMachine`(상태 전이, 해소 지연), `SessionPlanner`(질문 수, 클로징 앞당김), `ScriptedEngine`(꼬리 질문 선택, 재질문, 종료 명령, 요약), 질문 은행 JSON 파싱.
- 에뮬레이터 수동 시나리오: 목표의 장면 1~5를 시나리오 주입과 Virtual Console로 재현한다.

## 미확정

- Pleos TTS/STT 온디바이스의 실제 음성 입출력. 안 되면 `answer` 주입으로 녹화하고 영상에 밝힌다.
- NaviHelper의 경로 주행 중 남은 시간 갱신 주기와 단위.
