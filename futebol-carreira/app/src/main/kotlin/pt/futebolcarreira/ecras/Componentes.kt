package pt.futebolcarreira.ecras

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

/** Emblema (escudo) de um clube: cores e padrão do clube, desenhado em EmblemaClube.kt. */
@Composable
internal fun Emblema(e: EmblemaUi, tamanho: Dp = 40.dp) = EmblemaEscudo(e, tamanho)

/** Cartão: um bloco com fundo próprio. Se "aoTocar" existir, o cartão é um botão. */
@Composable
internal fun Bloco(modifier: Modifier = Modifier, aoTocar: (() -> Unit)? = null, conteudo: @Composable ColumnScope.() -> Unit) {
    val forma = MaterialTheme.shapes.medium
    val toque = if (aoTocar != null) Modifier.clip(forma).clickable(onClick = aoTocar) else Modifier
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = forma, modifier = modifier.fillMaxWidth().then(toque)) {
        Column(Modifier.padding(14.dp), content = conteudo)
    }
}

/** Título pequeno em maiúsculas, por cima de um bloco de informação. */
@Composable
internal fun Rotulo(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto.uppercase(), modifier, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp,
    )
}

/** Botão principal, alto e largo, fácil de tocar. */
@Composable
internal fun BotaoGrande(texto: String, modifier: Modifier = Modifier, ativo: Boolean = true, aoTocar: () -> Unit) {
    Button(onClick = aoTocar, enabled = ativo, modifier = modifier.fillMaxWidth().heightIn(min = 54.dp)) {
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

/** Quadradinho colorido com V, E ou D (vitória, empate, derrota). */
@Composable
internal fun Desfecho(letra: String) {
    val cor = when (letra) {
        "V" -> COR_VITORIA
        "D" -> COR_DERROTA
        else -> COR_EMPATE
    }
    Box(Modifier.size(24.dp).background(cor, MaterialTheme.shapes.extraSmall), contentAlignment = Alignment.Center) {
        Text(letra, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
