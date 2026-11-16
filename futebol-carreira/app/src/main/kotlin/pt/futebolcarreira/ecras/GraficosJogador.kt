package pt.futebolcarreira.ecras

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.FichaJogadorUi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// B2: gráficos da ficha de jogador, desenhados em Canvas.

private fun abreviatura(nome: String) = when (nome) {
    "Ritmo" -> "RIT"
    "Remate" -> "REM"
    "Passe" -> "PAS"
    "Drible" -> "DRI"
    "Defesa" -> "DEF"
    "Físico" -> "FÍS"
    "Guarda-redes" -> "GR"
    else -> nome.take(3).uppercase()
}

/** Radar (teia de aranha) com os atributos (1 a 99): um eixo por atributo. */
@Composable
internal fun RadarAtributos(atributos: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val cor = MaterialTheme.colorScheme.primary
    val grelha = MaterialTheme.colorScheme.outlineVariant
    val texto = MaterialTheme.colorScheme.onSurface
    val medidor = rememberTextMeasurer()
    val n = atributos.size
    Canvas(modifier.fillMaxWidth().height(250.dp)) {
        if (n < 3) return@Canvas
        val centro = Offset(size.width / 2f, size.height / 2f)
        val raio = size.minDimension / 2f - 34.dp.toPx()
        fun ponto(i: Int, fracao: Float): Offset {
            val ang = -PI / 2 + 2 * PI * i / n
            return Offset(centro.x + (raio * fracao * cos(ang)).toFloat(), centro.y + (raio * fracao * sin(ang)).toFloat())
        }
        // Anéis (25, 50, 75 e 100%).
        for (nivel in 1..4) {
            val caminho = Path()
            for (i in 0 until n) ponto(i, nivel / 4f).let { if (i == 0) caminho.moveTo(it.x, it.y) else caminho.lineTo(it.x, it.y) }
            caminho.close()
            drawPath(caminho, grelha, style = Stroke(width = 1.dp.toPx()))
        }
        for (i in 0 until n) drawLine(grelha, centro, ponto(i, 1f), strokeWidth = 1.dp.toPx())
        // Área dos atributos.
        val area = Path()
        atributos.forEachIndexed { i, (_, v) ->
            ponto(i, v.coerceIn(0, 99) / 99f).let { if (i == 0) area.moveTo(it.x, it.y) else area.lineTo(it.x, it.y) }
        }
        area.close()
        drawPath(area, cor.copy(alpha = 0.28f))
        drawPath(area, cor, style = Stroke(width = 2.5.dp.toPx()))
        atributos.forEachIndexed { i, (_, v) -> drawCircle(cor, 3.5.dp.toPx(), ponto(i, v.coerceIn(0, 99) / 99f)) }
        // Etiquetas: sigla e valor, fora do último anel.
        val estilo = TextStyle(color = texto, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        atributos.forEachIndexed { i, (nome, v) ->
            val r = medidor.measure("${abreviatura(nome)} $v", estilo)
            val p = ponto(i, 1f)
            val ang = -PI / 2 + 2 * PI * i / n
            val extra = 14.dp.toPx()
            val x = p.x + (cos(ang) * (extra + r.size.width / 2f)).toFloat() - r.size.width / 2f
            val y = p.y + (sin(ang) * (extra + r.size.height / 2f)).toFloat() - r.size.height / 2f
            drawText(r, topLeft = Offset(x.coerceIn(0f, size.width - r.size.width), y.coerceIn(0f, size.height - r.size.height)))
        }
    }
}

/** Evolução do geral ao longo das épocas: pontos (idade, geral) ligados por uma linha. */
@Composable
internal fun EvolucaoGeral(pontos: List<Pair<Int, Int>>, modifier: Modifier = Modifier) {
    val cor = MaterialTheme.colorScheme.primary
    val grelha = MaterialTheme.colorScheme.outlineVariant
    val texto = MaterialTheme.colorScheme.onSurfaceVariant
    val medidor = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        if (pontos.size < 2) return@Canvas
        val estilo = TextStyle(color = texto, fontSize = 11.sp)
        val minimo = (pontos.minOf { it.second } - 3).coerceAtLeast(1)
        val maximo = (pontos.maxOf { it.second } + 3).coerceAtMost(99).coerceAtLeast(minimo + 4)
        val esq = 30.dp.toPx()
        val dir = 14.dp.toPx()
        val topo = 14.dp.toPx()
        val base = 24.dp.toPx()
        val larg = size.width - esq - dir
        val alt = size.height - topo - base
        fun x(i: Int) = esq + larg * i / (pontos.size - 1).toFloat()
        fun y(v: Int) = topo + alt * (1f - (v - minimo) / (maximo - minimo).toFloat())
        for (v in listOf(minimo, (minimo + maximo) / 2, maximo)) {
            drawLine(grelha, Offset(esq, y(v)), Offset(esq + larg, y(v)), strokeWidth = 1.dp.toPx())
            val r = medidor.measure("$v", estilo)
            drawText(r, topLeft = Offset(0f, y(v) - r.size.height / 2f))
        }
        val linha = Path()
        pontos.forEachIndexed { i, (_, v) -> if (i == 0) linha.moveTo(x(i), y(v)) else linha.lineTo(x(i), y(v)) }
        drawPath(linha, cor, style = Stroke(width = 3.dp.toPx()))
        // Só se escrevem algumas idades, para não se sobreporem.
        val passo = (pontos.size / 6) + 1
        pontos.forEachIndexed { i, (idade, v) ->
            drawCircle(cor, 4.dp.toPx(), Offset(x(i), y(v)))
            if (i % passo == 0 || i == pontos.size - 1) {
                val r = medidor.measure("$idade", estilo)
                drawText(r, topLeft = Offset((x(i) - r.size.width / 2f).coerceIn(0f, size.width - r.size.width), size.height - r.size.height))
            }
        }
    }
}

/** Radar dos atributos e evolução do geral, para a ficha de jogador. */
@Composable
internal fun GraficosFicha(f: FichaJogadorUi) {
    Rotulo("Radar de atributos", Modifier.padding(top = 14.dp, bottom = 2.dp))
    RadarAtributos(f.atributos)
    Rotulo("Evolução do geral (por idade)", Modifier.padding(top = 10.dp, bottom = 4.dp))
    if (f.evolucao.size >= 2) {
        EvolucaoGeral(f.evolucao)
    } else {
        Column { Text("Ainda sem histórico: o gráfico aparece a partir da próxima época.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
