package pt.futebolcarreira.ecras

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.JogoViewModel
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.DIAS_DA_SEMANA
import pt.futebolcarreira.nucleo.carreira.PlanoSemanal
import pt.futebolcarreira.nucleo.carreira.Sessao
import pt.futebolcarreira.nucleo.modelos.FocoTreino
import pt.futebolcarreira.nucleo.modelos.Jogador

private class JogadorTreino(val id: Int, val nome: String, val posicao: String, val idade: Int, val geral: Int, val fadiga: Int, val poupado: Boolean, val foco: FocoTreino)

private val NOMES_ATRIBUTOS = mapOf("ritmo" to "ritmo", "remate" to "remate", "passe" to "passe", "drible" to "drible", "defesa" to "defesa", "fisico" to "físico", "guarda_redes" to "guarda-redes")

/** Separador «Treino»: plano semanal, gestão da carga, treino individual e mudança de posição. */
@Composable
internal fun EcraTreinoPlanos(c: Carreira, ativo: Boolean, jogo: JogoViewModel, versao: Int) {
    val t = c.gestaoClube.treino
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    val plano = rememberGestao(c, versao) { t.plano().dias.toList() }
    val temPlano = rememberGestao(c, versao) { t.temPlano() }
    val efeitos = rememberGestao(c, versao) { t.efeitos() }
    val pesos = rememberGestao(c, versao) { t.pesosDoPlano() }
    val ajustes = rememberGestao(c, versao) { c.equipa.ajustes.riscoLesao to c.equipa.ajustes.crescimento }
    val rotacao = rememberGestao(c, versao) { t.rotacao() }
    val jogadores = rememberGestao(c, versao) {
        c.equipa.plantel.map { JogadorTreino(it.id, it.nome, it.posicao, it.idade, it.geral(), t.fadiga(it), t.poupado(it), t.individual(it)) }.sortedByDescending { it.geral }
    }
    val reconversoes = rememberGestao(c, versao) { t.reconversoes().toList() }
    val vagas = rememberGestao(c, versao) { t.vagasIndividuais() to t.maxReconversoes() }
    var escolherIndividual by remember { mutableStateOf(false) }
    var escolherPosicao by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ---- Plano semanal ----
        item {
            Bloco {
                Rotulo("Plano semanal")
                Text(
                    if (temPlano) "Toca num dia para mudar a sessão. O plano soma-se à intensidade e ao foco do separador Clube."
                    else "Sem plano escolhido: o treino é o habitual. Escolhe um plano para treinar com mais detalhe.",
                    fontSize = 12.sp, color = secundaria,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (nome in PlanoSemanal.PLANOS.keys) ChipToque(false, { jogo.gerir { frase(t.usarPreset(nome), "Plano «$nome» em vigor.") } }, { Text(nome) }, enabled = ativo)
                }
                for ((i, sessao) in plano.withIndex()) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = ativo) {
                        val proxima = Sessao.entries[(sessao.ordinal + 1) % Sessao.entries.size]
                        jogo.gerir { frase(t.definirSessao(i, proxima), "") .takeIf { it.isNotEmpty() } }
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Text(DIAS_DA_SEMANA[i], Modifier.weight(0.35f), fontSize = 14.sp)
                        Column(Modifier.weight(0.65f)) {
                            Text(sessao.nome + "  ▸", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (sessao.carga == 0) secundaria else MaterialTheme.colorScheme.primary)
                            Text(sessao.descricao, fontSize = 11.sp, color = secundaria, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        item {
            Bloco {
                Rotulo("O que o plano faz")
                LinhaValor("Carga da semana (9 = habitual)", "${efeitos.volume}")
                LinhaValor("Evolução dos jovens", "×${"%.2f".format(ajustes.second)}")
                LinhaValor("Risco de lesão no treino", "×${"%.2f".format(ajustes.first)}")
                LinhaValor("Recuperação da fadiga por dia", "%.1f".format(efeitos.recuperacaoPorDia))
                if (temPlano) {
                    val mais = pesos.filter { it.value >= 1.15 }.keys.map { NOMES_ATRIBUTOS[it] ?: it }
                    val menos = pesos.filter { it.value <= 0.85 }.keys.map { NOMES_ATRIBUTOS[it] ?: it }
                    if (mais.isNotEmpty()) Text("Evoluem mais: ${mais.joinToString()}.", Modifier.padding(top = 4.dp), fontSize = 13.sp, color = COR_VITORIA)
                    if (menos.isNotEmpty()) Text("Evoluem menos: ${menos.joinToString()}.", fontSize = 13.sp, color = secundaria)
                }
                Text("A equipa técnica (preparador, médico, fisioterapeuta e adjunto) melhora estes números.", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = secundaria)
            }
        }
        // ---- Carga ----
        item {
            Bloco {
                Rotulo("Gestão da carga")
                Text("Jogar muito seguido e treinar a fundo cansa. Com fadiga alta há mais lesões e a forma cai. Recupera-se com dias livres e com a equipa técnica.", fontSize = 12.sp, color = secundaria)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Rotação automática (os mais cansados jogam menos)", Modifier.weight(1f), fontSize = 14.sp)
                    Switch(checked = rotacao, onCheckedChange = { ligado -> jogo.gerir { t.definirRotacao(ligado); null } }, enabled = ativo)
                }
                for (j in jogadores.sortedByDescending { it.fadiga }.take(12)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Celula(j.posicao, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(j.nome, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            LinearProgressIndicator(progress = { j.fadiga / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(6.dp), color = if (j.fadiga >= 70) COR_DERROTA else if (j.fadiga >= 45) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                        }
                        Celula("${j.fadiga}", 36.dp, negrito = j.fadiga >= 70)
                        ChipToque(j.poupado, { jogo.gerir { frase(t.alternarPoupar(c.bd.jogadores.getValue(j.id)), "").takeIf { it.isNotEmpty() } } }, { Text(if (j.poupado) "Poupado" else "Poupar", fontSize = 12.sp) }, enabled = ativo)
                    }
                }
            }
        }
        // ---- Individual ----
        item {
            Bloco {
                val usados = jogadores.filter { it.foco != FocoTreino.EQUILIBRADO }
                Rotulo("Treino individual · ${usados.size} de ${vagas.first}")
                Text("Um foco só para um jogador: ele evolui mais nesses atributos. O adjunto dá mais vagas.", fontSize = 12.sp, color = secundaria)
                for (j in usados) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Celula(j.posicao, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(j.nome, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(j.foco.nome, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { jogo.gerir { frase(t.definirIndividual(c.bd.jogadores.getValue(j.id), FocoTreino.EQUILIBRADO), "").takeIf { it.isNotEmpty() } } }, enabled = ativo, modifier = Modifier.heightIn(min = 48.dp)) { Text("Tirar") }
                    }
                }
                BotaoSecundario("Dar treino individual a um jogador", ativo && usados.size < vagas.first, Modifier.padding(top = 6.dp)) { escolherIndividual = true }
            }
        }
        // ---- Mudança de posição ----
        item {
            Bloco {
                Rotulo("Mudança de posição · ${reconversoes.size} de ${vagas.second}")
                Text("Um jogador aprende uma posição nova em 2 a 4 meses e chega lá a jogar quase tão bem como na antiga.", fontSize = 12.sp, color = secundaria)
                for (r in reconversoes) {
                    val j = c.bd.jogadores[r.jogadorId] ?: continue
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${j.nome}: ${r.de} → ${r.para}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            LinearProgressIndicator(progress = { (r.total - r.meses) / r.total.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(6.dp))
                            Text("Falta${if (r.meses == 1) "" else "m"} ${r.meses} ${if (r.meses == 1) "mês" else "meses"}", fontSize = 12.sp, color = secundaria)
                        }
                        TextButton(onClick = { jogo.gerir { frase(t.cancelarReconversao(r.jogadorId), "").takeIf { it.isNotEmpty() } } }, enabled = ativo, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") }
                    }
                }
                BotaoSecundario("Treinar um jogador para outra posição", ativo && reconversoes.size < vagas.second, Modifier.padding(top = 6.dp)) { escolherPosicao = true }
            }
        }
    }

    if (escolherIndividual) {
        DialogoJogadorFoco(jogadores.filter { it.foco == FocoTreino.EQUILIBRADO }, { escolherIndividual = false }) { id, foco ->
            escolherIndividual = false
            jogo.gerir { frase(t.definirIndividual(it.bd.jogadores.getValue(id), foco), "Treino individual definido.") }
        }
    }
    if (escolherPosicao) {
        DialogoPosicao(c, jogadores.filter { it.posicao != "GR" && reconversoes.none { r -> r.jogadorId == it.id } }, { escolherPosicao = false }) { id, posicao ->
            escolherPosicao = false
            jogo.gerir { frase(t.iniciarReconversao(it.bd.jogadores.getValue(id), posicao), "Treino de posição iniciado.") }
        }
    }
}

@Composable
private fun LinhaEscolha(j: JogadorTreino, selecionado: Boolean, aoTocar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = aoTocar).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Celula(j.posicao, 40.dp)
        Text(j.nome, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal, color = if (selecionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Celula("${j.geral}", 36.dp, negrito = true)
    }
}

@Composable
private fun DialogoJogadorFoco(jogadores: List<JogadorTreino>, aoFechar: () -> Unit, aoGuardar: (Int, FocoTreino) -> Unit) {
    var escolhido by remember { mutableStateOf<Int?>(null) }
    var foco by remember { mutableStateOf(FocoTreino.ATAQUE) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Treino individual") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Foco", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (f in FocoTreino.entries.filter { it != FocoTreino.EQUILIBRADO }) ChipToque(foco == f, { foco = f }, { Text(f.nome) })
                }
                Text("Jogador", Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                for (j in jogadores) LinhaEscolha(j, escolhido == j.id) { escolhido = j.id }
            }
        },
        confirmButton = { TextButton(onClick = { escolhido?.let { aoGuardar(it, foco) } }, enabled = escolhido != null) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoPosicao(c: Carreira, jogadores: List<JogadorTreino>, aoFechar: () -> Unit, aoIniciar: (Int, String) -> Unit) {
    var escolhido by remember { mutableStateOf<Int?>(null) }
    var posicao by remember { mutableStateOf("") }
    val t = c.gestaoClube.treino
    val jogador = escolhido?.let { c.bd.jogadores[it] }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Mudança de posição") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Jogador", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                for (j in jogadores) LinhaEscolha(j, escolhido == j.id) { escolhido = j.id; posicao = "" }
                if (jogador != null) {
                    Text("Nova posição de ${jogador.nome}", Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (p in t.posicoesPossiveis(jogador)) ChipToque(posicao == p, { posicao = p }, { Text("$p · ${t.mesesPara(jogador, p)} m") })
                    }
                    Text("Posições parecidas demoram 2 meses; as outras 4.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { escolhido?.let { aoIniciar(it, posicao) } }, enabled = escolhido != null && posicao.isNotEmpty()) { Text("Começar") } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}
