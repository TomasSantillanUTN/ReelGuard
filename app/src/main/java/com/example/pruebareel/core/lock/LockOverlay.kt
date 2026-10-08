package com.example.pruebareel.core.lock

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.example.pruebareel.R
import java.util.Locale

/**
 * Gestiona la ventana flotante (overlay) que bloquea la pantalla.
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

    /**
     * Muestra el overlay bloqueante.
     *
     * @param endTimeMillis El timestamp en el que debe finalizar el bloqueo.
     */
    fun show(endTimeMillis: Long) {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        overlayView = LayoutInflater.from(context).inflate(R.layout.layout_lock_overlay, null)
        
        val tvTimer = overlayView?.findViewById<TextView>(R.id.tvTimer)
        val btnHome = overlayView?.findViewById<Button>(R.id.btnReturnHome)

        btnHome?.setOnClickListener {
            hide()
            onReturnHome()
        }

        updateRunnable = object : Runnable {
            override fun run() {
                val remaining = endTimeMillis - System.currentTimeMillis()
                if (remaining > 0) {
                    val seconds = (remaining / 1000) % 60
                    val minutes = (remaining / (1000 * 60)) % 60
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
        }
    }

    /**
     * Remueve el overlay de la pantalla.
     */
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
