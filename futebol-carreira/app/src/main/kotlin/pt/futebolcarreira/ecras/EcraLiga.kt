package pt.futebolcarreira.ecras

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.Fase7Ui
import pt.futebolcarreira.LigaUi
import pt.futebolcarreira.TipoCompeticao
import pt.futebolcarreira.LinhaJogadorUi
import pt.futebolcarreira.LinhaUi
import pt.futebolcarreira.Zona

/**
 * Separador «Liga»: tabela, jogos de cada jornada, melhores jogadores e histórico.
 * No topo escolhe-se a liga (pode ver-se qualquer liga do jogo).
 */

private val SEPARADORES = listOf("Tabela", "Jogos", "Jogadores", "Histórico")

@Composable
internal fun SeparadorLiga(
    liga: LigaUi, aoEscolherLiga: (String) -> Unit, aoMudarJornada: (Int) -> Unit,
    aoAbrirJogo: (Int) -> Unit, aoAbrirPlantel: (String) -> Unit,
    fase7: Fase7Ui = Fase7Ui(), aoEscolherCompeticao: (String) -> Unit = {}, aoAbrirChave: (String) -> Unit = {},
) {
    val competicoes = fase7.competicoes.filter { it.tipo != TipoCompeticao.SELECOES }
    val competicaoAtual = competicoes.firstOrNull { it.id == fase7.competicaoId }
    var separador by rememberSaveable { mutableIntStateOf(0) }
    var menuAberto by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 4.dp)) {
            TextButton(onClick = { menuAberto = true }) {
                Text((competicaoAtual?.nome ?: liga.ligas.firstOrNull { it.id == liga.ligaId }?.texto ?: "Escolher liga") + "  ▾", fontSize = 16.sp)
            }
            DropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
                for (opcao in liga.ligas) {
                    DropdownMenuItem(text = { Text(opcao.texto) }, onClick = {
                        menuAberto = false
                        aoEscolherLiga(opcao.id)
                    })
                }
                for (c in competicoes) {
                    DropdownMenuItem(text = { Text(c.nome + if (c.pais.isEmpty()) "" else " (${c.pais})") }, onClick = {
                        menuAberto = false
                        if (c.tipo == TipoCompeticao.LIGA) aoEscolherLiga(c.id) else aoEscolherCompeticao(c.id)
                    })
                }
            }
        }
        if (competicaoAtual != null) {
            SeparadorCompeticao(competicaoAtual, fase7.competicao, aoAbrirChave)
            return@Column
        }
        TabRow(selectedTabIndex = separador) {
            SEPARADORES.forEachIndexed { i, nome ->
                Tab(selected = separador == i, onClick = { separador = i }, text = { Text(nome, maxLines = 1, fontSize = 13.sp) })
            }
        }
        when (separador) {
            0 -> Tabela(liga.tabela, aoAbrirPlantel)
            1 -> Resultados(liga, aoMudarJornada, aoAbrirJogo)
            2 -> Jogadores(liga)
            else -> Historico(liga)
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
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
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
                    Text(
                        (if (l.meu) "▸ " else "") + l.nome, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (l.meu) FontWeight.Bold else FontWeight.Normal,
                    )
                    Celula(l.j.toString())
                    Celula(l.v.toString())
                    Celula(l.e.toString())
                    Celula(l.d.toString())
                    if (comGolos) {
                        Celula(l.gm.toString())
                        Celula(l.gs.toString())
                    }
                    Celula(saldo(l.gm - l.gs), 36.dp)
                    Celula(l.pts.toString(), 36.dp, negrito = true)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

// -------------------------------------------------------------- resultados

@Composable
private fun Resultados(liga: LigaUi, aoMudarJornada: (Int) -> Unit, aoAbrirJogo: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        if (liga.resultados.isEmpty()) {
            item { Text("Esta liga ainda não começou.", Modifier.padding(top = 12.dp)) }
        } else {
            item {
                // Setas para ver as jornadas anteriores.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { aoMudarJornada(-1) }, enabled = liga.jornadaVista > 1) { Text("◀", fontSize = 20.sp) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Jornada ${liga.jornadaVista}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(liga.dataJornada, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { aoMudarJornada(1) }, enabled = liga.jornadaVista < liga.jornada) { Text("▶", fontSize = 20.sp) }
                }
            }
            items(liga.resultados) { r ->
                val peso = if (r.meu) FontWeight.Bold else FontWeight.Normal
                Row(
                    Modifier.fillMaxWidth().clickable { aoAbrirJogo(r.indice) }.padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.casa, Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = peso)
                    Text("${r.golosCasa} - ${r.golosFora}", Modifier.width(64.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    Text(r.fora, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = peso)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

// --------------------------------------------------------------- jogadores

@Composable
private fun Jogadores(liga: LigaUi) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp)) {
        if (liga.marcadores.isEmpty()) {
            item { Text("Ainda não há golos esta época.", Modifier.padding(top = 12.dp)) }
        }
        listaMelhores("Melhores marcadores", "Golos", liga.marcadores)
        listaMelhores("Assistências", "Ass.", liga.assistencias)
        listaMelhores("Melhores notas médias", "Nota", liga.notas)
    }
}

/** Uma lista de dez jogadores com título (não aparece se estiver vazia). */
private fun LazyListScope.listaMelhores(titulo: String, coluna: String, linhas: List<LinhaJogadorUi>) {
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
private fun Historico(liga: LigaUi) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
        if (liga.historico.isEmpty()) {
            item { Text("Ainda não terminou nenhuma época.") }
        } else {
            item { Text("Total de títulos", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            items(liga.titulos) { (equipa, vezes) -> Text("$vezes × $equipa") }
            item { Text("Vencedores", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp)) }
            items(liga.historico) { e ->
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
