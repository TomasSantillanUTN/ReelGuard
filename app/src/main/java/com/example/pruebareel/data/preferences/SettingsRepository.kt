package com.example.pruebareel.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extensión de Context para crear una instancia singleton de DataStore.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Repositorio para gestionar la configuración de la aplicación.
 *
 * Esta clase es la única fuente de verdad para toda la configuración. Utiliza Jetpack DataStore
 * para persistir los datos de forma asíncrona y segura.
 *
 * @param context El contexto de la aplicación, necesario para inicializar DataStore.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val REELS_ENABLED = booleanPreferencesKey("reels_enabled")
        val REELS_LIMIT = intPreferencesKey("reels_limit")
        val SHORTS_ENABLED = booleanPreferencesKey("shorts_enabled")
        val SHORTS_TIME_MS = longPreferencesKey("shorts_time_ms")
        val LOCK_DURATION_SECONDS = intPreferencesKey("lock_duration_seconds")
        val IS_LOCK_ACTIVE = booleanPreferencesKey("is_lock_active")
        val LOCK_END_TIMESTAMP = longPreferencesKey("lock_end_timestamp")
    }

    /**
     * Un [Flow] que emite la configuración actual ([AppSettings]) cada vez que cambia.
     *
     * Los consumidores pueden observar este Flow para reaccionar en tiempo real a los cambios
     * en la configuración. Proporciona valores por defecto si no se ha guardado nada previamente.
     */
    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .map { preferences ->
            val reelsEnabled = preferences[Keys.REELS_ENABLED] ?: true
            val reelsLimit = preferences[Keys.REELS_LIMIT] ?: 5
            val shortsEnabled = preferences[Keys.SHORTS_ENABLED] ?: true
            val shortsTimeMs = preferences[Keys.SHORTS_TIME_MS] ?: 15_000L
            val lockDurationSeconds = preferences[Keys.LOCK_DURATION_SECONDS] ?: 10
            val isLockActive = preferences[Keys.IS_LOCK_ACTIVE] ?: false
            val lockEndTimestamp = preferences[Keys.LOCK_END_TIMESTAMP] ?: 0L

            AppSettings(
                reelsEnabled = reelsEnabled,
                reelsLimit = reelsLimit,
                shortsEnabled = shortsEnabled,
                shortsTimeMs = shortsTimeMs,
                lockDurationSeconds = lockDurationSeconds,
                isLockActive = isLockActive,
                lockEndTimestamp = lockEndTimestamp
            )
        }

    /** Guarda el estado de activación del bloqueo de Reels. */
    suspend fun setReelsEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.REELS_ENABLED] = enabled
        }
    }

    /** Guarda el límite máximo de Reels. */
    suspend fun setReelsLimit(limit: Int) {
        context.dataStore.edit {
            it[Keys.REELS_LIMIT] = limit
        }
    }

    /** Guarda el estado de activación del bloqueo de Shorts. */
    suspend fun setShortsEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.SHORTS_ENABLED] = enabled
        }
    }

    /** Guarda el tiempo máximo (en milisegundos) para ver Shorts. */
    suspend fun setShortsTimeMs(timeMs: Long) {
        context.dataStore.edit {
            it[Keys.SHORTS_TIME_MS] = timeMs
        }
    }

    /** Guarda la duración del bloqueo en segundos. */
    suspend fun setLockDuration(seconds: Int) {
        context.dataStore.edit {
            it[Keys.LOCK_DURATION_SECONDS] = seconds
        }
    }

    /** Guarda el estado del bloqueo persistente. */
    suspend fun setLockActive(active: Boolean, endTimestamp: Long = 0L) {
        context.dataStore.edit {
            it[Keys.IS_LOCK_ACTIVE] = active
            it[Keys.LOCK_END_TIMESTAMP] = endTimestamp
        }
    }
}
