package com.example.pruebareel.feature.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.pruebareel.core.lock.LockManager
import com.example.pruebareel.core.lock.LockOverlay
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

    private lateinit var settingsRepository: SettingsRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var lockManager: LockManager
    private lateinit var lockOverlay: LockOverlay

    private var reelsEnabled = true
    private var maxReels: Int = 5
    private var lockDurationSeconds: Int = 10
    private var reelCount = 0
    private var inReelViewer = false

    private var lastReelCountTime = 0L
    private val DEBOUNCE_TIME_MS = 500L

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsRepository = SettingsRepository(this)
        lockManager = LockManager(settingsRepository)
        
        // Inicializamos el overlay con la acción de volver al Home y limpiar el bloqueo
        lockOverlay = LockOverlay(this) {
            serviceScope.launch {
                lockManager.clearLock()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }

        // PERSISTENCIA: Chequeo inicial al conectar el servicio
        checkPersistentLock()

        serviceScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                reelsEnabled = settings.reelsEnabled
                maxReels = settings.reelsLimit
                lockDurationSeconds = settings.lockDurationSeconds
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !reelsEnabled) return

        // GESTIÓN DE VISIBILIDAD DEL OVERLAY
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (packageName == "com.instagram.android") {
                // Si el usuario está en Instagram, verificamos si debe estar bloqueado
                checkPersistentLock()
            } else {
                // Si sale de Instagram, ocultamos el overlay para no bloquear otras apps
                lockOverlay.hide()
            }
        }

        // Si no estamos en Instagram, no procesamos el conteo
        if (event.packageName != "com.instagram.android") return

        val root = rootInActiveWindow ?: return
        inReelViewer = isFullScreenReel(event, root)

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (inReelViewer) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastReelCountTime > DEBOUNCE_TIME_MS) {
                        lastReelCountTime = currentTime
                        reelCount++

                        // TRIGGER: Al llegar al límite, disparamos el bloqueo
                        if (reelCount >= maxReels) {
                            triggerLock()
                        }
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (!inReelViewer) {
                    reelCount = 0
                }
            }
        }
    }

    /**
     * Verifica si hay un bloqueo activo en DataStore y muestra el overlay si corresponde.
     */
    private fun checkPersistentLock() {
        serviceScope.launch {
            val remaining = lockManager.getRemainingMillis()
            if (remaining > 0) {
                // Solo mostramos si Instagram es la app activa
                if (rootInActiveWindow?.packageName == "com.instagram.android") {
                    lockOverlay.show(System.currentTimeMillis() + remaining)
                }
            } else {
                // Si el tiempo expiró, ocultamos y limpiamos el estado
                lockManager.clearLock()
                lockOverlay.hide()
            }
        }
    }

    /**
     * Inicia el periodo de bloqueo persistente.
     */
    private fun triggerLock() {
        reelCount = 0
        serviceScope.launch {
            lockManager.startLock(lockDurationSeconds)
            checkPersistentLock()
        }
    }

    private fun isFullScreenReel(event: AccessibilityEvent, root: AccessibilityNodeInfo): Boolean {
        val srcClass = event.source?.className?.toString() ?: ""
        val fromViewPager = srcClass.contains("ViewPager", ignoreCase = true)
        if (!fromViewPager) return false

        return root.findAny {
            it.contentDescription?.toString()?.startsWith("Reel de ") == true
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        lockOverlay.hide()
        serviceScope.cancel()
    }
}
