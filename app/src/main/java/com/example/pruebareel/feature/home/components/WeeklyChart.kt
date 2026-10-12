package com.example.pruebareel.feature.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pruebareel.data.stats.DayStats
import com.example.pruebareel.data.stats.StatMetric
import com.example.pruebareel.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Una serie del gráfico: qué métrica, con qué nombre y color. */
private data class Series(val metric: StatMetric, val name: String, val color: Color)

private val SHORT_DAYS = listOf("D", "L", "M", "M", "J", "V", "S")
private val LONG_DAYS = listOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")

/**
 * Barras apiladas de los últimos 7 días (Instagram / YouTube / TikTok).
 *
 * - Tocar una barra la selecciona y muestra el detalle abajo (funciona como tooltip y tabla).
 * - Por defecto está seleccionado hoy.
 * - La leyenda con los valores siempre está visible, así la app no se identifica solo por color.
 *
 * @param week Últimos 7 días, de hoy hacia atrás (como lo da StatsRepository).
 */
@Composable
fun WeeklyChart(week: List<DayStats>, modifier: Modifier = Modifier) {
    if (week.isEmpty()) return
    val colors = LocalAppColors.current
    val series = listOf(
        Series(StatMetric.IG_REELS, "Reels", colors.instagram),
        Series(StatMetric.YT_SHORTS, "Shorts", colors.youtube),
        Series(StatMetric.TT_VIDEOS, "TikTok", colors.tiktok)
    )

    val days = week.reversed() // del más viejo (izquierda) a hoy (derecha)
    val totals = days.map { d -> series.sumOf { d[it.metric] } }
    val maxTotal = totals.maxOrNull()?.coerceAtLeast(1) ?: 1
    var selected by remember(week) { mutableIntStateOf(days.lastIndex) }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .semantics {
                    contentDescription = "Gráfico de videos por día de los últimos 7 días"
                }
                .pointerInput(days.size) {
                    detectTapGestures { offset ->
                        val slot = size.width.toFloat() / days.size
                        selected = (offset.x / slot).toInt().coerceIn(0, days.lastIndex)
                    }
                }
        ) {
            val topPad = 22.dp.toPx()   // espacio para el número arriba de la barra
            val chartH = size.height - topPad
            val slot = size.width / days.size
            val barW = slot * 0.52f
            val gap = 2.dp.toPx()       // separación entre segmentos apilados
            val radius = 4.dp.toPx()

            // Línea guía recesiva en el máximo de la semana.
            drawLine(
                color = gridColor,
                start = Offset(0f, topPad),
                end = Offset(size.width, topPad),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
            drawLine(
                color = gridColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )

            days.forEachIndexed { i, day ->
                val x = slot * i + (slot - barW) / 2
                val dim = if (i == selected) 1f else 0.45f
                var bottom = size.height
                val visible = series.filter { day[it.metric] > 0 }

                visible.forEachIndexed { segIndex, s ->
                    val h = chartH * day[s.metric] / maxTotal
                    val top = bottom - h
                    val isTop = segIndex == visible.lastIndex
                    val rect = Rect(x, top, x + barW, bottom)
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect,
                                topLeft = if (isTop) CornerRadius(radius) else CornerRadius.Zero,
                                topRight = if (isTop) CornerRadius(radius) else CornerRadius.Zero,
                                bottomLeft = CornerRadius.Zero,
                                bottomRight = CornerRadius.Zero
                            )
                        )
                    }
                    drawPath(path, s.color.copy(alpha = dim))
                    // Separación de 2dp (color de la tarjeta) entre segmentos.
                    if (!isTop) {
                        drawRect(surface, Offset(x, top - gap / 2), Size(barW, gap))
                    }
                    bottom = top
                }

                // Número solo sobre la barra seleccionada (etiqueta selectiva).
                if (i == selected) {
                    val label = measurer.measure(
                        totals[i].toString(),
                        labelStyle.copy(color = textPrimary, fontWeight = FontWeight.SemiBold)
                    )
                    drawText(
                        label,
                        topLeft = Offset(
                            x + barW / 2 - label.size.width / 2,
                            (bottom - label.size.height - 4.dp.toPx()).coerceAtLeast(0f)
                        )
                    )
                }
            }
        }

        // Etiquetas de los días.
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            days.forEachIndexed { i, d ->
                Text(
                    text = if (i == days.lastIndex) "Hoy" else SHORT_DAYS[dayOfWeek(d.dayKey)],
                    style = labelStyle,
                    color = if (i == selected) textPrimary else textMuted,
                    fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Detalle del día seleccionado + leyenda (siempre visible).
        val day = days[selected]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${dayTitle(d = day, isToday = selected == days.lastIndex)} · ${totals[selected]} videos",
                    style = MaterialTheme.typography.titleSmall,
                    color = textPrimary
                )
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    series.forEach { s -> LegendItem(s.color, s.name, day[s.metric]) }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, name: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$name ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 0 = domingo ... 6 = sábado. */
private fun dayOfWeek(dayKey: String): Int {
    val date = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dayKey) }.getOrNull()
        ?: return 0
    return Calendar.getInstance().apply { time = date }.get(Calendar.DAY_OF_WEEK) - 1
}

private fun dayTitle(d: DayStats, isToday: Boolean): String {
    if (isToday) return "Hoy"
    val dd = d.dayKey.substring(8, 10)
    val mm = d.dayKey.substring(5, 7)
    return "${LONG_DAYS[dayOfWeek(d.dayKey)]} $dd/$mm"
}

/** Etiqueta de color para usar fuera del gráfico (tarjetas de cada app). */
@Composable
fun AppDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(color, CircleShape))
}
