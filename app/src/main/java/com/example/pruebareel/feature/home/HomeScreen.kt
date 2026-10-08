package com.example.pruebareel.feature.home

import androidx.compose.foundation.layout.Arrangement
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
                .fillMaxSize(),
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
                        text = stringResource(id = R.string.lock_settings_slider_label, state.lockDurationSeconds),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Slider(
                        value = state.lockDurationSeconds.toFloat(),
                        onValueChange = { onLockDurationChange(it.toInt()) },
                        valueRange = 5f..60f,
                        steps = 55
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(id = R.string.home_privacy_disclaimer),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
