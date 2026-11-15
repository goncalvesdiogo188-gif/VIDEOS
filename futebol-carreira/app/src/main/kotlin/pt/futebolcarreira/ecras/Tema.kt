package pt.futebolcarreira.ecras

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Cores da aplicação: tema escuro com verde de relvado e um toque de dourado. */
private val CORES = darkColorScheme(
    primary = Color(0xFF66BB6A),
    onPrimary = Color(0xFF00210B),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFA5D6A7),
    onSecondary = Color(0xFF0B2E12),
    secondaryContainer = Color(0xFF2E4A33),
    onSecondaryContainer = Color(0xFFDCEFDC),
    tertiary = Color(0xFFFFD54F),
    onTertiary = Color(0xFF3A2F00),
    background = Color(0xFF0E1210),
    onBackground = Color(0xFFE3E8E3),
    surface = Color(0xFF0E1210),
    onSurface = Color(0xFFE3E8E3),
    surfaceVariant = Color(0xFF1E2621),
    onSurfaceVariant = Color(0xFFB5C2B7),
    surfaceContainerLowest = Color(0xFF0A0D0B),
    surfaceContainerLow = Color(0xFF151A17),
    surfaceContainer = Color(0xFF1A211C),
    surfaceContainerHigh = Color(0xFF232C26),
    surfaceContainerHighest = Color(0xFF2B352E),
    outline = Color(0xFF5A6B5E),
    outlineVariant = Color(0xFF34403A),
)

/** Cantos arredondados, para botões e cartões grandes e fáceis de tocar. */
private val FORMAS = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun Tema(conteudo: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CORES, shapes = FORMAS, content = conteudo)
}
