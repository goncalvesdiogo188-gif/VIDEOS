package pt.futebolcarreira.ecras

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.CarreiraUi
import pt.futebolcarreira.JogoCalendarioUi
import pt.futebolcarreira.JogoViewModel
import pt.futebolcarreira.LinhaUi
import pt.futebolcarreira.NoticiaUi

/**
 * Ecrã central da carreira. Em baixo há cinco separadores:
 * Início (próximo jogo e mensagens), Plantel, Mercado, Liga e Mais
 * (em «Mais» estão as finanças, o calendário e as opções).
 */

private val SEPARADORES = listOf("🏠" to "Início", "👥" to "Plantel", "💰" to "Mercado", "🏆" to "Liga", "☰" to "Mais")

@Composable
fun EcraCarreira(ui: CarreiraUi, jogo: JogoViewModel, separadorInicial: Int = 0) {
    var separador by rememberSaveable { mutableIntStateOf(separadorInicial) }
    // A lista completa de notícias abre por cima do Início.
    var todasNoticias by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { Cabecalho(ui, jogo.ocupado) },
        bottomBar = {
            NavigationBar {
                SEPARADORES.forEachIndexed { i, (simbolo, nome) ->
                    NavigationBarItem(
                        selected = separador == i, onClick = { separador = i; todasNoticias = false },
                        icon = { Text(simbolo, fontSize = 20.sp) }, label = { Text(nome, maxLines = 1) },
                    )
                }
            }
        },
    ) { margens ->
        Box(Modifier.padding(margens).fillMaxSize()) {
            when {
                separador == 0 && todasNoticias -> ListaNoticias(ui.noticias) { todasNoticias = false }
                else -> when (separador) {
                0 -> Inicio(
                    ui, !jogo.ocupado, jogo::avancar, jogo::novaEpoca, jogo::abrirJogoDoClube, jogo::abrirJogoPorChave, aoVerLiga = { separador = 3 },
                    aoAceitar = jogo::aceitarProposta, aoRecusar = jogo::recusarPropostas, aoVerNoticias = { todasNoticias = true },
                )
                1 -> ConteudoPlantel(
                    ui.plantel, Modifier.padding(horizontal = 12.dp), jogo::mudarFormacao, jogo::mudarMentalidade,
                    jogo::abrirEscolha, jogo::onzeAutomatico, jogo::abrirJogador,
                )
                2 -> SeparadorMercado(ui.mercado, jogo::filtrarMercado, jogo::abrirJogador)
                3 -> SeparadorLiga(ui.liga, jogo::escolherLiga, jogo::mudarJornada, jogo::abrirJogo, jogo::abrirPlantel, jogo.fase7, jogo::escolherCompeticao, jogo::abrirJogoPorChave)
                else -> Mais(ui, !jogo.ocupado, jogo)
                }
            }
        }
    }
}

/** Barra de topo: emblema e nome do clube, época, data e estado do mercado. */
@Composable
private fun Cabecalho(ui: CarreiraUi, ocupado: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Emblema(ui.emblema, 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(ui.clube, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Época ${ui.epoca} · ${ui.data}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (ui.mercadoAberto) {
                    Surface(color = MaterialTheme.colorScheme.tertiary, shape = MaterialTheme.shapes.small) {
                        Text(
                            "Mercado\naberto", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, lineHeight = 12.sp,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiary, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            // Barra de progresso enquanto se simula ou grava.
            if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
        }
    }
}

// ------------------------------------------------------------------ início

@Composable
private fun Inicio(
    ui: CarreiraUi, ativo: Boolean, aoJogar: () -> Unit, aoNovaEpoca: () -> Unit,
    aoAbrirJogo: (Int) -> Unit, aoAbrirChave: (String) -> Unit, aoVerLiga: () -> Unit,
    aoAceitar: (String) -> Unit, aoRecusar: () -> Unit, aoVerNoticias: () -> Unit = {},
) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ---- Propostas de outros clubes (obrigatório escolher uma, se o treinador foi despedido) ----
        if (ui.propostas.isNotEmpty()) {
            item {
                Bloco {
                    Rotulo(if (ui.despedido) "Foste despedido · escolhe um novo clube" else "Propostas de outros clubes")
                    for (p in ui.propostas) {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Emblema(p.emblema, 40.dp)
                            Column(Modifier.weight(1f)) {
                                Text(p.nome, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(p.detalhes, fontSize = 12.sp, color = secundaria)
                            }
                            OutlinedButton(onClick = { aoAceitar(p.equipaId) }, enabled = ativo) { Text("Aceitar") }
                        }
                    }
                    if (!ui.despedido) {
                        TextButton(onClick = aoRecusar, enabled = ativo, modifier = Modifier.align(Alignment.End)) { Text("Recusar e ficar") }
                    }
                }
            }
        }

        // ---- O que há para fazer a seguir ----
        if (!ui.despedido) item {
            Bloco {
                val proximo = ui.proximo
                when {
                    ui.terminada -> {
                        Rotulo("Época ${ui.epoca} terminada")
                        Text(ui.resumoFim, Modifier.padding(vertical = 8.dp))
                        BotaoGrande("Começar a época ${ui.proximaEpoca}", ativo = ativo, aoTocar = aoNovaEpoca)
                    }
                    proximo == null -> {
                        Rotulo("Fim do campeonato")
                        Text("O teu clube já fez todos os jogos. Avança para as outras ligas acabarem a época.", Modifier.padding(vertical = 8.dp))
                        BotaoGrande("Avançar", ativo = ativo, aoTocar = aoJogar)
                    }
                    else -> {
                        Rotulo(
                            "Próximo jogo · " + if (proximo.competicao.isEmpty()) "Jornada ${proximo.jornada} de ${proximo.totalJornadas}"
                            else listOf(proximo.competicao, proximo.ronda).filter { it.isNotEmpty() }.joinToString(" · "),
                        )
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            // A equipa da casa aparece sempre à esquerda.
                            val eu: @Composable () -> Unit = { EquipaGrande(ui.clube, ui.emblema) }
                            val adversario: @Composable () -> Unit = { EquipaGrande(proximo.adversario, proximo.emblemaAdversario) }
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { if (proximo.emCasa) eu() else adversario() }
                            Text("vs", color = secundaria, fontSize = 16.sp)
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { if (proximo.emCasa) adversario() else eu() }
                        }
                        if (proximo.derby.isNotEmpty()) {
                            Text(
                                proximo.derby, Modifier.fillMaxWidth().padding(bottom = 4.dp), textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold, fontSize = 15.sp, color = COR_DERBY, maxLines = 2,
                            )
                        }
                        Text(
                            "${proximo.data} · ${if (proximo.neutro) "campo neutro" else if (proximo.emCasa) "em casa" else "fora"}", Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Adversário: ${proximo.detalhes}", Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            textAlign = TextAlign.Center, fontSize = 13.sp, color = secundaria,
                        )
                        BotaoGrande("Jogar", ativo = ativo, aoTocar = aoJogar)
                    }
                }
            }
        }

        // ---- Classificação e último jogo, lado a lado ----
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Bloco(Modifier.weight(1f), aoTocar = aoVerLiga) {
                    Rotulo("Classificação")
                    Text("${ui.posicao}.º lugar", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${ui.pontos} pontos · ${ui.nomeLiga}", fontSize = 13.sp, color = secundaria, maxLines = 2)
                }
                val ultimo = ui.ultimo
                Bloco(Modifier.weight(1f), aoTocar = ultimo?.let { { if (it.competicao.isEmpty()) aoAbrirJogo(it.jornada) else aoAbrirChave(it.chave) } }) {
                    Rotulo("Último jogo")
                    if (ultimo == null) {
                        Text("—", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Ainda não jogaste", fontSize = 13.sp, color = secundaria)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Desfecho(ultimo.desfecho)
                            Text(ultimo.resultado, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            (if (ultimo.neutro) "em campo neutro contra" else "${if (ultimo.emCasa) "em casa" else "fora"} contra") + " ${ultimo.adversario}" +
                                (if (ultimo.competicao.isNotEmpty()) " (${ultimo.competicao})" else ""),
                            fontSize = 13.sp, color = secundaria, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // ---- Direção e adeptos (via de treinador) ----
        ui.direcao?.let { d ->
            item {
                Bloco {
                    Rotulo("Objetivo da direção")
                    Text(d.objetivo, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    Confianca("Direção", d.direcao)
                    Confianca("Adeptos", d.adeptos)
                    Ambiente(d.ambiente, d.ambienteNivel, d.ambienteTexto)
                }
            }
        }

        // ---- Notícias ----
        item {
            Bloco {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Rotulo("Notícias", Modifier.weight(1f))
                    if (ui.noticias.isNotEmpty()) TextButton(onClick = aoVerNoticias) { Text("Ver todas") }
                }
                if (ui.noticias.isEmpty()) {
                    Text("Ainda não há notícias. Joga o primeiro jogo.", Modifier.padding(top = 4.dp), fontSize = 14.sp, color = secundaria)
                }
                for (n in ui.noticias.take(3)) {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(n.titulo, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${n.data} · ${n.texto}", fontSize = 12.sp, color = secundaria, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // ---- O jogador do utilizador (via de jogador) ----
        ui.meuJogador?.let { j ->
            item {
                Bloco {
                    Rotulo("O teu jogador")
                    Text(j.nome, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(j.linha1)
                    Text(j.linha2, fontSize = 13.sp, color = secundaria)
                }
            }
        }

        // ---- A zona da tabela onde está o clube ----
        item {
            Bloco(aoTocar = aoVerLiga) {
                Rotulo(ui.nomeLiga)
                for (l in ui.miniTabela) LinhaMiniTabela(l)
            }
        }

        // ---- Mensagens ----
        item { Rotulo("Mensagens", Modifier.padding(top = 6.dp, start = 4.dp)) }
        items(ui.mensagens) { m ->
            Bloco {
                Row {
                    Text(m.titulo, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(m.data, fontSize = 12.sp, color = secundaria, modifier = Modifier.padding(start = 8.dp))
                }
                Text(m.texto, fontSize = 14.sp, color = secundaria)
            }
        }
    }
}

private val COR_DERBY = Color(0xFFEF6C00)

/** O ambiente à volta do clube: etiqueta colorida (eufórico a hostil) e uma frase. */
@Composable
private fun Ambiente(nome: String, nivel: Int, texto: String) {
    val cor = when (nivel) {
        0 -> COR_VITORIA
        1 -> Color(0xFF689F38)
        2 -> COR_EMPATE
        3 -> COR_DERBY
        else -> COR_DERROTA
    }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Ambiente", Modifier.width(70.dp), fontSize = 13.sp)
        Surface(color = cor, shape = MaterialTheme.shapes.small) {
            Text(nome, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }
        Text(texto, Modifier.weight(1f).padding(start = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Todas as notícias guardadas (as últimas 80). */
@Composable
private fun ListaNoticias(noticias: List<NoticiaUi>, aoFechar: () -> Unit) {
    BackHandler(onBack = aoFechar)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notícias", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = aoFechar) { Text("Fechar") }
            }
        }
        if (noticias.isEmpty()) item { Text("Ainda não há notícias.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(noticias) { n ->
            Bloco {
                Row {
                    if (n.doClube) Rotulo("O teu clube", Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                    Text(n.data, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(n.titulo, Modifier.padding(top = 2.dp), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(n.texto, Modifier.padding(top = 2.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Barra de confiança (0 a 100) com uma palavra que a descreve. */
@Composable
private fun Confianca(quem: String, valor: Int) {
    val (palavra, cor) = when {
        valor >= 75 -> "muito satisfeita" to COR_VITORIA
        valor >= 50 -> "satisfeita" to MaterialTheme.colorScheme.primary
        valor >= 30 -> "preocupada" to MaterialTheme.colorScheme.tertiary
        else -> "lugar em risco" to COR_DERROTA
    }
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(quem, Modifier.width(70.dp), fontSize = 13.sp)
        LinearProgressIndicator(progress = { valor / 100f }, modifier = Modifier.weight(1f).height(8.dp), color = cor)
        Text(if (quem == "Adeptos") palavra.replace("satisfeita", "satisfeitos").replace("preocupada", "preocupados") else palavra,
            Modifier.width(120.dp).padding(start = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EquipaGrande(nome: String, emblema: pt.futebolcarreira.EmblemaUi) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Emblema(emblema, 56.dp)
        Text(nome, Modifier.padding(top = 4.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, fontSize = 14.sp)
    }
}

@Composable
private fun LinhaMiniTabela(l: LinhaUi) {
    val cor = if (l.meu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Celula("${l.posicao}", 26.dp, negrito = l.meu, cor = cor)
        Text(
            l.nome, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cor,
            fontWeight = if (l.meu) FontWeight.Bold else FontWeight.Normal,
        )
        Celula("${l.j}", negrito = l.meu, cor = cor)
        Celula(saldo(l.gm - l.gs), 36.dp, negrito = l.meu, cor = cor)
        Celula("${l.pts}", 36.dp, negrito = true, cor = cor)
    }
}

// -------------------------------------------------------------- calendário

@Composable
private fun Calendario(jogos: List<JogoCalendarioUi>, aoAbrirJogo: (Int) -> Unit, aoAbrirChave: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Text(
                "Jogos do teu clube nesta época. Toca num jogo já feito para ver o relato.\nMercado de transferências: julho, agosto e janeiro.",
                Modifier.padding(vertical = 10.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(jogos, key = { "${it.competicao}|${it.jornada}|${it.data}|${it.ronda}" }) { j ->
            val foraDaLiga = j.competicao.isNotEmpty()
            val toque = when {
                !j.temRelato -> Modifier
                foraDaLiga && j.chave.isNotEmpty() -> Modifier.clickable { aoAbrirChave(j.chave) }
                else -> Modifier.clickable { aoAbrirJogo(j.jornada) }
            }
            Row(
                Modifier.fillMaxWidth().then(toque).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.width(54.dp)) {
                    if (foraDaLiga) {
                        Text(j.data, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    } else {
                        Text("J${j.jornada}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(j.data, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                Emblema(j.emblema, 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(j.adversario, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val local = if (j.neutro) "Neutro" else if (j.emCasa) "Casa" else "Fora"
                    val etiqueta = if (foraDaLiga) listOf(local, j.competicao, j.ronda).filter { it.isNotEmpty() }.joinToString(" · ") else local
                    Text(etiqueta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                if (j.desfecho.isNotEmpty()) {
                    Text(j.resultado, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Desfecho(j.desfecho)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

// -------------------------------------------------------------------- mais

@Composable
private fun Mais(ui: CarreiraUi, ativo: Boolean, jogo: JogoViewModel) {
    var separador by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        // Cinco separadores: com letra de 13sp cabem todos em 411dp.
        TabRow(selectedTabIndex = separador) {
            listOf("Clube", "Finanças", "Jogos", "Seleções", "Opções").forEachIndexed { i, nome ->
                // Sem a margem normal do separador, para os cinco nomes caberem sem cortes.
                Tab(selected = separador == i, onClick = { separador = i }) {
                    Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                        Text(nome, maxLines = 1, fontSize = 14.sp, color = if (separador == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        when (separador) {
            0 -> SeparadorClube(ui.gestao, ativo, jogo)
            1 -> SeparadorFinancas(ui.financas, ativo, jogo::ampliarEstadio)
            2 -> Calendario(ui.calendario, jogo::abrirJogoDoClube, jogo::abrirJogoPorChave)
            3 -> SeparadorSelecoes(jogo.fase7.selecoes, jogo::abrirJogoPorChave)
            else -> Opcoes(ui, ativo, jogo::simularEpoca, jogo::sairParaMenu, jogo::abrirEditorCarreira)
        }
    }
}

@Composable
private fun Opcoes(ui: CarreiraUi, ativo: Boolean, aoSimularEpoca: () -> Unit, aoSair: () -> Unit, aoEditar: () -> Unit) {
    var confirmar by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Bloco {
            Rotulo("A tua carreira")
            Text(ui.utilizador, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("${ui.modo.nome} · ${ui.clube}")
        }
        Bloco {
            Rotulo("Gravação")
            Text("O jogo grava-se sozinho depois de cada jornada. Podes fechar a aplicação quando quiseres.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = { confirmar = true }, enabled = ativo && !ui.terminada, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            Text("Simular o resto da época", fontSize = 16.sp)
        }
        OutlinedButton(onClick = aoEditar, enabled = ativo, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            Text("Editar esta carreira", fontSize = 16.sp)
        }
        OutlinedButton(onClick = aoSair, enabled = ativo, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            Text("Gravar e sair para o menu", fontSize = 16.sp)
        }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Simular o resto da época?") },
            text = { Text("Todos os jogos que faltam são jogados de seguida, sem parar. Não dá para voltar atrás.") },
            confirmButton = { TextButton(onClick = { confirmar = false; aoSimularEpoca() }) { Text("Simular") } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
        )
    }
}
