package com.example.pruebareel.feature.service.modules

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.pruebareel.core.lock.LockReason
import com.example.pruebareel.core.lock.LockTarget
import com.example.pruebareel.core.utils.findAny
import com.example.pruebareel.core.utils.hasFullScreenPager
import com.example.pruebareel.core.utils.looksLikeCommentsSheet
import com.example.pruebareel.data.preferences.AppSettings
import com.example.pruebareel.data.stats.StatMetric
import com.example.pruebareel.feature.service.BlockerModule
import com.example.pruebareel.feature.service.ReelGuardService

/**
 * Instagram Reels: cuenta los Reels seguidos y los del día.
 *
 * **Sección de Reels** = hay un ViewPager que ocupa casi toda la pantalla Y nodos con
 * descripción "Reel de …". Ojo: los Reels que aparecen en el feed principal también tienen
 * "Reel de …", pero ahí no hay un ViewPager a pantalla completa, así que el feed (y abrir los
 * comentarios desde el feed) NO cuenta como sección de Reels.
 *
 * Si Instagram cambia su diseño, hay que actualizar estas heurísticas.
 */
class InstagramModule(private val host: ReelGuardService) : BlockerModule {

    companion object {
        private const val TAG = "ReelGuard-Instagram"
        private const val DEBOUNCE_TIME_MS = 500L
        /** Tiempo de gracia antes de reiniciar el contador al salir de Reels. */
        private const val RESET_GRACE_MS = 3_000L
        /** Los cambios de contenido llegan muchísimas veces por segundo: se revisan cada tanto. */
        private const val CONTENT_CHECK_INTERVAL_MS = 700L
    }

    override val target = LockTarget.INSTAGRAM
    override val packages = setOf("com.instagram.android")

    private var reelCount = 0
    private var lastReelCountTime = 0L
    private var lastReelActivityTime = 0L
    private var lastContentCheck = 0L
    private var wasInReels = false

    override fun isEnabled(settings: AppSettings) = settings.reelsEnabled

    override fun onAppEvent(event: AccessibilityEvent, root: AccessibilityNodeInfo, now: Long) {
        val s = host.settings ?: return
        val type = event.eventType

        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (now - lastContentCheck < CONTENT_CHECK_INTERVAL_MS) return
            lastContentCheck = now
        }

        val inReels = isReelsSection(root)
        if (inReels != wasInReels) {
            wasInReels = inReels
            Log.d(TAG, "seccion de Reels: $inReels (count=$reelCount)")
        }

        // Horario sin videos / límite diario: solo se saca al usuario de la sección de Reels.
        if (inReels) {
            lastReelActivityTime = now
            if (host.enforceUsageRules(target, dailyLimitReached(s), ::stillInReels)) return
        }

        when (type) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (!inReels || !isFromViewPager(event)) return
                if (now - lastReelCountTime > DEBOUNCE_TIME_MS) {
                    lastReelCountTime = now
                    reelCount++
                    host.addStat(StatMetric.IG_REELS)

                    when {
                        reelCount >= s.reelsLimit -> host.requestLock(target, LockReason.STREAK)
                        dailyLimitReached(s) ->
                            host.kickOutOfSection(target, LockReason.DAILY_LIMIT, ::stillInReels)
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Abrir comentarios / compartir no reinicia el contador: solo se reinicia si el
                // usuario salió de Reels de verdad y pasó un tiempo de gracia.
                if (!inReels && !root.looksLikeCommentsSheet() &&
                    now - lastReelActivityTime > RESET_GRACE_MS
                ) {
                    reelCount = 0
                }
            }
        }
    }

    override fun onLocked() {
        reelCount = 0
    }

    private fun stillInReels(): Boolean = host.rootInActiveWindow?.let { isReelsSection(it) } == true

    private fun dailyLimitReached(s: AppSettings) =
        s.reelsDailyLimit > 0 && host.todayStat(StatMetric.IG_REELS) >= s.reelsDailyLimit

    private fun isReelsSection(root: AccessibilityNodeInfo): Boolean =
        hasReelLabel(root) && root.hasFullScreenPager(
            screenHeight = host.resources.displayMetrics.heightPixels,
            classFilter = { it.contains("ViewPager", ignoreCase = true) }
        )

    private fun isFromViewPager(event: AccessibilityEvent): Boolean =
        event.source?.className?.toString()?.contains("ViewPager", ignoreCase = true) == true

    private fun hasReelLabel(root: AccessibilityNodeInfo): Boolean = root.findAny {
        it.contentDescription?.toString()?.startsWith("Reel de ") == true
    }
}
