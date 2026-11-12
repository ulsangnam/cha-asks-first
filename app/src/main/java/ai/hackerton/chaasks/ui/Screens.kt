package ai.hackerton.chaasks.ui

import ai.hackerton.chaasks.app.UiState
import ai.hackerton.chaasks.app.VoiceActivity
import ai.hackerton.chaasks.conversation.Mode
import ai.hackerton.chaasks.core.Load
import ai.hackerton.chaasks.storage.SessionEntity
import ai.hackerton.chaasks.ui.theme.PleosButton
import ai.hackerton.chaasks.ui.theme.PleosTheme
import ai.hackerton.chaasks.ui.theme.PleosType
import ai.hackerton.chaasks.ui.effects.EffectMath
import ai.hackerton.chaasks.ui.effects.StageColors
import ai.hackerton.chaasks.ui.effects.VoiceBackdrop
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PagePadding = 40.dp
private val CardShape = RoundedCornerShape(32.dp)

@Composable
fun ConsentScreen(parked: Boolean, onAgree: () -> Unit) {
    val c = PleosTheme.colors
    Page {
        Column(
            modifier = Modifier.fillMaxSize().clip(CardShape).background(c.surface).padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("차가 먼저 묻는다", style = PleosType.headlineSmall, color = c.textPrimary)
            Text("주행 중 대화를 기록하려면 동의가 필요해요.", style = PleosType.bodyLarge, color = c.textSecondary)
            ConsentItem("수집 항목", "대화 텍스트, 주행 이벤트(경로 시작·도착, 방향지시등, 조향, 브레이크, 기어)")
            ConsentItem("이용 목적", "음성 대화 진행, 주행 기록과 요약 제공")
            ConsentItem("보관", "이 차량 안에만 암호화해 저장하고, 동의를 철회하면 즉시 삭제해요. 음성 원본은 저장하지 않아요.")
            ConsentItem("거부할 권리", "동의하지 않아도 차량 이용에는 영향이 없어요. 대화 기록 기능만 쓸 수 없어요.")
            Spacer(Modifier.weight(1f))
            if (parked) {
                PleosButton("동의합니다", onAgree, filled = true)
            } else {
                Text("정차(P단) 후 진행해 주세요.", style = PleosType.titleSmall, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun ConsentItem(title: String, body: String) {
    val c = PleosTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = PleosType.labelMedium, color = c.gleoSpeaker)
        Text(body, style = PleosType.bodySmall, color = c.textPrimary)
    }
}

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

/** 진행자의 현재 문장. 문장이 바뀌면 투명도만 바꿔 넘긴다. */
@Composable
private fun HostLine(state: UiState, paused: Boolean) {
    val c = PleosTheme.colors
    AnimatedContent(
        targetState = state.currentLine,
        transitionSpec = { fadeIn(tween(LINE_FADE_MS)) togetherWith fadeOut(tween(LINE_FADE_MS)) },
        label = "hostLine",
    ) { line ->
        if (line.isEmpty()) {
            Text("경로가 잡히면 먼저 말을 걸게요.", style = PleosType.titleLarge, color = c.textTertiary)
        } else {
            ReadingLine(
                text = line,
                speaking = state.voice == VoiceActivity.SPEAKING && !paused,
                startedAt = state.speakingStartedAt,
                dimmed = paused,
            )
        }
    }
}

@Composable
private fun EngineLabel(state: UiState) {
    Text(voiceLabel(state.voiceEngineReady), style = PleosType.labelSmall, color = PleosTheme.colors.textTertiary)
}

/**
 * 진행자가 읽는 동안 읽은 부분은 진하게, 남은 부분은 흐리게 보여 준다.
 * TTS 완료 이벤트가 없어 진행 정도는 글자 수 기반 추정이다. 움직이는 효과 없이 글자 색만 바뀐다.
 */
@Composable
private fun ReadingLine(text: String, speaking: Boolean, startedAt: Long, dimmed: Boolean) {
    val c = PleosTheme.colors
    var progress by remember(text, startedAt, speaking) { mutableFloatStateOf(if (speaking) 0f else 1f) }
    if (speaking) {
        LaunchedEffect(text, startedAt) {
            val total = estimatedSpeechMillis(text).toFloat()
            while (progress < 1f) {
                progress = ((System.currentTimeMillis() - startedAt) / total).coerceIn(0f, 1f)
                delay(READING_TICK_MS)
            }
        }
    }
    val read = if (dimmed) 0 else (text.length * progress).toInt()
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = c.textPrimary)) { append(text.take(read)) }
            withStyle(SpanStyle(color = c.textQuaternary)) { append(text.drop(read)) }
        },
        style = PleosType.titleLarge,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Gleo AI 색의 진행자 표시. 말하는 중엔 음성 막대, 듣는 중엔 귀 모양 고리, 멈춤엔 회색. 정지 그림이다. */
@Composable
private fun HostOrb(activity: VoiceActivity, paused: Boolean) {
    val c = PleosTheme.colors
    Box(
        modifier = Modifier.size(88.dp).clip(CircleShape).background(if (paused) c.textQuaternary else c.gleoBubble),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(44.dp)) {
            val ink = Color.White
            if (activity == VoiceActivity.LISTENING && !paused) {
                drawCircle(ink, radius = size.minDimension * 0.42f, style = Stroke(width = size.minDimension * 0.1f))
                drawCircle(ink, radius = size.minDimension * 0.14f)
            } else {
                val heights = if (activity == VoiceActivity.SPEAKING && !paused) SPEAKING_BARS else IDLE_BARS
                val barWidth = size.width / (heights.size * 2 - 1)
                heights.forEachIndexed { i, h ->
                    val barHeight = size.height * h
                    drawRoundRect(
                        color = ink,
                        topLeft = Offset(i * barWidth * 2, (size.height - barHeight) / 2),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2),
                    )
                }
            }
        }
    }
}

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

@Composable
fun RecordsScreen(state: UiState, sessions: List<SessionEntity>, onRevoke: () -> Unit) {
    val c = PleosTheme.colors
    var confirming by remember { mutableStateOf(false) }
    Page {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            StatusRow(state)
            Text("기록", style = PleosType.headlineSmall, color = c.textPrimary)
            if (sessions.isEmpty()) {
                Text("아직 기록이 없어요.", style = PleosType.bodyLarge, color = c.textSecondary)
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(sessions, key = { it.id }) { SessionCard(it) }
            }
            PleosButton("동의 철회 및 기록 삭제", { confirming = true })
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = c.backgroundPopup,
            title = { Text("기록을 모두 삭제할까요?", style = PleosType.titleSmall, color = c.textPrimary) },
            text = {
                Text("동의를 철회하면 저장된 대화와 요약이 바로 지워지고 되돌릴 수 없어요.", style = PleosType.bodySmall, color = c.textSecondary)
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onRevoke()
                }) { Text("삭제", style = PleosType.labelMedium, color = c.informativeNegative) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("취소", style = PleosType.labelMedium, color = c.textSecondary) }
            },
        )
    }
}

@Composable
private fun SessionCard(session: SessionEntity) {
    val c = PleosTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().clip(CardShape).background(c.surface).padding(36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(modeLabel(session.mode), c.buttonBasic, c.gleoSpeaker)
            Spacer(Modifier.width(16.dp))
            Text("${formatTime(session.startedAt)} · ${session.destinationLabel}", style = PleosType.labelMedium, color = c.textSecondary)
        }
        Text(SessionPreview.cardText(session.summary), style = PleosType.bodyMedium, color = c.textPrimary)
    }
}

@Composable
private fun StatusRow(state: UiState) {
    val c = PleosTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.load == Load.HIGH) {
            Chip(loadLabel(state.load), c.buttonFilled, c.onButtonFilled)
        } else {
            Chip(loadLabel(state.load), c.surface, c.textPrimary)
        }
        state.mode?.let { Chip(modeLabel(it.name), c.surface, c.gleoSpeaker) }
        state.remainMinutes?.let { Chip("도착까지 약 ${it}분", c.surface, c.textSecondary) }
    }
}

@Composable
private fun Chip(text: String, container: Color, content: Color) {
    Text(
        text = text,
        style = PleosType.labelMedium,
        color = content,
        modifier = Modifier.clip(CircleShape).background(container).padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(PleosTheme.colors.backgroundPrimary).padding(PagePadding)) {
        content()
    }
}

private fun hostStateLabel(state: UiState, paused: Boolean): String = when {
    paused -> "운전 집중 · 잠시 멈췄어요"
    state.voice == VoiceActivity.SPEAKING -> "질문을 읽는 중"
    state.voice == VoiceActivity.LISTENING -> "답을 듣는 중"
    state.sessionActive -> "대화 중"
    else -> "대기 중"
}

private fun loadLabel(load: Load): String = when (load) {
    Load.HIGH -> "운전 집중"
    Load.NORMAL -> "주행 중"
    Load.STOPPED -> "정차"
}

private fun modeLabel(mode: String): String = when (mode) {
    Mode.TALK_SHOW.name -> "토크쇼"
    Mode.DAILY_LOG.name -> "하루 기록"
    else -> mode
}

private fun voiceLabel(ready: Boolean?): String = when (ready) {
    null -> "음성 엔진 준비 중"
    true -> "Gleo 음성 엔진 · 온디바이스"
    false -> "화면 표시 모드 · 음성 엔진을 쓸 수 없어요"
}

private fun formatTime(millis: Long): String = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(millis))

/** 한국어 TTS 읽기 속도 추정(시작 지연 + 글자당 시간). 화면의 읽기 진행 표시에만 쓴다. */
private fun estimatedSpeechMillis(text: String): Long = 700L + text.length * 150L

private const val READING_TICK_MS = 120L
// 분할 화면 절반 창(약 840dp)은 이보다 좁다. 이 폭 미만이면 세로로 쌓는다.
private val WIDE_MIN_WIDTH: Dp = 1000.dp
private const val TYPE_PER_CHAR_MS = 45L
private const val LINE_FADE_MS = 300
private val SPEAKING_BARS = listOf(0.45f, 0.85f, 1f, 0.6f)
private val IDLE_BARS = listOf(0.25f, 0.25f, 0.25f, 0.25f)
