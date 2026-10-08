package com.example.pruebareel.data.preferences

/**
 * Modelo de datos para la configuración de la aplicación.
 *
 * @property reelsEnabled Indica si el bloqueo de Reels de Instagram está activado.
 * @property reelsLimit El número máximo de Reels consecutivos permitidos.
 * @property shortsEnabled Indica si el bloqueo de Shorts de YouTube está activado.
 * @property shortsTimeMs El tiempo máximo (en milisegundos) permitido para ver Shorts.
 * @property lockDurationSeconds La duración del periodo de bloqueo en segundos.
 * @property isLockActive Indica si hay un periodo de bloqueo activo en este momento.
 * @property lockEndTimestamp El timestamp (ms) en el que debería terminar el bloqueo actual.
 */
data class AppSettings(
    val reelsEnabled: Boolean,
    val reelsLimit: Int,
    val shortsEnabled: Boolean,
    val shortsTimeMs: Long,
    val lockDurationSeconds: Int,
    val isLockActive: Boolean,
    val lockEndTimestamp: Long
)
