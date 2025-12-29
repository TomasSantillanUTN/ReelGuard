package com.example.pruebareel.feature.home

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pruebareel.ui.theme.PruebareelTheme

/**
 * Actividad principal de la aplicación.
 *
 * Esta actividad es el punto de entrada de la interfaz de usuario. Se encarga de mostrar la pantalla
 * principal [HomeScreen] y de conectar el [HomeViewModel] para la gestión del estado.
 */
class MainActivity : ComponentActivity() {

    /**
     * Se llama cuando la actividad es creada por primera vez.
     *
     * Aquí se configura el contenido de la interfaz de usuario utilizando Jetpack Compose. Se establece
     * el tema de la aplicación y se inicializa [HomeScreen] con su estado y los manejadores de eventos
     * correspondientes desde el [HomeViewModel].
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PruebareelTheme {
                val homeViewModel: HomeViewModel = viewModel()

                val state by homeViewModel.uiState.collectAsStateWithLifecycle()

                HomeScreen(
                    state = state,
                    onReelsEnabledChange = homeViewModel::onReelsEnabledChange,
                    onReelsLimitChange = homeViewModel::onReelsLimitChange,
                    onShortsEnabledChange = homeViewModel::onShortsEnabledChange,
                    onShortsTimeChange = homeViewModel::onShortsTimeChange,
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }
        }
    }
}
