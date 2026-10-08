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
 * - **Bloqueante** (`blockNavigation = true`): ocupa toda la pantalla, toma el foco y no deja
 *   usar los botones del sistema (comportamiento original).
 * - **No bloqueante** (`blockNavigation = false`): no toma el foco y deja libre la zona de las
 *   barras del sistema, así se puede usar Home / Volver / Recientes y seguir usando el celular.
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

    val isShowing: Boolean get() = overlayView != null

    /**
     * Muestra el overlay.
     *
     * @param endTimeMillis El timestamp en el que debe finalizar el bloqueo.
     * @param message Texto que se muestra arriba de la cuenta regresiva.
     * @param blockNavigation Si es `true` se usa el modo bloqueante.
     */
    fun show(endTimeMillis: Long, message: String, blockNavigation: Boolean) {
        if (overlayView != null) return

        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (blockNavigation) {
            flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            // Sin foco: las teclas/gestos del sistema (Volver, Home, Recientes) siguen funcionando.
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        if (!blockNavigation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // No tapar la barra de navegación ni la de estado.
            params.fitInsetsTypes = WindowInsets.Type.systemBars()
        }

        overlayView = LayoutInflater.from(context).inflate(R.layout.layout_lock_overlay, null)

        val tvMessage = overlayView?.findViewById<TextView>(R.id.tvMessage)
        val tvTimer = overlayView?.findViewById<TextView>(R.id.tvTimer)
        val btnHome = overlayView?.findViewById<Button>(R.id.btnReturnHome)

        tvMessage?.text = message

        btnHome?.setOnClickListener {
            hide()
            onReturnHome()
        }

        updateRunnable = object : Runnable {
            override fun run() {
                val remaining = endTimeMillis - System.currentTimeMillis()
                if (remaining > 0) {
                    val totalSeconds = (remaining + 999) / 1000 // redondeo hacia arriba
                    val minutes = totalSeconds / 60
                    val seconds = totalSeconds % 60
                    tvTimer?.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                    btnHome?.isEnabled = false
                    handler.postDelayed(this, 500)
                } else {
                    tvTimer?.text = "00:00"
                    btnHome?.isEnabled = true
                }
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
