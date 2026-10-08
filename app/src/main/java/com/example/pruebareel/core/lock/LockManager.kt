package com.example.pruebareel.core.lock

import com.example.pruebareel.data.preferences.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Gestiona la lógica de bloqueo persistente.
 *
 * Utiliza [SettingsRepository] para persistir el estado del bloqueo y el timestamp de finalización,
 * lo que permite que el bloqueo sobreviva a reinicios del proceso o del sistema.
 */
class LockManager(private val repository: SettingsRepository) {

    /**
     * Inicia un periodo de bloqueo.
     *
     * @param durationSeconds La duración del bloqueo en segundos.
     */
    suspend fun startLock(durationSeconds: Int) {
        val endTime = System.currentTimeMillis() + (durationSeconds * 1000L)
        repository.setLockActive(true, endTime)
    }

    /**
     * Retorna el tiempo restante del bloqueo en milisegundos.
     * Retorna 0 si no hay un bloqueo activo o si el tiempo ya expiró.
     */
    suspend fun getRemainingMillis(): Long {
        val settings = repository.settingsFlow.first()
        if (!settings.isLockActive) return 0
        
        val remaining = settings.lockEndTimestamp - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    /**
     * Verifica si el bloqueo está realmente activo comparando el timestamp actual con el de finalización.
     */
    suspend fun isLockActuallyActive(): Boolean {
        return getRemainingMillis() > 0
    }

    /**
     * Limpia el estado de bloqueo.
     */
    suspend fun clearLock() {
        repository.setLockActive(false, 0)
    }
}
