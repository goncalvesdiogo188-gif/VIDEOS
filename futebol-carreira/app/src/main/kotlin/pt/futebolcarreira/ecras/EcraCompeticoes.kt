package pt.futebolcarreira.ecras

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import pt.futebolcarreira.CompeticaoOpcaoUi
import pt.futebolcarreira.CompeticaoUi
import pt.futebolcarreira.EliminatoriaParUi
import pt.futebolcarreira.EliminatoriasUi
import pt.futebolcarreira.FaseLigaUi
import pt.futebolcarreira.JornadaFaseUi
import pt.futebolcarreira.LinhaUi
import pt.futebolcarreira.TipoCompeticao
import pt.futebolcarreira.VencedorUi
import pt.futebolcarreira.Zona

/**
 * Taças, supertaças e competições europeias no separador «Liga»:
 * eliminatórias por rondas, fase de liga (Europa) e vencedores.
 */

// ------------------------------------------------- ecrã de uma competição

@Composable
internal fun SeparadorCompeticao(
    opcao: CompeticaoOpcaoUi?, competicao: CompeticaoUi, aoAbrirChave: (String) -> Unit,
) {
    val temFase = competicao.faseLiga != null && opcao?.tipo == TipoCompeticao.EUROPA
    val nomes = if (temFase) listOf("Fase de liga", "Eliminatórias", "Vencedores") else listOf("Eliminatórias", "Vencedores")
    var separador by rememberSaveable(opcao?.id) { mutableIntStateOf(0) }
    val atual = nomes[separador.coerceIn(0, nomes.size - 1)]

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = nomes.indexOf(atual)) {
            nomes.forEachIndexed { i, nome ->
                Tab(selected = atual == nome, onClick = { separador = i }, text = { Text(nome, maxLines = 1, fontSize = 13.sp) })
            }
        }
        when (atual) {
            "Fase de liga" -> FaseDeLiga(competicao.faseLiga!!, opcao?.id ?: "", aoAbrirChave)
            "Eliminatórias" -> Eliminatorias(competicao.eliminatorias, opcao?.id ?: "", aoAbrirChave)
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                itensVencedores(competicao.vencedores)
            }
        }
    }
}

@Composable
private fun Eliminatorias(elim: EliminatoriasUi?, id: String, aoAbrirChave: (String) -> Unit) {
    if (elim == null || elim.rondas.isEmpty()) {
        Text("Esta competição ainda não começou.", Modifier.padding(12.dp))
        return
    }
    var ronda by rememberSaveable(id) { mutableIntStateOf(elim.rondaAtual) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itensEliminatorias(elim, ronda, { ronda = it }, aoAbrirChave)
    }
}

@Composable
private fun FaseDeLiga(fase: FaseLigaUi, id: String, aoAbrirChave: (String) -> Unit) {
    var jornada by rememberSaveable(id) { mutableIntStateOf(fase.jornadaAtual) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LegendaZona(COR_SUBIDA, "1.º a ${fase.diretos}.º: passam direto aos oitavos")
                LegendaZona(COR_PLAYOFF, "${fase.diretos + 1}.º a ${fase.playoff}.º: vão ao play-off")
                if (fase.tabela.size > fase.playoff) LegendaZona(COR_DESCIDA, "Restantes: eliminados")
            }
        }
        itensTabelaZonas(fase)
        item {
            Rotulo("Jogos", Modifier.padding(start = 12.dp, top = 18.dp))
        }
        itensJornadas(fase.jornadas, jornada, { jornada = it }, aoAbrirChave)
    }
}

@Composable
private fun LegendaZona(cor: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = cor, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.width(14.dp).heightIn(min = 14.dp)) {}
        Text(texto, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// --------------------------------------------------------- listas (partes)

/** Tabela com as zonas coloridas (Europa). */
internal fun LazyListScope.itensTabelaZonas(fase: FaseLigaUi) {
    item { CabecalhoTabela() }
    items(fase.tabela, key = { it.equipaId }) { l ->
        val fundo = when {
            l.posicao <= fase.diretos -> COR_SUBIDA
            l.posicao <= fase.playoff -> COR_PLAYOFF
            else -> COR_DESCIDA
        }
        LinhaTabelaCompacta(l, fundo)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun CabecalhoTabela() {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Celula("#", 28.dp, negrito = true)
        Text("Equipa", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        for (t in listOf("J", "V", "E", "D")) Celula(t, 28.dp, negrito = true)
        Celula("DG", 36.dp, negrito = true)
        Celula("Pts", 36.dp, negrito = true)
    }
}

@Composable
internal fun LinhaTabelaCompacta(l: LinhaUi, fundo: Color, modifier: Modifier = Modifier) {
    val peso = if (l.meu) FontWeight.Bold else FontWeight.Normal
    Row(
        modifier.fillMaxWidth().background(fundo).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Celula(l.posicao.toString(), 28.dp, negrito = l.meu)
        Text((if (l.meu) "▸ " else "") + l.nome, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = peso)
        Celula(l.j.toString(), 28.dp)
        Celula(l.v.toString(), 28.dp)
        Celula(l.e.toString(), 28.dp)
        Celula(l.d.toString(), 28.dp)
        Celula(saldo(l.gm - l.gs), 36.dp)
        Celula(l.pts.toString(), 36.dp, negrito = true)
    }
}

/** Jogos por jornada, com setas para andar entre jornadas. */
internal fun LazyListScope.itensJornadas(
    jornadas: List<JornadaFaseUi>, vista: Int, aoMudar: (Int) -> Unit, aoAbrirChave: (String) -> Unit,
) {
    if (jornadas.isEmpty()) {
        item { Text("Ainda não há jogos marcados.", Modifier.padding(12.dp)) }
        return
    }
    val i = vista.coerceIn(0, jornadas.size - 1)
    val j = jornadas[i]
    item {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { aoMudar(i - 1) }, enabled = i > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text("◀", fontSize = 20.sp) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(j.nome, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(j.data, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { aoMudar(i + 1) }, enabled = i < jornadas.size - 1, modifier = Modifier.heightIn(min = 48.dp)) { Text("▶", fontSize = 20.sp) }
        }
    }
    items(j.jogos) { g ->
        val peso = if (g.meu) FontWeight.Bold else FontWeight.Normal
        val jogado = g.golosCasa != null && g.golosFora != null
        val toque = if (jogado && g.chave.isNotEmpty()) Modifier.clickable { aoAbrirChave(g.chave) } else Modifier
        Row(
            Modifier.fillMaxWidth().then(toque).heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(g.casa, Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = peso)
            Text(
                if (jogado) "${g.golosCasa} - ${g.golosFora}" else "vs", Modifier.width(60.dp), textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold, color = if (jogado) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(g.fora, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = peso)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Vencedores de uma competição, época a época. */
internal fun LazyListScope.itensVencedores(lista: List<VencedorUi>) {
    item { Rotulo("Vencedores") }
    if (lista.isEmpty()) {
        item { Text("Ainda não há vencedores.", Modifier.padding(top = 8.dp)) }
        return
    }
    items(lista) { v ->
        Row(Modifier.padding(vertical = 7.dp)) {
            Text(v.epoca, Modifier.width(76.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Column(Modifier.weight(1f)) {
                Text(v.vencedor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                val extra = listOfNotNull(v.competicao.takeIf { it.isNotEmpty() }, v.finalista.takeIf { it.isNotEmpty() }?.let { "final contra $it" })
                if (extra.isNotEmpty()) Text(extra.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ------------------------------------------------------------ eliminatórias

/** Botões das rondas e a lista de eliminatórias da ronda escolhida. */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.itensEliminatorias(
    elim: EliminatoriasUi, ronda: Int, aoMudarRonda: (Int) -> Unit, aoAbrirChave: (String) -> Unit,
) {
    val i = ronda.coerceIn(0, elim.rondas.size - 1)
    item {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            elim.rondas.forEachIndexed { k, r ->
                FilterChip(selected = k == i, onClick = { aoMudarRonda(k) }, label = { Text(r.nome, maxLines = 1) })
            }
        }
    }
    val pares = elim.rondas[i].pares
    items(pares.size) { k -> CartaoPar(pares[k], aoAbrirChave) }
}

@Composable
internal fun CartaoPar(p: EliminatoriaParUi, aoAbrirChave: (String) -> Unit) {
    val forma = MaterialTheme.shapes.medium
    val unico = p.maos == 1 && p.pernas.firstOrNull()?.chave?.isNotEmpty() == true
    val toque = if (unico) Modifier.clickable { aoAbrirChave(p.pernas[0].chave) } else Modifier
    val borda = if (p.meu) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = forma, border = borda, modifier = Modifier.fillMaxWidth().then(toque)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (p.maos == 2) {
                Row(Modifier.fillMaxWidth()) {
                    Text("", Modifier.weight(1f))
                    Celula("Ida", 36.dp, negrito = true, cor = MaterialTheme.colorScheme.onSurfaceVariant)
                    Celula("Volta", 40.dp, negrito = true, cor = MaterialTheme.colorScheme.onSurfaceVariant)
                    Celula("Ag.", 36.dp, negrito = true, cor = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LinhaEquipa(p, lado = 1)
            LinhaEquipa(p, lado = 2)
            val notas = listOfNotNull(
                if (p.prolongamento) "a.p." else null,
                if (p.penaltisA != null && p.penaltisB != null) "g.p. ${p.penaltisA}-${p.penaltisB}" else null,
                p.dataProxima.takeIf { it.isNotEmpty() && p.pernas.size < p.maos }?.let { "Próximo jogo: $it" },
            )
            val comRelatos = p.maos == 2 && p.pernas.any { it.chave.isNotEmpty() }
            if (notas.isNotEmpty() || comRelatos) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        notas.joinToString(" · "), Modifier.weight(1f).padding(top = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (comRelatos) {
                        p.pernas.forEachIndexed { k, perna ->
                            if (perna.chave.isNotEmpty()) {
                                TextButton(onClick = { aoAbrirChave(perna.chave) }, modifier = Modifier.heightIn(min = 44.dp)) {
                                    Text(if (k == 0) "Ver ida" else "Ver volta", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaEquipa(p: EliminatoriaParUi, lado: Int) {
    val nome = if (lado == 1) p.casa else p.fora
    val emb = if (lado == 1) p.emblemaCasa else p.emblemaFora
    val ganhou = p.vencedor == lado
    val perdeu = p.vencedor != 0 && !ganhou
    val corTexto = if (perdeu) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Emblema(emb, 28.dp)
        Text(
            nome, Modifier.weight(1f).padding(horizontal = 8.dp), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (ganhou || (p.vencedor == 0 && p.meu)) FontWeight.Bold else FontWeight.Normal, color = corTexto,
        )
        for (k in 0 until p.maos) {
            val perna = p.pernas.getOrNull(k)
            val texto = perna?.let { if (lado == 1) it.golosA else it.golosB }?.toString() ?: "–"
            Celula(texto, if (p.maos == 2 && k == 1) 40.dp else 36.dp, negrito = p.maos == 1 && ganhou, cor = corTexto)
        }
        if (p.maos == 2) {
            val ag = if (lado == 1) p.agregadoA else p.agregadoB
            Celula(ag?.toString() ?: "", 36.dp, negrito = true, cor = corTexto)
        }
    }
}
