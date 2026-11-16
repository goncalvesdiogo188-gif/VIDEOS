package pt.futebolcarreira.ecras

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.CarreiraUi
import pt.futebolcarreira.EmblemaUi
import pt.futebolcarreira.LinhaUi
import pt.futebolcarreira.NoticiaUi

// O separador «Início»: próximo jogo, forma recente, estado do clube, notícias e tabela.

@Composable
internal fun Inicio(
    ui: CarreiraUi, ativo: Boolean, aoJogar: () -> Unit, aoNovaEpoca: () -> Unit,
    aoAbrirJogo: (Int) -> Unit, aoAbrirChave: (String) -> Unit, aoVerLiga: () -> Unit,
    aoAceitar: (String) -> Unit, aoRecusar: () -> Unit, aoVerNoticias: () -> Unit = {}, aoJogarAoVivo: () -> Unit = {},
    aoDecidir: (Int, Int) -> Unit = { _, _ -> },
) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
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

        // ---- Decisões pendentes (imprensa, palestras, conversas, balneário) ----
        if (!ui.despedido && ui.emocao.decisoes.isNotEmpty()) item { CaixaDecisoes(ui.emocao.decisoes, ativo, aoDecidir) }

        // ---- O que há para fazer a seguir ----
        if (!ui.despedido) item { CartaoProximoJogo(ui, ativo, aoJogar, aoNovaEpoca, aoJogarAoVivo) }

        // ---- Forma recente ----
        if (!ui.despedido && ui.forma.isNotEmpty()) item { CartaoForma(ui.forma) }

        // ---- Classificação e último jogo, lado a lado ----
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Bloco(Modifier.weight(1f), aoTocar = aoVerLiga) {
                    Rotulo("Classificação")
                    Text("${ui.posicao}.º lugar", Modifier.padding(top = 4.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("${ui.pontos} pontos · ${ui.nomeLiga}", fontSize = 13.sp, color = secundaria, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val ultimo = ui.ultimo
                Bloco(Modifier.weight(1f), aoTocar = ultimo?.let { { if (it.competicao.isEmpty()) aoAbrirJogo(it.jornada) else aoAbrirChave(it.chave) } }) {
                    Rotulo("Último jogo")
                    if (ultimo == null) {
                        Text("—", Modifier.padding(top = 4.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Ainda não jogaste", fontSize = 13.sp, color = secundaria)
                    } else {
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Desfecho(ultimo.desfecho)
                            Text(ultimo.resultado, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Text(
                            (if (ultimo.neutro) "neutro" else if (ultimo.emCasa) "em casa" else "fora") + " · ${ultimo.adversario}" +
                                (if (ultimo.competicao.isNotEmpty()) " (${ultimo.competicao})" else ""),
                            fontSize = 13.sp, color = secundaria, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // ---- Estado do clube: direção, adeptos e ambiente (via de treinador) ----
        ui.direcao?.let { d ->
            item {
                Bloco {
                    Rotulo("Estado do clube")
                    Text("Objetivo: ${d.objetivo}", Modifier.padding(top = 4.dp, bottom = 8.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Confianca("Direção", d.direcao)
                    Confianca("Adeptos", d.adeptos)
                    Ambiente(d.ambiente, d.ambienteNivel, d.ambienteTexto)
                }
            }
        }

        // ---- Notícias ----
        item { CartaoNoticias(ui.noticias, aoVerNoticias) }

        // ---- O jogador do utilizador (via de jogador) ----
        ui.meuJogador?.let { j ->
            item {
                Bloco {
                    Rotulo("O teu jogador")
                    Text(j.nome, Modifier.padding(top = 4.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(j.linha1)
                    Text(j.linha2, fontSize = 13.sp, color = secundaria)
                    if (j.aviso.isNotEmpty()) Text(j.aviso, Modifier.padding(top = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }

        // ---- A zona da tabela onde está o clube ----
        item {
            Bloco(aoTocar = aoVerLiga) {
                Rotulo(ui.nomeLiga)
                Row(Modifier.padding(top = 6.dp, bottom = 2.dp)) {
                    Celula("#", 26.dp, negrito = true, cor = secundaria)
                    Text("Equipa", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = secundaria)
                    Celula("J", negrito = true, cor = secundaria)
                    Celula("DG", 36.dp, negrito = true, cor = secundaria)
                    Celula("Pts", 36.dp, negrito = true, cor = secundaria)
                }
                for (l in ui.miniTabela) LinhaMiniTabela(l)
            }
        }

        // ---- Mensagens ----
        item { Rotulo("Mensagens", Modifier.padding(top = 4.dp, start = 4.dp)) }
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

/** Pequena etiqueta arredondada (competição, ronda, derby). */
@Composable
private fun Etiqueta(texto: String, fundo: Color, letras: Color) {
    Surface(color = fundo, shape = MaterialTheme.shapes.small) {
        Text(texto, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = letras, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** O cartão grande do próximo jogo (ou do fim da época / do campeonato). */
@Composable
private fun CartaoProximoJogo(ui: CarreiraUi, ativo: Boolean, aoJogar: () -> Unit, aoNovaEpoca: () -> Unit, aoJogarAoVivo: () -> Unit) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    val proximo = ui.proximo
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            when {
                ui.terminada -> {
                    Rotulo("Época ${ui.epoca} terminada")
                    Text(ui.resumoFim, Modifier.padding(vertical = 10.dp))
                    BotaoGrande("Começar a época ${ui.proximaEpoca}", ativo = ativo, aoTocar = aoNovaEpoca)
                }
                proximo == null -> {
                    Rotulo("Fim do campeonato")
                    Text("O teu clube já fez todos os jogos. Avança para as outras ligas acabarem a época.", Modifier.padding(vertical = 10.dp))
                    BotaoGrande("Avançar", ativo = ativo, aoTocar = aoJogar)
                }
                else -> {
                    Rotulo("Próximo jogo")
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val comp = if (proximo.competicao.isEmpty()) "Jornada ${proximo.jornada} de ${proximo.totalJornadas}" else proximo.competicao
                        Box(Modifier.weight(1f, fill = false)) { Etiqueta(comp, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
                        if (proximo.ronda.isNotEmpty()) Box(Modifier.weight(1f, fill = false)) { Etiqueta(proximo.ronda, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
                    }
                    if (proximo.derby.isNotEmpty()) {
                        Box(Modifier.padding(top = 6.dp)) { Etiqueta(proximo.derby, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        // A equipa da casa aparece sempre à esquerda.
                        val eu: @Composable () -> Unit = { EquipaGrande(ui.clube, ui.emblema) }
                        val adversario: @Composable () -> Unit = { EquipaGrande(proximo.adversario, proximo.emblemaAdversario) }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { if (proximo.emCasa) eu() else adversario() }
                        Text("vs", Modifier.padding(horizontal = 4.dp), color = secundaria, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { if (proximo.emCasa) adversario() else eu() }
                    }
                    Text(proximo.data, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        if (proximo.neutro) "Em campo neutro" else if (proximo.emCasa) "Em casa" else "Fora de casa",
                        Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Adversário: ${proximo.detalhes}", Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp),
                        textAlign = TextAlign.Center, fontSize = 13.sp, color = secundaria,
                    )
                    BotaoGrande("Jogar", ativo = ativo, aoTocar = aoJogar)
                    OutlinedButton(onClick = aoJogarAoVivo, enabled = ativo, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp)) {
                        Text("Jogar ao vivo (3D)", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EquipaGrande(nome: String, emblema: EmblemaUi) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Emblema(emblema, 72.dp)
        Text(nome, Modifier.padding(top = 6.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, fontSize = 14.sp, overflow = TextOverflow.Ellipsis)
    }
}

/** Pílulas V/E/D dos últimos jogos (a mais recente à direita). */
@Composable
private fun CartaoForma(forma: List<String>) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    val vitorias = forma.count { it == "V" }
    val empates = forma.count { it == "E" }
    val derrotas = forma.count { it == "D" }
    Bloco {
        Rotulo("Forma recente")
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for (d in forma) PilulaForma(d) }
            Text(
                "$vitorias V · $empates E · $derrotas D", Modifier.weight(1f), textAlign = TextAlign.End,
                fontSize = 13.sp, color = secundaria, maxLines = 1,
            )
        }
    }
}

@Composable
private fun PilulaForma(letra: String) {
    val cor = when (letra) {
        "V" -> COR_VITORIA
        "D" -> COR_DERROTA
        else -> COR_EMPATE
    }
    Box(Modifier.size(34.dp).background(cor, CircleShape), contentAlignment = Alignment.Center) {
        Text(letra, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun CartaoNoticias(noticias: List<NoticiaUi>, aoVerNoticias: () -> Unit) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    Bloco {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Rotulo("Notícias", Modifier.weight(1f))
            if (noticias.isNotEmpty()) TextButton(onClick = aoVerNoticias) { Text("Ver todas") }
        }
        if (noticias.isEmpty()) {
            Text("Ainda não há notícias. Joga o primeiro jogo.", Modifier.padding(top = 4.dp), fontSize = 14.sp, color = secundaria)
        }
        noticias.take(3).forEachIndexed { i, n ->
            if (i > 0) HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(top = 8.dp)) {
                Text(n.titulo, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (n.doClube) "O teu clube · " else "") + "${n.data} · ${n.texto}",
                    fontSize = 12.sp, color = secundaria, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** O ambiente à volta do clube: etiqueta colorida (eufórico a hostil) e uma frase. */
@Composable
private fun Ambiente(nome: String, nivel: Int, texto: String) {
    val cor = when (nivel) {
        0 -> COR_VITORIA
        1 -> Color(0xFF689F38)
        2 -> COR_EMPATE
        3 -> MaterialTheme.colorScheme.tertiary
        else -> COR_DERROTA
    }
    val letras = if (nivel == 3) MaterialTheme.colorScheme.onTertiary else Color.White
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Ambiente", Modifier.width(76.dp), fontSize = 13.sp)
        Etiqueta(nome, cor, letras)
        Text(texto, Modifier.weight(1f).padding(start = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Todas as notícias guardadas (as últimas 80). */
@Composable
internal fun ListaNoticias(noticias: List<NoticiaUi>, aoFechar: () -> Unit) {
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
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(quem, Modifier.width(76.dp), fontSize = 13.sp)
        LinearProgressIndicator(progress = { valor / 100f }, modifier = Modifier.weight(1f).height(8.dp), color = cor)
        Text(
            if (quem == "Adeptos") palavra.replace("satisfeita", "satisfeitos").replace("preocupada", "preocupados") else palavra,
            Modifier.width(112.dp).padding(start = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        )
    }
}

@Composable
private fun LinhaMiniTabela(l: LinhaUi) {
    val cor = if (l.meu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val fundo = if (l.meu) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent
    Row(Modifier.fillMaxWidth().background(fundo, MaterialTheme.shapes.small).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
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
