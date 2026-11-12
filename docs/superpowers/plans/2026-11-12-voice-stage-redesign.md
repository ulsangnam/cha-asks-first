# 주행 화면 개편 (어두운 무대 + 음성 효과) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 주행 화면을 어두운 무대로 바꾸고, 말할 때 음파·들을 때 동심원·답 타자 효과를 넣고, 좁은(분할) 창에서도 맞게 나오게 한 뒤, 운행 중인 내비와 함께 시연 영상을 다시 찍는다.

**Architecture:** 효과의 모양은 `EffectMath`(순수 함수, 단위 테스트)로 계산하고, `VoiceEffects.kt`의 Compose Canvas가 그것을 그린다. `DrivingScreen`은 `BoxWithConstraints`로 폭 1000dp를 기준으로 넓은/좁은 배치를 고른다. `Orchestrator`/`UiState`는 바꾸지 않는다.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.10.01), JUnit4, adb, Python(시연 스크립트), Pleos Connect Emulator v2.1.0.

설계: `docs/superpowers/specs/2026-11-12-voice-stage-redesign-design.md`

공통 환경:
- JDK: PowerShell에서 `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"`
- adb: `$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"`
- 스크래치: `$s = "C:\Users\pjmpi\AppData\Local\Temp\claude\c--Hackerton\8cfd5994-f8be-429f-9c3e-a9b4655ba623\scratchpad"`
- 앱 루트: `C:\Hackerton\HMC_CAR_APP` (이하 상대 경로는 여기 기준)
- 화면 캡처는 PowerShell `>` 리디렉션이 PNG를 깨뜨리므로 `screencap -p /data/local/tmp/x.png` 후 `pull`
- 에뮬레이터에는 지금 release 빌드(자체 서명)가 깔려 있다. 디버그 빌드를 깔려면 `adb uninstall ai.hackerton.chaasks` 먼저.

---

## 파일 구조

| 파일 | 상태 | 책임 |
|---|---|---|
| `app/src/main/java/ai/hackerton/chaasks/ui/effects/EffectMath.kt` | 새로 | 파형·포락선·동심원·타자 진행 계산 (순수) |
| `app/src/test/java/ai/hackerton/chaasks/ui/effects/EffectMathTest.kt` | 새로 | 위 함수 단위 테스트 |
| `app/src/main/java/ai/hackerton/chaasks/ui/effects/VoiceEffects.kt` | 새로 | 무대 색, 효과 시계, `SpeakingWave`/`ListeningRipples`/`IdleLine`/`VoiceBackdrop` |
| `app/src/main/java/ai/hackerton/chaasks/ui/theme/PleosTheme.kt` | 수정 | `darkTheme` 인자 |
| `app/src/main/java/ai/hackerton/chaasks/ui/MainActivity.kt` | 수정 | 다크 고정 |
| `app/src/main/java/ai/hackerton/chaasks/ui/Screens.kt` | 수정 | `DrivingScreen` 넓은/좁은 배치, `DriverBubble` 타자 효과 |
| `C:\Hackerton\submission\make_demo_video.py` | 수정 (Task 7) | 분할 배치·자막 |

---

### Task 1: 스파이크 — 분할 화면과 내비 경로 (30분 한도)

코드를 바꾸지 않는다. 결과만 설계 문서에 적는다. 30분이 지나면 그때까지의 결과로 끝낸다.

**Files:**
- Modify: `docs/superpowers/specs/2026-11-12-voice-stage-redesign-design.md` (맨 끝에 "스파이크 결과" 절 추가)

- [ ] **Step 1: 내비를 띄우고 이 앱을 띄운 뒤 창 상태 기록**

```powershell
& $adb shell input tap 1097 1370          # 하단 바 내비 아이콘
Start-Sleep 3
& $adb shell am start -n ai.hackerton.chaasks/.ui.MainActivity
Start-Sleep 3
& $adb shell dumpsys activity activities | Select-String "mResumedActivity|topResumedActivity|windowingMode=" | Select-Object -First 12
```

기록할 것: 두 액티비티의 `windowingMode` 값과 화면 캡처.

- [ ] **Step 2: 분할 시도 (하나라도 되면 멈춘다)**

```powershell
# (a) 다중 창 모드로 실행 (WINDOWING_MODE_MULTI_WINDOW = 6)
& $adb shell am start --windowingMode 6 -n ai.hackerton.chaasks/.ui.MainActivity
# (b) 창 바 탭: 앱 영역 오른쪽 끝 손잡이
& $adb shell input tap 2547 718
# (c) 창 바를 길게 끌기
& $adb shell input swipe 2547 718 1700 718 800
```

매 시도 뒤 캡처로 확인한다. 세 손가락 제스처는 adb로 못 하므로, 위가 모두 실패하면 사용자에게 에뮬레이터 창에서 직접 해 볼 수 있는지 묻는다(Window Policy: "three-finger swipe or tapping the window bar").

- [ ] **Step 3: 내비 경로 시도**

```powershell
& $adb shell input tap 1166 1110          # "어디로 갈까요?" 검색창
Start-Sleep 2
& $adb shell input text "Busan"           # adb input text는 한글을 못 넣는다
& $adb shell input keyevent 66            # Enter
Start-Sleep 3
```

캡처로 검색 결과가 나오는지 확인하고, 나오면 첫 결과 → 안내 시작을 탭한다. 안내가 시작되면:

```powershell
& $adb logcat -d -v brief -s ChaAsks.Navi:I ChaAsks.Orchestrator:I | Select-Object -Last 20
```

`RouteStarted`/남은 시간 로그가 실제 NaviHelper에서 오는지 본다.

- [ ] **Step 4: 결과 기록 후 커밋**

설계 문서 끝에 추가 (꺾쇠 안은 실제 결과로 채운다):

```markdown
## 스파이크 결과 (2026-11-12)

- 분할 화면: <성공한 방법과 명령 / 실패: 시도한 것과 증상>
- 내비 경로: <성공: 검색어·탭 순서, NaviHelper 수신 여부 / 실패: 증상>
- 영상 방식: <설계 표의 세 경우 중 하나>
```

```powershell
git add docs/superpowers/specs/2026-11-12-voice-stage-redesign-design.md
git commit -m "docs: 분할 화면·내비 경로 스파이크 결과"
```

---

### Task 2: 다크 테마 고정

**Files:**
- Modify: `app/src/main/java/ai/hackerton/chaasks/ui/theme/PleosTheme.kt:34-37`
- Modify: `app/src/main/java/ai/hackerton/chaasks/ui/MainActivity.kt:23`

- [ ] **Step 1: `PleosTheme`에 인자 추가**

```kotlin
/** Pleos 색상 토큰과 Asta Sans 글자 위계를 적용한다. 기본은 시스템 라이트·다크 설정을 따른다. */
@Composable
fun PleosTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) PleosDarkColors else PleosLightColors
```

(나머지 본문은 그대로.)

- [ ] **Step 2: `MainActivity`에서 다크 고정**

```kotlin
            // 음성 효과가 빛나 보이도록 어두운 무대로 고정한다.
            PleosTheme(darkTheme = true) {
```

- [ ] **Step 3: 빌드 확인**

Run: `.\gradlew.bat :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```powershell
git add app/src/main/java/ai/hackerton/chaasks/ui/theme/PleosTheme.kt app/src/main/java/ai/hackerton/chaasks/ui/MainActivity.kt
git commit -m "feat(ui): 앱을 다크 테마로 고정"
```

---

### Task 3: `EffectMath` (TDD)

**Files:**
- Create: `app/src/test/java/ai/hackerton/chaasks/ui/effects/EffectMathTest.kt`
- Create: `app/src/main/java/ai/hackerton/chaasks/ui/effects/EffectMath.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
package ai.hackerton.chaasks.ui.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class EffectMathTest {
    @Test fun envelopeIsZeroAtEdgesAndOneInMiddle() {
        assertEquals(0f, EffectMath.waveEnvelope(0f), 1e-4f)
        assertEquals(0f, EffectMath.waveEnvelope(1f), 1e-4f)
        assertEquals(1f, EffectMath.waveEnvelope(0.5f), 1e-4f)
        assertTrue(EffectMath.waveEnvelope(0.25f) < 1f)
    }

    @Test fun waveSampleStaysInRangeAndVanishesAtEdges() {
        for (layer in 0 until EffectMath.WAVE_LAYERS) {
            assertEquals(0f, EffectMath.waveSample(0f, 1.1f, layer), 1e-4f)
            assertEquals(0f, EffectMath.waveSample(1f, 1.1f, layer), 1e-4f)
            for (i in 0..100) {
                assertTrue(abs(EffectMath.waveSample(i / 100f, 2.3f, layer)) <= 1f)
            }
        }
    }

    @Test fun phaseCompletesOneTurnPerPeriod() {
        assertEquals((2 * PI).toFloat(), EffectMath.wavePhase(EffectMath.WAVE_PERIOD_MS), 1e-3f)
    }

    @Test fun rippleGrowsAndFadesOverItsLife() {
        val born = EffectMath.ripple(0L, 0)!!
        assertEquals(0f, born.radius, 1e-4f)
        assertEquals(1f, born.alpha, 1e-4f)
        val half = EffectMath.ripple(EffectMath.RIPPLE_LIFE_MS / 2, 0)!!
        assertEquals(0.5f, half.radius, 1e-3f)
        assertEquals(0.5f, half.alpha, 1e-3f)
    }

    @Test fun laterRingsWaitForTheirTurn() {
        assertNull(EffectMath.ripple(EffectMath.RIPPLE_SPAWN_MS - 1, 1))
        assertNotNull(EffectMath.ripple(EffectMath.RIPPLE_SPAWN_MS, 1))
    }

    @Test fun ringRestartsAfterItsLife() {
        val reborn = EffectMath.ripple(EffectMath.RIPPLE_LIFE_MS, 0)!!
        assertEquals(0f, reborn.radius, 1e-4f)
    }

    @Test fun revealCountIsClampedToText() {
        assertEquals(0, EffectMath.revealCount(-5L, 10, 45L))
        assertEquals(0, EffectMath.revealCount(0L, 10, 45L))
        assertEquals(3, EffectMath.revealCount(135L, 10, 45L))
        assertEquals(10, EffectMath.revealCount(10_000L, 10, 45L))
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "ai.hackerton.chaasks.ui.effects.EffectMathTest" --console=plain`
Expected: 컴파일 실패 (`Unresolved reference: EffectMath`)

- [ ] **Step 3: 구현**

```kotlin
package ai.hackerton.chaasks.ui.effects

import kotlin.math.PI
import kotlin.math.sin

/** 음성 효과의 모양을 정하는 순수 계산. 그리기와 분리해 단위 테스트한다. */
object EffectMath {
    const val WAVE_LAYERS = 3
    /** 파형이 한 번 흘러가는 시간. 느리게 움직여 시선을 끌지 않게 한다. */
    const val WAVE_PERIOD_MS = 3_000L
    /** 동심원이 하나씩 생기는 간격과 수명. 수명/간격 = 동시에 보이는 최대 개수(3). */
    const val RIPPLE_SPAWN_MS = 1_200L
    const val RIPPLE_LIFE_MS = 3_600L
    const val RIPPLE_RINGS = (RIPPLE_LIFE_MS / RIPPLE_SPAWN_MS).toInt()

    private val WAVE_FREQUENCIES = floatArrayOf(1.5f, 2.3f, 3.1f)

    data class Ripple(val radius: Float, val alpha: Float)

    /** 가로 위치 x(0..1)의 진폭 배율. 양 끝 0, 가운데 1. */
    fun waveEnvelope(x: Float): Float {
        val s = sin(PI * x.coerceIn(0f, 1f)).toFloat()
        return s * s
    }

    /** layer번째 파형의 x(0..1) 위치 값(-1..1). phase는 라디안. */
    fun waveSample(x: Float, phase: Float, layer: Int): Float {
        val wave = sin(2 * PI * WAVE_FREQUENCIES[layer] * x + phase + layer * 1.3f).toFloat()
        return waveEnvelope(x) * wave
    }

    /** 경과 시간을 파형 위상(라디안)으로 바꾼다. */
    fun wavePhase(elapsedMs: Long): Float = (2 * PI * elapsedMs / WAVE_PERIOD_MS).toFloat()

    /** ring번째 동심원의 상태. 처음 생기기 전이면 null. radius는 0..1 비율. */
    fun ripple(elapsedMs: Long, ring: Int): Ripple? {
        val sinceBirth = elapsedMs - ring * RIPPLE_SPAWN_MS
        if (sinceBirth < 0) return null
        val progress = (sinceBirth % RIPPLE_LIFE_MS).toFloat() / RIPPLE_LIFE_MS
        return Ripple(radius = progress, alpha = 1f - progress)
    }

    /** 타자 효과로 지금까지 보여 줄 글자 수. */
    fun revealCount(elapsedMs: Long, length: Int, perCharMs: Long): Int =
        (elapsedMs / perCharMs).toInt().coerceIn(0, length)
}
```

- [ ] **Step 4: 통과 확인**

Run: `.\gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL` (기존 27개 + 새 7개)

- [ ] **Step 5: 커밋**

```powershell
git add app/src/main/java/ai/hackerton/chaasks/ui/effects/EffectMath.kt app/src/test/java/ai/hackerton/chaasks/ui/effects/EffectMathTest.kt
git commit -m "feat(ui): 음성 효과 계산(파형, 동심원, 타자 진행)"
```

---

### Task 4: `VoiceEffects` 그리기

**Files:**
- Create: `app/src/main/java/ai/hackerton/chaasks/ui/effects/VoiceEffects.kt`

- [ ] **Step 1: 구현**

```kotlin
package ai.hackerton.chaasks.ui.effects

import ai.hackerton.chaasks.app.VoiceActivity
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** 무대 배경과 효과에 쓰는 앱 전용 색. Pleos 가이드 토큰에는 없는 값이다. */
object StageColors {
    val Top = Color(0xFF141B33)
    val Bottom = Color(0xFF0B1020)
    val Waves = listOf(Color(0xFF8896D3), Color(0xFF9BA8E2), Color(0xFF6FA8FF))
    val Ripple = Color(0xFF4FC3D9)
    val Frozen = Color(0x52FFFFFF)
}

/** 진행자 상태에 맞는 배경 효과. frozen이면 지금 모양 그대로 멈추고 회색이 된다. */
@Composable
fun VoiceBackdrop(voice: VoiceActivity, frozen: Boolean, modifier: Modifier = Modifier) {
    when (voice) {
        VoiceActivity.SPEAKING -> SpeakingWave(frozen, modifier)
        VoiceActivity.LISTENING -> ListeningRipples(frozen, modifier)
        VoiceActivity.IDLE -> IdleLine(modifier)
    }
}

/** 프레임마다 흐르는 경과 시간. 멈췄다 다시 움직이면 멈춘 지점부터 이어 간다. */
@Composable
private fun rememberEffectClock(frozen: Boolean): Long {
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(frozen) {
        if (frozen) return@LaunchedEffect
        val start = withFrameMillis { it } - elapsed
        while (true) {
            elapsed = withFrameMillis { it } - start
        }
    }
    return elapsed
}

@Composable
fun SpeakingWave(frozen: Boolean, modifier: Modifier = Modifier) {
    val elapsed = rememberEffectClock(frozen)
    Canvas(modifier) {
        val phase = EffectMath.wavePhase(elapsed)
        val mid = size.height / 2
        for (layer in 0 until EffectMath.WAVE_LAYERS) {
            val amplitude = mid * (0.9f - layer * 0.22f)
            val path = Path()
            var x = 0f
            while (x <= size.width) {
                val y = mid + EffectMath.waveSample(x / size.width, phase, layer) * amplitude
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += WAVE_STEP_PX
            }
            val color = if (frozen) StageColors.Frozen else StageColors.Waves[layer]
            // 굵고 옅은 선 위에 가늘고 진한 선을 겹쳐 빛이 번지는 느낌을 낸다.
            drawPath(path, color.copy(alpha = 0.12f), style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, color.copy(alpha = 0.40f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
fun ListeningRipples(frozen: Boolean, modifier: Modifier = Modifier) {
    val elapsed = rememberEffectClock(frozen)
    Canvas(modifier) {
        val maxRadius = size.minDimension / 2
        for (ring in 0 until EffectMath.RIPPLE_RINGS) {
            val ripple = EffectMath.ripple(elapsed, ring) ?: continue
            val color = if (frozen) StageColors.Frozen else StageColors.Ripple
            drawCircle(
                color = color.copy(alpha = 0.45f * ripple.alpha),
                radius = maxRadius * (0.15f + 0.85f * ripple.radius),
                style = Stroke(width = 4.dp.toPx()),
            )
        }
    }
}

@Composable
fun IdleLine(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawLine(
            color = StageColors.Waves[1].copy(alpha = 0.25f),
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

private const val WAVE_STEP_PX = 6f
```

- [ ] **Step 2: 빌드 확인**

Run: `.\gradlew.bat :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 커밋**

```powershell
git add app/src/main/java/ai/hackerton/chaasks/ui/effects/VoiceEffects.kt
git commit -m "feat(ui): 음파·동심원 배경 효과"
```

---

### Task 5: `DrivingScreen` 넓은/좁은 배치와 답 타자 효과

**Files:**
- Modify: `app/src/main/java/ai/hackerton/chaasks/ui/Screens.kt` (`DrivingScreen` 92~130행, `DriverBubble` 191~204행 교체, import·상수 추가)

- [ ] **Step 1: import 추가** (기존 import 목록에 알파벳 순으로 합친다)

```kotlin
import ai.hackerton.chaasks.ui.effects.EffectMath
import ai.hackerton.chaasks.ui.effects.StageColors
import ai.hackerton.chaasks.ui.effects.VoiceBackdrop
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
```

- [ ] **Step 2: `DrivingScreen`을 다음으로 교체**

```kotlin
/** 주행 중 화면. 터치 요소 없이 진행자가 지금 무엇을 하는지만 보여 준다. 분할 화면처럼 좁으면 세로로 쌓는다. */
@Composable
fun DrivingScreen(state: UiState) {
    val paused = state.load == Load.HIGH
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(StageColors.Top, StageColors.Bottom)))
            .padding(PagePadding),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= WIDE_MIN_WIDTH) WideStage(state, paused) else NarrowStage(state, paused)
        }
    }
}

@Composable
private fun WideStage(state: UiState, paused: Boolean) {
    Box(Modifier.fillMaxSize()) {
        VoiceBackdrop(state.voice, paused, Modifier.fillMaxWidth().height(360.dp).align(Alignment.Center))
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                    StatusRow(state)
                    HostHeader(state, paused)
                }
                // 운전자의 직전 답을 진행자의 다음 말 위에 두어 "내 답 → 진행자의 다음 질문" 순서로 읽히게 한다.
                if (state.driverLine.isNotEmpty()) DriverBubble(state.driverLine, Modifier.widthIn(max = 720.dp))
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
                HostLine(state, paused)
            }
            EngineLabel(state)
        }
    }
}

@Composable
private fun NarrowStage(state: UiState, paused: Boolean) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        StatusRow(state)
        if (state.driverLine.isNotEmpty()) DriverBubble(state.driverLine, Modifier.fillMaxWidth())
        HostHeader(state, paused)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            VoiceBackdrop(state.voice, paused, Modifier.fillMaxWidth().height(240.dp))
            HostLine(state, paused)
        }
        EngineLabel(state)
    }
}

@Composable
private fun HostHeader(state: UiState, paused: Boolean) {
    val c = PleosTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        HostOrb(activity = state.voice, paused = paused)
        Spacer(Modifier.width(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("진행자", style = PleosType.titleSmall, color = c.gleoSpeaker)
            Text(hostStateLabel(state, paused), style = PleosType.bodyMedium, color = c.textSecondary)
        }
    }
}

@Composable
private fun HostLine(state: UiState, paused: Boolean) {
    val c = PleosTheme.colors
    if (state.currentLine.isEmpty()) {
        Text("경로가 잡히면 먼저 말을 걸게요.", style = PleosType.titleLarge, color = c.textTertiary)
    } else {
        ReadingLine(
            text = state.currentLine,
            speaking = state.voice == VoiceActivity.SPEAKING && !paused,
            startedAt = state.speakingStartedAt,
            dimmed = paused,
        )
    }
}

@Composable
private fun EngineLabel(state: UiState) {
    Text(voiceLabel(state.voiceEngineReady), style = PleosType.labelSmall, color = PleosTheme.colors.textTertiary)
}
```

- [ ] **Step 3: `DriverBubble`을 다음으로 교체**

```kotlin
/** 운전자 답. 새 답이 오면 인식되는 것처럼 한 글자씩 나타난다. 같은 문장이면 다시 쓰지 않는다. */
@Composable
private fun DriverBubble(text: String, modifier: Modifier = Modifier) {
    val c = PleosTheme.colors
    var shown by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        val start = System.currentTimeMillis()
        while (shown < text.length) {
            delay(TYPE_PER_CHAR_MS)
            shown = EffectMath.revealCount(System.currentTimeMillis() - start, text.length, TYPE_PER_CHAR_MS)
        }
    }
    Column(
        modifier = modifier.clip(CardShape).background(c.surface.copy(alpha = 0.72f))
            .padding(horizontal = 36.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("나", style = PleosType.labelMedium, color = c.textTertiary)
        Text(text.take(shown), style = PleosType.bodyLarge, color = c.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
```

- [ ] **Step 4: 상수 추가** (파일 끝 `READING_TICK_MS` 옆)

```kotlin
// 분할 화면 절반 창은 이보다 좁다. 이 폭 미만이면 세로로 쌓는다.
private val WIDE_MIN_WIDTH: Dp = 1000.dp
private const val TYPE_PER_CHAR_MS = 45L
```

- [ ] **Step 5: 빌드와 테스트**

Run: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`. 사용하지 않게 된 import가 경고로 남으면 지운다.

- [ ] **Step 6: 커밋**

```powershell
git add app/src/main/java/ai/hackerton/chaasks/ui/Screens.kt
git commit -m "feat(ui): 어두운 무대 주행 화면, 좁은 창 배치, 답 타자 효과"
```

---

### Task 6: 에뮬레이터 확인

**Files:** 없음 (확인만. 결과는 설계 문서 "확인" 절 아래에 적는다)

- [ ] **Step 1: 디버그 빌드 설치**

```powershell
& $adb uninstall ai.hackerton.chaasks
& $adb install app\build\outputs\apk\debug\app-debug.apk
```

- [ ] **Step 2: 넓은 창 캡처**

`$s\ui_check.py`(동의 → D단 → 부산 90분 → 캡처 → 방향지시등)를 `python "$s\ui_check.py" $s`로 실행한다. 파일이 지워졌으면 `C:\Hackerton\submission\make_demo_video.py`의 `adb`, `scenario`, `tap_text`를 import해 같은 순서로 다시 만든다. 확인할 화면:
- 말하는 중: 남색 배경, 음파가 질문 뒤에서 흐름, 읽은 부분이 밝음
- 듣는 중: 청록 동심원
- 답 주입 직후: 말풍선 글자가 한 글자씩 늘어남 (0.5초 간격으로 두 번 캡처해 비교)
- 방향지시등 켜짐: 효과가 회색으로 멈춤, 질문이 흐려짐

- [ ] **Step 3: 좁은 창 캡처**

Task 1에서 분할이 됐으면 분할 상태로, 아니면:

```powershell
& $adb shell wm size 1800x1440      # 앱 영역 폭이 1000dp 미만이 되도록
# Step 2의 캡처 순서를 다시 실행
& $adb shell wm size reset          # 반드시 되돌린다
```

앱 영역이 좁은 배치(세로 쌓기)로 바뀌었는지 확인한다. 1800으로도 넓은 배치면 1600으로 줄인다.

- [ ] **Step 4: 앱이 뒤에 있을 때 대화가 이어지는지**

```powershell
& $adb logcat -c
& $adb shell am broadcast -n ai.hackerton.chaasks/.demo.ScenarioReceiver --es cmd gear --es gear DRIVE
& $adb shell am broadcast -n ai.hackerton.chaasks/.demo.ScenarioReceiver --es cmd route --es dest Busan --ei min 90
Start-Sleep 12
& $adb shell input tap 1097 1370    # 내비를 띄워 이 앱을 뒤로 보낸다
& $adb shell am broadcast -n ai.hackerton.chaasks/.demo.ScenarioReceiver --es cmd answer --es text "'좋아요'"
Start-Sleep 10
& $adb logcat -d -v brief -s ChaAsks.Orchestrator:I | Select-Object -Last 10
```

Expected: 앱이 뒤에 있어도 답 주입 뒤 `host QUESTION:` 로그가 새로 찍힌다. 결과(이어짐/끊김)를 설계 문서와 `CLAUDE.md` "에뮬레이터 실측 결과"에 한 줄로 적는다.

- [ ] **Step 5: 커밋** (설계 문서만. CLAUDE.md는 gitignore)

```powershell
git add docs/superpowers/specs/2026-11-12-voice-stage-redesign-design.md
git commit -m "docs: 주행 화면 개편 에뮬레이터 확인 결과"
```

---

### Task 7: 시연 영상 재녹화 (Task 1 결과에 따라)

**Files:**
- Modify: `C:\Hackerton\submission\make_demo_video.py` (`record_talk_show` 앞에 분할 준비, 자막)

- [ ] **Step 1: 분할 준비 함수 추가 (분할 성공 시에만)**

Task 1에서 성공한 명령을 그대로 옮긴다. 예: (a)가 성공했다면

```python
def arrange_split() -> None:
    """내비와 이 앱을 1:1 분할로 띄운다 (Task 1 스파이크에서 확인한 방법)."""
    adb("shell", "input", "tap", "1097", "1370")  # 내비
    time.sleep(3)
    adb("shell", "am", "start", "--windowingMode", "6", "-n", f"{PKG}/.ui.MainActivity")
    time.sleep(3)
```

`record_talk_show()`의 `with Scene(...)` 바로 앞에서 `arrange_split()`을 부른다. 내비 경로가 됐다면 같은 함수에서 Task 1 Step 3의 탭 순서를 이어서 실행한다. NaviHelper 수신까지 확인됐을 때만 `s.sub("[시연 주입] 기어 D, 부산까지 90분 경로")`를 `s.sub("내비 안내 시작 → NaviHelper가 경로·남은 시간을 받음")`으로 바꾸고 `scenario("route", ...)` 호출을 지운다.

- [ ] **Step 2: 분할 실패 시 자막만 추가**

`record_talk_show()`의 첫 `s.sub(...)` 다음에:

```python
        s.sub("실차에서는 내비 옆 분할 화면으로 함께 보입니다 (에뮬레이터는 단독 화면)")
        time.sleep(3)
```

- [ ] **Step 3: release 빌드로 재녹화**

```powershell
.\gradlew.bat :app:assembleRelease --console=plain
& $adb uninstall ai.hackerton.chaasks
& $adb install app\build\outputs\apk\release\app-release.apk
Copy-Item C:\Hackerton\submission\video\cha-asks-first-demo.mp4 C:\Hackerton\submission\archive\cha-asks-first-demo-before-stage.mp4
Set-Location C:\Hackerton\submission; python make_demo_video.py
```

Expected: `done: ...cha-asks-first-demo.mp4`. 접촉 시트로 다섯 장면과 효과를 확인한다:

```powershell
ffmpeg -y -loglevel error -i C:\Hackerton\submission\video\cha-asks-first-demo.mp4 -vf "fps=1/12,scale=640:-1,tile=4x4" -frames:v 1 "$s\sheet3.png"
```

---

### Task 8: 제출물 갱신

**Files:**
- Modify: `apk/TeamCAF_cha-asks-first_release.apk` (교체)

- [ ] **Step 1: APK 교체와 커밋**

```powershell
Copy-Item app\build\outputs\apk\release\app-release.apk apk\TeamCAF_cha-asks-first_release.apk -Force
git add apk/TeamCAF_cha-asks-first_release.apk
git commit -m "build: 주행 화면 개편 release APK"
```

- [ ] **Step 2: 사용자 확인 후 푸시하고 해시 알리기**

```powershell
git push origin main
git rev-parse HEAD
```

새 해시를 사용자에게 "접수 메일용 최종 커밋"으로 알리고, `C:\Hackerton\HANDOFF.md`의 커밋 해시를 갱신한다.
