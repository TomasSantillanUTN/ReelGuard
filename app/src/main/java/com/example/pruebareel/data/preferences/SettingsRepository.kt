package com.example.pruebareel.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.pruebareel.core.lock.LockReason
import com.example.pruebareel.core.lock.LockState
import com.example.pruebareel.core.lock.LockTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extensión de Context para crear una instancia singleton de DataStore.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Repositorio para gestionar la configuración de la aplicación.
 *
 * Esta clase es la única fuente de verdad para toda la configuración. Utiliza Jetpack DataStore
 * para persistir los datos de forma asíncrona y segura.
 */
class SettingsRepository(private val context: Context) {

    companion object {
        const val MIN_LOCK_SECONDS = 5
        const val MAX_LOCK_SECONDS = 10 * 60 // 10 minutos
        const val MINUTES_IN_DAY = 24 * 60
    }

    private object Keys {
        val REELS_ENABLED = booleanPreferencesKey("reels_enabled")
        val REELS_LIMIT = intPreferencesKey("reels_limit")
        val REELS_DAILY_LIMIT = intPreferencesKey("reels_daily_limit")

        val SHORTS_ENABLED = booleanPreferencesKey("shorts_enabled")
        val SHORTS_TIME_MS = longPreferencesKey("shorts_time_ms")
        val SHORTS_DAILY_MINUTES = intPreferencesKey("shorts_daily_minutes")

        val TIKTOK_ENABLED = booleanPreferencesKey("tiktok_enabled")
        val TIKTOK_LIMIT = intPreferencesKey("tiktok_limit")
        val TIKTOK_DAILY_LIMIT = intPreferencesKey("tiktok_daily_limit")

        val LOCK_DURATION_SECONDS = intPreferencesKey("lock_duration_seconds")
        val BLOCK_NAVIGATION = booleanPreferencesKey("block_navigation")

        val QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        val QUIET_START = intPreferencesKey("quiet_start_minute")
        val QUIET_END = intPreferencesKey("quiet_end_minute")

        fun lockEnd(t: LockTarget) = longPreferencesKey("lock_end_${t.key}")
        fun lockReason(t: LockTarget) = stringPreferencesKey("lock_reason_${t.key}")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .map { p ->
            val locks = LockTarget.entries.mapNotNull { t ->
                val end = p[Keys.lockEnd(t)] ?: return@mapNotNull null
                val reason = p[Keys.lockReason(t)]
                    ?.let { runCatching { LockReason.valueOf(it) }.getOrNull() }
                    ?: LockReason.STREAK
                t to LockState(end, reason)
            }.toMap()

            AppSettings(
                reelsEnabled = p[Keys.REELS_ENABLED] ?: true,
                reelsLimit = p[Keys.REELS_LIMIT] ?: 5,
                reelsDailyLimit = p[Keys.REELS_DAILY_LIMIT] ?: 0,
                shortsEnabled = p[Keys.SHORTS_ENABLED] ?: true,
                shortsTimeMs = p[Keys.SHORTS_TIME_MS] ?: 15_000L,
                shortsDailyMinutes = p[Keys.SHORTS_DAILY_MINUTES] ?: 0,
                tiktokEnabled = p[Keys.TIKTOK_ENABLED] ?: true,
                tiktokLimit = p[Keys.TIKTOK_LIMIT] ?: 5,
                tiktokDailyLimit = p[Keys.TIKTOK_DAILY_LIMIT] ?: 0,
                lockDurationSeconds = p[Keys.LOCK_DURATION_SECONDS] ?: 10,
                blockNavigation = p[Keys.BLOCK_NAVIGATION] ?: true,
                quietHoursEnabled = p[Keys.QUIET_ENABLED] ?: false,
                quietStartMinute = p[Keys.QUIET_START] ?: (23 * 60),
                quietEndMinute = p[Keys.QUIET_END] ?: (7 * 60),
                locks = locks
            )
        }

    // ---------- Instagram ----------
    suspend fun setReelsEnabled(v: Boolean) = edit { it[Keys.REELS_ENABLED] = v }
    suspend fun setReelsLimit(v: Int) = edit { it[Keys.REELS_LIMIT] = v }
    suspend fun setReelsDailyLimit(v: Int) = edit { it[Keys.REELS_DAILY_LIMIT] = v.coerceIn(0, 999) }

    // ---------- YouTube ----------
    suspend fun setShortsEnabled(v: Boolean) = edit { it[Keys.SHORTS_ENABLED] = v }
    suspend fun setShortsTimeMs(v: Long) = edit { it[Keys.SHORTS_TIME_MS] = v }
    suspend fun setShortsDailyMinutes(v: Int) = edit { it[Keys.SHORTS_DAILY_MINUTES] = v.coerceIn(0, 999) }

    // ---------- TikTok ----------
    suspend fun setTiktokEnabled(v: Boolean) = edit { it[Keys.TIKTOK_ENABLED] = v }
    suspend fun setTiktokLimit(v: Int) = edit { it[Keys.TIKTOK_LIMIT] = v }
    suspend fun setTiktokDailyLimit(v: Int) = edit { it[Keys.TIKTOK_DAILY_LIMIT] = v.coerceIn(0, 999) }

    // ---------- Bloqueo ----------
    /** Guarda la duración del bloqueo en segundos (entre 5 s y 10 min). */
    suspend fun setLockDuration(seconds: Int) =
        edit { it[Keys.LOCK_DURATION_SECONDS] = seconds.coerceIn(MIN_LOCK_SECONDS, MAX_LOCK_SECONDS) }

    suspend fun setBlockNavigation(v: Boolean) = edit { it[Keys.BLOCK_NAVIGATION] = v }

    // ---------- Horario sin videos ----------
    suspend fun setQuietHoursEnabled(v: Boolean) = edit { it[Keys.QUIET_ENABLED] = v }

    suspend fun setQuietHours(startMinute: Int, endMinute: Int) = edit {
        it[Keys.QUIET_START] = startMinute.mod(MINUTES_IN_DAY)
        it[Keys.QUIET_END] = endMinute.mod(MINUTES_IN_DAY)
    }

    // ---------- Estado de bloqueos ----------
    /** Guarda (o borra, si [state] es null) el bloqueo de una app. */
    suspend fun setLock(target: LockTarget, state: LockState?) = edit {
        if (state == null) {
            it.remove(Keys.lockEnd(target))
            it.remove(Keys.lockReason(target))
        } else {
            it[Keys.lockEnd(target)] = state.endTimestamp
            it[Keys.lockReason(target)] = state.reason.name
        }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
