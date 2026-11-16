package pt.futebolcarreira.ecras

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.ConvocadoUi
import pt.futebolcarreira.GrupoUi
import pt.futebolcarreira.SelecoesUi
import pt.futebolcarreira.TorneioUi
import pt.futebolcarreira.Zona

/** Seleções (em «Mais»): torneios, ranking, convocados e vencedores. */

private val PARTES = listOf("Torneios", "Ranking", "Convocados", "Vencedores")

@Composable
internal fun SeparadorSelecoes(sel: SelecoesUi, aoAbrirChave: (String) -> Unit) {
    if (sel.vazio) {
        Text("Ainda não há dados das seleções.", Modifier.padding(12.dp))
        return
    }
    var parte by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = parte, edgePadding = 0.dp) {
            PARTES.forEachIndexed { i, nome ->
                Tab(selected = parte == i, onClick = { parte = i }, text = { Text(nome, maxLines = 1, fontSize = 12.sp) })
            }
        }
        when (parte) {
            0 -> Torneios(sel, aoAbrirChave)
            1 -> Ranking(sel)
            2 -> Convocados(sel)
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) { itensVencedores(sel.vencedores) }
        }
    }
}

// ---------------------------------------------------------------- torneios

@Composable
private fun Torneios(sel: SelecoesUi, aoAbrirChave: (String) -> Unit) {
    var escolhido by rememberSaveable { mutableIntStateOf(0) }
    val torneio = sel.torneios.getOrNull(escolhido.coerceIn(0, maxOf(0, sel.torneios.size - 1)))
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (sel.proximos.isNotEmpty()) {
            item {
                Bloco {
                    Rotulo("Próximos torneios")
                    for ((nome, datas) in sel.proximos) {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(nome, fontWeight = FontWeight.Bold)
                            Text(datas, fontSize = 13.sp, color = secundaria)
                        }
                    }
                }
            }
        }
        if (torneio == null) return@LazyColumn
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sel.torneios.forEachIndexed { i, t ->
                    FilterChip(selected = i == escolhido, onClick = { escolhido = i }, label = { Text(t.nome, maxLines = 1) })
                }
            }
        }
        item {
            Column {
                Text(torneio.nome, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(torneio.estado, fontSize = 13.sp, color = secundaria)
            }
        }
        if (torneio.grupos.isNotEmpty()) {
            item { Rotulo("Grupos") }
            items(torneio.grupos.size) { g -> CartaoGrupo(torneio.id, torneio.grupos[g], aoAbrirChave) }
        }
        val elim = torneio.eliminatorias
        if (elim != null && elim.rondas.isNotEmpty()) {
            item { Rotulo("Eliminatórias") }
            itensEliminatoriasComEstado(torneio, elim, aoAbrirChave)
        }
    }
}

/** Como as eliminatórias vivem numa lista que já existe, a ronda escolhida guarda-se aqui em cima. */
@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.itensEliminatoriasComEstado(
    torneio: TorneioUi, elim: pt.futebolcarreira.EliminatoriasUi, aoAbrirChave: (String) -> Unit,
) {
    item {
        var ronda by rememberSaveable(torneio.id) { mutableIntStateOf(elim.rondaAtual) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                elim.rondas.forEachIndexed { k, r ->
                    FilterChip(selected = k == ronda.coerceIn(0, elim.rondas.size - 1), onClick = { ronda = k }, label = { Text(r.nome, maxLines = 1) })
                }
            }
            for (par in elim.rondas[ronda.coerceIn(0, elim.rondas.size - 1)].pares) CartaoPar(par, aoAbrirChave)
        }
    }
}

@Composable
private fun CartaoGrupo(torneioId: String, grupo: GrupoUi, aoAbrirChave: (String) -> Unit) {
    var jogosVisiveis by rememberSaveable(torneioId, grupo.nome) { mutableStateOf(false) }
    Bloco {
        Rotulo(grupo.nome)
        Row(Modifier.padding(top = 4.dp)) {
            Celula("#", 24.dp, negrito = true)
            Text("Seleção", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            for (t in listOf("J", "V", "E", "D")) Celula(t, 26.dp, negrito = true)
            Celula("DG", 34.dp, negrito = true)
            Celula("Pts", 34.dp, negrito = true)
        }
        for (l in grupo.tabela) {
            val fundo = if (l.zona == Zona.SUBIDA) COR_SUBIDA else Color.Transparent
            Row(Modifier.fillMaxWidth().background(fundo).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Celula("${l.posicao}", 24.dp, negrito = l.meu)
                Text(
                    (if (l.meu) "▸ " else "") + l.nome, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (l.meu) FontWeight.Bold else FontWeight.Normal,
                )
                Celula("${l.j}", 26.dp)
                Celula("${l.v}", 26.dp)
                Celula("${l.e}", 26.dp)
                Celula("${l.d}", 26.dp)
                Celula(saldo(l.gm - l.gs), 34.dp)
                Celula("${l.pts}", 34.dp, negrito = true)
            }
        }
        if (grupo.jornadas.isNotEmpty()) {
            TextButton(onClick = { jogosVisiveis = !jogosVisiveis }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (jogosVisiveis) "Esconder jogos" else "Ver jogos", fontSize = 13.sp)
            }
            if (jogosVisiveis) {
                for (j in grupo.jornadas) {
                    Text("${j.nome} · ${j.data}", Modifier.padding(top = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    for (g in j.jogos) {
                        val jogado = g.golosCasa != null && g.golosFora != null
                        val peso = if (g.meu) FontWeight.Bold else FontWeight.Normal
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(g.casa, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = peso)
                            Text(if (jogado) "${g.golosCasa} - ${g.golosFora}" else "vs", Modifier.width(52.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(g.fora, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = peso)
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------- ranking

@Composable
private fun Ranking(sel: SelecoesUi) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Celula("#", 30.dp, negrito = true)
                Text("Seleção", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Celula("Pontos", 70.dp, negrito = true)
                Celula("", 40.dp)
            }
        }
        items(sel.ranking) { r ->
            val fundo = if (r.meu) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            Row(
                Modifier.fillMaxWidth().background(fundo).heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Celula("${r.posicao}", 30.dp, negrito = r.meu)
                Text(
                    r.selecao, Modifier.weight(1f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (r.meu) FontWeight.Bold else FontWeight.Normal,
                )
                Celula(r.pontos, 70.dp, negrito = true)
                val (texto, cor) = when {
                    r.variacao > 0 -> "▲${r.variacao}" to COR_VITORIA
                    r.variacao < 0 -> "▼${-r.variacao}" to COR_DERROTA
                    else -> "–" to COR_EMPATE
                }
                Celula(texto, 40.dp, cor = cor)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

// -------------------------------------------------------------- convocados

@Composable
private fun Convocados(sel: SelecoesUi) {
    var escolhida by rememberSaveable { mutableIntStateOf(0) }
    val conv = sel.convocatorias.getOrNull(escolhida.coerceIn(0, maxOf(0, sel.convocatorias.size - 1)))
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sel.meusConvocados.isNotEmpty()) {
            item {
                Bloco {
                    Rotulo("Convocados do teu clube")
                    for (c in sel.meusConvocados) {
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.nome, fontWeight = FontWeight.Bold)
                                Text("${c.selecao} · ${c.posicao} · Geral ${c.geral}", fontSize = 12.sp, color = secundaria)
                            }
                        }
                    }
                }
            }
        }
        if (sel.convocatorias.isEmpty() || conv == null) {
            item { Text("Ainda não há convocatórias.") }
            return@LazyColumn
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sel.convocatorias.forEachIndexed { i, c ->
                    FilterChip(selected = i == escolhida, onClick = { escolhida = i }, label = { Text(c.selecao, maxLines = 1) })
                }
            }
        }
        item {
            Column {
                Text("${conv.selecao} · ${conv.convocados.size} convocados", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (conv.selecionador.isNotEmpty()) Text("Selecionador: ${conv.selecionador}", fontSize = 13.sp, color = secundaria)
            }
        }
        items(conv.convocados) { c -> LinhaConvocado(c) }
    }
}

@Composable
private fun LinhaConvocado(c: ConvocadoUi) {
    val fundo = if (c.meu) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        Modifier.fillMaxWidth().background(fundo, MaterialTheme.shapes.small).heightIn(min = 44.dp).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Celula(c.posicao, 42.dp, negrito = true, cor = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(c.nome, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (c.meu) FontWeight.Bold else FontWeight.Normal)
            Text(c.clube, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Celula("${c.geral}", 36.dp, negrito = true)
    }
}
