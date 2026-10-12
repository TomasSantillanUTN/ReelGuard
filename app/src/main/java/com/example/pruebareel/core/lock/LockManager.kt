package com.example.pruebareel.core.lock

import com.example.pruebareel.data.preferences.SettingsRepository
import kotlinx.coroutines.flow.first

/** App sobre la que se aplica un bloqueo. Cada una tiene su propio estado. */
enum class LockTarget(val key: String) {
    INSTAGRAM("ig"),
    YOUTUBE("yt"),
    TIKTOK("tt")
}

/** Motivo del bloqueo. Define cuánto dura y si puede bloquear los botones del sistema. */
enum class LockReason {
    /** Se llegó al límite de videos seguidos / tiempo seguido. Dura lo configurado. */
    STREAK,
    /** Se llegó al límite total del día. Dura hasta las 00:00. */
    DAILY_LIMIT,
    /** Horario sin videos. Dura hasta que termina el horario. */
    QUIET_HOURS;

    /**
     * Los bloqueos largos (límite diario y horario) nunca bloquean los botones del sistema,
     * aunque esté marcada la casilla: si no, el celular podría quedar inutilizable por horas.
     */
    val canBlockNavigation: Boolean get() = this == STREAK
}

/** Estado persistido de un bloqueo. */
data class LockState(val endTimestamp: Long, val reason: LockReason) {
    fun remainingMillis(now: Long = System.currentTimeMillis()): Long =
        (endTimestamp - now).coerceAtLeast(0)

    fun isActive(now: Long = System.currentTimeMillis()): Boolean = endTimestamp > now
}

/**
 * Gestiona la lógica de bloqueo persistente.
 *
 * Utiliza [SettingsRepository] para persistir el fin y el motivo del bloqueo de cada app,
 * lo que permite que el bloqueo sobreviva a reinicios del proceso o del sistema.
 */
class LockManager(private val repository: SettingsRepository) {

    suspend fun startLock(target: LockTarget, endTimestamp: Long, reason: LockReason) {
        repository.setLock(target, LockState(endTimestamp, reason))
    }

    suspend fun getLock(target: LockTarget): LockState? =
        repository.settingsFlow.first().locks[target]

    suspend fun clearLock(target: LockTarget) {
        repository.setLock(target, null)
    }
}
