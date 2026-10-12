package com.example.pruebareel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Sage40,
    onPrimary = Color.White,
    primaryContainer = Sage90,
    onPrimaryContainer = Sage10,
    secondary = Ink40,
    onSecondary = Color.White,
    secondaryContainer = Ink90,
    onSecondaryContainer = InkText,
    tertiary = Clay40,
    onTertiary = Color.White,
    tertiaryContainer = Clay90,
    onTertiaryContainer = Clay30,
    background = Paper,
    onBackground = InkText,
    surface = PaperCard,
    onSurface = InkText,
    surfaceVariant = PaperHigh,
    onSurfaceVariant = InkMuted,
    outline = Outline,
    outlineVariant = Outline
)

private val DarkColors = darkColorScheme(
    primary = Sage80,
    onPrimary = Sage10,
    primaryContainer = Sage30,
    onPrimaryContainer = Sage90,
    secondary = Ink80,
    onSecondary = Ink30,
    secondaryContainer = Ink30,
    onSecondaryContainer = NightText,
    tertiary = Clay80,
    onTertiary = Clay30,
    tertiaryContainer = Clay30,
    onTertiaryContainer = Clay90,
    background = Night,
    onBackground = NightText,
    surface = NightCard,
    onSurface = NightText,
    surfaceVariant = NightHigh,
    onSurfaceVariant = NightMuted,
    outline = NightOutline,
    outlineVariant = NightOutline
)

/** Colores de cada app para el gráfico y los indicadores (mismo orden siempre). */
@Immutable
data class AppColors(
    val instagram: Color,
    val youtube: Color,
    val tiktok: Color
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(SeriesInstagramLight, SeriesYoutubeLight, SeriesTiktokLight)
}

/**
 * Tema de ReelGuard.
 *
 * Se usa siempre la paleta propia (sin "dynamic color" de Android 12+), para que la app se vea
 * igual en todos los celulares y los colores del gráfico combinen con el resto.
 */
@Composable
fun PruebareelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val appColors = if (darkTheme) {
        AppColors(SeriesInstagramDark, SeriesYoutubeDark, SeriesTiktokDark)
    } else {
        AppColors(SeriesInstagramLight, SeriesYoutubeLight, SeriesTiktokLight)
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            content = content
        )
    }
}
