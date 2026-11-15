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
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import pt.futebolcarreira.LinhaJogadorUi
import pt.futebolcarreira.LinhaUi
import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.Mentalidade
import pt.futebolcarreira.PlantelUi
import pt.futebolcarreira.Zona

/**
 * Ecrã de teste das Fases 1 e 2: mostra uma liga a ser simulada, época após época,
 * com os jogos em detalhe, os melhores jogadores e as táticas de cada equipa.
 * (Os menus "a sério" chegam na Fase 3.)
 */

private val COR_SUBIDA = Color(0x8C2E7D32)
private val COR_DESCIDA = Color(0x8CB71C1C)
internal val LARGURA_NUMERO = 30.dp
private val SEPARADORES = listOf("Tabela", "Jogos", "Jogadores", "Histórico")

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
                aoDezEpocas = { jogo.simularEpocas(10); separador = 3 },
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
                1 -> Resultados(ecra, jogo::mudarJornada, jogo::abrirJogo)
                2 -> Jogadores(ecra)
                else -> Historico(ecra)
            }
        }
    }

    jogo.plantel?.let { JanelaPlantel(it, jogo::fecharPlantel, jogo::mudarFormacao, jogo::mudarMentalidade) }
    jogo.jogo?.let { EcraJogo(it, jogo::fecharJogo) }
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
internal fun Celula(texto: String, largura: Dp = LARGURA_NUMERO, negrito: Boolean = false) {
    Text(
        texto, Modifier.width(largura), textAlign = TextAlign.Center, fontSize = 14.sp, maxLines = 1,
        fontWeight = if (negrito) FontWeight.Bold else FontWeight.Normal,
    )
}

// -------------------------------------------------------------- resultados

@Composable
private fun Resultados(ecra: EstadoEcra, aoMudarJornada: (Int) -> Unit, aoAbrirJogo: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        if (ecra.resultados.isEmpty()) {
            item {
                Text(
                    "A época ainda não começou.\n\nToca em «Jornada» para simular uma jornada, ou em «Época» para ir até ao fim.",
                    Modifier.padding(top = 12.dp),
                )
            }
        } else {
            item {
                // Setas para ver as jornadas anteriores.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { aoMudarJornada(-1) }, enabled = ecra.jornadaVista > 1) { Text("◀", fontSize = 20.sp) }
                    Text("Jornada ${ecra.jornadaVista}", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    TextButton(onClick = { aoMudarJornada(1) }, enabled = ecra.jornadaVista < ecra.jornada) { Text("▶", fontSize = 20.sp) }
                }
                Text(
                    "Toca num jogo para ver o relato.", Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(ecra.resultados) { r ->
                Row(
                    Modifier.fillMaxWidth().clickable { aoAbrirJogo(r.indice) }.padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.casa, Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${r.golosCasa} - ${r.golosFora}", Modifier.width(64.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    Text(r.fora, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            if (ecra.terminada) {
                item {
                    Text("Época terminada. Toca em «Jornada» para começar a próxima.", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

// --------------------------------------------------------------- jogadores

@Composable
private fun Jogadores(ecra: EstadoEcra) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        if (ecra.marcadores.isEmpty()) {
            item { Text("Ainda não há golos esta época.", Modifier.padding(top = 12.dp)) }
        }
        listaMelhores("Melhores marcadores", "Golos", ecra.marcadores)
        listaMelhores("Assistências", "Ass.", ecra.assistencias)
        listaMelhores("Melhores notas médias", "Nota", ecra.notas)
    }
}

/** Uma lista de dez jogadores com título (não aparece se estiver vazia). */
private fun androidx.compose.foundation.lazy.LazyListScope.listaMelhores(titulo: String, coluna: String, linhas: List<LinhaJogadorUi>) {
    if (linhas.isEmpty()) return
    item {
        Row(Modifier.padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(titulo, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Celula("Jogos", 48.dp, negrito = true)
            Celula(coluna, 48.dp, negrito = true)
        }
    }
    items(linhas.size) { i ->
        val l = linhas[i]
        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Celula("${i + 1}", 26.dp)
            Text(l.nome, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(l.clube, Modifier.width(44.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Celula("${l.jogos}", 48.dp)
            Celula(l.valor, 48.dp, negrito = true)
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
                    if (e.marcador.isNotEmpty()) Text("Melhor marcador: ${e.marcador}", fontSize = 13.sp, color = secundaria, modifier = Modifier.padding(start = 76.dp))
                    if (e.sobem.isNotEmpty()) Text("Subiram: ${e.sobem}", fontSize = 13.sp, color = secundaria, modifier = Modifier.padding(start = 76.dp))
                    if (e.descem.isNotEmpty()) Text("Desceram: ${e.descem}", fontSize = 13.sp, color = secundaria, modifier = Modifier.padding(start = 76.dp))
                }
            }
        }
    }
}

// ----------------------------------------------------------------- plantel

@Composable
private fun JanelaPlantel(
    plantel: PlantelUi, aoFechar: () -> Unit,
    aoMudarFormacao: (String, Formacao?) -> Unit, aoMudarMentalidade: (String, Mentalidade?) -> Unit,
) {
    Dialog(onDismissRequest = aoFechar, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f)) {
            Column(Modifier.padding(12.dp)) {
                Text(plantel.titulo, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(plantel.detalhes, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Tática: "Auto" deixa o treinador (computador) decidir.
                Text("Formação (em uso: ${plantel.formacaoEmUso})", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(plantel.formacao == null, { aoMudarFormacao(plantel.equipaId, null) }, { Text("Auto") })
                    for (f in Formacao.entries) {
                        FilterChip(plantel.formacao == f, { aoMudarFormacao(plantel.equipaId, f) }, { Text(f.nome) })
                    }
                }
                Text("Mentalidade", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(plantel.mentalidade == null, { aoMudarMentalidade(plantel.equipaId, null) }, { Text("Auto") })
                    for (m in Mentalidade.entries) {
                        FilterChip(plantel.mentalidade == m, { aoMudarMentalidade(plantel.equipaId, m) }, { Text(m.nome) })
                    }
                }

                Row(Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                    Celula("Pos", 40.dp, negrito = true)
                    Text("Nome", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Celula("Id.", 28.dp, negrito = true)
                    Celula("Ger", negrito = true)
                    Celula("J", 26.dp, negrito = true)
                    Celula("G", 26.dp, negrito = true)
                    Celula("A", 26.dp, negrito = true)
                    Celula("Nota", 38.dp, negrito = true)
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(plantel.jogadores) { j ->
                        Column(Modifier.padding(vertical = 5.dp)) {
                            Row {
                                Celula(j.posicao, 40.dp)
                                Text(j.nome, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Celula(j.idade.toString(), 28.dp)
                                Celula(j.geral.toString(), negrito = true)
                                Celula(j.jogos.toString(), 26.dp)
                                Celula(j.golos.toString(), 26.dp)
                                Celula(j.assistencias.toString(), 26.dp)
                                Celula(j.nota, 38.dp)
                            }
                            val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
                            Row(Modifier.padding(start = 40.dp)) {
                                Text("Potencial ${j.potencial} · ${j.valor}", fontSize = 12.sp, color = secundaria)
                                if (j.estado.isNotEmpty()) Text("  ·  ${j.estado}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                TextButton(onClick = aoFechar, modifier = Modifier.align(Alignment.End)) { Text("Fechar") }
            }
        }
    }
}
