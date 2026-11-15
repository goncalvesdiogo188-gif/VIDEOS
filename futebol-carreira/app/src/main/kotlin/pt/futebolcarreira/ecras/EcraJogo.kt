package pt.futebolcarreira.ecras

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pt.futebolcarreira.EventoUi
import pt.futebolcarreira.FichaUi
import pt.futebolcarreira.JogoUi
import pt.futebolcarreira.nucleo.simulacao.Relato
import pt.futebolcarreira.nucleo.simulacao.TipoEvento

/**
 * Ecrã de um jogo: marcador, relato em texto, estatísticas e notas dos jogadores.
 * O botão «Ver em direto» volta a passar o jogo minuto a minuto.
 */

private val AMARELO = Color(0xFFFDD835)
private val VERMELHO = Color(0xFFE53935)
private val COR_GOLO = Color(0x662E7D32)

/** Tempo real (em milésimos de segundo) que dura cada minuto de jogo no «direto». */
private const val PAUSA_POR_MINUTO = 350L

@Composable
fun EcraJogo(jogo: JogoUi, aoFechar: () -> Unit) {
    var separador by remember { mutableIntStateOf(0) }
    // Minuto até onde o relato é mostrado; fora do direto mostra-se tudo.
    var minuto by remember(jogo) { mutableIntStateOf(if (jogo.emDireto) 0 else jogo.duracao) }
    var emDireto by remember(jogo) { mutableStateOf(jogo.emDireto) }

    LaunchedEffect(emDireto) {
        if (emDireto) {
            minuto = 0
            while (minuto < jogo.duracao) {
                delay(PAUSA_POR_MINUTO)
                minuto++
            }
            emDireto = false
        }
    }

    val visiveis = jogo.eventos.filter { it.minuto <= minuto }
    val ultimo = visiveis.lastOrNull()
    val acabou = minuto >= jogo.duracao

    // O botão "voltar" do telemóvel fecha o jogo.
    BackHandler(onBack = aoFechar)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.systemBarsPadding()) {
            // ---- Marcador ----
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Emblema(jogo.emblemaCasa, 34.dp)
                        Text(jogo.casa, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 2)
                        Text(
                            "${ultimo?.golosCasa ?: 0} - ${ultimo?.golosFora ?: 0}", Modifier.width(96.dp),
                            textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 30.sp,
                        )
                        Text(jogo.fora, Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, maxLines = 2)
                        Emblema(jogo.emblemaFora, 34.dp)
                    }
                    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
                    Row {
                        Text(jogo.taticaCasa, Modifier.weight(1f), fontSize = 12.sp, color = secundaria)
                        Text(if (acabou) "Final" else Relato.minuto(minuto), Modifier.width(96.dp), textAlign = TextAlign.Center, fontSize = 13.sp)
                        Text(jogo.taticaFora, Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 12.sp, color = secundaria)
                    }
                }
            }
            TabRow(selectedTabIndex = separador) {
                listOf("Relato", "Estatísticas", "Notas").forEachIndexed { i, nome ->
                    Tab(selected = separador == i, onClick = { separador = i }, text = { Text(nome) })
                }
            }
            Box(Modifier.weight(1f)) {
                when (separador) {
                    // No direto, o mais recente aparece em cima.
                    0 -> ListaRelato(if (emDireto) visiveis.reversed() else visiveis)
                    1 -> if (acabou) Estatisticas(jogo) else Aviso()
                    else -> if (acabou) Notas(jogo) else Aviso()
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (emDireto) {
                    OutlinedButton(onClick = { emDireto = false; minuto = jogo.duracao }, modifier = Modifier.weight(1f)) { Text("Saltar para o fim") }
                } else {
                    OutlinedButton(onClick = { separador = 0; emDireto = true }, modifier = Modifier.weight(1f)) { Text("▶ Ver em direto") }
                }
                Button(onClick = aoFechar, modifier = Modifier.weight(1f)) { Text("Fechar") }
            }
        }
    }
}

@Composable
private fun Aviso() {
    Text("Disponível quando o jogo acabar.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ------------------------------------------------------------------ relato

@Composable
private fun ListaRelato(eventos: List<EventoUi>) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(eventos) { e ->
            val fundo = if (e.tipo == TipoEvento.GOLO) COR_GOLO else Color.Transparent
            Row(Modifier.fillMaxWidth().background(fundo).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e.minutoTexto, Modifier.width(48.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) { Simbolo(e.tipo) }
                Text(
                    e.texto, Modifier.weight(1f).padding(start = 6.dp), fontSize = 14.sp,
                    fontWeight = if (e.tipo == TipoEvento.GOLO || e.tipo == null) FontWeight.Bold else FontWeight.Normal,
                )
                if (e.tipo == TipoEvento.GOLO) Text("${e.golosCasa}-${e.golosFora}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Pequeno símbolo para cada tipo de acontecimento. */
@Composable
private fun Simbolo(tipo: TipoEvento?) {
    when (tipo) {
        TipoEvento.GOLO -> Text("⚽", fontSize = 15.sp)
        TipoEvento.AMARELO -> Cartao(AMARELO)
        TipoEvento.VERMELHO -> Cartao(VERMELHO)
        TipoEvento.SEGUNDO_AMARELO -> Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) { Cartao(AMARELO); Cartao(VERMELHO) }
        TipoEvento.SUBSTITUICAO -> Text("⇄", fontSize = 16.sp)
        TipoEvento.LESAO -> Text("✚", fontSize = 15.sp, color = VERMELHO)
        TipoEvento.DEFESA, TipoEvento.REMATE_FORA -> Text("•", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        null -> Text("⏱", fontSize = 14.sp)
    }
}

@Composable
private fun Cartao(cor: Color) {
    Box(Modifier.size(width = 9.dp, height = 13.dp).background(cor, MaterialTheme.shapes.extraSmall))
}

// ------------------------------------------------------------ estatísticas

@Composable
private fun Estatisticas(jogo: JogoUi) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(jogo.estatisticas) { (nome, casa, fora) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(casa, Modifier.width(56.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(nome, Modifier.weight(1f), textAlign = TextAlign.Center)
                Text(fora, Modifier.width(56.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (jogo.melhorEmCampo.isNotEmpty()) {
            item { Text("Melhor em campo: ${jogo.melhorEmCampo}", Modifier.padding(top = 14.dp), fontWeight = FontWeight.Bold) }
        }
    }
}

// ------------------------------------------------------------------- notas

@Composable
private fun Notas(jogo: JogoUi) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
        item { Text(jogo.casa, Modifier.padding(top = 10.dp, bottom = 4.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp) }
        items(jogo.fichasCasa) { Ficha(it) }
        item { Text(jogo.fora, Modifier.padding(top = 16.dp, bottom = 4.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp) }
        items(jogo.fichasFora) { Ficha(it) }
    }
}

/** Uma linha por jogador: lugar, nome, o que fez e a nota. */
@Composable
private fun Ficha(f: FichaUi) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(f.lugar, Modifier.width(40.dp), fontSize = 13.sp, color = secundaria)
        Text(
            f.nome, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (f.titular) MaterialTheme.colorScheme.onSurface else secundaria,
        )
        if (!f.titular) Text("↑${f.entrou}'", fontSize = 12.sp, color = secundaria)
        if (f.saiu > 0 && !f.vermelho) Text("↓${f.saiu}'", fontSize = 12.sp, color = secundaria)
        if (f.golos > 0) Text(if (f.golos == 1) "⚽" else "⚽×${f.golos}", fontSize = 12.sp)
        if (f.assistencias > 0) Text(if (f.assistencias == 1) "A" else "A×${f.assistencias}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (f.amarelos > 0) Cartao(AMARELO)
        if (f.vermelho) Cartao(VERMELHO)
        if (f.lesionado) Text("✚", fontSize = 12.sp, color = VERMELHO)
        Text(f.nota, Modifier.width(34.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
    }
}
