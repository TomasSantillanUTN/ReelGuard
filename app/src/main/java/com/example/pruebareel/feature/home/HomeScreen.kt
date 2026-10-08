package com.example.pruebareel.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.example.pruebareel.data.preferences.SettingsRepository
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pruebareel.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onReelsEnabledChange: (Boolean) -> Unit,
    onReelsLimitChange: (Int) -> Unit,
    onShortsEnabledChange: (Boolean) -> Unit,
    onShortsTimeChange: (Int) -> Unit,
    onLockDurationChange: (Int) -> Unit,
    onBlockNavigationChange: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.home_screen_title),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.home_screen_subtitle),
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedButton(
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(id = R.string.home_open_accessibility_button))
            }

            // --- Bloque Instagram Reels ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = stringResource(id = R.string.reels_card_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(id = R.string.reels_card_subtitle),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = state.reelsEnabled,
                            onCheckedChange = onReelsEnabledChange
                        )
                    }

                    Text(
                        text = stringResource(id = R.string.reels_card_slider_label, state.reelsLimit),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Slider(
                        value = state.reelsLimit.toFloat(),
                        onValueChange = { onReelsLimitChange(it.toInt()) },
                        valueRange = 1f..20f,
                        steps = 18
                    )

                    Text(
                        text = stringResource(id = R.string.reels_card_recommendation),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // --- Bloque YouTube Shorts ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = stringResource(id = R.string.shorts_card_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(id = R.string.shorts_card_subtitle),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = state.shortsEnabled,
                            onCheckedChange = onShortsEnabledChange
                        )
                    }

                    Text(
                        text = stringResource(id = R.string.shorts_card_slider_label, state.shortsTimeSeconds),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Slider(
                        value = state.shortsTimeSeconds.toFloat(),
                        onValueChange = { onShortsTimeChange(it.toInt()) },
                        valueRange = 5f..180f,
                        steps = 35
                    )

                    Text(
                        text = stringResource(id = R.string.shorts_card_recommendation),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // --- Bloque Ajustes de Bloqueo ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.lock_settings_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(id = R.string.lock_settings_subtitle),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = stringResource(id = R.string.lock_settings_duration_label),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    LockDurationInput(
                        savedSeconds = state.lockDurationSeconds,
                        loaded = state.loaded,
                        onSave = onLockDurationChange
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = state.blockNavigation,
                            onCheckedChange = onBlockNavigationChange
                        )
                        Text(
                            text = stringResource(id = R.string.lock_settings_block_nav),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = stringResource(id = R.string.lock_settings_block_nav_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(id = R.string.home_privacy_disclaimer),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Input de duración del bloqueo en minutos + segundos (máximo 10 minutos).
 * Se guarda al tocar "Guardar" para no pisar lo que el usuario está escribiendo.
 */
@Composable
private fun LockDurationInput(
    savedSeconds: Int,
    loaded: Boolean,
    onSave: (Int) -> Unit
) {
    var minutesText by remember(savedSeconds, loaded) { mutableStateOf((savedSeconds / 60).toString()) }
    var secondsText by remember(savedSeconds, loaded) { mutableStateOf((savedSeconds % 60).toString()) }

    val minutes = minutesText.toIntOrNull()
    val seconds = secondsText.toIntOrNull()
    val total = if (minutes != null && seconds != null) minutes * 60 + seconds else null
    val isValid = total != null && seconds!! in 0..59 &&
        total in SettingsRepository.MIN_LOCK_SECONDS..SettingsRepository.MAX_LOCK_SECONDS

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = minutesText,
            onValueChange = { minutesText = it.filter(Char::isDigit).take(2) },
            label = { Text(stringResource(id = R.string.lock_settings_minutes)) },
            singleLine = true,
            isError = !isValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = secondsText,
            onValueChange = { secondsText = it.filter(Char::isDigit).take(2) },
            label = { Text(stringResource(id = R.string.lock_settings_seconds)) },
            singleLine = true,
            isError = !isValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { total?.let(onSave) },
            enabled = isValid && total != savedSeconds
        ) {
            Text("Guardar")
        }
    }

    Text(
        text = stringResource(id = R.string.lock_settings_current, savedSeconds / 60, savedSeconds % 60),
        style = MaterialTheme.typography.bodySmall
    )
}
