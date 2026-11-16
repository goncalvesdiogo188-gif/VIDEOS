package pt.futebolcarreira.aovivo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.ceil
import pt.futebolcarreira.nucleo.aovivo.AcaoParada
import pt.futebolcarreira.nucleo.aovivo.BolaParada
import pt.futebolcarreira.nucleo.aovivo.ComandoParada
import pt.futebolcarreira.nucleo.aovivo.PartidaAoVivo
import pt.futebolcarreira.nucleo.aovivo.TipoParada

// Mira dos lances parados (livres, cantos, lançamentos, penáltis) e escolha do mergulho do guarda-redes.
// No ecrã o utilizador ataca sempre para a direita, e «longe» é o lado de cima do ecrã.

/** Traduz um ângulo do ecrã (0 = em frente, positivo = para o lado de longe) em ângulo do campo (radianos, 0 = +x). */
internal fun anguloDoCampo(anguloEcra: Float, sentido: Int): Float =
    if (sentido >= 0) anguloEcra else anguloEcra + PI.toFloat()

/** Traduz «longe» (+1) / «perto» (-1) no eixo y do campo, conforme o sentido em que o utilizador ataca. */
internal fun ladoDoCampo(ladoEcra: Float, sentido: Int): Float = ladoEcra * (if (sentido >= 0) 1f else -1f)

/** O painel só aparece quando há um lance do utilizador para cobrar (ou um penálti do adversário para defender). */
internal fun precisaPainel(b: BolaParada?): Boolean =
    b != null && b.tipo != TipoParada.PONTAPE_SAIDA && (b.doUtilizador || b.guardaRedesDoUtilizador)

@Composable
internal fun PainelBolaParada(partida: PartidaAoVivo, b: BolaParada, sentido: Int, modifier: Modifier = Modifier) {
    Surface(modifier.width(380.dp), shape = RoundedCornerShape(14.dp), color = Color(0xCC000000)) {
        if (b.guardaRedesDoUtilizador && !b.doUtilizador) PainelMergulho(partida, b, sentido)
        else PainelCobrar(partida, b, sentido)
    }
}

@Composable
private fun PainelMergulho(partida: PartidaAoVivo, b: BolaParada, sentido: Int) {
    var escolhido by remember(b.cobradorId, b.x, b.y) { mutableStateOf<Int?>(null) }
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Penálti contra ti: para onde mergulha o guarda-redes?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((rotulo, valor) in listOf("Longe" to 1, "Centro" to 0, "Perto" to -1)) {
                val lado = if (valor == 0) 0 else ladoDoCampo(valor.toFloat(), sentido).toInt()
                FilterChip(
                    selected = escolhido == lado, onClick = { escolhido = lado; partida.escolherMergulho(lado) },
                    label = { Text(rotulo, fontSize = 15.sp, maxLines = 1) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            }
        }
        Text("Se não escolheres, o guarda-redes decide sozinho.", color = Color(0xFFCCCCCC), fontSize = 12.sp)
    }
}

@Composable
private fun PainelCobrar(partida: PartidaAoVivo, b: BolaParada, sentido: Int) {
    val chave = listOf(b.tipo, b.cobradorId, b.x, b.y)
    var acao by remember(chave) { mutableStateOf(AcaoParada.AUTO) }
    var forca by remember(chave) { mutableFloatStateOf(0.6f) }
    var angulo by remember(chave) { mutableFloatStateOf(0f) }
    var anguloMexido by remember(chave) { mutableStateOf(false) }
    var mira by remember(chave) { mutableFloatStateOf(0f) }
    var altura by remember(chave) { mutableFloatStateOf(0.3f) }
    val remate = acao == AcaoParada.REMATAR || (acao == AcaoParada.AUTO && b.podeRematar && b.tipo == TipoParada.PENALTI)
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(titulo(b), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val opcoes = buildList {
                add("Auto" to AcaoParada.AUTO); add("Passe" to AcaoParada.PASSE)
                if (b.tipo != TipoParada.LANCAMENTO) add("Cruzar" to AcaoParada.CRUZAR)
                if (b.podeRematar) add("Rematar" to AcaoParada.REMATAR)
            }
            for ((nome, a) in opcoes) FilterChip(
                selected = acao == a, onClick = { acao = a },
                label = { Text(nome, fontSize = 13.sp, maxLines = 1) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            )
        }
        if (remate) {
            Text("Canto da baliza (perto ← → longe)", color = Color(0xFFCCCCCC), fontSize = 12.sp)
            Slider(value = mira, onValueChange = { mira = it }, valueRange = -1f..1f, modifier = Modifier.heightIn(min = 32.dp))
            Text("Altura (rasteiro ← → ao ângulo)", color = Color(0xFFCCCCCC), fontSize = 12.sp)
            Slider(value = altura, onValueChange = { altura = it }, valueRange = 0f..1f, modifier = Modifier.heightIn(min = 32.dp))
        } else if (acao != AcaoParada.AUTO) {
            Text("Direção (baixo ← frente → cima)", color = Color(0xFFCCCCCC), fontSize = 12.sp)
            Slider(value = angulo, onValueChange = { angulo = it; anguloMexido = true }, valueRange = -2.4f..2.4f, modifier = Modifier.heightIn(min = 32.dp))
        }
        if (!(acao == AcaoParada.AUTO)) {
            Text("Força", color = Color(0xFFCCCCCC), fontSize = 12.sp)
            Slider(value = forca, onValueChange = { forca = it }, valueRange = 0f..1f, modifier = Modifier.heightIn(min = 32.dp))
        }
        val s = ceil(b.tempoRestante.coerceAtLeast(0f)).toInt()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    partida.executarParada(
                        ComandoParada(
                            acao = acao,
                            direcao = if (anguloMexido && !remate) anguloDoCampo(angulo, sentido) else null,
                            forca = forca, mira = ladoDoCampo(mira, sentido), altura = altura,
                        ),
                    )
                },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(if (s > 0) "Cobrar ($s s)" else "Cobrar", fontSize = 15.sp) }
            OutlinedButton(
                onClick = { partida.executarParada(ComandoParada()) }, modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Deixar", fontSize = 14.sp, color = Color.White) }
        }
    }
}

private fun titulo(b: BolaParada): String = when (b.tipo) {
    TipoParada.CANTO -> "Canto a teu favor"
    TipoParada.LIVRE -> if (b.podeRematar) "Livre perigoso a ${b.distanciaBaliza.toInt()} m" else "Livre a teu favor"
    TipoParada.LANCAMENTO -> "Lançamento lateral"
    TipoParada.PONTAPE_BALIZA -> "Pontapé de baliza"
    TipoParada.PENALTI -> if (b.desempate) "Penálti de desempate" else "Penálti a teu favor"
    TipoParada.PONTAPE_SAIDA -> "Pontapé de saída"
}
