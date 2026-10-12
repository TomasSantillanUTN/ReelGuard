package com.example.pruebareel.feature.service.modules

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.pruebareel.core.lock.LockReason
import com.example.pruebareel.core.lock.LockTarget
import com.example.pruebareel.core.utils.findAny
import com.example.pruebareel.core.utils.looksLikeCommentsSheet
import com.example.pruebareel.data.preferences.AppSettings
import com.example.pruebareel.data.stats.StatMetric
import com.example.pruebareel.feature.service.BlockerModule
import com.example.pruebareel.feature.service.ReelGuardService

/**
 * YouTube Shorts: cuenta los Shorts seguidos y los del día (igual que Reels y TikTok).
 *
 * Detección (heurística, depende del diseño actual de YouTube — ver docs/youtube_shorts.jpeg):
 * - **Estamos en el reproductor de Shorts** si hay una barra de progreso (SeekBar) y el botón
 *   "Remix" de la columna derecha (o un nodo con "Short" en su descripción, como respaldo).
 * - **Pasó un Short** cuando scrollea un contenedor que ocupa casi toda la pantalla.
 *
 * Además, si un Short pasa a ventana flotante (picture-in-picture) al tocar Inicio, la cierra.
 *
 * Si no cuenta bien, filtrar Logcat por "ReelGuard-YouTube".
 */
class YoutubeShortsModule(private val host: ReelGuardService) : BlockerModule {

    companion object {
        private const val TAG = "ReelGuard-YouTube"
        private const val DEBOUNCE_TIME_MS = 500L
        private const val RESET_GRACE_MS = 3_000L
        private const val FULLSCREEN_RATIO = 0.8f

        /** Si se vio un Short hace menos de esto y aparece la ventana flotante, se cierra. */
        private const val PIP_AFTER_SHORTS_MS = 3_000L
        private const val PIP_DISMISS_THROTTLE_MS = 1_500L
    }

    override val target = LockTarget.YOUTUBE
    override val packages = setOf("com.google.android.youtube")

    private var shortsCount = 0
    private var lastCountTime = 0L
    private var lastShortsSeenTime = 0L
    private var lastPipDismissTime = 0L

    override fun isEnabled(settings: AppSettings) = settings.shortsEnabled

    override fun onAnyEvent(event: AccessibilityEvent, now: Long) {
        // VENTANA FLOTANTE (PiP): si viene de un Short (o hay bloqueo activo), se cierra.
        if (host.isLocked(target, now) || now - lastShortsSeenTime < PIP_AFTER_SHORTS_MS) {
            findYoutubePipWindow()?.let { dismissPip(it) }
        }
    }

    override fun onAppEvent(event: AccessibilityEvent, root: AccessibilityNodeInfo, now: Long) {
        val s = host.settings ?: return
        val inShorts = isShortsPlayer(root)
        val justEntered = inShorts && now - lastShortsSeenTime > RESET_GRACE_MS

        if (inShorts) {
            lastShortsSeenTime = now
            // Horario sin videos o límite diario: no se deja ni empezar.
            if (justEntered && host.enforceUsageRules(target, dailyLimitReached(s))) {
                host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                return
            }
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val source = event.source
                val fullScreen = source != null && isFullScreen(source)
                Log.d(TAG, "scroll: class=${source?.className} fullScreen=$fullScreen inShorts=$inShorts count=$shortsCount")
                if (!inShorts || !fullScreen) return

                if (now - lastCountTime > DEBOUNCE_TIME_MS) {
                    lastCountTime = now
                    shortsCount++
                    host.addStat(StatMetric.YT_SHORTS)

                    val reason = when {
                        shortsCount >= s.shortsLimit -> LockReason.STREAK
                        dailyLimitReached(s) -> LockReason.DAILY_LIMIT
                        else -> null
                    }
                    if (reason != null) {
                        // Se sale de Shorts para que el video deje de reproducirse y se bloquea.
                        host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        host.requestLock(target, reason)
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Abrir comentarios no reinicia el contador; salir de Shorts sí (con tiempo de gracia).
                if (!inShorts && !root.looksLikeCommentsSheet() &&
                    now - lastShortsSeenTime > RESET_GRACE_MS
                ) {
                    shortsCount = 0
                }
            }
        }
    }

    override fun onLocked() {
        shortsCount = 0
    }

    private fun dailyLimitReached(s: AppSettings) =
        s.shortsDailyLimit > 0 && host.todayStat(StatMetric.YT_SHORTS) >= s.shortsDailyLimit

    private fun isShortsPlayer(root: AccessibilityNodeInfo): Boolean {
        val hasSeekBar = root.findAny { it.className == "android.widget.SeekBar" }
        if (!hasSeekBar) return false
        return root.findAny { node ->
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()
            text.equals("Remix", ignoreCase = true) ||
                desc?.startsWith("Remix", ignoreCase = true) == true ||
                (desc != null && desc.contains("Short", ignoreCase = true) && !desc.equals("Shorts", ignoreCase = true))
        }
    }

    private fun isFullScreen(node: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        node.getBoundsInScreen(r)
        val screenHeight = host.resources.displayMetrics.heightPixels
        return screenHeight > 0 && r.height() >= screenHeight * FULLSCREEN_RATIO
    }

    // ---------- Picture-in-picture ----------

    private fun findYoutubePipWindow(): AccessibilityWindowInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return try {
            host.windows.firstOrNull { w ->
                w.isInPictureInPictureMode && w.root?.packageName?.toString() in packages
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Arrastra la ventana flotante hasta la "X" de abajo al centro (gesto estándar de Android). */
    private fun dismissPip(pip: AccessibilityWindowInfo) {
        val now = System.currentTimeMillis()
        if (now - lastPipDismissTime < PIP_DISMISS_THROTTLE_MS) return
        lastPipDismissTime = now

        val bounds = Rect()
        pip.getBoundsInScreen(bounds)
        val metrics = host.resources.displayMetrics

        val path = Path().apply {
            moveTo(bounds.exactCenterX(), bounds.exactCenterY())
            lineTo(metrics.widthPixels / 2f, metrics.heightPixels - 10f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 600))
            .build()
        host.dispatchGesture(gesture, null, null)
    }
}
