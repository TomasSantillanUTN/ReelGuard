package com.example.pruebareel.core.lock

import com.example.pruebareel.data.preferences.SettingsRepository
import kotlinx.coroutines.flow.first

/** App sobre la que se aplica un bloqueo. Cada una tiene su propio estado. */
enum class LockTarget { INSTAGRAM, YOUTUBE }

/**
 * Gestiona la lógica de bloqueo persistente para una app concreta.
 *
 * Utiliza [SettingsRepository] para persistir el estado del bloqueo y el timestamp de finalización,
 * lo que permite que el bloqueo sobreviva a reinicios del proceso o del sistema.
 */
class LockManager(
    private val repository: SettingsRepository,
    private val target: LockTarget
) {

    /** Inicia un periodo de bloqueo de [durationSeconds] segundos. */
    suspend fun startLock(durationSeconds: Int) {
        val endTime = System.currentTimeMillis() + (durationSeconds * 1000L)
        repository.setLockActive(target, true, endTime)
    }

    /** Retorna el tiempo restante del bloqueo en ms (0 si no hay bloqueo o ya expiró). */
    suspend fun getRemainingMillis(): Long {
        val settings = repository.settingsFlow.first()
        val (active, end) = when (target) {
            LockTarget.INSTAGRAM -> settings.isLockActive to settings.lockEndTimestamp
            LockTarget.YOUTUBE -> settings.ytLockActive to settings.ytLockEndTimestamp
        }
        if (!active) return 0
        val remaining = end - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    suspend fun isLockActuallyActive(): Boolean = getRemainingMillis() > 0

    suspend fun clearLock() {
        repository.setLockActive(target, false, 0)
    }
}
