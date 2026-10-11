package com.example.pruebareel.feature.home

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pruebareel.data.preferences.AppSettings
import com.example.pruebareel.data.preferences.SettingsRepository
import com.example.pruebareel.data.stats.DayStats
import com.example.pruebareel.data.stats.StatsRepository
import com.example.pruebareel.feature.service.ReelGuardService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de los permisos que necesita la app. */
data class PermissionStatus(
    val accessibilityEnabled: Boolean = false,
    val overlayGranted: Boolean = false
)

/** Estado de la UI de [HomeScreen]. */
data class HomeUiState(
    val loaded: Boolean = false,
    val permissions: PermissionStatus = PermissionStatus(),

    val reelsEnabled: Boolean = true,
    val reelsLimit: Int = 5,
    val reelsDailyLimit: Int = 0,

    val shortsEnabled: Boolean = true,
    val shortsTimeSeconds: Int = 15,
    val shortsDailyMinutes: Int = 0,

    val tiktokEnabled: Boolean = true,
    val tiktokLimit: Int = 5,
    val tiktokDailyLimit: Int = 0,

    val lockDurationSeconds: Int = 10,
    val blockNavigation: Boolean = true,

    val quietHoursEnabled: Boolean = false,
    val quietStartMinute: Int = 23 * 60,
    val quietEndMinute: Int = 7 * 60,

    /** Últimos 7 días, de hoy hacia atrás. */
    val week: List<DayStats> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val statsRepository = StatsRepository(application)
    private val permissions = MutableStateFlow(readPermissions(application))

    val uiState: StateFlow<HomeUiState> = combine(
        settingsRepository.settingsFlow,
        statsRepository.lastWeekFlow,
        permissions
    ) { settings, week, perms -> settingsToUi(settings, week, perms) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState()
        )

    /** Se llama en onResume: el usuario puede volver de Ajustes con un permiso nuevo. */
    fun refreshPermissions() {
        permissions.value = readPermissions(getApplication())
    }

    // ---------- Acciones desde la UI ----------

    fun onReelsEnabledChange(v: Boolean) = launch { settingsRepository.setReelsEnabled(v) }
    fun onReelsLimitChange(v: Int) = launch { settingsRepository.setReelsLimit(v) }
    fun onReelsDailyLimitChange(v: Int) = launch { settingsRepository.setReelsDailyLimit(v) }

    fun onShortsEnabledChange(v: Boolean) = launch { settingsRepository.setShortsEnabled(v) }
    fun onShortsTimeChange(seconds: Int) = launch {
        settingsRepository.setShortsTimeMs(seconds.coerceIn(5, 300) * 1_000L)
    }
    fun onShortsDailyMinutesChange(v: Int) = launch { settingsRepository.setShortsDailyMinutes(v) }

    fun onTiktokEnabledChange(v: Boolean) = launch { settingsRepository.setTiktokEnabled(v) }
    fun onTiktokLimitChange(v: Int) = launch { settingsRepository.setTiktokLimit(v) }
    fun onTiktokDailyLimitChange(v: Int) = launch { settingsRepository.setTiktokDailyLimit(v) }

    fun onLockDurationChange(seconds: Int) = launch { settingsRepository.setLockDuration(seconds) }
    fun onBlockNavigationChange(v: Boolean) = launch { settingsRepository.setBlockNavigation(v) }

    fun onQuietHoursEnabledChange(v: Boolean) = launch { settingsRepository.setQuietHoursEnabled(v) }
    fun onQuietHoursChange(start: Int, end: Int) = launch { settingsRepository.setQuietHours(start, end) }

    // ---------- Helpers ----------

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun settingsToUi(s: AppSettings, week: List<DayStats>, perms: PermissionStatus) = HomeUiState(
        loaded = true,
        permissions = perms,
        reelsEnabled = s.reelsEnabled,
        reelsLimit = s.reelsLimit,
        reelsDailyLimit = s.reelsDailyLimit,
        shortsEnabled = s.shortsEnabled,
        shortsTimeSeconds = (s.shortsTimeMs / 1000L).toInt(),
        shortsDailyMinutes = s.shortsDailyMinutes,
        tiktokEnabled = s.tiktokEnabled,
        tiktokLimit = s.tiktokLimit,
        tiktokDailyLimit = s.tiktokDailyLimit,
        lockDurationSeconds = s.lockDurationSeconds,
        blockNavigation = s.blockNavigation,
        quietHoursEnabled = s.quietHoursEnabled,
        quietStartMinute = s.quietStartMinute,
        quietEndMinute = s.quietEndMinute,
        week = week
    )

    private fun readPermissions(context: Context) = PermissionStatus(
        accessibilityEnabled = isServiceEnabled(context),
        overlayGranted = Settings.canDrawOverlays(context)
    )

    /** Revisa en los ajustes del sistema si nuestro servicio de accesibilidad está activado. */
    private fun isServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ReelGuardService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }
}
