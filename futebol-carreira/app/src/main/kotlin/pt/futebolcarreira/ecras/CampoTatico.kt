package pt.futebolcarreira.ecras

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import pt.futebolcarreira.PlantelUi
import pt.futebolcarreira.nucleo.modelos.Formacao
import kotlin.math.roundToInt

// B2: o campo da tática. Cada lugar do onze é uma ficha redonda com a nota do jogador;
// toca-se para escolher quem joga, ou arrasta-se uma ficha para cima de outra para as trocar.

private val RELVA_CLARA = Color(0xFF2F7A3B)
private val RELVA_ESCURA = Color(0xFF296D34)
private val LINHAS_CAMPO = Color(0xB3FFFFFF)

/**
 * Linhas da formação, da defesa para o ataque, com os índices dos lugares (os de "Formacao.lugares")
 * ordenados da esquerda para a direita. O guarda-redes (índice 0) é sempre a primeira linha.
 */
private fun linhasDaFormacao(f: Formacao): List<List<Int>> = when (f) {
    Formacao.F433 -> listOf(listOf(0), listOf(4, 2, 3, 1), listOf(6, 5, 7), listOf(9, 10, 8))
    Formacao.F442 -> listOf(listOf(0), listOf(4, 2, 3, 1), listOf(8, 6, 7, 5), listOf(9, 10))
    Formacao.F4231 -> listOf(listOf(0), listOf(4, 2, 3, 1), listOf(5, 6), listOf(9, 8, 7), listOf(10))
    Formacao.F352 -> listOf(listOf(0), listOf(1, 2, 3), listOf(8, 6, 5, 7, 4), listOf(9, 10))
    Formacao.F532 -> listOf(listOf(0), listOf(5, 2, 3, 4, 1), listOf(7, 6, 8), listOf(9, 10))
}

/** Posição (0 a 1) de cada lugar no campo: x da esquerda para a direita, y de cima (ataque) para baixo (baliza). */
private fun posicoesDoCampo(f: Formacao): Map<Int, Offset> {
    val linhas = linhasDaFormacao(f)
    val mapa = HashMap<Int, Offset>()
    linhas.forEachIndexed { l, indices ->
        // A linha 0 (guarda-redes) fica em baixo; a última (ataque) em cima.
        val y = if (linhas.size == 1) 0.5f else 0.81f - (0.81f - 0.12f) * l / (linhas.size - 1)
        indices.forEachIndexed { k, indice -> mapa[indice] = Offset((k + 1f) / (indices.size + 1f), y) }
    }
    return mapa
}

private fun apelido(nome: String): String = nome.trim().substringAfterLast(' ').ifEmpty { nome }

/** O campo com o onze. "aoTrocar" só existe se o utilizador é o treinador deste clube. */
@Composable
internal fun CampoTatico(
    plantel: PlantelUi, aoEscolherLugar: (Int) -> Unit, aoTrocar: (Int, Int) -> Unit, modifier: Modifier = Modifier,
) {
    val formacao = Formacao.entries.firstOrNull { it.nome == plantel.formacaoEmUso } ?: Formacao.F433
    val posicoes = remember(formacao) { posicoesDoCampo(formacao) }
    val editavel = plantel.podeMudarTatica
    var arrastado by remember { mutableStateOf(-1) }
    var deslocamento by remember { mutableStateOf(Offset.Zero) }
    var alvo by remember { mutableStateOf(-1) }

    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(0.92f).clip(RoundedCornerShape(16.dp))) {
        val largura = constraints.maxWidth.toFloat()
        val altura = constraints.maxHeight.toFloat()
        val ficha = 40.dp
        val fichaPx = with(LocalDensity.current) { ficha.toPx() }
        CampoDesenhado()
        plantel.onze.forEach { l ->
            val p = posicoes[l.indice] ?: return@forEach
            val centro = Offset(p.x * largura, p.y * altura)
            val emArrasto = arrastado == l.indice
            val sobreposto = alvo == l.indice
            val largEtiqueta = 74.dp
            Column(
                Modifier
                    .width(largEtiqueta)
                    .offset {
                        val d = if (emArrasto) deslocamento else Offset.Zero
                        IntOffset((centro.x + d.x - largEtiqueta.toPx() / 2f).roundToInt(), (centro.y + d.y - fichaPx / 2f).roundToInt())
                    }
                    .zIndex(if (emArrasto) 2f else 0f)
                    .pointerInput(l.indice, formacao) { detectTapGestures { aoEscolherLugar(l.indice) } }
                    .pointerInput(l.indice, formacao, editavel, plantel.onze.size) {
                        if (!editavel) return@pointerInput
                        detectDragGestures(
                            onDragStart = { arrastado = l.indice; deslocamento = Offset.Zero; alvo = -1 },
                            onDrag = { c, d ->
                                c.consume()
                                deslocamento += d
                                val ponto = centro + deslocamento
                                alvo = plantel.onze.firstOrNull { o ->
                                    o.indice != l.indice && posicoes[o.indice]?.let { q -> (Offset(q.x * largura, q.y * altura) - ponto).getDistance() < fichaPx * 0.9f } == true
                                }?.indice ?: -1
                            },
                            onDragEnd = {
                                if (alvo >= 0) aoTrocar(l.indice, alvo)
                                arrastado = -1; deslocamento = Offset.Zero; alvo = -1
                            },
                            onDragCancel = { arrastado = -1; deslocamento = Offset.Zero; alvo = -1 },
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val foraDePosicao = l.posicao.isNotEmpty() && l.posicao != l.lugar
                val contorno = when {
                    sobreposto -> MaterialTheme.colorScheme.tertiary
                    foraDePosicao -> MaterialTheme.colorScheme.error
                    else -> Color.White
                }
                Box(
                    Modifier.size(if (emArrasto || sobreposto) ficha + 6.dp else ficha)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .border(if (sobreposto) 4.dp else 2.5.dp, contorno, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(l.nota.toString(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
                Text(
                    apelido(l.nome), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp).background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                )
                Text(
                    l.lugar, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 1,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

/** Relva às riscas, linhas, grande área e círculo central. */
@Composable
private fun CampoDesenhado() {
    Canvas(Modifier.fillMaxWidth().aspectRatio(0.92f)) {
        val riscas = 8
        for (i in 0 until riscas) {
            drawRect(if (i % 2 == 0) RELVA_CLARA else RELVA_ESCURA, Offset(0f, size.height * i / riscas), Size(size.width, size.height / riscas + 1f))
        }
        val m = 10.dp.toPx()
        val traco = Stroke(width = 2.dp.toPx())
        drawRect(LINHAS_CAMPO, Offset(m, m), Size(size.width - 2 * m, size.height - 2 * m), style = traco)
        drawLine(LINHAS_CAMPO, Offset(m, size.height / 2), Offset(size.width - m, size.height / 2), strokeWidth = 2.dp.toPx())
        drawCircle(LINHAS_CAMPO, size.width * 0.14f, Offset(size.width / 2, size.height / 2), style = traco)
        // Áreas (a de baixo é a nossa).
        val larguraArea = size.width * 0.52f
        val alturaArea = size.height * 0.16f
        drawRect(LINHAS_CAMPO, Offset((size.width - larguraArea) / 2, m), Size(larguraArea, alturaArea), style = traco)
        drawRect(LINHAS_CAMPO, Offset((size.width - larguraArea) / 2, size.height - m - alturaArea), Size(larguraArea, alturaArea), style = traco)
        val pequena = size.width * 0.24f
        drawRect(LINHAS_CAMPO, Offset((size.width - pequena) / 2, m), Size(pequena, alturaArea * 0.45f), style = traco)
        drawRect(LINHAS_CAMPO, Offset((size.width - pequena) / 2, size.height - m - alturaArea * 0.45f), Size(pequena, alturaArea * 0.45f), style = traco)
    }
}
