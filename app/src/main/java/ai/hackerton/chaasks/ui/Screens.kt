package ai.hackerton.chaasks.ui

import ai.hackerton.chaasks.app.UiState
import ai.hackerton.chaasks.conversation.Mode
import ai.hackerton.chaasks.core.Load
import ai.hackerton.chaasks.storage.SessionEntity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConsentScreen(parked: Boolean, onAgree: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("차가 먼저 묻는다", style = MaterialTheme.typography.displaySmall)
        Text("주행 중 대화를 기록하려면 동의가 필요해요.", style = MaterialTheme.typography.titleLarge)
        ConsentItem("수집 항목", "대화 텍스트, 주행 이벤트(경로 시작·도착, 방향지시등, 조향, 브레이크, 기어)")
        ConsentItem("이용 목적", "음성 대화 진행, 주행 기록과 요약 제공")
        ConsentItem("보관", "이 차량 안에만 암호화해 저장하고, 동의를 철회하면 즉시 삭제해요. 음성 원본은 저장하지 않아요.")
        ConsentItem("거부할 권리", "동의하지 않아도 차량 이용에는 영향이 없어요. 대화 기록 기능만 쓸 수 없어요.")
        Spacer(Modifier.weight(1f))
        if (parked) {
            Button(onClick = onAgree) { Text("동의합니다", style = MaterialTheme.typography.titleLarge) }
        } else {
            Text(
                "정차(P단) 후 진행해 주세요.",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ConsentItem(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}

/** 주행 중 화면. 터치 요소를 두지 않는다. */
@Composable
fun DrivingScreen(state: UiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        StatusRow(state)
        Spacer(Modifier.weight(1f))
        Text(
            text = state.currentLine.ifEmpty { if (state.sessionActive) "" else "경로가 잡히면 먼저 말을 걸게요." },
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.weight(1f))
        Text(voiceLabel(state.voiceEngineReady), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun RecordsScreen(state: UiState, sessions: List<SessionEntity>, onRevoke: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatusRow(state)
        Text("기록", style = MaterialTheme.typography.headlineMedium)
        if (sessions.isEmpty()) {
            Text("아직 기록이 없어요.", style = MaterialTheme.typography.bodyLarge)
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(sessions, key = { it.id }) { SessionCard(it) }
        }
        OutlinedButton(onClick = { confirming = true }) { Text("동의 철회 및 기록 삭제") }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("기록을 모두 삭제할까요?") },
            text = { Text("동의를 철회하면 저장된 대화와 요약이 바로 지워지고 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onRevoke()
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun SessionCard(session: SessionEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${modeLabel(session.mode)} · ${formatTime(session.startedAt)} · ${session.destinationLabel}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(session.summary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun StatusRow(state: UiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(loadLabel(state.load), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(24.dp))
        state.mode?.let { Text(modeLabel(it.name), style = MaterialTheme.typography.titleLarge) }
        Spacer(Modifier.width(24.dp))
        state.remainMinutes?.let { Text("도착까지 약 ${it}분", style = MaterialTheme.typography.titleLarge) }
    }
}

private fun loadLabel(load: Load): String = when (load) {
    Load.HIGH -> "운전 집중 · 잠시 멈춤"
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
    true -> "Pleos 음성 엔진 사용 중"
    false -> "화면 표시 모드 · 음성 엔진을 쓸 수 없어요"
}

private fun formatTime(millis: Long): String = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(millis))
