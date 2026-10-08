package com.example.pruebareel.feature.service

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
import com.example.pruebareel.R
import com.example.pruebareel.core.lock.LockManager
import com.example.pruebareel.core.lock.LockOverlay
import com.example.pruebareel.core.lock.LockTarget
import com.example.pruebareel.core.utils.findAny
import com.example.pruebareel.data.preferences.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio de Accesibilidad para limitar el tiempo de visualización de YouTube Shorts.
 *
 * - Cuando se acaba el tiempo, sale de Shorts y muestra la misma pantalla de bloqueo con cuenta
 *   regresiva que Instagram (respeta la opción "Bloquear botones del sistema").
 * - Si un Short pasa a modo "ventana flotante" (picture-in-picture) al tocar Home, la ventana se
 *   cierra para que no se pueda seguir mirando.
 *
 * **Importante:** las detecciones son heurísticas y dependen del diseño actual de YouTube.
 */
class YoutubeShortsTimeBlockerService : AccessibilityService() {

    companion object {
        private const val YOUTUBE = "com.google.android.youtube"
        private const val SYSTEM_UI = "com.android.systemui"

        /** Si se vio un Short hace menos de esto y aparece la ventana flotante, se cierra. */
        private const val PIP_AFTER_SHORTS_MS = 3_000L
        private const val PIP_DISMISS_THROTTLE_MS = 1_500L
    }

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var lockManager: LockManager
    private lateinit var lockOverlay: LockOverlay
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var shortsEnabled = true
    private var maxTimeMs: Long = 5_000L
    private var lockDurationSeconds = 10
    private var blockNavigation = true

    private var inShorts = false
    private var startTime = 0L
    private var lastShortsSeenTime = 0L
    private var lastPipDismissTime = 0L
    /** Copia en memoria del fin del bloqueo, para no tener que leer DataStore en cada evento. */
    private var lockEndCache = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var checkerRunning = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsRepository = SettingsRepository(this)
        lockManager = LockManager(settingsRepository, LockTarget.YOUTUBE)

        lockOverlay = LockOverlay(this) {
            serviceScope.launch {
                lockManager.clearLock()
                lockEndCache = 0L
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }

        checkPersistentLock()

        serviceScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                shortsEnabled = settings.shortsEnabled
                maxTimeMs = settings.shortsTimeMs
                lockDurationSeconds = settings.lockDurationSeconds
                blockNavigation = settings.blockNavigation
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !shortsEnabled) return
        val pkg = event.packageName?.toString()
        val now = System.currentTimeMillis()
        val lockActive = now < lockEndCache || lockOverlay.isShowing

        // 1) VENTANA FLOTANTE (PiP): si viene de un Short (o hay bloqueo activo), se cierra.
        if (inShorts || lockActive || now - lastShortsSeenTime < PIP_AFTER_SHORTS_MS) {
            findYoutubePipWindow()?.let { pip ->
                dismissPip(pip)
                endShortsSession()
                return
            }
        }

        // 2) VISIBILIDAD DEL OVERLAY
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg == YOUTUBE) {
                checkPersistentLock()
            } else if (pkg != null && pkg != packageName && pkg != SYSTEM_UI && !blockNavigation) {
                lockOverlay.hide()
            }
        }

        if (pkg != YOUTUBE) return
        if (lockActive) return // Mientras dura el bloqueo no se vuelve a contar tiempo.

        val root = rootInActiveWindow ?: return
        if (root.packageName != YOUTUBE) return

        val isShortsNow = isYoutubeShorts(root)
        if (isShortsNow) lastShortsSeenTime = now

        if (isShortsNow && !inShorts) {
            inShorts = true
            startTime = now
            startTimer()
        }

        if (!isShortsNow && inShorts) {
            endShortsSession()
        }
    }

    private fun endShortsSession() {
        stopTimer()
        inShorts = false
        startTime = 0L
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
            if (!checkerRunning || !shortsEnabled) {
                stopTimer()
                return
            }
            if (System.currentTimeMillis() - startTime >= maxTimeMs) {
                blockYoutube()
                return
            }
            handler.postDelayed(this, 200)
        }
    }

    private fun isYoutubeShorts(root: AccessibilityNodeInfo): Boolean {
        val hasShortLabel = root.findAny {
            it.contentDescription?.contains("Short", ignoreCase = true) == true ||
                it.text?.toString()?.contains("Short", ignoreCase = true) == true
        }
        val hasSeekBar = root.findAny { it.className == "android.widget.SeekBar" }
        return hasShortLabel && hasSeekBar
    }

    /**
     * Se terminó el tiempo: se sale de Shorts (Volver) para que el video deje de reproducirse
     * y se muestra la pantalla de bloqueo con la cuenta regresiva.
     */
    private fun blockYoutube() {
        endShortsSession()
        lastShortsSeenTime = 0L
        performGlobalAction(GLOBAL_ACTION_BACK)
        serviceScope.launch {
            lockManager.startLock(lockDurationSeconds)
            lockEndCache = System.currentTimeMillis() + lockDurationSeconds * 1000L
            checkPersistentLock()
        }
    }

    private fun checkPersistentLock() {
        serviceScope.launch {
            val remaining = lockManager.getRemainingMillis()
            if (remaining > 0) {
                lockEndCache = System.currentTimeMillis() + remaining
                if (rootInActiveWindow?.packageName == YOUTUBE) {
                    lockOverlay.show(
                        endTimeMillis = lockEndCache,
                        message = getString(R.string.lock_overlay_message_shorts),
                        blockNavigation = blockNavigation
                    )
                }
            } else if (!lockOverlay.isShowing) {
                lockEndCache = 0L
                lockManager.clearLock()
            }
        }
    }

    // ---------- Picture-in-picture ----------

    /** Busca una ventana de YouTube en modo picture-in-picture (Android 8+). */
    private fun findYoutubePipWindow(): AccessibilityWindowInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return try {
            windows.firstOrNull { w ->
                w.isInPictureInPictureMode && w.root?.packageName == YOUTUBE
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cierra la ventana flotante arrastrándola hasta la "X" que aparece abajo en el centro
     * de la pantalla (gesto estándar de Android para descartar el PiP).
     */
    private fun dismissPip(pip: AccessibilityWindowInfo) {
        val now = System.currentTimeMillis()
        if (now - lastPipDismissTime < PIP_DISMISS_THROTTLE_MS) return
        lastPipDismissTime = now

        val bounds = Rect()
        pip.getBoundsInScreen(bounds)
        val metrics = resources.displayMetrics

        val path = Path().apply {
            moveTo(bounds.exactCenterX(), bounds.exactCenterY())
            lineTo(metrics.widthPixels / 2f, metrics.heightPixels - 10f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 600))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        lockOverlay.hide()
        serviceScope.cancel()
    }
}
