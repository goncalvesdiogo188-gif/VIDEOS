package pt.futebolcarreira.ecras

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import pt.futebolcarreira.EstadoEcra
import pt.futebolcarreira.JogoViewModel
import pt.futebolcarreira.LinhaUi
import pt.futebolcarreira.PlantelUi
import pt.futebolcarreira.Zona

/**
 * Ecrã de teste da Fase 1: mostra uma liga a ser simulada, época após época.
 * (Os menus "a sério" chegam na Fase 3.)
 */

private val COR_SUBIDA = Color(0x8C2E7D32)
private val COR_DESCIDA = Color(0x8CB71C1C)
private val LARGURA_NUMERO = 30.dp
private val SEPARADORES = listOf("Tabela", "Resultados", "Histórico")

@Composable
fun EcraLiga(jogo: JogoViewModel) {
    val ecra = jogo.ecra
    var separador by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = { BarraTopo(ecra, jogo.ocupado, jogo::escolherLiga) },
        bottomBar = {
            BarraBotoes(
                ativos = !jogo.ocupado,
                aoJornada = jogo::jornadaSeguinte,
                aoEpoca = { jogo.simularEpocas(1) },
                aoDezEpocas = { jogo.simularEpocas(10); separador = 2 },
                aoRecomecar = jogo::novoJogo,
            )
        },
    ) { margens ->
        Column(Modifier.padding(margens).fillMaxSize()) {
            TabRow(selectedTabIndex = separador) {
                SEPARADORES.forEachIndexed { i, nome ->
                    Tab(selected = separador == i, onClick = { separador = i }, text = { Text(nome) })
                }
            }
            if (ecra.erros.isNotEmpty()) {
                Text(
                    "Problemas na base de dados:\n" + ecra.erros.take(5).joinToString("\n"),
                    color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(8.dp),
                )
            }
            when (separador) {
                0 -> Tabela(ecra.tabela, jogo::abrirPlantel)
                1 -> Resultados(ecra)
                else -> Historico(ecra)
            }
        }
    }

    jogo.plantel?.let { JanelaPlantel(it, jogo::fecharPlantel) }
}

// ------------------------------------------------------------------ barras

@Composable
private fun BarraTopo(ecra: EstadoEcra, ocupado: Boolean, aoEscolherLiga: (String) -> Unit) {
    var menuAberto by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(ecra.titulo.ifEmpty { "A carregar…" }, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Box {
                TextButton(onClick = { menuAberto = true }, contentPadding = PaddingValues(0.dp)) {
                    Text((ecra.ligas.firstOrNull { it.id == ecra.ligaId }?.texto ?: "Escolher liga") + "  ▾", fontSize = 16.sp)
                }
                DropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
                    for (liga in ecra.ligas) {
                        DropdownMenuItem(text = { Text(liga.texto) }, onClick = {
                            menuAberto = false
                            aoEscolherLiga(liga.id)
                        })
                    }
                }
            }
            // Barra de progresso enquanto carrega ou simula.
            if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun BarraBotoes(ativos: Boolean, aoJornada: () -> Unit, aoEpoca: () -> Unit, aoDezEpocas: () -> Unit, aoRecomecar: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.navigationBarsPadding().fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val margem = PaddingValues(horizontal = 4.dp, vertical = 12.dp)
            Button(aoJornada, Modifier.weight(1.1f), enabled = ativos, contentPadding = margem) { Text("Jornada", maxLines = 1) }
            Button(aoEpoca, Modifier.weight(1f), enabled = ativos, contentPadding = margem) { Text("Época", maxLines = 1) }
            Button(aoDezEpocas, Modifier.weight(1.3f), enabled = ativos, contentPadding = margem) { Text("10 épocas", maxLines = 1) }
            OutlinedButton(aoRecomecar, Modifier.weight(1.3f), enabled = ativos, contentPadding = margem) { Text("Recomeçar", maxLines = 1) }
        }
    }
}

// ------------------------------------------------------------------ tabela

@Composable
private fun Tabela(linhas: List<LinhaUi>, aoTocarEquipa: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Em ecrãs largos (telemóvel deitado, tablet) há espaço para os golos marcados e sofridos.
        val comGolos = maxWidth > 480.dp
        LazyColumn {
            item {
                Row(Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Celula("#", 28.dp, negrito = true)
                    Text("Equipa", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    for (titulo in listOf("J", "V", "E", "D")) Celula(titulo, negrito = true)
                    if (comGolos) {
                        Celula("GM", negrito = true)
                        Celula("GS", negrito = true)
                    }
                    Celula("DG", 36.dp, negrito = true)
                    Celula("Pts", 36.dp, negrito = true)
                }
            }
            items(linhas, key = { it.equipaId }) { l ->
                val fundo = when (l.zona) {
                    Zona.SUBIDA -> COR_SUBIDA      // campeão ou zona de subida
                    Zona.DESCIDA -> COR_DESCIDA    // zona de descida
                    Zona.NORMAL -> Color.Transparent
                }
                Row(
                    Modifier.clickable { aoTocarEquipa(l.equipaId) }.background(fundo).padding(horizontal = 8.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Celula(l.posicao.toString(), 28.dp)
                    Text(l.nome, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Celula(l.j.toString())
                    Celula(l.v.toString())
                    Celula(l.e.toString())
                    Celula(l.d.toString())
                    if (comGolos) {
                        Celula(l.gm.toString())
                        Celula(l.gs.toString())
                    }
                    Celula("%+d".format(l.gm - l.gs), 36.dp)
                    Celula(l.pts.toString(), 36.dp, negrito = true)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable
private fun Celula(texto: String, largura: Dp = LARGURA_NUMERO, negrito: Boolean = false) {
    Text(
        texto, Modifier.width(largura), textAlign = TextAlign.Center, fontSize = 14.sp, maxLines = 1,
        fontWeight = if (negrito) FontWeight.Bold else FontWeight.Normal,
    )
}

// -------------------------------------------------------------- resultados

@Composable
private fun Resultados(ecra: EstadoEcra) {
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        if (ecra.resultados.isEmpty()) {
            item {
                Text("A época ainda não começou.\n\nToca em «Jornada» para simular uma jornada, ou em «Época» para ir até ao fim.")
            }
        } else {
            item {
                Text("Jornada ${ecra.jornada}", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            items(ecra.resultados) { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.casa, Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${r.golosCasa} - ${r.golosFora}", Modifier.width(64.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    Text(r.fora, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (ecra.terminada) {
                item {
                    Text("Época terminada. Toca em «Jornada» para começar a próxima.", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

// --------------------------------------------------------------- histórico

@Composable
private fun Historico(ecra: EstadoEcra) {
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        if (ecra.historico.isEmpty()) {
            item { Text("Ainda não terminou nenhuma época.") }
        } else {
            item { Text("Total de títulos", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            items(ecra.titulos) { (equipa, vezes) -> Text("$vezes × $equipa") }
            item { Text("Campeões", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp)) }
            items(ecra.historico) { e ->
                Column(Modifier.padding(vertical = 5.dp)) {
                    Row {
                        Text(e.epoca, Modifier.width(76.dp), fontWeight = FontWeight.Bold)
                        Text("${e.campeao} (${e.pontos} pts)")
                    }
                    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
                    if (e.sobem.isNotEmpty()) Text("Subiram: ${e.sobem}", fontSize = 13.sp, color = secundaria, modifier = Modifier.padding(start = 76.dp))
                    if (e.descem.isNotEmpty()) Text("Desceram: ${e.descem}", fontSize = 13.sp, color = secundaria, modifier = Modifier.padding(start = 76.dp))
                }
            }
        }
    }
}

// ----------------------------------------------------------------- plantel

@Composable
private fun JanelaPlantel(plantel: PlantelUi, aoFechar: () -> Unit) {
    Dialog(onDismissRequest = aoFechar) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxHeight(0.9f)) {
            Column(Modifier.padding(12.dp)) {
                Text(plantel.titulo, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(plantel.detalhes, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                    Celula("Pos", 42.dp, negrito = true)
                    Text("Nome", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Celula("Id.", negrito = true)
                    Celula("Ger", 34.dp, negrito = true)
                    Celula("Pot", 34.dp, negrito = true)
                    Celula("Valor", 66.dp, negrito = true)
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(plantel.jogadores) { j ->
                        Row(Modifier.padding(vertical = 5.dp)) {
                            Celula(j.posicao, 42.dp)
                            Text(j.nome, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Celula(j.idade.toString())
                            Celula(j.geral.toString(), 34.dp, negrito = true)
                            Celula(j.potencial.toString(), 34.dp)
                            Celula(j.valor, 66.dp)
                        }
                    }
                }
                TextButton(onClick = aoFechar, modifier = Modifier.align(Alignment.End)) { Text("Fechar") }
            }
        }
    }
}
