package com.example.pruebareel.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pruebareel.R
import com.example.pruebareel.data.preferences.SettingsRepository
import com.example.pruebareel.data.stats.DayStats
import com.example.pruebareel.data.stats.StatMetric
import java.util.Locale

/** Todas las acciones que puede disparar la pantalla principal. */
data class HomeActions(
    val onOpenAccessibilitySettings: () -> Unit,
    val onOpenOverlaySettings: () -> Unit,
    val onReelsEnabledChange: (Boolean) -> Unit,
    val onReelsLimitChange: (Int) -> Unit,
    val onReelsDailyLimitChange: (Int) -> Unit,
    val onShortsEnabledChange: (Boolean) -> Unit,
    val onShortsTimeChange: (Int) -> Unit,
    val onShortsDailyMinutesChange: (Int) -> Unit,
    val onTiktokEnabledChange: (Boolean) -> Unit,
    val onTiktokLimitChange: (Int) -> Unit,
    val onTiktokDailyLimitChange: (Int) -> Unit,
    val onLockDurationChange: (Int) -> Unit,
    val onBlockNavigationChange: (Boolean) -> Unit,
    val onQuietHoursEnabledChange: (Boolean) -> Unit,
    val onQuietHoursChange: (Int, Int) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions) {
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

            StatusCard(state.permissions, actions)
            StatsCard(state.week)

            AppCard(
                title = stringResource(R.string.reels_card_title),
                subtitle = stringResource(R.string.reels_card_subtitle),
                enabled = state.reelsEnabled,
                onEnabledChange = actions.onReelsEnabledChange,
                sliderLabel = stringResource(R.string.reels_card_slider_label, state.reelsLimit),
                sliderValue = state.reelsLimit,
                sliderRange = 1..20,
                onSliderChange = actions.onReelsLimitChange,
                dailyLabel = stringResource(R.string.daily_limit_reels),
                dailyValue = state.reelsDailyLimit,
                loaded = state.loaded,
                onDailySave = actions.onReelsDailyLimitChange
            )

            AppCard(
                title = stringResource(R.string.shorts_card_title),
                subtitle = stringResource(R.string.shorts_card_subtitle),
                enabled = state.shortsEnabled,
                onEnabledChange = actions.onShortsEnabledChange,
                sliderLabel = stringResource(R.string.shorts_card_slider_label, state.shortsTimeSeconds),
                sliderValue = state.shortsTimeSeconds,
                sliderRange = 5..180,
                sliderStep = 5,
                onSliderChange = actions.onShortsTimeChange,
                dailyLabel = stringResource(R.string.daily_limit_shorts),
                dailyValue = state.shortsDailyMinutes,
                loaded = state.loaded,
                onDailySave = actions.onShortsDailyMinutesChange
            )

            AppCard(
                title = stringResource(R.string.tiktok_card_title),
                subtitle = stringResource(R.string.tiktok_card_subtitle),
                enabled = state.tiktokEnabled,
                onEnabledChange = actions.onTiktokEnabledChange,
                sliderLabel = stringResource(R.string.tiktok_card_slider_label, state.tiktokLimit),
                sliderValue = state.tiktokLimit,
                sliderRange = 1..20,
                onSliderChange = actions.onTiktokLimitChange,
                dailyLabel = stringResource(R.string.daily_limit_tiktok),
                dailyValue = state.tiktokDailyLimit,
                loaded = state.loaded,
                onDailySave = actions.onTiktokDailyLimitChange
            )

            LockSettingsCard(state, actions)
            QuietHoursCard(state, actions)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(id = R.string.home_privacy_disclaimer),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// =========================================================================================
// Tarjetas
// =========================================================================================

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { content() }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text = text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
}

/** Estado de los permisos, con botones para ir a activarlos. */
@Composable
private fun StatusCard(p: PermissionStatus, actions: HomeActions) {
    SectionCard {
        CardTitle(stringResource(R.string.status_title))

        StatusRow(
            ok = p.accessibilityEnabled,
            okText = stringResource(R.string.status_service_ok),
            missingText = stringResource(R.string.status_service_missing),
            buttonText = stringResource(R.string.status_service_button),
            onClick = actions.onOpenAccessibilitySettings
        )
        StatusRow(
            ok = p.overlayGranted,
            okText = stringResource(R.string.status_overlay_ok),
            missingText = stringResource(R.string.status_overlay_missing),
            buttonText = stringResource(R.string.status_overlay_button),
            onClick = actions.onOpenOverlaySettings
        )
        if (!p.accessibilityEnabled || !p.overlayGranted) {
            Text(
                text = stringResource(R.string.status_help),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun StatusRow(
    ok: Boolean,
    okText: String,
    missingText: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (ok) "✅ $okText" else "⚠️ $missingText",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (!ok) {
            OutlinedButton(onClick = onClick) { Text(buttonText) }
        }
    }
}

/** Uso de hoy y de los últimos 7 días. */
@Composable
private fun StatsCard(week: List<DayStats>) {
    SectionCard {
        CardTitle(stringResource(R.string.stats_title))

        val today = week.firstOrNull()
        if (today == null) {
            Text(stringResource(R.string.stats_empty), style = MaterialTheme.typography.bodySmall)
            return@SectionCard
        }

        Text(stringResource(R.string.stats_today), fontWeight = FontWeight.SemiBold)
        Text(dayLine(today), style = MaterialTheme.typography.bodyMedium)

        HorizontalDivider()

        Text(stringResource(R.string.stats_week), fontWeight = FontWeight.SemiBold)
        val total = DayStats("total", StatMetric.entries.associateWith { m -> week.sumOf { it[m] } })
        Text(dayLine(total), style = MaterialTheme.typography.bodyMedium)

        week.forEachIndexed { index, day ->
            val label = when (index) {
                0 -> stringResource(R.string.stats_label_today)
                1 -> stringResource(R.string.stats_label_yesterday)
                else -> day.dayKey.substring(8, 10) + "/" + day.dayKey.substring(5, 7)
            }
            Text(
                text = "$label: ${dayLine(day)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun dayLine(d: DayStats): String = stringResource(
    R.string.stats_line,
    d[StatMetric.IG_REELS],
    d[StatMetric.TT_VIDEOS],
    formatMinutes(d[StatMetric.YT_SHORTS_SECONDS]),
    d[StatMetric.LOCKS]
)

private fun formatMinutes(seconds: Int): String =
    if (seconds < 60) "${seconds}s" else "${seconds / 60} min"

/** Tarjeta de una app: switch, límite seguido (barra) y límite diario (campo). */
@Composable
private fun AppCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    sliderLabel: String,
    sliderValue: Int,
    sliderRange: IntRange,
    sliderStep: Int = 1,
    onSliderChange: (Int) -> Unit,
    dailyLabel: String,
    dailyValue: Int,
    loaded: Boolean,
    onDailySave: (Int) -> Unit
) {
    SectionCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CardTitle(title)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }

        Text(text = sliderLabel, style = MaterialTheme.typography.bodyMedium)
        val stepsCount = (sliderRange.last - sliderRange.first) / sliderStep - 1
        Slider(
            value = sliderValue.toFloat(),
            onValueChange = { onSliderChange(it.toInt()) },
            valueRange = sliderRange.first.toFloat()..sliderRange.last.toFloat(),
            steps = stepsCount.coerceAtLeast(0)
        )

        Text(text = dailyLabel, style = MaterialTheme.typography.bodyMedium)
        NumberInputWithSave(
            savedValue = dailyValue,
            loaded = loaded,
            range = 0..999,
            label = stringResource(R.string.daily_limit_field),
            onSave = onDailySave
        )
        Text(
            text = stringResource(R.string.daily_limit_hint),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** Duración del bloqueo y opción de bloquear los botones del sistema. */
@Composable
private fun LockSettingsCard(state: HomeUiState, actions: HomeActions) {
    SectionCard {
        CardTitle(stringResource(R.string.lock_settings_title))
        Text(
            text = stringResource(R.string.lock_settings_subtitle),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(R.string.lock_settings_duration_label),
            style = MaterialTheme.typography.bodyMedium
        )
        LockDurationInput(
            savedSeconds = state.lockDurationSeconds,
            loaded = state.loaded,
            onSave = actions.onLockDurationChange
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = state.blockNavigation, onCheckedChange = actions.onBlockNavigationChange)
            Text(
                text = stringResource(R.string.lock_settings_block_nav),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = stringResource(R.string.lock_settings_block_nav_desc),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** Horario sin videos (ej. 23:00 a 07:00). */
@Composable
private fun QuietHoursCard(state: HomeUiState, actions: HomeActions) {
    SectionCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CardTitle(stringResource(R.string.quiet_title))
                Text(
                    text = stringResource(R.string.quiet_subtitle),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = state.quietHoursEnabled,
                onCheckedChange = actions.onQuietHoursEnabledChange
            )
        }

        var startText by remember(state.quietStartMinute, state.loaded) {
            mutableStateOf(formatHhMm(state.quietStartMinute))
        }
        var endText by remember(state.quietEndMinute, state.loaded) {
            mutableStateOf(formatHhMm(state.quietEndMinute))
        }
        val start = parseHhMm(startText)
        val end = parseHhMm(endText)
        val valid = start != null && end != null && start != end

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = startText,
                onValueChange = { startText = it.take(5) },
                label = { Text(stringResource(R.string.quiet_from)) },
                singleLine = true,
                isError = start == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = endText,
                onValueChange = { endText = it.take(5) },
                label = { Text(stringResource(R.string.quiet_to)) },
                singleLine = true,
                isError = end == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { if (valid) actions.onQuietHoursChange(start!!, end!!) },
                enabled = valid &&
                    (start != state.quietStartMinute || end != state.quietEndMinute)
            ) { Text(stringResource(R.string.save)) }
        }
        Text(
            text = stringResource(R.string.quiet_hint),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// =========================================================================================
// Campos reutilizables
// =========================================================================================

/** Campo numérico con botón "Guardar" (no guarda mientras se escribe). */
@Composable
private fun NumberInputWithSave(
    savedValue: Int,
    loaded: Boolean,
    range: IntRange,
    label: String,
    onSave: (Int) -> Unit
) {
    var text by remember(savedValue, loaded) { mutableStateOf(savedValue.toString()) }
    val value = text.toIntOrNull()
    val valid = value != null && value in range

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter(Char::isDigit).take(3) },
            label = { Text(label) },
            singleLine = true,
            isError = !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { value?.let(onSave) },
            enabled = valid && value != savedValue
        ) { Text(stringResource(R.string.save)) }
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
            Text(stringResource(R.string.save))
        }
    }

    Text(
        text = stringResource(id = R.string.lock_settings_current, savedSeconds / 60, savedSeconds % 60),
        style = MaterialTheme.typography.bodySmall
    )
}

private fun formatHhMm(minuteOfDay: Int): String =
    String.format(Locale.US, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

/** Acepta "23:00", "23.00", "2300" o "7" (→ 07:00). Devuelve minutos desde las 00:00. */
private fun parseHhMm(text: String): Int? {
    val clean = text.trim().replace('.', ':')
    val (h, m) = when {
        ':' in clean -> clean.split(':').let { it[0] to it.getOrElse(1) { "0" } }
        clean.length <= 2 -> clean to "0"
        clean.length in 3..4 -> clean.dropLast(2) to clean.takeLast(2)
        else -> return null
    }
    val hours = h.toIntOrNull() ?: return null
    val minutes = m.ifEmpty { "0" }.toIntOrNull() ?: return null
    if (hours !in 0..23 || minutes !in 0..59) return null
    return hours * 60 + minutes
}
