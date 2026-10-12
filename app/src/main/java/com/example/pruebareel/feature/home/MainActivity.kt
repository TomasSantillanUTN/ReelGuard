package com.example.pruebareel.feature.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pruebareel.ui.theme.PruebareelTheme

/**
 * Actividad principal de la aplicación.
 *
 * Muestra [HomeScreen]. Ya no abre los ajustes de permisos sola al iniciar: la pantalla muestra
 * qué falta y el usuario decide cuándo ir a activarlo.
 */
class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm = homeViewModel
        val actions = HomeActions(
            onOpenAccessibilitySettings = {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onOpenOverlaySettings = {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            },
            onReelsEnabledChange = vm::onReelsEnabledChange,
            onReelsLimitChange = vm::onReelsLimitChange,
            onReelsDailyLimitChange = vm::onReelsDailyLimitChange,
            onShortsEnabledChange = vm::onShortsEnabledChange,
            onShortsLimitChange = vm::onShortsLimitChange,
            onShortsDailyLimitChange = vm::onShortsDailyLimitChange,
            onTiktokEnabledChange = vm::onTiktokEnabledChange,
            onTiktokLimitChange = vm::onTiktokLimitChange,
            onTiktokDailyLimitChange = vm::onTiktokDailyLimitChange,
            onLockDurationChange = vm::onLockDurationChange,
            onBlockNavigationChange = vm::onBlockNavigationChange,
            onQuietHoursEnabledChange = vm::onQuietHoursEnabledChange,
            onQuietHoursChange = vm::onQuietHoursChange
        )

        setContent {
            PruebareelTheme {
                val state by vm.uiState.collectAsStateWithLifecycle()
                HomeScreen(state = state, actions = actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Al volver de Ajustes, actualizar si se activó el servicio o el permiso.
        homeViewModel.refreshPermissions()
    }
}
