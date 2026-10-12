package com.example.pruebareel.data.preferences

import com.example.pruebareel.core.lock.LockState
import com.example.pruebareel.core.lock.LockTarget

/**
 * Modelo de datos para la configuración de la aplicación.
 *
 * Límites "seguidos" (streak): cantidad de videos consecutivos antes del bloqueo corto.
 * Límites diarios: total por día; `0` significa sin límite.
 *
 * @property lockDurationSeconds Duración del bloqueo corto (máx. 10 minutos).
 * @property blockNavigation Si el bloqueo corto impide usar Inicio / Volver / Recientes.
 * @property quietHoursEnabled Si está activo el horario sin videos.
 * @property quietStartMinute Inicio del horario, en minutos desde las 00:00 (ej. 23:00 = 1380).
 * @property quietEndMinute Fin del horario, en minutos desde las 00:00 (puede ser menor que el inicio).
 * @property locks Bloqueos activos o vencidos guardados, por app.
 */
data class AppSettings(
    val reelsEnabled: Boolean,
    val reelsLimit: Int,
    val reelsDailyLimit: Int,

    val shortsEnabled: Boolean,
    val shortsLimit: Int,
    val shortsDailyLimit: Int,

    val tiktokEnabled: Boolean,
    val tiktokLimit: Int,
    val tiktokDailyLimit: Int,

    val lockDurationSeconds: Int,
    val blockNavigation: Boolean,

    val quietHoursEnabled: Boolean,
    val quietStartMinute: Int,
    val quietEndMinute: Int,

    val locks: Map<LockTarget, LockState>
)
