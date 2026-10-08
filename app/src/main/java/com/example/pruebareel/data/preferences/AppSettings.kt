package com.example.pruebareel.data.preferences

/**
 * Modelo de datos para la configuración de la aplicación.
 *
 * @property reelsEnabled Indica si el bloqueo de Reels de Instagram está activado.
 * @property reelsLimit El número máximo de Reels consecutivos permitidos.
 * @property shortsEnabled Indica si el bloqueo de Shorts de YouTube está activado.
 * @property shortsTimeMs El tiempo máximo (en milisegundos) permitido para ver Shorts.
 * @property lockDurationSeconds La duración del periodo de bloqueo en segundos (máx. 10 minutos).
 * @property blockNavigation Si es `true`, la pantalla de bloqueo no deja usar Home/Volver/Recientes.
 *   Si es `false`, la pantalla deja libres los botones del sistema y se oculta al salir de la app.
 * @property isLockActive Indica si hay un periodo de bloqueo activo para Instagram.
 * @property lockEndTimestamp El timestamp (ms) en el que termina el bloqueo de Instagram.
 * @property ytLockActive Indica si hay un periodo de bloqueo activo para YouTube.
 * @property ytLockEndTimestamp El timestamp (ms) en el que termina el bloqueo de YouTube.
 */
data class AppSettings(
    val reelsEnabled: Boolean,
    val reelsLimit: Int,
    val shortsEnabled: Boolean,
    val shortsTimeMs: Long,
    val lockDurationSeconds: Int,
    val blockNavigation: Boolean,
    val isLockActive: Boolean,
    val lockEndTimestamp: Long,
    val ytLockActive: Boolean,
    val ytLockEndTimestamp: Long
)
