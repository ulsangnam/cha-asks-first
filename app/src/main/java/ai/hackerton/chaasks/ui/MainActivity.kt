package ai.hackerton.chaasks.ui

import ai.hackerton.chaasks.app.ChaAsksApp
import ai.hackerton.chaasks.core.Gear
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
            PleosTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = PleosTheme.colors.backgroundPrimary) {
                    val state by app.orchestrator.state.collectAsStateWithLifecycle()
                    val consented by app.consent.granted.collectAsStateWithLifecycle()
                    val parked = state.gear == Gear.PARK
                    when {
                        !consented -> ConsentScreen(parked = parked, onAgree = app.consent::grant)
                        parked -> {
                            val sessions by remember(consented) { app.repository.sessions() }
                                .collectAsStateWithLifecycle(initialValue = emptyList())
                            RecordsScreen(state = state, sessions = sessions, onRevoke = app::revokeConsent)
                        }
                        else -> DrivingScreen(state = state)
                    }
                }
            }
        }
    }
}
