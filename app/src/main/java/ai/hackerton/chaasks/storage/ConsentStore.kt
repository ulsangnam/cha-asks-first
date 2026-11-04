package ai.hackerton.chaasks.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 개인정보 수집 동의 여부. 동의는 정차 중 화면 버튼으로만 받는다. */
class ConsentStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _granted = MutableStateFlow(prefs.getBoolean(KEY_GRANTED, false))
    val granted: StateFlow<Boolean> = _granted.asStateFlow()

    fun grant() = set(true)

    fun revoke() = set(false)

    private fun set(value: Boolean) {
        prefs.edit().putBoolean(KEY_GRANTED, value).apply()
        _granted.value = value
    }

    private companion object {
        const val PREFS = "consent"
        const val KEY_GRANTED = "granted"
    }
}
