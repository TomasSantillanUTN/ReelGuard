package com.example.pruebareel.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pruebareel.R
import com.example.pruebareel.data.preferences.SettingsRepository
import com.example.pruebareel.data.stats.DayStats
import com.example.pruebareel.data.stats.StatMetric
import com.example.pruebareel.feature.home.components.AppDot
import com.example.pruebareel.feature.home.components.WeeklyChart
import com.example.pruebareel.ui.theme.LocalAppColors
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/** Todas las acciones que puede disparar la pantalla principal. */
data class HomeActions(
    val onOpenAccessibilitySettings: () -> Unit,
    val onOpenOverlaySettings: () -> Unit,
    val onReelsEnabledChange: (Boolean) -> Unit,
    val onReelsLimitChange: (Int) -> Unit,
    val onReelsDailyLimitChange: (Int) -> Unit,
    val onShortsEnabledChange: (Boolean) -> Unit,
    val onShortsLimitChange: (Int) -> Unit,
    val onShortsDailyLimitChange: (Int) -> Unit,
    val onTiktokEnabledChange: (Boolean) -> Unit,
    val onTiktokLimitChange: (Int) -> Unit,
    val onTiktokDailyLimitChange: (Int) -> Unit,
    val onLockDurationChange: (Int) -> Unit,
    val onBlockNavigationChange: (Boolean) -> Unit,
    val onQuietHoursEnabledChange: (Boolean) -> Unit,
    val onQuietHoursChange: (Int, Int) -> Unit
)

private val CardShape = RoundedCornerShape(24.dp)

@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions) {
    val colors = LocalAppColors.current

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header()
            PermissionBanner(state.permissions, actions)

            TodayCard(state)
            WeekCard(state.week)

            SectionLabel(stringResource(R.string.section_limits))
            AppCard(
                color = colors.instagram,
                title = stringResource(R.string.reels_card_title),
                subtitle = stringResource(R.string.reels_card_subtitle),
                enabled = state.reelsEnabled,
                onEnabledChange = actions.onReelsEnabledChange,
                sliderLabel = stringResource(R.string.reels_card_slider_label, state.reelsLimit),
                sliderValue = state.reelsLimit,
                onSliderChange = actions.onReelsLimitChange,
                dailyLabel = stringResource(R.string.daily_limit_reels),
                dailyValue = state.reelsDailyLimit,
                loaded = state.loaded,
                onDailySave = actions.onReelsDailyLimitChange
            )
            AppCard(
                color = colors.youtube,
                title = stringResource(R.string.shorts_card_title),
                subtitle = stringResource(R.string.shorts_card_subtitle),
                enabled = state.shortsEnabled,
                onEnabledChange = actions.onShortsEnabledChange,
                sliderLabel = stringResource(R.string.shorts_card_slider_label, state.shortsLimit),
                sliderValue = state.shortsLimit,
                onSliderChange = actions.onShortsLimitChange,
                dailyLabel = stringResource(R.string.daily_limit_shorts),
                dailyValue = state.shortsDailyLimit,
                loaded = state.loaded,
                onDailySave = actions.onShortsDailyLimitChange
            )
            AppCard(
                color = colors.tiktok,
                title = stringResource(R.string.tiktok_card_title),
                subtitle = stringResource(R.string.tiktok_card_subtitle),
                enabled = state.tiktokEnabled,
                onEnabledChange = actions.onTiktokEnabledChange,
                sliderLabel = stringResource(R.string.tiktok_card_slider_label, state.tiktokLimit),
                sliderValue = state.tiktokLimit,
                onSliderChange = actions.onTiktokLimitChange,
                dailyLabel = stringResource(R.string.daily_limit_tiktok),
                dailyValue = state.tiktokDailyLimit,
                loaded = state.loaded,
                onDailySave = actions.onTiktokDailyLimitChange
            )

            SectionLabel(stringResource(R.string.section_lock))
            LockSettingsCard(state, actions)
            QuietHoursCard(state, actions)

            Text(
                text = stringResource(R.string.home_privacy_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
        }
    }
}

// =========================================================================================
// Encabezado y estado
// =========================================================================================

@Composable
private fun Header() {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 6..12 -> stringResource(R.string.greeting_morning)
        in 13..19 -> stringResource(R.string.greeting_afternoon)
        else -> stringResource(R.string.greeting_night)
    }
    Column {
        Text(
            text = greeting,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** Si todo está activo, una píldora discreta. Si falta algo, una tarjeta con botones. */
@Composable
private fun PermissionBanner(p: PermissionStatus, actions: HomeActions) {
    if (p.accessibilityEnabled && p.overlayGranted) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            AppDot(MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.status_all_ok),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        return
    }

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.status_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            PermissionRow(
                ok = p.accessibilityEnabled,
                text = stringResource(
                    if (p.accessibilityEnabled) R.string.status_service_ok else R.string.status_service_missing
                ),
                button = stringResource(R.string.status_service_button),
                onClick = actions.onOpenAccessibilitySettings
            )
            PermissionRow(
                ok = p.overlayGranted,
                text = stringResource(
                    if (p.overlayGranted) R.string.status_overlay_ok else R.string.status_overlay_missing
                ),
                button = stringResource(R.string.status_overlay_button),
                onClick = actions.onOpenOverlaySettings
            )
            Text(
                text = stringResource(R.string.status_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun PermissionRow(ok: Boolean, text: String, button: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = (if (ok) "✓  " else "!  ") + text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f)
        )
        if (!ok) FilledTonalButton(onClick = onClick) { Text(button) }
    }
}

// =========================================================================================
// Estadísticas
// =========================================================================================

/** Número grande de hoy + comparación con el promedio + una mini-tarjeta por app. */
@Composable
private fun TodayCard(state: HomeUiState) {
    val colors = LocalAppColors.current
    val today = state.week.firstOrNull()
    val ig = today?.get(StatMetric.IG_REELS) ?: 0
    val yt = today?.get(StatMetric.YT_SHORTS) ?: 0
    val tt = today?.get(StatMetric.TT_VIDEOS) ?: 0
    val total = ig + yt + tt
    val locks = today?.get(StatMetric.LOCKS) ?: 0

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.stats_today),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = total.toString(),
                    fontSize = 56.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.stats_videos_today),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = comparisonText(state.week, total) + " · " +
                    stringResource(R.string.stats_locks_today, locks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiniTile("Reels", ig, state.reelsDailyLimit, colors.instagram, Modifier.weight(1f))
                MiniTile("Shorts", yt, state.shortsDailyLimit, colors.youtube, Modifier.weight(1f))
                MiniTile("TikTok", tt, state.tiktokDailyLimit, colors.tiktok, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun comparisonText(week: List<DayStats>, today: Int): String {
    val previous = week.drop(1)
    val avg = if (previous.isEmpty()) 0.0 else previous.map {
        it[StatMetric.IG_REELS] + it[StatMetric.YT_SHORTS] + it[StatMetric.TT_VIDEOS]
    }.average()
    if (avg < 1) return stringResource(R.string.stats_compare_none)
    val diff = ((today - avg) / avg * 100).roundToInt()
    return when {
        diff <= -5 -> stringResource(R.string.stats_compare_less, -diff)
        diff >= 5 -> stringResource(R.string.stats_compare_more, diff)
        else -> stringResource(R.string.stats_compare_same)
    }
}

/** Mini-tarjeta: app, cantidad de hoy y, si hay límite diario, barra de progreso. */
@Composable
private fun MiniTile(name: String, value: Int, dailyLimit: Int, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppDot(color)
            Spacer(Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (dailyLimit > 0) "$value/$dailyLimit" else value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (dailyLimit > 0) {
            LinearProgressIndicator(
                progress = { (value.toFloat() / dailyLimit).coerceIn(0f, 1f) },
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WeekCard(week: List<DayStats>) {
    val total = week.sumOf {
        it[StatMetric.IG_REELS] + it[StatMetric.YT_SHORTS] + it[StatMetric.TT_VIDEOS]
    }
    SectionCard {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.stats_week),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.stats_week_total, total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (total == 0) {
            Text(
                text = stringResource(R.string.stats_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = stringResource(R.string.stats_chart_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            WeeklyChart(week = week, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// =========================================================================================
// Configuración
// =========================================================================================

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelLarge,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, start = 4.dp)
    )
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun CardHeader(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    dot: Color? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (dot != null) {
            Box(
                Modifier
                    .background(dot.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) { AppDot(dot) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Tarjeta de una app: switch y, si está activa, límite seguido (barra) y límite diario. */
@Composable
private fun AppCard(
    color: Color,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    sliderLabel: String,
    sliderValue: Int,
    onSliderChange: (Int) -> Unit,
    dailyLabel: String,
    dailyValue: Int,
    loaded: Boolean,
    onDailySave: (Int) -> Unit
) {
    SectionCard {
        CardHeader(title, subtitle, enabled, onEnabledChange, dot = color)

        AnimatedVisibility(visible = enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = sliderLabel, style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = sliderValue.toFloat(),
                    onValueChange = { onSliderChange(it.roundToInt()) },
                    valueRange = 1f..20f,
                    steps = 18
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Duración del bloqueo y opción de bloquear los botones del sistema. */
@Composable
private fun LockSettingsCard(state: HomeUiState, actions: HomeActions) {
    SectionCard {
        Text(
            text = stringResource(R.string.lock_settings_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.lock_settings_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Horario sin videos (ej. 23:00 a 07:00). */
@Composable
private fun QuietHoursCard(state: HomeUiState, actions: HomeActions) {
    SectionCard {
        CardHeader(
            title = stringResource(R.string.quiet_title),
            subtitle = stringResource(R.string.quiet_subtitle),
            checked = state.quietHoursEnabled,
            onCheckedChange = actions.onQuietHoursEnabledChange
        )

        AnimatedVisibility(visible = state.quietHoursEnabled) {
            var startText by remember(state.quietStartMinute, state.loaded) {
                mutableStateOf(formatHhMm(state.quietStartMinute))
            }
            var endText by remember(state.quietEndMinute, state.loaded) {
                mutableStateOf(formatHhMm(state.quietEndMinute))
            }
            val start = parseHhMm(startText)
            val end = parseHhMm(endText)
            val valid = start != null && end != null && start != end

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it.take(5) },
                        label = { Text(stringResource(R.string.quiet_to)) },
                        singleLine = true,
                        isError = end == null,
                        shape = RoundedCornerShape(14.dp),
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
            shape = RoundedCornerShape(14.dp),
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

    Text(
        text = stringResource(R.string.lock_settings_duration_label),
        style = MaterialTheme.typography.bodyMedium
    )
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
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = secondsText,
            onValueChange = { secondsText = it.filter(Char::isDigit).take(2) },
            label = { Text(stringResource(id = R.string.lock_settings_seconds)) },
            singleLine = true,
            isError = !isValid,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { total?.let(onSave) },
            enabled = isValid && total != savedSeconds
        ) { Text(stringResource(R.string.save)) }
    }
    Text(
        text = stringResource(id = R.string.lock_settings_current, savedSeconds / 60, savedSeconds % 60),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
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
