package pt.futebolcarreira.ecras

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Componentes reutilizáveis do sistema de design.

/** Transição que desliza (e esbate) o conteúdo para a esquerda (avançar) ou para a direita (recuar). */
fun transicaoDeslizante(avancar: Boolean): ContentTransform {
    val sentido = if (avancar) 1 else -1
    return (slideInHorizontally(tween(280)) { it / 4 * sentido } + fadeIn(tween(280)))
        .togetherWith(slideOutHorizontally(tween(280)) { -it / 4 * sentido } + fadeOut(tween(160)))
}

/** Devolve uma função que dá uma vibração suave; usa-se nos toques importantes. */
@Composable
fun rememberVibracao(): () -> Unit {
    val tato = LocalHapticFeedback.current
    return remember(tato) { { tato.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

/** Número que conta de 0 até ao valor (e, se o valor mudar, do valor antigo para o novo). */
@Composable
fun AnimatedCounter(
    valor: Int, modifier: Modifier = Modifier, estilo: TextStyle = MaterialTheme.typography.headlineMedium,
    cor: Color = Color.Unspecified, duracaoMs: Int = 800, formatar: (Int) -> String = { it.toString() },
) {
    var alvo by remember { mutableIntStateOf(0) }
    LaunchedEffect(valor) { alvo = valor }
    val atual by animateIntAsState(alvo, tween(duracaoMs, easing = FastOutSlowInEasing), label = "contador")
    Text(formatar(atual), modifier, style = estilo, color = cor)
}

/** Título de uma secção, com um subtítulo opcional por baixo. */
@Composable
fun TituloSecao(texto: String, modifier: Modifier = Modifier, subtitulo: String? = null) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
        Text(texto, style = MaterialTheme.typography.titleLarge)
        if (subtitulo != null) Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Pílula pequena com V, E ou D (vitória, empate, derrota). */
@Composable
fun PilulaForma(letra: String, modifier: Modifier = Modifier) {
    val cor = when (letra) {
        "V" -> COR_VITORIA
        "D" -> COR_DERROTA
        else -> COR_EMPATE
    }
    Box(modifier.size(width = 26.dp, height = 24.dp).background(cor, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
        Text(letra, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** Uma fila de pílulas com os últimos resultados, por exemplo "VVEDV". */
@Composable
fun SequenciaForma(resultados: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (letra in resultados) PilulaForma(letra.toString())
    }
}

/** Cartão de destaque: fundo com degradê na cor de destaque do clube e sombra mais forte. */
@Composable
fun CartaoDestaque(modifier: Modifier = Modifier, aoTocar: (() -> Unit)? = null, conteudo: @Composable ColumnScope.() -> Unit) {
    val forma = MaterialTheme.shapes.large
    val cores = MaterialTheme.colorScheme
    val vibrar = rememberVibracao()
    val toque = if (aoTocar != null) Modifier.clickable { vibrar(); aoTocar() } else Modifier
    Surface(shape = forma, shadowElevation = Elevacoes.destaque, color = cores.primaryContainer, modifier = modifier.fillMaxWidth().clip(forma)) {
        Column(
            Modifier.background(Brush.linearGradient(listOf(cores.primaryContainer, cores.surfaceContainerHigh))).then(toque).padding(16.dp),
            content = conteudo,
        )
    }
}

/** Ecrã de carregamento: um indicador a rodar e uma frase, enquanto a carreira abre. */
@Composable
fun EcraCarregamento(texto: String = "A preparar a carreira…") {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(Modifier.size(44.dp), color = MaterialTheme.colorScheme.primary)
            Text(texto, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
