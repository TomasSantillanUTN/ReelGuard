package com.example.pruebareel.feature.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.pruebareel.R
import com.example.pruebareel.core.utils.findAny
import com.example.pruebareel.data.preferences.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Servicio de Accesibilidad para bloquear Instagram Reels después de un límite.
 *
 * **Importante:** Este servicio se basa en heurísticas para detectar cuándo el usuario está
 * viendo Reels. Su funcionamiento depende de la estructura de la interfaz de usuario de la app
 * de Instagram. Si Instagram actualiza su diseño, es muy probable que este servicio deje de
 * funcionar hasta que se actualicen las heurísticas de detección.
 */
class ReelBlockerService : AccessibilityService() {

    private lateinit var settingsRepository: SettingsRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var reelsEnabled = true
    private var maxReels: Int = 5
    private var reelCount = 0
    private var inReelViewer = false

    /**
     * Se llama cuando el sistema conecta con el servicio.
     *
     * Aquí se inicializa el repositorio de configuración y se comienza a observar el Flow
     * de ajustes para obtener los valores más recientes de `reelsEnabled` y `maxReels`.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsRepository = SettingsRepository(this)

        serviceScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                reelsEnabled = settings.reelsEnabled
                maxReels = settings.reelsLimit
            }
        }
    }

    /**
     * Callback principal que recibe los eventos de accesibilidad del sistema.
     *
     * Filtra los eventos para actuar solo sobre los de la app de Instagram. Llama a las
     * funciones de detección y, si se cumple la condición de bloqueo, activa la acción.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !reelsEnabled) return
        if (event.packageName != "com.instagram.android") return

        val root = rootInActiveWindow ?: return

        // Se determina si el usuario está en la pantalla de Reels a pantalla completa.
        inReelViewer = isFullScreenReel(event, root)

        when (event.eventType) {

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (inReelViewer) {
                    reelCount++

                    if (reelCount >= maxReels) {
                        blockInstagram()
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Si el usuario sale de la pantalla de Reels, se resetea el contador.
                if (!inReelViewer) {
                    reelCount = 0
                }
            }
        }
    }

    /**
     * Determina si el usuario está viendo un Reel en pantalla completa.
     *
     * La detección se basa en dos heurísticas:
     * 1. El evento de scroll proviene de un componente `ViewPager`.
     * 2. Existe un nodo en la pantalla cuya descripción de contenido empieza con "Reel de ".
     *
     * @return `true` si parece que el usuario está en la interfaz de Reels, `false` en caso contrario.
     */
    private fun isFullScreenReel(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo
    ): Boolean {

        val srcClass = event.source?.className?.toString() ?: ""

        val fromViewPager =
            srcClass.contains("ViewPager", ignoreCase = true)

        if (!fromViewPager) return false

        val hasReelLabel = root.findAny {
            val desc = it.contentDescription?.toString() ?: return@findAny false
            desc.startsWith("Reel de ")
        }

        return hasReelLabel
    }

    /**
     * Ejecuta la acción de bloqueo: resetea el contador, lleva al usuario a la pantalla de inicio
     * y muestra un mensaje informativo.
     */
    private fun blockInstagram() {
        reelCount = 0
        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(this, getString(R.string.reel_blocker_toast), Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {}

    /**
     * Se llama cuando el servicio se va a destruir. Cancela la corutina para evitar fugas de memoria.
     */
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
