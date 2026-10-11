package com.example.pruebareel.feature.service

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.pruebareel.core.lock.LockTarget
import com.example.pruebareel.data.preferences.AppSettings

/**
 * Lógica específica de una app (Instagram, YouTube, TikTok).
 *
 * [ReelGuardService] se encarga de todo lo común (pantalla de bloqueo, horarios, límites
 * diarios, estadísticas) y le pasa a cada módulo solo los eventos de su app.
 */
interface BlockerModule {
    val target: LockTarget

    /** Paquetes de Android de la app. */
    val packages: Set<String>

    fun isEnabled(settings: AppSettings): Boolean

    /**
     * Evento de la app del módulo, con la app en primer plano y SIN bloqueo activo.
     * [root] es la raíz de la ventana activa.
     */
    fun onAppEvent(event: AccessibilityEvent, root: AccessibilityNodeInfo, now: Long)

    /** Cualquier evento del sistema (de cualquier app). Por defecto no hace nada. */
    fun onAnyEvent(event: AccessibilityEvent, now: Long) {}

    /** Se llamó cuando se activó un bloqueo para esta app: reiniciar contadores. */
    fun onLocked() {}

    fun onDestroy() {}
}
