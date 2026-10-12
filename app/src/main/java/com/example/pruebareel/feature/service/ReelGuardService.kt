package com.example.pruebareel.feature.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.example.pruebareel.R
import com.example.pruebareel.core.lock.LockManager
import com.example.pruebareel.core.lock.LockOverlay
import com.example.pruebareel.core.lock.LockReason
import com.example.pruebareel.core.lock.LockState
import com.example.pruebareel.core.lock.LockTarget
import com.example.pruebareel.data.preferences.AppSettings
import com.example.pruebareel.data.preferences.SettingsRepository
import com.example.pruebareel.data.stats.StatMetric
import com.example.pruebareel.data.stats.StatsRepository
import com.example.pruebareel.feature.service.modules.InstagramModule
import com.example.pruebareel.feature.service.modules.TikTokModule
import com.example.pruebareel.feature.service.modules.YoutubeShortsModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Único servicio de accesibilidad de ReelGuard.
 *
 * Reemplaza a los tres servicios anteriores (Instagram, YouTube y TikTok): el usuario activa
 * un solo servicio y este reparte los eventos a cada [BlockerModule].
 *
 * Lo común vive acá:
 * - La pantalla de bloqueo con cuenta regresiva (límite de videos seguidos).
 * - El horario sin videos y los límites diarios: estos NO bloquean la app entera, solo sacan
 *   al usuario de la sección de videos (Reels / Shorts / feed de TikTok) y muestran un aviso.
 * - Las estadísticas.
 */
class ReelGuardService : AccessibilityService() {

    companion object {
        const val SYSTEM_UI = "com.android.systemui"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var statsRepository: StatsRepository
    private lateinit var lockManager: LockManager
    private lateinit var overlay: LockOverlay

    private lateinit var modules: List<BlockerModule>

    /** Configuración actual. `null` hasta que se lee por primera vez. */
    var settings: AppSettings? = null
        private set

    /** Estadísticas de hoy leídas de disco. */
    private var storedToday: Map<StatMetric, Int> = emptyMap()
    private var storedTodayKey = ""
    /** Copia local de hoy, que se actualiza al instante (el disco tarda un poco). */
    private val localToday = mutableMapOf<StatMetric, Int>()
    private var localTodayKey = ""

    /** Bloqueos recién creados, hasta que DataStore los devuelva. */
    private val localLocks = mutableMapOf<LockTarget, LockState>()

    private var overlayTarget: LockTarget? = null
    private var overlayBlocksNavigation = false

    // =====================================================================================
    // Ciclo de vida
    // =====================================================================================

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsRepository = SettingsRepository(this)
        statsRepository = StatsRepository(this)
        lockManager = LockManager(settingsRepository)
        overlay = LockOverlay(this) { onOverlayHomePressed() }

        modules = listOf(
            InstagramModule(this),
            YoutubeShortsModule(this),
            TikTokModule(this)
        )

        scope.launch {
            settingsRepository.settingsFlow.collect { s ->
                val first = settings == null
                settings = s
                // Los bloqueos que ya llegaron a disco no hace falta tenerlos en memoria.
                localLocks.entries.removeAll { (t, l) -> s.locks[t]?.endTimestamp == l.endTimestamp }
                if (first) showOverlayForForegroundApp()
            }
        }
        scope.launch {
            statsRepository.lastWeekFlow.collect { week ->
                week.firstOrNull()?.let {
                    storedToday = it.values
                    storedTodayKey = it.dayKey
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val s = settings ?: return
        val pkg = event.packageName?.toString()
        val now = System.currentTimeMillis()
        val module = moduleFor(pkg)

        // 1) Pantalla de bloqueo: mostrar al entrar a una app bloqueada, ocultar al salir.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (module != null && module.isEnabled(s)) {
                // Si quedó visible la pantalla de otra app (modo no bloqueante), se cambia.
                if (overlay.isShowing && overlayTarget != module.target && !overlayBlocksNavigation) {
                    overlay.hide()
                }
                showOverlayIfLocked(module.target, eventFromTargetApp = true)
            } else if (pkg != null && pkg != packageName && pkg != SYSTEM_UI && !overlayBlocksNavigation) {
                overlay.hide()
            }
        }

        // 2) Eventos globales (ej. YouTube necesita ver la ventana flotante).
        modules.forEach { if (it.isEnabled(s)) it.onAnyEvent(event, now) }

        // 3) Eventos de la app del módulo.
        if (module == null || !module.isEnabled(s)) return

        // Red de seguridad: si hay un bloqueo activo y la pantalla no está visible (por ejemplo,
        // se volvió a la app desde el inicio del celular), se vuelve a mostrar con CUALQUIER
        // evento de la app, no solo cuando se abre una pantalla nueva.
        if (!overlay.isShowing && event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val lock = currentLock(module.target)
            if (lock != null && lock.reason == LockReason.STREAK && lock.isActive(now) &&
                rootInActiveWindow?.packageName?.toString() in module.packages
            ) {
                showOverlayIfLocked(module.target, eventFromTargetApp = true)
            }
        }

        if (isLocked(module.target, now)) return

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() !in module.packages) return
        module.onAppEvent(event, root, now)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (::modules.isInitialized) modules.forEach { it.onDestroy() }
        if (::overlay.isInitialized) overlay.hide()
        scope.cancel()
    }

    // =====================================================================================
    // API para los módulos
    // =====================================================================================

    /** Valor de hoy de una métrica (incluye lo que todavía no se guardó en disco). */
    fun todayStat(metric: StatMetric): Int {
        val today = StatsRepository.dayKey()
        val stored = if (storedTodayKey == today) storedToday[metric] ?: 0 else 0
        val local = if (localTodayKey == today) localToday[metric] ?: 0 else 0
        return maxOf(stored, local)
    }

    fun addStat(metric: StatMetric, amount: Int = 1) {
        if (amount <= 0) return
        val today = StatsRepository.dayKey()
        if (localTodayKey != today) {
            localToday.clear()
            localTodayKey = today
        }
        localToday[metric] = todayStat(metric) + amount
        scope.launch { statsRepository.add(metric, amount) }
    }

    fun isLocked(target: LockTarget, now: Long = System.currentTimeMillis()): Boolean =
        currentLock(target)?.isActive(now) == true || (overlay.isShowing && overlayTarget == target)

    private val handler = Handler(Looper.getMainLooper())
    private val lastKick = mutableMapOf<LockTarget, Long>()

    /**
     * Revisa el horario sin videos y el límite diario. Si corresponde, saca al usuario de la
     * sección de videos y devuelve `true`. Los módulos lo llaman SOLO cuando detectan que el
     * usuario está en la sección de videos; el resto de la app se puede usar normalmente.
     *
     * @param stillInSection se consulta 1 s después para ver si la salida funcionó.
     */
    fun enforceUsageRules(
        target: LockTarget,
        dailyLimitReached: Boolean,
        stillInSection: () -> Boolean
    ): Boolean {
        val reason = when {
            isQuietHoursNow() -> LockReason.QUIET_HOURS
            dailyLimitReached -> LockReason.DAILY_LIMIT
            else -> return false
        }
        kickOutOfSection(target, reason, stillInSection)
        return true
    }

    /**
     * Saca al usuario de la sección de videos: primero intenta que el módulo navegue a otra
     * parte de la app (ej. el perfil en TikTok); si no, "Volver". Si 1 s después sigue en la
     * sección, va al inicio del celular.
     */
    fun kickOutOfSection(target: LockTarget, reason: LockReason, stillInSection: () -> Boolean) {
        val now = System.currentTimeMillis()
        val last = lastKick[target] ?: 0L
        if (now - last < 1_500) return
        lastKick[target] = now
        if (now - last > 60_000) addStat(StatMetric.LOCKS) // una vez por "intento", no por cada evento

        Toast.makeText(this, sectionBlockedMessage(target, reason), Toast.LENGTH_LONG).show()

        val module = moduleFor(target)
        val escaped = rootInActiveWindow?.let { module.escapeSection(it) } == true
        if (!escaped) performGlobalAction(GLOBAL_ACTION_BACK)

        handler.postDelayed({
            if (foregroundPackage() in module.packages && stillInSection()) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }, 1_000)
    }

    private fun sectionBlockedMessage(target: LockTarget, reason: LockReason): String {
        val section = when (target) {
            LockTarget.INSTAGRAM -> "Reels"
            LockTarget.YOUTUBE -> "Shorts"
            LockTarget.TIKTOK -> "Videos de TikTok"
        }
        return if (reason == LockReason.QUIET_HOURS) {
            val end = settings?.quietEndMinute ?: 0
            getString(
                R.string.section_blocked_quiet, section,
                String.format(Locale.US, "%02d:%02d", end / 60, end % 60)
            )
        } else {
            getString(R.string.section_blocked_daily, section)
        }
    }

    /** Activa un bloqueo para [target] y muestra la pantalla si la app está abierta. */
    fun requestLock(target: LockTarget, reason: LockReason) {
        val s = settings ?: return
        val now = System.currentTimeMillis()
        val end = when (reason) {
            LockReason.STREAK -> now + s.lockDurationSeconds * 1000L
            LockReason.DAILY_LIMIT -> nextMidnight(now)
            LockReason.QUIET_HOURS -> quietHoursEnd(s, now)
        }
        val state = LockState(end, reason)
        localLocks[target] = state
        moduleFor(target).onLocked()
        addStat(StatMetric.LOCKS)
        scope.launch { lockManager.startLock(target, end, reason) }
        showOverlayIfLocked(target, eventFromTargetApp = true) // los módulos solo bloquean con la app abierta
    }

    /** Paquete de la app que está realmente en primer plano. */
    fun foregroundPackage(): String? {
        val fromWindows = try {
            windows.firstOrNull {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isFocused || it.isActive)
            }?.root?.packageName?.toString()
        } catch (e: Exception) {
            null
        }
        return fromWindows ?: rootInActiveWindow?.packageName?.toString()
    }

    // =====================================================================================
    // Pantalla de bloqueo
    // =====================================================================================

    private fun currentLock(target: LockTarget): LockState? {
        val local = localLocks[target]
        val stored = settings?.locks?.get(target)
        return listOfNotNull(local, stored).maxByOrNull { it.endTimestamp }
    }

    private fun showOverlayForForegroundApp() {
        val fg = foregroundPackage() ?: return
        moduleFor(fg)?.let { showOverlayIfLocked(it.target) }
    }

    /**
     * @param eventFromTargetApp `true` si el evento que lo disparó vino de la app bloqueada:
     *   en ese caso no se espera a que la lista de ventanas se actualice (a veces tarda).
     */
    private fun showOverlayIfLocked(target: LockTarget, eventFromTargetApp: Boolean = false) {
        val s = settings ?: return
        val lock = currentLock(target)

        // Versiones anteriores guardaban bloqueos de toda la app por horario / límite diario.
        // Ahora esos solo afectan a la sección de videos: se borran si quedó alguno.
        if (lock != null && lock.reason != LockReason.STREAK) {
            localLocks.remove(target)
            scope.launch { lockManager.clearLock(target) }
            return
        }

        if (lock == null || !lock.isActive()) {
            // Venció: se limpia, salvo que la pantalla siga visible esperando que toquen el botón.
            if (lock != null && !(overlay.isShowing && overlayTarget == target)) {
                localLocks.remove(target)
                scope.launch { lockManager.clearLock(target) }
            }
            return
        }
        if (overlay.isShowing) return
        if (!eventFromTargetApp && foregroundPackage() !in moduleFor(target).packages) return

        val block = lock.reason.canBlockNavigation && s.blockNavigation
        overlayTarget = target
        overlayBlocksNavigation = block
        overlay.show(
            endTimeMillis = lock.endTimestamp,
            message = overlayMessage(target, lock.reason),
            blockNavigation = block,
            shouldStayVisible = { stillIn(target) }
        )
    }

    private fun stillIn(target: LockTarget): Boolean {
        val fg = foregroundPackage() ?: return true
        return fg in moduleFor(target).packages || fg == SYSTEM_UI || fg == packageName
    }

    private fun onOverlayHomePressed() {
        val target = overlayTarget ?: return
        overlayTarget = null
        overlayBlocksNavigation = false
        localLocks.remove(target)
        scope.launch {
            lockManager.clearLock(target)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun overlayMessage(target: LockTarget, reason: LockReason): String = when (reason) {
        LockReason.QUIET_HOURS -> getString(R.string.lock_overlay_message_quiet)
        LockReason.DAILY_LIMIT -> getString(R.string.lock_overlay_message_daily)
        LockReason.STREAK -> when (target) {
            LockTarget.INSTAGRAM -> getString(R.string.lock_overlay_message)
            LockTarget.YOUTUBE -> getString(R.string.lock_overlay_message_shorts)
            LockTarget.TIKTOK -> getString(R.string.lock_overlay_message_tiktok)
        }
    }

    // =====================================================================================
    // Horarios
    // =====================================================================================

    private fun minuteOfDay(now: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun isQuietHoursNow(now: Long = System.currentTimeMillis()): Boolean {
        val s = settings ?: return false
        if (!s.quietHoursEnabled) return false
        val m = minuteOfDay(now)
        val start = s.quietStartMinute
        val end = s.quietEndMinute
        return when {
            start == end -> false
            start < end -> m in start until end // ej. 13:00 a 15:00
            else -> m >= start || m < end        // ej. 23:00 a 07:00 (cruza la medianoche)
        }
    }

    /** Próxima vez que el reloj marca la hora de fin del horario sin videos. */
    private fun quietHoursEnd(s: AppSettings, now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, s.quietEndMinute / 60)
            set(Calendar.MINUTE, s.quietEndMinute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun nextMidnight(now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private fun moduleFor(pkg: String?): BlockerModule? =
        if (pkg == null) null else modules.firstOrNull { pkg in it.packages }

    private fun moduleFor(target: LockTarget): BlockerModule = modules.first { it.target == target }
}
