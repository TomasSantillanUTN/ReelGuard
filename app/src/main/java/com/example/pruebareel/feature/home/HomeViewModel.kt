package com.example.pruebareel.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pruebareel.data.preferences.AppSettings
import com.example.pruebareel.data.preferences.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Representa el estado de la interfaz de usuario para la pantalla [HomeScreen].
 *
 * @property lockDurationSeconds La duración del periodo de bloqueo en segundos (máx. 10 min).
 * @property blockNavigation Si la pantalla de bloqueo impide usar los botones del sistema.
 * @property loaded `true` cuando ya se leyó la configuración guardada.
 */
data class HomeUiState(
    val reelsEnabled: Boolean = true,
    val reelsLimit: Int = 5,
    val shortsEnabled: Boolean = true,
    val shortsTimeSeconds: Int = 5,
    val lockDurationSeconds: Int = 10,
    val blockNavigation: Boolean = true,
    val loaded: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    val uiState: StateFlow<HomeUiState> = settingsRepository.settingsFlow
        .map(::settingsToUi)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState()
        )

    // ---------- Acciones desde la UI ----------

    fun onReelsEnabledChange(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setReelsEnabled(enabled)
    }

    fun onReelsLimitChange(newLimit: Int) = viewModelScope.launch {
        settingsRepository.setReelsLimit(newLimit)
    }

    fun onShortsEnabledChange(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setShortsEnabled(enabled)
    }

    fun onShortsTimeChange(seconds: Int) = viewModelScope.launch {
        val clamped = seconds.coerceIn(5, 300)
        settingsRepository.setShortsTimeMs(clamped * 1_000L)
    }

    /** Actualiza la duración del periodo de bloqueo (se limita a 5 s – 10 min). */
    fun onLockDurationChange(seconds: Int) = viewModelScope.launch {
        settingsRepository.setLockDuration(seconds)
    }

    /** Activa/desactiva el bloqueo de los botones del sistema durante la cuenta regresiva. */
    fun onBlockNavigationChange(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setBlockNavigation(enabled)
    }

    // ---------- Helpers ----------

    private fun settingsToUi(settings: AppSettings): HomeUiState {
        return HomeUiState(
            reelsEnabled = settings.reelsEnabled,
            reelsLimit = settings.reelsLimit,
            shortsEnabled = settings.shortsEnabled,
            shortsTimeSeconds = (settings.shortsTimeMs / 1000L).toInt(),
            lockDurationSeconds = settings.lockDurationSeconds,
            blockNavigation = settings.blockNavigation,
            loaded = true
        )
    }
}
