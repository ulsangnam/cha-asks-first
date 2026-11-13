# 차가 먼저 묻는다

차량 이벤트가 먼저 말을 거는 AI 드라이브 파트너. Pleos Connect 차량용 앱으로, 2026 현대자동차그룹 차량용 앱 개발 공모전(일반 부문) 출품작이다.

운전자는 주행 중 화면을 만지지 않고 음성으로만 대화한다. 남은 주행 시간이 대화 방식을 고른다.

- **장거리(30분 이상) → 토크쇼:** 진행자가 오프닝, 본 질문 3~5개와 꼬리 질문, 클로징으로 운전자를 인터뷰한다. 분량은 도착 시점에 맞춘다.
- **단거리 → 하루 기록:** 도착하면 "오늘 하루 한 줄로 남겨볼까요?"라고 묻고 답을 기록한다.
- **주행 부하 페이싱:** 방향지시등, 큰 조향, 브레이크가 들어오면 즉시 말을 멈추고, 해소되면 같은 문장부터 다시 말한다.

## 1차 시연 범위 (2026-11-15 접수)

| 기능 | 상태 |
|---|---|
| 장거리 토크쇼 1회분 | 동작 (대본 엔진) |
| 남은 시간 기반 모드 전환 | 동작 |
| 방향지시등·조향·브레이크 페이싱 | 동작 |
| 도착 시 한 줄 일기 | 동작 |
| "그만" 한마디로 종료 | 동작 |
| 정차(P단) 중 기록·요약 열람 | 동작 |
| 개인정보 동의(정차 중 버튼), 철회 시 즉시 삭제 | 동작 |
| 주유·급정거 감지 기록 | 목업 예정 |

## 구조

```
Vehicle SDK ─┐
NaviHelper ──┼─> DrivingEvent ─> Orchestrator ─┬─> PacingStateMachine (HIGH / NORMAL / STOPPED)
시연 주입 ───┘                                  ├─> ConversationEngine (1차: ScriptedEngine)
                                               ├─> VoiceIO (Gleo TTS/STT 온디바이스)
                                               └─> 암호화 로컬 DB (Room + SQLCipher)
```

| 패키지 | 역할 |
|---|---|
| `core` | 주행 이벤트, 기어, 주행 부하 타입 |
| `pacing` | 차량 신호로 주행 부하를 판단하는 상태 머신 |
| `conversation` | 세션 구성, 대본 엔진, 질문 은행 |
| `event` | Pleos Vehicle·NaviHelper 어댑터 |
| `voice` | Gleo TTS/STT 어댑터, 음성 엔진이 없을 때의 화면 표시 모드 |
| `storage` | Room + SQLCipher 저장소, Keystore로 보호하는 DB 키, 동의 상태 |
| `app` | 이벤트·페이싱·대화·저장을 묶는 오케스트레이터 |
| `ui` | 동의 화면, 주행 중 화면(터치 요소 없음), 기록 화면 |

## 사용한 Pleos SDK

| SDK | 버전 | 쓰는 곳 |
|---|---|---|
| Vehicle | 2.3.2 | 방향지시등, 조향각, 브레이크, 기어 |
| NaviHelper | 3.0.5 | 경로 시작, 남은 시간, 도착 |
| Gleo TextToSpeech | 3.3.1 | 진행자 발화 (온디바이스) |
| Gleo SpeechToText | 3.3.1 | 운전자 답 인식 (온디바이스) |
| Gleo LLM | 2.1.3.2 | 승인 후 적용 예정 (아래 "현재 한계") |

## 실행

### APK

release APK: [`apk/TeamCAF_cha-asks-first_release.apk`](apk/TeamCAF_cha-asks-first_release.apk)

```bash
adb install -r apk/TeamCAF_cha-asks-first_release.apk
adb shell am start -n ai.hackerton.chaasks/.ui.MainActivity
```

첫 화면은 개인정보 동의다. 기어가 P일 때만 "동의합니다" 버튼이 열린다(에뮬레이터 기본값이 P). 동의하면 기록 화면이 나오고, 경로가 시작되면 진행자가 먼저 말을 건다. 경로는 아래 "시연 시나리오"처럼 주입한다.

### 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (`RELEASE.release_connect_v2.1.0`), Android 14 (API 34) |
| 화면 규격 | 2560 × 1440, 밀도 160 (mdpi) |
| AVD | RAM 4GB, cold boot |
| 개발 도구 | Android Studio, JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.9.1 |

### 필요한 설정

- 에뮬레이터 설치는 [공식 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)를 따른다. AVD는 cold boot로 설정해야 차량 신호(VHAL)가 동작한다.
- 별도 계정이나 API 키는 필요 없다. Gleo TTS/STT를 온디바이스 모드로 써서 앱 등록 없이 동작한다.
- 에뮬레이터의 운전자 계정은 사용자 10이다. 앱 데이터를 지우려면 `adb shell pm clear --user current ai.hackerton.chaasks`를 쓴다.

### 소스에서 빌드

```bash
./gradlew :app:testDebugUnitTest   # 단위 테스트 27개
./gradlew :app:assembleDebug       # 서명 키 없이 바로 설치할 수 있는 빌드
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`./gradlew :app:assembleRelease`는 저장소에 없는 `keystore.properties`(storeFile, storePassword, keyAlias, keyPassword)로 서명한다. 이 파일이 없으면 서명되지 않은 release APK가 나온다.

### 시연 시나리오

실제 내비 경로로도 시작할 수 있다. 앱을 띄운 뒤 에뮬레이터 내비 검색창에서 "현대자동차"를 검색해 "목적지로"를 누르면, NaviHelper가 경로 시작과 남은 시간을 보내고 30분 이상이면 토크쇼가 열린다. 지도 타일은 에뮬레이터에서 비어 보인다.

에뮬레이터 내비는 도착·남은 시간을 원하는 값으로 바꿀 수 없어, 시연용 수신기로 경로·도착·답변을 주입한다. 수신기는 `android.permission.DUMP`를 요구하므로 adb 셸에서만 보낼 수 있고 다른 앱은 보낼 수 없다. 시연 영상에서도 주입 사실을 밝힌다.

```bash
R="adb shell am broadcast -n ai.hackerton.chaasks/.demo.ScenarioReceiver"
$R --es cmd gear --es gear DRIVE
$R --es cmd route --es dest 부산 --ei min 90          # 토크쇼 시작
$R --es cmd answer --es text "'아버지가 타던 중고차였어요'"   # 운전자 답 (꼬리 질문 유도)
$R --es cmd signal --ez on true                        # 방향지시등: 말 멈춤
$R --es cmd signal --ez on false                       # 1.5초 뒤 같은 문장 재개
$R --es cmd answer --es text "'그만'"                   # 종료
$R --es cmd route --es dest 회사 --ei min 15 && $R --es cmd arrive   # 하루 기록
$R --es cmd gear --es gear PARK                        # 기록 화면
```

방향지시등과 기어는 Pleos Virtual Console(`adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088`)로도 넣을 수 있다.

## 현재 한계

- **Gleo LLM 미적용:** 에뮬레이터에서 온디바이스 모드는 응답이 없고, 서버 모드는 인증 후 생성 요청이 HTTP 400으로 거부된다. 개발자 프로젝트의 SDK가 Coming Soon 상태라 Pleos에 문의 중이다. 그동안은 질문 은행과 키워드 규칙으로 동작하는 대본 엔진을 쓰고, 승인 후 같은 `ConversationEngine` 인터페이스로 교체한다.
- **TTS 완료 이벤트 없음:** 온디바이스 TTS는 오디오를 출력하지만 완료 이벤트가 오지 않아, 글자 수 기반 시간으로 다음 단계로 넘어간다.
- **STT 실제 인식 미검증:** 시연은 답변 주입으로 진행한다.
- 꼬리 질문은 미리 쓴 문장을 답변 키워드로 고르는 방식이다.

## 프라이버시

- 자체 서버를 두지 않는다. 대화는 차량 안에서만 처리하고 저장한다.
- 음성 원본은 저장하지 않는다. 대화 텍스트만 SQLCipher로 암호화해 저장한다.
- 동의는 정차 중 화면 버튼으로만 받고, 철회하면 기록과 암호 키를 즉시 지운다.

## 문서

- [설계](docs/superpowers/specs/2026-11-03-cha-asks-first-app-design.md), [구현 계획](docs/superpowers/plans/2026-11-03-cha-asks-first-app.md)
- `spikes/sdk-probe`: SDK 동작을 확인한 시험 앱
