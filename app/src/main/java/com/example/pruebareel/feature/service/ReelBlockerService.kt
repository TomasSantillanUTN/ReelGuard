package com.example.pruebareel.feature.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
 * Servicio de Accesibilidad para bloquear Instagram Reels después de un límite.
 *
 * Implementa un bloqueo persistente mediante un overlay que sobrevive a reinicios del proceso.
 */
class ReelBlockerService : AccessibilityService() {

    companion object {
        private const val INSTAGRAM = "com.instagram.android"
        /** La barra de notificaciones / panel de volumen no cuentan como "salir" de Instagram. */
        private const val SYSTEM_UI = "com.android.systemui"
        private const val DEBOUNCE_TIME_MS = 500L

        /**
         * Tiempo de gracia antes de reiniciar el contador al salir de Reels.
         * Evita reinicios por pantallas "intermedias" (comentarios, compartir, perfil rápido, etc.).
         */
        private const val RESET_GRACE_MS = 3_000L
    }

    private lateinit var settingsRepository: SettingsRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var lockManager: LockManager
    private lateinit var lockOverlay: LockOverlay

    private var reelsEnabled = true
    private var maxReels: Int = 5
    private var lockDurationSeconds: Int = 10
    private var blockNavigation = true
    private var reelCount = 0

    private var lastReelCountTime = 0L
    /** Última vez que se vio al usuario en Reels (o en los comentarios de un Reel). */
    private var lastReelActivityTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsRepository = SettingsRepository(this)
        lockManager = LockManager(settingsRepository, LockTarget.INSTAGRAM)

        lockOverlay = LockOverlay(this) {
            serviceScope.launch {
                lockManager.clearLock()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }

        checkPersistentLock()

        serviceScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                reelsEnabled = settings.reelsEnabled
                maxReels = settings.reelsLimit
                lockDurationSeconds = settings.lockDurationSeconds
                blockNavigation = settings.blockNavigation
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !reelsEnabled) return
        val pkg = event.packageName?.toString()

        // GESTIÓN DE VISIBILIDAD DEL OVERLAY
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg == INSTAGRAM) {
                checkPersistentLock()
            } else if (pkg != null && pkg != packageName && pkg != SYSTEM_UI && !blockNavigation) {
                // Modo no bloqueante: si el usuario se va a otra app, liberamos la pantalla.
                // Cuando vuelva a Instagram, checkPersistentLock() lo muestra de nuevo.
                lockOverlay.hide()
            }
        }

        if (pkg != INSTAGRAM) return

        val root = rootInActiveWindow ?: return
        val now = System.currentTimeMillis()

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (isFullScreenReel(event, root)) {
                    lastReelActivityTime = now
                    if (now - lastReelCountTime > DEBOUNCE_TIME_MS) {
                        lastReelCountTime = now
                        reelCount++
                        if (reelCount >= maxReels) {
                            triggerLock()
                        }
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // FIX comentarios: antes se reiniciaba con cualquier cambio de ventana (por ejemplo
                // al abrir los comentarios). Ahora solo se reinicia si el usuario realmente salió de
                // Reels y pasó un tiempo de gracia.
                if (hasReelLabel(root) || looksLikeComments(root)) {
                    lastReelActivityTime = now
                } else if (now - lastReelActivityTime > RESET_GRACE_MS) {
                    reelCount = 0
                }
            }
        }
    }

    /** Verifica si hay un bloqueo activo en DataStore y muestra el overlay si corresponde. */
    private fun checkPersistentLock() {
        serviceScope.launch {
            val remaining = lockManager.getRemainingMillis()
            if (remaining > 0) {
                if (rootInActiveWindow?.packageName == INSTAGRAM) {
                    lockOverlay.show(
                        endTimeMillis = System.currentTimeMillis() + remaining,
                        message = getString(R.string.lock_overlay_message),
                        blockNavigation = blockNavigation
                    )
                }
            } else if (!lockOverlay.isShowing) {
                // Si el overlay está visible con el tiempo cumplido, se deja hasta que toquen el botón.
                lockManager.clearLock()
            }
        }
    }

    private fun triggerLock() {
        reelCount = 0
        serviceScope.launch {
            lockManager.startLock(lockDurationSeconds)
            checkPersistentLock()
        }
    }

    private fun isFullScreenReel(event: AccessibilityEvent, root: AccessibilityNodeInfo): Boolean {
        val srcClass = event.source?.className?.toString() ?: ""
        if (!srcClass.contains("ViewPager", ignoreCase = true)) return false
        return hasReelLabel(root)
    }

    private fun hasReelLabel(root: AccessibilityNodeInfo): Boolean = root.findAny {
        it.contentDescription?.toString()?.startsWith("Reel de ") == true
    }

    /**
     * Heurística: la hoja de comentarios tiene un campo de texto (EditText) para escribir un
     * comentario ("Agrega un comentario…" / "Add a comment…").
     * Igual que el resto de las heurísticas, depende del diseño actual de Instagram.
     */
    private fun looksLikeComments(root: AccessibilityNodeInfo): Boolean = root.findAny { node ->
        if (node.className?.toString()?.contains("EditText") != true) return@findAny false
        val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.hintText else null
        listOfNotNull(node.text, hint, node.contentDescription).any {
            val s = it.toString()
            s.contains("comentario", ignoreCase = true) || s.contains("comment", ignoreCase = true)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        lockOverlay.hide()
        serviceScope.cancel()
    }
}
