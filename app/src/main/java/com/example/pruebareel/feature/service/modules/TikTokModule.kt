package com.example.pruebareel.feature.service.modules

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.pruebareel.core.lock.LockReason
import com.example.pruebareel.core.lock.LockTarget
import com.example.pruebareel.core.utils.clickByLabel
import com.example.pruebareel.core.utils.findAnyVisible
import com.example.pruebareel.core.utils.looksLikeCommentsSheet
import com.example.pruebareel.data.preferences.AppSettings
import com.example.pruebareel.data.stats.StatMetric
import com.example.pruebareel.feature.service.BlockerModule
import com.example.pruebareel.feature.service.ReelGuardService

/**
 * TikTok: cuenta los videos seguidos y los del día.
 *
 * Detección (heurística, depende del diseño actual de TikTok — ver docs/tiktok_feed.jpeg):
 * - **Sección de videos (feed)** si se ve la pestaña de arriba "Para ti" ("For You"). En Perfil, Mensajes, Amigos, etc. no están, así que esas
 *   secciones se pueden usar aunque sea horario sin videos.
 * - **Pasó un video** cuando scrollea un contenedor que ocupa casi toda la pantalla.
 *
 * Si no cuenta bien, filtrar Logcat por "ReelGuard-TikTok".
 */
class TikTokModule(private val host: ReelGuardService) : BlockerModule {

    companion object {
        private const val TAG = "ReelGuard-TikTok"
        /**
         * Pestaña "Para ti" de arriba del feed. NO se usa "Siguiendo": esa palabra también aparece
         * en el perfil (cantidad de cuentas que seguís) y daba falsos positivos.
         */
        private val FEED_TABS = setOf("para ti", "for you")
        /** Pestañas de abajo a las que se puede "escapar" del feed. */
        private val ESCAPE_TABS = setOf("perfil", "profile", "mensajes", "inbox", "bandeja de entrada")
        private const val DEBOUNCE_TIME_MS = 500L
        private const val RESET_GRACE_MS = 3_000L
        private const val FULLSCREEN_RATIO = 0.8f
        private const val CONTENT_CHECK_INTERVAL_MS = 700L
    }

    override val target = LockTarget.TIKTOK
    /** Paquetes conocidos de TikTok (global y algunas regiones). */
    override val packages = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

    private var videoCount = 0
    private var lastCountTime = 0L
    private var lastFeedActivityTime = 0L
    private var lastContentCheck = 0L

    override fun isEnabled(settings: AppSettings) = settings.tiktokEnabled

    override fun onAppEvent(event: AccessibilityEvent, root: AccessibilityNodeInfo, now: Long) {
        val s = host.settings ?: return
        val type = event.eventType

        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (now - lastContentCheck < CONTENT_CHECK_INTERVAL_MS) return
            lastContentCheck = now
        }

        val inFeed = isInFeed(root)

        // Horario sin videos / límite diario: solo se saca al usuario del feed de videos.
        if (inFeed) {
            lastFeedActivityTime = now
            if (host.enforceUsageRules(target, dailyLimitReached(s), ::stillInFeed)) return
        }

        when (type) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val source = event.source
                val fullScreen = source != null && isFullScreen(source)
                Log.d(TAG, "scroll: class=${source?.className} fullScreen=$fullScreen inFeed=$inFeed count=$videoCount")
                if (!fullScreen || !inFeed) return

                if (now - lastCountTime > DEBOUNCE_TIME_MS) {
                    lastCountTime = now
                    videoCount++
                    host.addStat(StatMetric.TT_VIDEOS)

                    when {
                        videoCount >= s.tiktokLimit -> host.requestLock(target, LockReason.STREAK)
                        dailyLimitReached(s) ->
                            host.kickOutOfSection(target, LockReason.DAILY_LIMIT, ::stillInFeed)
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Abrir comentarios / compartir no reinicia el contador.
                if (!inFeed && !root.looksLikeCommentsSheet() &&
                    now - lastFeedActivityTime > RESET_GRACE_MS
                ) {
                    videoCount = 0
                }
            }
        }
    }

    /** Para salir del feed se toca la pestaña "Perfil" (o "Mensajes"): el resto de TikTok sigue usable. */
    override fun escapeSection(root: AccessibilityNodeInfo): Boolean = root.clickByLabel(ESCAPE_TABS)

    override fun onLocked() {
        videoCount = 0
    }

    private fun stillInFeed(): Boolean = host.rootInActiveWindow?.let { isInFeed(it) } == true

    private fun dailyLimitReached(s: AppSettings) =
        s.tiktokDailyLimit > 0 && host.todayStat(StatMetric.TT_VIDEOS) >= s.tiktokDailyLimit

    private fun isFullScreen(node: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        node.getBoundsInScreen(r)
        val screenHeight = host.resources.displayMetrics.heightPixels
        return screenHeight > 0 && r.height() >= screenHeight * FULLSCREEN_RATIO
    }

    /** Solo nodos visibles: al ir a Perfil, TikTok deja el feed cargado detrás. */
    private fun isInFeed(root: AccessibilityNodeInfo): Boolean = root.findAnyVisible { node ->
        val t = (node.text ?: node.contentDescription)?.toString()?.trim()?.lowercase()
        t != null && t in FEED_TABS
    }
}
