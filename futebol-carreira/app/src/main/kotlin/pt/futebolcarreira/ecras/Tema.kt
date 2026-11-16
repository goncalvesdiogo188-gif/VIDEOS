package pt.futebolcarreira.ecras

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.EmblemaUi

// Sistema de design: cores, tipografia, formas e elevações definidas num só sítio.

/** Como o tema é escolhido: pelo sistema, ou forçado claro / escuro (opção em «Mais → Opções»). */
enum class ModoTema(val nome: String) {
    SISTEMA("Sistema"), CLARO("Claro"), ESCURO("Escuro");

    companion object {
        fun deNome(nome: String?): ModoTema = entries.firstOrNull { it.name == nome } ?: SISTEMA
    }
}

/** A escolha atual do tema. É lida pelo [Tema] e gravada nas preferências da aplicação. */
object PreferenciaTema {
    var modo by mutableStateOf(ModoTema.SISTEMA)
        private set
    private var carregada = false

    /** Lê a escolha gravada (só da primeira vez). */
    internal fun carregar(contexto: Context) {
        if (carregada) return
        carregada = true
        modo = ModoTema.deNome(contexto.getSharedPreferences("opcoes", Context.MODE_PRIVATE).getString("tema", null))
    }

    fun definir(contexto: Context, novo: ModoTema) {
        modo = novo
        carregada = true
        contexto.getSharedPreferences("opcoes", Context.MODE_PRIVATE).edit().putString("tema", novo.name).apply()
    }
}

/** Elevações (sombras tonais) usadas pelos componentes. */
object Elevacoes {
    val cartao: Dp = 1.dp
    val destaque: Dp = 4.dp
    val barra: Dp = 3.dp
}

private val VERDE_RELVADO = Color(0xFF66BB6A)

/** Cores do tema escuro: verde de relvado e um toque de dourado. */
private val CORES_ESCURAS = darkColorScheme(
    primary = VERDE_RELVADO,
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

/** Cores do tema claro: o mesmo verde, em tons mais fundos para ler bem sobre fundo claro. */
private val CORES_CLARAS = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0B3D12),
    secondary = Color(0xFF4C6B50),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E8D6),
    onSecondaryContainer = Color(0xFF0F2A13),
    tertiary = Color(0xFF7A5C00),
    onTertiary = Color.White,
    background = Color(0xFFF6F8F5),
    onBackground = Color(0xFF181D19),
    surface = Color(0xFFF6F8F5),
    onSurface = Color(0xFF181D19),
    surfaceVariant = Color(0xFFE0E8E0),
    onSurfaceVariant = Color(0xFF414B43),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F4EF),
    surfaceContainer = Color(0xFFEAEFE9),
    surfaceContainerHigh = Color(0xFFE3E9E2),
    surfaceContainerHighest = Color(0xFFDCE3DB),
    outline = Color(0xFF6F7B71),
    outlineVariant = Color(0xFFC3CCC4),
)

/** Cantos arredondados, para botões e cartões grandes e fáceis de tocar. */
private val FORMAS = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun estilo(tamanho: Int, peso: FontWeight, linha: Int, espaco: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = peso, fontSize = tamanho.sp, lineHeight = linha.sp, letterSpacing = espaco.sp,
)

/**
 * Hierarquia de tamanhos e pesos. Não há acesso à internet para incluir uma fonte livre em res/font,
 * por isso usa-se a letra sem serifa do sistema (Roboto no Android), com pesos bem marcados.
 */
private val TIPOGRAFIA = Typography(
    displayLarge = estilo(48, FontWeight.Bold, 54, -0.5),
    displayMedium = estilo(40, FontWeight.Bold, 46, -0.25),
    displaySmall = estilo(32, FontWeight.Bold, 38),
    headlineLarge = estilo(28, FontWeight.Bold, 34),
    headlineMedium = estilo(24, FontWeight.Bold, 30),
    headlineSmall = estilo(20, FontWeight.Bold, 26),
    titleLarge = estilo(20, FontWeight.SemiBold, 26),
    titleMedium = estilo(16, FontWeight.SemiBold, 22, 0.1),
    titleSmall = estilo(14, FontWeight.SemiBold, 20, 0.1),
    bodyLarge = estilo(16, FontWeight.Normal, 23, 0.1),
    bodyMedium = estilo(14, FontWeight.Normal, 20, 0.15),
    bodySmall = estilo(12, FontWeight.Normal, 17, 0.2),
    labelLarge = estilo(14, FontWeight.SemiBold, 20, 0.1),
    labelMedium = estilo(12, FontWeight.SemiBold, 16, 0.4),
    labelSmall = estilo(11, FontWeight.SemiBold, 15, 0.5),
)

// ---------------------------------------------------------------- cor de destaque do clube

private fun contraste(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/** Aproxima a cor de [alvo] (branco ou preto) até ter contraste legível com [fundo]. */
private fun comContraste(cor: Color, fundo: Color, alvo: Color, minimo: Float = 4.5f): Color {
    var c = cor
    var passo = 0
    while (contraste(c, fundo) < minimo && passo < 20) {
        c = lerp(c, alvo, 0.1f)
        passo++
    }
    return c
}

private fun textoSobre(fundo: Color): Color = if (contraste(Color.Black, fundo) >= contraste(Color.White, fundo)) Color.Black else Color.White

/** Escolhe a cor do clube a partir do emblema: o fundo, ou as letras se o fundo for cinzento/branco. Null = sem cor própria. */
fun corDoClube(e: EmblemaUi): Color? {
    for (valor in listOf(e.fundo, e.letras)) {
        val c = Color(valor.toInt())
        val cor = Color(c.red, c.green, c.blue, 1f)
        val cromatismo = maxOf(cor.red, cor.green, cor.blue) - minOf(cor.red, cor.green, cor.blue)
        if (cromatismo > 0.18f) return cor
    }
    return null
}

/** Esquema de cores com a cor de destaque do clube no lugar do verde, sempre com contraste legível. */
private fun comDestaque(base: ColorScheme, destaque: Color, escuro: Boolean): ColorScheme {
    val fundo = base.background
    val alvo = if (escuro) Color.White else Color.Black
    val primaria = comContraste(destaque, fundo, alvo)
    val recipiente = if (escuro) lerp(destaque, fundo, 0.65f) else lerp(destaque, Color.White, 0.8f)
    val sobreRecipiente = comContraste(if (escuro) lerp(destaque, Color.White, 0.6f) else lerp(destaque, Color.Black, 0.6f), recipiente, alvo, 7f)
    val secundaria = lerp(primaria, base.onSurfaceVariant, 0.5f)
    val recipienteSec = if (escuro) lerp(destaque, base.surfaceContainerHigh, 0.75f) else lerp(destaque, Color.White, 0.88f)
    return base.copy(
        primary = primaria,
        onPrimary = textoSobre(primaria),
        primaryContainer = recipiente,
        onPrimaryContainer = sobreRecipiente,
        secondary = secundaria,
        onSecondary = textoSobre(secundaria),
        secondaryContainer = recipienteSec,
        onSecondaryContainer = comContraste(base.onSurface, recipienteSec, alvo, 7f),
    )
}

/**
 * O tema da aplicação. Segue o sistema (claro/escuro) a não ser que as Opções forcem um dos dois.
 * Com [corDestaque] (a cor do clube da carreira) os tons principais mudam para essa cor.
 */
@Composable
fun Tema(corDestaque: Color? = null, conteudo: @Composable () -> Unit) {
    PreferenciaTema.carregar(LocalContext.current)
    val escuro = when (PreferenciaTema.modo) {
        ModoTema.SISTEMA -> isSystemInDarkTheme()
        ModoTema.CLARO -> false
        ModoTema.ESCURO -> true
    }
    val base = if (escuro) CORES_ESCURAS else CORES_CLARAS
    val cores = if (corDestaque != null) comDestaque(base, corDestaque, escuro) else base
    MaterialTheme(colorScheme = cores, shapes = FORMAS, typography = TIPOGRAFIA, content = conteudo)
}
