package com.example.pruebareel.feature.service.modules

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
 * Instagram Reels: cuenta los Reels seguidos y los del día.
 *
 * **Importante:** la detección se basa en la estructura de la interfaz de Instagram
 * (ViewPager + nodos con descripción "Reel de …"). Si Instagram cambia su diseño, hay que
 * actualizar estas heurísticas.
 */
class InstagramModule(private val host: ReelGuardService) : BlockerModule {

    companion object {
        private const val DEBOUNCE_TIME_MS = 500L
        /** Tiempo de gracia antes de reiniciar el contador al salir de Reels. */
        private const val RESET_GRACE_MS = 3_000L
    }

    override val target = LockTarget.INSTAGRAM
    override val packages = setOf("com.instagram.android")

    private var reelCount = 0
    private var lastReelCountTime = 0L
    private var lastReelActivityTime = 0L

    override fun isEnabled(settings: AppSettings) = settings.reelsEnabled

    override fun onAppEvent(event: AccessibilityEvent, root: AccessibilityNodeInfo, now: Long) {
        val s = host.settings ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (!isFullScreenReel(event, root)) return
                lastReelActivityTime = now
                if (host.enforceUsageRules(target, dailyLimitReached(s))) return

                if (now - lastReelCountTime > DEBOUNCE_TIME_MS) {
                    lastReelCountTime = now
                    reelCount++
                    host.addStat(StatMetric.IG_REELS)

                    when {
                        reelCount >= s.reelsLimit -> host.requestLock(target, LockReason.STREAK)
                        dailyLimitReached(s) -> host.requestLock(target, LockReason.DAILY_LIMIT)
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Abrir comentarios / compartir no reinicia el contador: solo se reinicia si el
                // usuario salió de Reels de verdad y pasó un tiempo de gracia.
                val inReels = hasReelLabel(root)
                if (inReels || root.looksLikeCommentsSheet()) {
                    lastReelActivityTime = now
                    if (inReels) host.enforceUsageRules(target, dailyLimitReached(s))
                } else if (now - lastReelActivityTime > RESET_GRACE_MS) {
                    reelCount = 0
                }
            }
        }
    }

    override fun onLocked() {
        reelCount = 0
    }

    private fun dailyLimitReached(s: AppSettings) =
        s.reelsDailyLimit > 0 && host.todayStat(StatMetric.IG_REELS) >= s.reelsDailyLimit

    private fun isFullScreenReel(event: AccessibilityEvent, root: AccessibilityNodeInfo): Boolean {
        val srcClass = event.source?.className?.toString() ?: ""
        if (!srcClass.contains("ViewPager", ignoreCase = true)) return false
        return hasReelLabel(root)
    }

    private fun hasReelLabel(root: AccessibilityNodeInfo): Boolean = root.findAny {
        it.contentDescription?.toString()?.startsWith("Reel de ") == true
    }
}
