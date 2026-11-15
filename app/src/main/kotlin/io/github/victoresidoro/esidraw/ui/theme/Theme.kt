package io.github.victoresidoro.esidraw.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Visual padrão dos apps do Victor (o mesmo do AnyGest): azul-acinzentado, cards brancos sobre fundo
// azul-acinzentado, sem cor dinâmica do papel de parede. Todos os papéis são definidos de propósito:
// sobrescrever só alguns deixa o resto no roxo-base do Material 3.

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2C5EA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3FA),
    onPrimaryContainer = Color(0xFF0B2A57),
    secondary = Color(0xFF4A5B72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E3FA),
    onSecondaryContainer = Color(0xFF16305C),
    tertiary = Color(0xFF2C5EA8),
    onTertiary = Color.White,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFEAEFF5),
    onBackground = Color(0xFF10151C),
    surface = Color.White,
    onSurface = Color(0xFF10151C),
    surfaceVariant = Color(0xFFE3E8F0),
    onSurfaceVariant = Color(0xFF48525F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F5FA),
    surfaceContainer = Color(0xFFECF0F7),
    surfaceContainerHigh = Color(0xFFE6EBF3),
    surfaceContainerHighest = Color.White,
    outline = Color(0xFF79808B),
    outlineVariant = Color(0xFFC9D0DA),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B2A57),
    primaryContainer = Color(0xFF21456F),
    onPrimaryContainer = Color(0xFFD7E3FA),
    secondary = Color(0xFFB9C6DA),
    onSecondary = Color(0xFF23303F),
    secondaryContainer = Color(0xFF2A3F5C),
    onSecondaryContainer = Color(0xFFD7E3FA),
    tertiary = Color(0xFF8AB4F8),
    onTertiary = Color(0xFF0B2A57),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF5C1A17),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0C1117),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF151B23),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF2A3644),
    onSurfaceVariant = Color(0xFFC0C8D4),
    surfaceContainerLowest = Color(0xFF0B0F14),
    surfaceContainerLow = Color(0xFF151B23),
    surfaceContainer = Color(0xFF1A222C),
    surfaceContainerHigh = Color(0xFF232C38),
    surfaceContainerHighest = Color(0xFF1C2530),
    outline = Color(0xFF8A93A0),
    outlineVariant = Color(0xFF3C4654),
)

/** Cores de estado que o Material 3 não tem. */
val successContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1E5136) else Color(0xFFC3EDD4)

val onSuccessContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFA8E5BF) else Color(0xFF06331A)

val warningContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF5C3D00) else Color(0xFFFFE2A8)

val onWarningContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFDFA0) else Color(0xFF3A2600)

@Composable
fun EsiDrawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
