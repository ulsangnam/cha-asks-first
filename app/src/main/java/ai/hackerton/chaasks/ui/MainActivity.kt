package ai.hackerton.chaasks.ui

import ai.hackerton.chaasks.app.ChaAsksApp
import ai.hackerton.chaasks.core.Gear
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import ai.hackerton.chaasks.ui.theme.PleosTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 동의 전엔 동의 화면, 기어 P면 기록 화면, 주행 중엔 터치 없는 상태 화면을 보여 준다. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ChaAsksApp
        app.orchestrator.start()
        setContent {
            // Pleos 시스템 화면과 맞도록 시스템 라이트·다크 설정을 따른다.
            PleosTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = PleosTheme.colors.backgroundPrimary) {
                    val state by app.orchestrator.state.collectAsStateWithLifecycle()
                    val consented by app.consent.granted.collectAsStateWithLifecycle()
                    val parked = state.gear == Gear.PARK
                    val screen = when {
                        !consented -> Screen.CONSENT
                        parked -> Screen.RECORDS
                        else -> Screen.DRIVING
                    }
                    // 화면이 바뀔 때 투명도만 바꾼다. 이동·확대가 없어 시선을 끌지 않는다.
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS)) },
                        label = "screen",
                    ) { target ->
                        when (target) {
                            Screen.CONSENT -> ConsentScreen(parked = parked, onAgree = app.consent::grant)
                            Screen.RECORDS -> {
                                val sessions by remember(consented) { app.repository.sessions() }
                                    .collectAsStateWithLifecycle(initialValue = emptyList())
                                RecordsScreen(state = state, sessions = sessions, onRevoke = app::revokeConsent)
                            }
                            Screen.DRIVING -> DrivingScreen(state = state)
                        }
                    }
                }
            }
        }
    }
}

private enum class Screen { CONSENT, RECORDS, DRIVING }

private const val FADE_MS = 300
