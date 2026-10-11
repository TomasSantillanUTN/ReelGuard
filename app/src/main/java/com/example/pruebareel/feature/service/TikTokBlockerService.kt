package com.example.pruebareel.feature.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.util.Log
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
 * Servicio de Accesibilidad para limitar la cantidad de videos consecutivos en TikTok.
 *
 * Funciona igual que [ReelBlockerService]: cuenta cada video nuevo del feed y, al llegar al
 * límite, muestra la pantalla de bloqueo con cuenta regresiva.
 *
 * Detección (heurística, depende del diseño actual de TikTok):
 * - **Estamos en el feed** si se ven las pestañas de arriba ("Para ti" / "Siguiendo",
 *   o "For You" / "Following" si el celular está en inglés).
 * - **Pasó un video** cuando hay un evento de scroll de un contenedor que ocupa casi toda la
 *   pantalla (el feed). Así no cuentan los scrolls de la hoja de comentarios, que es más chica.
 *
 * Si no cuenta bien, filtrar Logcat por "ReelGuard-TikTok" para ver qué expone TikTok.
 */
class TikTokBlockerService : AccessibilityService() {

    companion object {
        private const val TAG = "ReelGuard-TikTok"

        /** Paquetes conocidos de TikTok (global y algunas regiones). */
        private val TIKTOK_PACKAGES = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
        private const val SYSTEM_UI = "com.android.systemui"

        private val FEED_TABS = setOf("para ti", "siguiendo", "for you", "following")

        private const val DEBOUNCE_TIME_MS = 500L
        private const val RESET_GRACE_MS = 3_000L
        /** El contenedor que scrollea tiene que ocupar al menos este % del alto de pantalla. */
        private const val FULLSCREEN_RATIO = 0.8f
    }

    private lateinit var settingsRepository: SettingsRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var lockManager: LockManager
    private lateinit var lockOverlay: LockOverlay

    private var tiktokEnabled = true
    private var maxVideos = 5
    private var lockDurationSeconds = 10
    private var blockNavigation = true
    private var videoCount = 0

    private var lastCountTime = 0L
    private var lastFeedActivityTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsRepository = SettingsRepository(this)
        lockManager = LockManager(settingsRepository, LockTarget.TIKTOK)

        lockOverlay = LockOverlay(this) {
            serviceScope.launch {
                lockManager.clearLock()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }

        checkPersistentLock()

        serviceScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                tiktokEnabled = settings.tiktokEnabled
                maxVideos = settings.tiktokLimit
                lockDurationSeconds = settings.lockDurationSeconds
                blockNavigation = settings.blockNavigation
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !tiktokEnabled) return
        val pkg = event.packageName?.toString()
        val isTikTok = pkg in TIKTOK_PACKAGES

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (isTikTok) {
                checkPersistentLock()
            } else if (pkg != null && pkg != packageName && pkg != SYSTEM_UI && !blockNavigation) {
                lockOverlay.hide()
            }
        }

        if (!isTikTok) return

        val root = rootInActiveWindow ?: return
        val now = System.currentTimeMillis()

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val source = event.source
                val fullScreen = source != null && isFullScreen(source)
                val inFeed = isInFeed(root)
                Log.d(TAG, "scroll: class=${source?.className} fullScreen=$fullScreen inFeed=$inFeed count=$videoCount")

                if (fullScreen && inFeed) {
                    lastFeedActivityTime = now
                    if (now - lastCountTime > DEBOUNCE_TIME_MS) {
                        lastCountTime = now
                        videoCount++
                        if (videoCount >= maxVideos) triggerLock()
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Igual que en Instagram: abrir comentarios / compartir no reinicia el contador.
                if (isInFeed(root) || looksLikeComments(root)) {
                    lastFeedActivityTime = now
                } else if (now - lastFeedActivityTime > RESET_GRACE_MS) {
                    videoCount = 0
                }
            }
        }
    }

    private fun isFullScreen(node: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        node.getBoundsInScreen(r)
        val screenHeight = resources.displayMetrics.heightPixels
        return screenHeight > 0 && r.height() >= screenHeight * FULLSCREEN_RATIO
    }

    /** Las pestañas de arriba del feed ("Para ti" / "Siguiendo"). */
    private fun isInFeed(root: AccessibilityNodeInfo): Boolean = root.findAny { node ->
        val t = (node.text ?: node.contentDescription)?.toString()?.trim()?.lowercase()
        t != null && t in FEED_TABS
    }

    private fun looksLikeComments(root: AccessibilityNodeInfo): Boolean = root.findAny { node ->
        if (node.className?.toString()?.contains("EditText") != true) return@findAny false
        val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.hintText else null
        listOfNotNull(node.text, hint, node.contentDescription).any {
            val s = it.toString()
            s.contains("comentario", ignoreCase = true) || s.contains("comment", ignoreCase = true)
        }
    }

    private fun checkPersistentLock() {
        serviceScope.launch {
            val remaining = lockManager.getRemainingMillis()
            if (remaining > 0) {
                if (rootInActiveWindow?.packageName?.toString() in TIKTOK_PACKAGES) {
                    lockOverlay.show(
                        endTimeMillis = System.currentTimeMillis() + remaining,
                        message = getString(R.string.lock_overlay_message_tiktok),
                        blockNavigation = blockNavigation,
                        shouldStayVisible = ::stillInBlockedApp
                    )
                }
            } else if (!lockOverlay.isShowing) {
                lockManager.clearLock()
            }
        }
    }

    private fun triggerLock() {
        videoCount = 0
        serviceScope.launch {
            lockManager.startLock(lockDurationSeconds)
            checkPersistentLock()
        }
    }

    private fun foregroundPackage(): String? {
        val fromWindows = try {
            windows.firstOrNull {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isFocused || it.isActive)
            }?.root?.packageName?.toString()
        } catch (e: Exception) {
            null
        }
        return fromWindows ?: rootInActiveWindow?.packageName?.toString()
    }

    private fun stillInBlockedApp(): Boolean {
        val fg = foregroundPackage() ?: return true
        return fg in TIKTOK_PACKAGES || fg == SYSTEM_UI || fg == packageName
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        lockOverlay.hide()
        serviceScope.cancel()
    }
}
