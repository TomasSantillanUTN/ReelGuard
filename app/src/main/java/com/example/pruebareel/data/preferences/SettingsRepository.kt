package com.example.pruebareel.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    }

    private object Keys {
        val REELS_ENABLED = booleanPreferencesKey("reels_enabled")
        val REELS_LIMIT = intPreferencesKey("reels_limit")
        val SHORTS_ENABLED = booleanPreferencesKey("shorts_enabled")
        val SHORTS_TIME_MS = longPreferencesKey("shorts_time_ms")
        val LOCK_DURATION_SECONDS = intPreferencesKey("lock_duration_seconds")
        val BLOCK_NAVIGATION = booleanPreferencesKey("block_navigation")

        // Bloqueo de Instagram (se mantienen los nombres originales para no perder datos guardados)
        val IS_LOCK_ACTIVE = booleanPreferencesKey("is_lock_active")
        val LOCK_END_TIMESTAMP = longPreferencesKey("lock_end_timestamp")

        // Bloqueo de YouTube
        val YT_IS_LOCK_ACTIVE = booleanPreferencesKey("yt_is_lock_active")
        val YT_LOCK_END_TIMESTAMP = longPreferencesKey("yt_lock_end_timestamp")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .map { preferences ->
            AppSettings(
                reelsEnabled = preferences[Keys.REELS_ENABLED] ?: true,
                reelsLimit = preferences[Keys.REELS_LIMIT] ?: 5,
                shortsEnabled = preferences[Keys.SHORTS_ENABLED] ?: true,
                shortsTimeMs = preferences[Keys.SHORTS_TIME_MS] ?: 15_000L,
                lockDurationSeconds = preferences[Keys.LOCK_DURATION_SECONDS] ?: 10,
                blockNavigation = preferences[Keys.BLOCK_NAVIGATION] ?: true,
                isLockActive = preferences[Keys.IS_LOCK_ACTIVE] ?: false,
                lockEndTimestamp = preferences[Keys.LOCK_END_TIMESTAMP] ?: 0L,
                ytLockActive = preferences[Keys.YT_IS_LOCK_ACTIVE] ?: false,
                ytLockEndTimestamp = preferences[Keys.YT_LOCK_END_TIMESTAMP] ?: 0L
            )
        }

    suspend fun setReelsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REELS_ENABLED] = enabled }
    }

    suspend fun setReelsLimit(limit: Int) {
        context.dataStore.edit { it[Keys.REELS_LIMIT] = limit }
    }

    suspend fun setShortsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHORTS_ENABLED] = enabled }
    }

    suspend fun setShortsTimeMs(timeMs: Long) {
        context.dataStore.edit { it[Keys.SHORTS_TIME_MS] = timeMs }
    }

    /** Guarda la duración del bloqueo en segundos (entre 5 s y 10 min). */
    suspend fun setLockDuration(seconds: Int) {
        context.dataStore.edit {
            it[Keys.LOCK_DURATION_SECONDS] = seconds.coerceIn(MIN_LOCK_SECONDS, MAX_LOCK_SECONDS)
        }
    }

    /** Guarda si la pantalla de bloqueo debe impedir el uso de los botones del sistema. */
    suspend fun setBlockNavigation(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BLOCK_NAVIGATION] = enabled }
    }

    /** Guarda el estado del bloqueo persistente para la app indicada. */
    suspend fun setLockActive(target: LockTarget, active: Boolean, endTimestamp: Long = 0L) {
        context.dataStore.edit {
            when (target) {
                LockTarget.INSTAGRAM -> {
                    it[Keys.IS_LOCK_ACTIVE] = active
                    it[Keys.LOCK_END_TIMESTAMP] = endTimestamp
                }
                LockTarget.YOUTUBE -> {
                    it[Keys.YT_IS_LOCK_ACTIVE] = active
                    it[Keys.YT_LOCK_END_TIMESTAMP] = endTimestamp
                }
            }
        }
    }
}
