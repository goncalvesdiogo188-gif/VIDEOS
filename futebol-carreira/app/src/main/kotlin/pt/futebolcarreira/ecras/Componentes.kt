package pt.futebolcarreira.ecras

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.EmblemaUi

// Peças pequenas usadas em vários ecrãs.

internal val LARGURA_NUMERO = 30.dp
internal val COR_SUBIDA = Color(0x8C2E7D32)
internal val COR_PLAYOFF = Color(0x8C9E7A00)
internal val COR_DESCIDA = Color(0x8CB71C1C)
internal val COR_VITORIA = Color(0xFF2E7D32)
internal val COR_EMPATE = Color(0xFF757575)
internal val COR_DERROTA = Color(0xFFC62828)

/** Emblema redondo de um clube: as cores do clube e a sigla. */
@Composable
internal fun Emblema(e: EmblemaUi, tamanho: Dp = 40.dp) {
    Box(
        Modifier.size(tamanho).background(Color(e.fundo), CircleShape).border(2.dp, Color(e.letras), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(e.sigla.take(3), color = Color(e.letras), fontWeight = FontWeight.Bold, fontSize = (tamanho.value * 0.3f).sp, maxLines = 1)
    }
}

/** Cartão: um bloco com fundo próprio e contorno fino. Se "aoTocar" existir, o cartão é um botão. */
@Composable
internal fun Bloco(modifier: Modifier = Modifier, aoTocar: (() -> Unit)? = null, conteudo: @Composable ColumnScope.() -> Unit) {
    val forma = MaterialTheme.shapes.medium
    val vibrar = rememberVibracao()
    val toque = if (aoTocar != null) Modifier.clip(forma).clickable { vibrar(); aoTocar() } else Modifier
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = forma, tonalElevation = Elevacoes.cartao,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth().then(toque),
    ) {
        Column(Modifier.padding(14.dp), content = conteudo)
    }
}

/** Título pequeno em maiúsculas, por cima de um bloco de informação. */
@Composable
internal fun Rotulo(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto.uppercase(), modifier, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp,
    )
}

/** Botão principal, alto e largo, fácil de tocar. */
@Composable
internal fun BotaoGrande(texto: String, modifier: Modifier = Modifier, ativo: Boolean = true, aoTocar: () -> Unit) {
    val vibrar = rememberVibracao()
    Button(onClick = { vibrar(); aoTocar() }, enabled = ativo, modifier = modifier.fillMaxWidth().heightIn(min = 54.dp)) {
        Text(texto, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** Célula de tabela com largura fixa e texto centrado. */
@Composable
internal fun Celula(texto: String, largura: Dp = LARGURA_NUMERO, negrito: Boolean = false, cor: Color = Color.Unspecified) {
    Text(
        texto, Modifier.width(largura), textAlign = TextAlign.Center, fontSize = 14.sp, maxLines = 1, color = cor,
        fontWeight = if (negrito) FontWeight.Bold else FontWeight.Normal,
    )
}

/** Diferença de golos com sinal: +3, 0, -2. */
internal fun saldo(golos: Int): String = if (golos == 0) "0" else "%+d".format(golos)

/** Pílula colorida com V, E ou D (vitória, empate, derrota). */
@Composable
internal fun Desfecho(letra: String) = PilulaForma(letra)
