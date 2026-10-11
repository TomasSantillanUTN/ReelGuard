package com.example.pruebareel.feature.service.modules

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.pruebareel.core.lock.LockReason
import com.example.pruebareel.core.lock.LockTarget
import com.example.pruebareel.core.utils.findAny
import com.example.pruebareel.data.preferences.AppSettings
import com.example.pruebareel.data.stats.StatMetric
import com.example.pruebareel.feature.service.BlockerModule
import com.example.pruebareel.feature.service.ReelGuardService

/**
 * YouTube Shorts: limita el tiempo seguido y el tiempo total del día.
 *
 * - Cuando se acaba el tiempo, sale de Shorts (Volver) y muestra la pantalla de bloqueo.
 * - Si un Short pasa a ventana flotante (picture-in-picture) al tocar Inicio, la cierra.
 *
 * **Importante:** las detecciones son heurísticas y dependen del diseño actual de YouTube.
 */
class YoutubeShortsModule(private val host: ReelGuardService) : BlockerModule {

    companion object {
        /** Si se vio un Short hace menos de esto y aparece la ventana flotante, se cierra. */
        private const val PIP_AFTER_SHORTS_MS = 3_000L
        private const val PIP_DISMISS_THROTTLE_MS = 1_500L
        private const val TICK_MS = 200L
    }

    override val target = LockTarget.YOUTUBE
    override val packages = setOf("com.google.android.youtube")

    private var inShorts = false
    private var startTime = 0L
    private var lastShortsSeenTime = 0L
    private var lastPipDismissTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var checkerRunning = false

    override fun isEnabled(settings: AppSettings) = settings.shortsEnabled

    override fun onAnyEvent(event: AccessibilityEvent, now: Long) {
        // VENTANA FLOTANTE (PiP): si viene de un Short (o hay bloqueo activo), se cierra.
        if (inShorts || host.isLocked(target, now) || now - lastShortsSeenTime < PIP_AFTER_SHORTS_MS) {
            findYoutubePipWindow()?.let { pip ->
                dismissPip(pip)
                endShortsSession()
            }
        }
    }

    override fun onAppEvent(event: AccessibilityEvent, root: AccessibilityNodeInfo, now: Long) {
        val s = host.settings ?: return
        val isShortsNow = isYoutubeShorts(root)
        if (isShortsNow) lastShortsSeenTime = now

        if (isShortsNow && !inShorts) {
            // Horario sin videos o límite diario: no se deja ni empezar.
            if (host.enforceUsageRules(target, dailyLimitReached(s, 0))) {
                host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                return
            }
            inShorts = true
            startTime = now
            startTimer()
        }

        if (!isShortsNow && inShorts) {
            endShortsSession()
        }
    }

    override fun onLocked() {
        endShortsSession()
        lastShortsSeenTime = 0L
    }

    override fun onDestroy() {
        stopTimer()
    }

    /** Termina la sesión de Shorts y guarda los segundos vistos en las estadísticas. */
    private fun endShortsSession() {
        if (inShorts && startTime > 0) {
            val seconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            host.addStat(StatMetric.YT_SHORTS_SECONDS, seconds)
        }
        stopTimer()
        inShorts = false
        startTime = 0L
    }

    private fun dailyLimitReached(s: AppSettings, sessionMs: Long): Boolean {
        if (s.shortsDailyMinutes <= 0) return false
        val totalSeconds = host.todayStat(StatMetric.YT_SHORTS_SECONDS) + sessionMs / 1000
        return totalSeconds >= s.shortsDailyMinutes * 60L
    }

    private fun startTimer() {
        if (checkerRunning) return
        checkerRunning = true
        handler.post(checkRunnable)
    }

    private fun stopTimer() {
        checkerRunning = false
        handler.removeCallbacks(checkRunnable)
    }

    private val checkRunnable = object : Runnable {
        override fun run() {
            val s = host.settings
            if (!checkerRunning || s == null || !s.shortsEnabled) {
                stopTimer()
                return
            }
            val elapsed = System.currentTimeMillis() - startTime
            val reason = when {
                elapsed >= s.shortsTimeMs -> LockReason.STREAK
                dailyLimitReached(s, elapsed) -> LockReason.DAILY_LIMIT
                host.isQuietHoursNow() -> LockReason.QUIET_HOURS
                else -> null
            }
            if (reason != null) {
                // Se sale de Shorts para que el video deje de reproducirse y se bloquea.
                host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                host.requestLock(target, reason) // llama a onLocked() → guarda estadísticas
                return
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    /**
     * Heurística: hay un nodo con "Short" en el texto/descripción y una SeekBar (barra de progreso).
     */
    private fun isYoutubeShorts(root: AccessibilityNodeInfo): Boolean {
        val hasShortLabel = root.findAny {
            it.contentDescription?.contains("Short", ignoreCase = true) == true ||
                it.text?.toString()?.contains("Short", ignoreCase = true) == true
        }
        val hasSeekBar = root.findAny { it.className == "android.widget.SeekBar" }
        return hasShortLabel && hasSeekBar
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
