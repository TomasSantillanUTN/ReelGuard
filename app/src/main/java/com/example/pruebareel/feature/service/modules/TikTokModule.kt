package com.example.pruebareel.feature.service.modules

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.pruebareel.core.lock.LockReason
import com.example.pruebareel.core.lock.LockTarget
import com.example.pruebareel.core.utils.findAny
import com.example.pruebareel.core.utils.looksLikeCommentsSheet
import com.example.pruebareel.data.preferences.AppSettings
import com.example.pruebareel.data.stats.StatMetric
import com.example.pruebareel.feature.service.BlockerModule
import com.example.pruebareel.feature.service.ReelGuardService

/**
 * TikTok: cuenta los videos seguidos y los del día.
 *
 * Detección (heurística, depende del diseño actual de TikTok):
 * - **Estamos en el feed** si se ven las pestañas de arriba ("Para ti" / "Siguiendo",
 *   o "For You" / "Following" si el celular está en inglés).
 * - **Pasó un video** cuando scrollea un contenedor que ocupa casi toda la pantalla.
 *   Así no cuentan los scrolls de la hoja de comentarios, que es más chica.
 *
 * Si no cuenta bien, filtrar Logcat por "ReelGuard-TikTok".
 */
class TikTokModule(private val host: ReelGuardService) : BlockerModule {

    companion object {
        private const val TAG = "ReelGuard-TikTok"
        private val FEED_TABS = setOf("para ti", "siguiendo", "for you", "following")
        private const val DEBOUNCE_TIME_MS = 500L
        private const val RESET_GRACE_MS = 3_000L
        private const val FULLSCREEN_RATIO = 0.8f
    }

    override val target = LockTarget.TIKTOK
    /** Paquetes conocidos de TikTok (global y algunas regiones). */
    override val packages = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

    private var videoCount = 0
    private var lastCountTime = 0L
    private var lastFeedActivityTime = 0L

    override fun isEnabled(settings: AppSettings) = settings.tiktokEnabled

    override fun onAppEvent(event: AccessibilityEvent, root: AccessibilityNodeInfo, now: Long) {
        val s = host.settings ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val source = event.source
                val fullScreen = source != null && isFullScreen(source)
                val inFeed = isInFeed(root)
                Log.d(TAG, "scroll: class=${source?.className} fullScreen=$fullScreen inFeed=$inFeed count=$videoCount")

                if (!fullScreen || !inFeed) return
                lastFeedActivityTime = now
                if (host.enforceUsageRules(target, dailyLimitReached(s))) return

                if (now - lastCountTime > DEBOUNCE_TIME_MS) {
                    lastCountTime = now
                    videoCount++
                    host.addStat(StatMetric.TT_VIDEOS)

                    when {
                        videoCount >= s.tiktokLimit -> host.requestLock(target, LockReason.STREAK)
                        dailyLimitReached(s) -> host.requestLock(target, LockReason.DAILY_LIMIT)
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val inFeed = isInFeed(root)
                if (inFeed || root.looksLikeCommentsSheet()) {
                    lastFeedActivityTime = now
                    if (inFeed) host.enforceUsageRules(target, dailyLimitReached(s))
                } else if (now - lastFeedActivityTime > RESET_GRACE_MS) {
                    videoCount = 0
                }
            }
        }
    }

    override fun onLocked() {
        videoCount = 0
    }

    private fun dailyLimitReached(s: AppSettings) =
        s.tiktokDailyLimit > 0 && host.todayStat(StatMetric.TT_VIDEOS) >= s.tiktokDailyLimit

    private fun isFullScreen(node: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        node.getBoundsInScreen(r)
        val screenHeight = host.resources.displayMetrics.heightPixels
        return screenHeight > 0 && r.height() >= screenHeight * FULLSCREEN_RATIO
    }

    private fun isInFeed(root: AccessibilityNodeInfo): Boolean = root.findAny { node ->
        val t = (node.text ?: node.contentDescription)?.toString()?.trim()?.lowercase()
        t != null && t in FEED_TABS
    }
}
