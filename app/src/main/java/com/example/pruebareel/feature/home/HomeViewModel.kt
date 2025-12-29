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
 * @property reelsEnabled Indica si el bloqueo de Reels está activado.
 * @property reelsLimit El número máximo de Reels consecutivos permitidos.
 * @property shortsEnabled Indica si el bloqueo de Shorts de YouTube está activado.
 * @property shortsTimeSeconds El tiempo máximo (en segundos) permitido para ver Shorts.
 */
data class HomeUiState(
    val reelsEnabled: Boolean = true,
    val reelsLimit: Int = 5,
    val shortsEnabled: Boolean = true,
    val shortsTimeSeconds: Int = 5
)

/**
 * ViewModel para la pantalla [HomeScreen].
 *
 * Se encarga de:
 * - Exponer el estado de la UI [HomeUiState] a la pantalla.
 * - Recibir eventos de la UI y delegar el guardado de la configuración al [SettingsRepository].
 * - Mapear el modelo de datos [AppSettings] a un [HomeUiState] que la UI pueda consumir.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    /**
     * Un [StateFlow] que emite el estado actual de la UI [HomeUiState].
     *
     * Se actualiza automáticamente cada vez que hay un cambio en la configuración guardada
     * gracias a que está conectado al `settingsFlow` del [SettingsRepository].
     */
    val uiState: StateFlow<HomeUiState> = settingsRepository.settingsFlow
        .map(::settingsToUi)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState()
        )

    // ---------- Acciones desde la UI ----------

    /** Actualiza el estado de activación del bloqueo de Reels. */
    fun onReelsEnabledChange(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setReelsEnabled(enabled)
    }

    /** Actualiza el límite máximo de Reels. */
    fun onReelsLimitChange(newLimit: Int) = viewModelScope.launch {
        settingsRepository.setReelsLimit(newLimit)
    }

    /** Actualiza el estado de activación del bloqueo de Shorts. */
    fun onShortsEnabledChange(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setShortsEnabled(enabled)
    }

    /** Actualiza el tiempo máximo (en segundos) para ver Shorts. */
    fun onShortsTimeChange(seconds: Int) = viewModelScope.launch {
        val clamped = seconds.coerceIn(5, 300) // Se asegura de que el valor esté en un rango razonable
        settingsRepository.setShortsTimeMs(clamped * 1_000L)
    }

    // ---------- Helpers ----------

    private fun settingsToUi(settings: AppSettings): HomeUiState {
        return HomeUiState(
            reelsEnabled = settings.reelsEnabled,
            reelsLimit = settings.reelsLimit,
            shortsEnabled = settings.shortsEnabled,
            shortsTimeSeconds = (settings.shortsTimeMs / 1000L).toInt()
        )
    }
}
