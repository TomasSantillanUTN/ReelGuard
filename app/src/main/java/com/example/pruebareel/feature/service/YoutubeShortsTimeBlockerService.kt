package com.example.pruebareel.feature.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
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
 * Servicio de Accesibilidad para limitar el tiempo de visualización de YouTube Shorts.
 *
 * **Importante:** Este servicio utiliza heurísticas para determinar si el usuario está en la
 * sección de Shorts de YouTube. Su funcionamiento es frágil y depende directamente de la
 * estructura de la interfaz de la app de YouTube. Si Google actualiza su diseño, es muy
 * probable que este servicio deje de funcionar correctamente.
 */
class YoutubeShortsTimeBlockerService : AccessibilityService() {

    private lateinit var settingsRepository: SettingsRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var shortsEnabled = true
    private var maxTimeMs: Long = 5_000L

    /**
     * Se llama cuando el sistema conecta con el servicio.
     *
     * Inicializa el [SettingsRepository] y comienza a observar el `settingsFlow` para recibir
     * actualizaciones en tiempo real sobre si el servicio está habilitado y cuál es el tiempo
     * máximo de visualización permitido.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsRepository = SettingsRepository(this)

        serviceScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                shortsEnabled = settings.shortsEnabled
                maxTimeMs = settings.shortsTimeMs
            }
        }
    }

    private var inShorts = false
    private var startTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var checkerRunning = false

    /**
     * Callback que se ejecuta cuando ocurre un evento de accesibilidad.
     *
     * Filtra los eventos para procesar solo los de la app de YouTube. Detecta si el usuario
     * entra o sale de la sección de Shorts para iniciar o detener el temporizador de bloqueo.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !shortsEnabled) return
        if (event.packageName != "com.google.android.youtube") return

        val root = rootInActiveWindow ?: return

        val isShortsNow = isYoutubeShorts(root)

        if (isShortsNow && !inShorts) {
            inShorts = true
            startTime = System.currentTimeMillis()
            startTimer()
        }

        if (!isShortsNow && inShorts) {
            stopTimer()
            inShorts = false
            startTime = 0L
        }
    }

    private fun startTimer() {
        if (checkerRunning) return
        checkerRunning = true
        handler.post(checkRunnable)
    }

    private fun stopTimer() {
        checkerRunning = false
        handler.removeCallbacks(checkRunnable)
    }

    /**
     * Un [Runnable] que se ejecuta periódicamente para comprobar si se ha excedido el tiempo
     * de visualización de Shorts. Si se supera, llama a [blockYoutube].
     */
    private val checkRunnable = object : Runnable {
        override fun run() {

            if (!checkerRunning || !shortsEnabled) {
                stopTimer()
                return
            }

            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed >= maxTimeMs) {
                blockYoutube()
                return
            }

            handler.postDelayed(this, 200)
        }
    }

    /**
     * Determina si la pantalla actual corresponde a la interfaz de YouTube Shorts.
     *
     * La heurística se basa en la presencia de dos elementos en la jerarquía de vistas:
     * 1. Un nodo cuyo texto o descripción contenga la palabra "Short".
     * 2. Un nodo que sea una `SeekBar` (la barra de progreso del vídeo).
     *
     * @return `true` si la detección es positiva, `false` en caso contrario.
     */
    private fun isYoutubeShorts(root: AccessibilityNodeInfo): Boolean {

        val hasShortLabel = root.findAny {
            it.contentDescription?.contains("Short", ignoreCase = true) == true ||
                it.text?.toString()?.contains("Short", ignoreCase = true) == true
        }

        val hasSeekBar = root.findAny {
            it.className == "android.widget.SeekBar"
        }

        return hasShortLabel && hasSeekBar
    }

    /**
     * Realiza la acción de bloqueo: detiene el temporizador, lleva al usuario a la pantalla
     * de inicio y muestra una notificación Toast.
     */
    private fun blockYoutube() {
        stopTimer()
        inShorts = false
        startTime = 0L
        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(this, getString(R.string.shorts_blocker_toast), Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
