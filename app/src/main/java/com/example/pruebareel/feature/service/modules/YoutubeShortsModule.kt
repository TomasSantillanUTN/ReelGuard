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
 * Lo que expone YouTube en el reproductor de Shorts (ver docs/logcat_youtube.txt):
 * - Un nodo con descripción **"Progreso del video"** y una **SeekBar** cuya descripción trae
 *   la duración ("0 minutos 3 segundos de 0 minutos 51 segundos").
 * - Un **RecyclerView que ocupa casi toda la pantalla** (el carrusel vertical de Shorts).
 * - La columna derecha (Me gusta, Remix…) NO aparece en el árbol de accesibilidad.
 *
 * YouTube manda muy pocos eventos al pasar de Short (a veces ninguno de scroll), así que un
 * Short nuevo se cuenta con cualquiera de estas dos señales:
 * 1. Scroll del carrusel a pantalla completa.
 * 2. Cambia la duración que muestra la barra de progreso (otro video).
 *
 * Si no cuenta bien, filtrar Logcat por "ReelGuard-YouTube".
 */
class YoutubeShortsModule(private val host: ReelGuardService) : BlockerModule {

    companion object {
        private const val TAG = "ReelGuard-YouTube"
        /** Las dos señales pueden llegar separadas por la carga del video: se cuentan una vez. */
        private const val DEBOUNCE_TIME_MS = 1_500L
        private const val FULLSCREEN_RATIO = 0.8f

        private val PROGRESS_LABELS = setOf("progreso del video", "video progress")
        private val LOADING_LABELS = setOf("espera", "loading", "cargando")

        /** Si se vio un Short hace menos de esto y aparece la ventana flotante, se cierra. */
        private const val PIP_AFTER_SHORTS_MS = 3_000L
        private const val PIP_DISMISS_THROTTLE_MS = 1_500L
    }

    override val target = LockTarget.YOUTUBE
    override val packages = setOf("com.google.android.youtube")

    private var shortsCount = 0
    private var lastCountTime = 0L
    private var wasInShorts = false
    private var lastShortsSeenTime = 0L
    private var lastDuration: String? = null
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
        val type = event.eventType
        val inShorts = isShortsScreen(root)
        val duration = if (inShorts) videoDuration(root) else null

        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || duration != lastDuration) {
            Log.d(TAG, "evento=${AccessibilityEvent.eventTypeToString(type)} inShorts=$inShorts duracion=$duration count=$shortsCount")
        }

        if (inShorts) {
            lastShortsSeenTime = now
            // Al entrar a Shorts: horario sin videos o límite diario → no se deja ni empezar.
            if (!wasInShorts) {
                wasInShorts = true
                if (host.enforceUsageRules(target, dailyLimitReached(s))) {
                    host.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    return
                }
            }
        }

        when (type) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val source = event.source
                if (inShorts && source != null && isFullScreen(source)) countShort(s, now, "scroll")
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (duration != null) {
                    val previous = lastDuration
                    lastDuration = duration
                    if (previous != null && previous != duration) countShort(s, now, "otra duración")
                }
                // Salió de Shorts de verdad (no es la pantalla de carga ni los comentarios).
                if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    !inShorts && !isLoading(root) && !root.looksLikeCommentsSheet()
                ) {
                    resetSession()
                }
            }
        }
    }

    private fun countShort(s: AppSettings, now: Long, why: String) {
        if (now - lastCountTime < DEBOUNCE_TIME_MS) return
        lastCountTime = now
        shortsCount++
        host.addStat(StatMetric.YT_SHORTS)
        Log.d(TAG, "Short contado ($why): $shortsCount / ${s.shortsLimit}")

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

    private fun resetSession() {
        if (wasInShorts) Log.d(TAG, "Salió de Shorts: contador a 0")
        shortsCount = 0
        wasInShorts = false
        lastDuration = null
    }

    override fun onLocked() {
        shortsCount = 0
        lastDuration = null
    }

    private fun dailyLimitReached(s: AppSettings) =
        s.shortsDailyLimit > 0 && host.todayStat(StatMetric.YT_SHORTS) >= s.shortsDailyLimit

    // ---------- Detección ----------

    /** Reproductor de Shorts: barra/indicador de progreso + carrusel a pantalla completa. */
    private fun isShortsScreen(root: AccessibilityNodeInfo): Boolean {
        val hasProgress = root.findAny { n ->
            n.className == "android.widget.SeekBar" ||
                n.contentDescription?.toString()?.trim()?.lowercase() in PROGRESS_LABELS
        }
        if (!hasProgress) return false
        return root.findAny { n ->
            val cls = n.className?.toString() ?: return@findAny false
            (cls.contains("RecyclerView") || cls.contains("ViewPager")) && isFullScreen(n)
        }
    }

    /** "0 minutos 51 segundos": la parte de la descripción de la SeekBar después de " de " / " of ". */
    private fun videoDuration(root: AccessibilityNodeInfo): String? {
        var result: String? = null
        root.findAny { n ->
            if (n.className != "android.widget.SeekBar") return@findAny false
            val desc = n.contentDescription?.toString() ?: return@findAny false
            val idx = maxOf(desc.lastIndexOf(" de "), desc.lastIndexOf(" of "))
            result = if (idx >= 0) desc.substring(idx + 4).trim() else null
            true
        }
        return result
    }

    private fun isLoading(root: AccessibilityNodeInfo): Boolean = root.findAny { n ->
        n.contentDescription?.toString()?.trim()?.lowercase() in LOADING_LABELS
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
