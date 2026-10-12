package com.example.pruebareel.core.lock

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.example.pruebareel.R
import java.util.Locale

/**
 * Gestiona la ventana flotante (overlay) que bloquea la pantalla.
 *
 * Tiene dos modos:
 * - **Bloqueante** (`blockNavigation = true`): ocupa toda la pantalla y toma el foco.
 * - **No bloqueante** (`blockNavigation = false`):
 *   - Se dibuja SOLO entre la barra de estado y la barra de navegación (no las tapa), así
 *     Inicio / Volver / Recientes siguen funcionando.
 *   - No toma el foco (las teclas del sistema no las "consume" el overlay).
 *   - Cada 500 ms se pregunta [shouldStayVisible]; si devuelve `false` (por ejemplo porque el
 *     usuario se fue a otra app) el overlay se oculta solo.
 *
 * @param context El contexto del servicio.
 * @param onReturnHome Acción a ejecutar cuando se pulsa el botón de volver al home.
 */
class LockOverlay(
    private val context: Context,
    private val onReturnHome: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    companion object {
        /** Recién mostrado no se oculta: Android tarda un momento en informar qué app está abierta. */
        private const val HIDE_GRACE_MS = 1_500L
        /** Hacen falta estos chequeos seguidos (cada 500 ms) "fuera de la app" para ocultar. */
        private const val CHECKS_TO_HIDE = 2
    }

    val isShowing: Boolean get() = overlayView != null

    /**
     * Muestra el overlay.
     *
     * @param endTimeMillis El timestamp en el que debe finalizar el bloqueo.
     * @param message Texto que se muestra arriba de la cuenta regresiva.
     * @param blockNavigation Si es `true` se usa el modo bloqueante.
     * @param shouldStayVisible Solo en modo no bloqueante: si devuelve `false`, el overlay se oculta.
     */
    fun show(
        endTimeMillis: Long,
        message: String,
        blockNavigation: Boolean,
        shouldStayVisible: () -> Boolean = { true }
    ) {
        if (overlayView != null) return

        val params = if (blockNavigation) fullScreenParams() else betweenSystemBarsParams()

        overlayView = LayoutInflater.from(context).inflate(R.layout.layout_lock_overlay, null)

        val tvMessage = overlayView?.findViewById<TextView>(R.id.tvMessage)
        val tvTimer = overlayView?.findViewById<TextView>(R.id.tvTimer)
        val btnHome = overlayView?.findViewById<Button>(R.id.btnReturnHome)

        tvMessage?.text = message

        btnHome?.setOnClickListener {
            hide()
            onReturnHome()
        }

        val shownAt = System.currentTimeMillis()
        var outsideChecks = 0

        updateRunnable = object : Runnable {
            override fun run() {
                if (overlayView == null) return

                // Modo no bloqueante: si el usuario ya no está en la app bloqueada, se oculta.
                // Se espera un poco y se confirma 2 veces para no ocultarla por error justo al
                // volver a la app (cuando Android todavía informa el inicio del celular).
                if (!blockNavigation && System.currentTimeMillis() - shownAt > HIDE_GRACE_MS) {
                    outsideChecks = if (shouldStayVisible()) 0 else outsideChecks + 1
                    if (outsideChecks >= CHECKS_TO_HIDE) {
                        hide()
                        return
                    }
                }

                val remaining = endTimeMillis - System.currentTimeMillis()
                if (remaining > 0) {
                    val totalSeconds = (remaining + 999) / 1000
                    tvTimer?.text = if (totalSeconds >= 3600) {
                        // Bloqueos largos (límite diario / horario): H:MM:SS
                        String.format(
                            Locale.getDefault(), "%d:%02d:%02d",
                            totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60
                        )
                    } else {
                        String.format(
                            Locale.getDefault(), "%02d:%02d", totalSeconds / 60, totalSeconds % 60
                        )
                    }
                    btnHome?.isEnabled = false
                } else {
                    tvTimer?.text = "00:00"
                    btnHome?.isEnabled = true
                }
                handler.postDelayed(this, 500)
            }
        }

        try {
            windowManager.addView(overlayView, params)
            handler.post(updateRunnable!!)
        } catch (e: Exception) {
            e.printStackTrace()
            overlayView = null
        }
    }

    /** Pantalla completa, con foco: comportamiento bloqueante original. */
    private fun fullScreenParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.CENTER }

    /**
     * Ventana que ocupa solo el espacio ENTRE la barra de estado y la de navegación, sin foco.
     * El tamaño se calcula a mano para no depender de cómo cada fabricante trata MATCH_PARENT.
     */
    private fun betweenSystemBarsParams(): WindowManager.LayoutParams {
        var top: Int
        var bottom: Int
        val screenHeight: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            top = insets.top
            bottom = insets.bottom
            screenHeight = metrics.bounds.height()
        } else {
            top = systemDimen("status_bar_height")
            bottom = systemDimen("navigation_bar_height")
            @Suppress("DEPRECATION")
            screenHeight = context.resources.displayMetrics.heightPixels + bottom
        }

        // Margen extra para que la zona de gestos de abajo quede siempre libre.
        val extraBottom = (16 * context.resources.displayMetrics.density).toInt()
        bottom += extraBottom

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (screenHeight - top - bottom).coerceAtLeast(1),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = top
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0 // ya posicionamos la ventana a mano
            }
        }
    }

    private fun systemDimen(name: String): Int {
        val id = context.resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    /** Remueve el overlay de la pantalla. */
    fun hide() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }
}
