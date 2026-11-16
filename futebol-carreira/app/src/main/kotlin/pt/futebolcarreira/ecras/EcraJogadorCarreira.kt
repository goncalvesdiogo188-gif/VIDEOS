package pt.futebolcarreira.ecras

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.ConquistaUi
import pt.futebolcarreira.JogadorCarreiraUi
import pt.futebolcarreira.JogoViewModel
import pt.futebolcarreira.OfertaUi
import pt.futebolcarreira.TemporadaUi
import pt.futebolcarreira.nucleo.modelos.FocoTreino

/**
 * O separador «Carreira» da via de jogador. Quatro partes: Perfil (atributos, contrato, minutos, pedido do treinador
 * e treino), Percurso (épocas e totais), Propostas (outros clubes e renovação) e Prémios (troféus e prémios individuais).
 */
@Composable
fun SeparadorJogador(j: JogadorCarreiraUi, ativo: Boolean, jogo: JogoViewModel, subInicial: Int = 0) {
    var sub by rememberSaveable { mutableIntStateOf(subInicial) }
    Column(Modifier.fillMaxSize()) {
        // Letra grande ou ecrã pequeno: os separadores deslizam em vez de ficarem cortados.
        ScrollableTabRow(selectedTabIndex = sub, edgePadding = 0.dp) {
            listOf("Perfil", "Percurso", "Propostas", "Prémios").forEachIndexed { i, nome ->
                Tab(selected = sub == i, onClick = { sub = i }) {
                    Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                        Text(nome, maxLines = 1, fontSize = 14.sp, color = if (sub == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        when (sub) {
            0 -> Perfil(j, ativo, jogo)
            1 -> Percurso(j)
            2 -> Propostas(j, ativo, jogo)
            else -> Premios(j)
        }
    }
}

private val CORES_ESTATUTO = listOf(Color(0xFF2E7D32), Color(0xFF558B2F), Color(0xFF9E7A00), Color(0xFFEF6C00), Color(0xFFC62828))

@Composable
private fun Perfil(j: JogadorCarreiraUi, ativo: Boolean, jogo: JogoViewModel) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Bloco {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Emblema(j.emblema, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(j.nome, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(j.linha1, fontSize = 13.sp, color = secundaria, maxLines = 2)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${j.geral}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("pot. ${j.potencial}", fontSize = 12.sp, color = secundaria)
                    }
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = CORES_ESTATUTO[j.estatutoNivel.coerceIn(0, 4)], shape = MaterialTheme.shapes.small) {
                        Text(j.estatuto, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    }
                    Text(j.formaMoral, Modifier.weight(1f).padding(start = 8.dp), fontSize = 12.sp, color = secundaria, maxLines = 2)
                }
            }
        }
        item {
            Bloco {
                Rotulo("Atributos")
                for ((nome, valor) in j.atributos) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(nome, Modifier.width(96.dp), fontSize = 14.sp)
                        LinearProgressIndicator(progress = { valor / 100f }, modifier = Modifier.weight(1f).height(8.dp))
                        Text("$valor", Modifier.width(34.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
        }
        item {
            Bloco {
                Rotulo("Contrato e valor")
                for ((titulo, valor) in j.contrato) {
                    Row(Modifier.padding(top = 4.dp)) {
                        Text(titulo, Modifier.width(150.dp), fontSize = 13.sp, color = secundaria)
                        Text(valor, Modifier.weight(1f), fontSize = 14.sp)
                    }
                }
            }
        }
        item {
            Bloco {
                Rotulo("Minutos e estatuto")
                Text("Esta época: ${j.estaEpoca}", Modifier.padding(top = 4.dp), fontSize = 14.sp)
                Text(j.selecao, Modifier.padding(top = 4.dp), fontSize = 13.sp, color = secundaria)
                OutlinedButton(
                    onClick = jogo::pedirMinutos, enabled = ativo,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
                ) { Text("Pedir mais minutos ao treinador") }
            }
        }
        item {
            Bloco {
                Rotulo("Pedido do treinador")
                val p = j.pedido
                if (p == null) {
                    Text("Sem pedidos por agora. De vez em quando o treinador põe-te uma meta; cumpri-la dá-te confiança e moral.", Modifier.padding(top = 4.dp), fontSize = 13.sp, color = secundaria)
                } else {
                    Text(p.texto, Modifier.padding(top = 4.dp), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(p.progresso, Modifier.padding(top = 2.dp), fontSize = 13.sp, color = secundaria)
                }
            }
        }
        item {
            Bloco {
                Rotulo("Foco do treino")
                Text("Escolhe o que treinas mais. Conta para os treinos mensais do jogador.", Modifier.padding(vertical = 4.dp), fontSize = 13.sp, color = secundaria)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (f in FocoTreino.entries) ChipToque(j.foco == f, { jogo.escolherFocoJogador(f) }, { Text(f.nome) }, enabled = ativo)
                }
            }
        }
    }
}

@Composable
private fun Percurso(j: JogadorCarreiraUi) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Bloco {
                Rotulo("Totais da carreira")
                Text(j.totais, Modifier.padding(top = 4.dp), fontSize = 14.sp)
                Text(j.selecao, Modifier.padding(top = 4.dp), fontSize = 13.sp, color = secundaria)
            }
        }
        item {
            Bloco {
                Rotulo("Época a época")
                Row(Modifier.padding(top = 6.dp)) {
                    Text("Época", Modifier.width(64.dp), fontSize = 11.sp, color = secundaria)
                    Text("Clube", Modifier.weight(1f), fontSize = 11.sp, color = secundaria)
                    Celula("J", 28.dp, cor = secundaria)
                    Celula("G", 28.dp, cor = secundaria)
                    Celula("A", 28.dp, cor = secundaria)
                    Celula("Nota", 52.dp, cor = secundaria)
                    Celula("Min.", 44.dp, cor = secundaria)
                }
                if (j.temporadas.isEmpty()) Text("Ainda não jogaste nenhuma partida.", Modifier.padding(top = 6.dp), fontSize = 13.sp, color = secundaria)
                for (t in j.temporadas) LinhaTemporada(t)
            }
        }
    }
}

@Composable
private fun LinhaTemporada(t: TemporadaUi) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t.epoca, Modifier.width(64.dp), fontSize = 12.sp, maxLines = 1)
        Text(t.clube, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Celula("${t.jogos}", 28.dp)
        Celula("${t.golos}", 28.dp)
        Celula("${t.assistencias}", 28.dp)
        Celula(t.nota, 52.dp)
        Celula("${t.minutos}", 44.dp)
    }
}

@Composable
private fun Propostas(j: JogadorCarreiraUi, ativo: Boolean, jogo: JogoViewModel) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        j.renovacao?.let { r ->
            item {
                Bloco {
                    Rotulo("Renovação de contrato")
                    Text("O ${j.clube} quer renovar contigo: $r.", Modifier.padding(vertical = 6.dp), fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = jogo::aceitarRenovacao, enabled = ativo, modifier = Modifier.weight(1f)) { Text("Renovar") }
                        TextButton(onClick = jogo::recusarRenovacao, enabled = ativo, modifier = Modifier.weight(1f)) { Text("Recusar") }
                    }
                }
            }
        }
        item {
            Bloco {
                Rotulo("Propostas de outros clubes")
                Text(
                    when {
                        j.emprestado -> "Estás emprestado: só podes mudar quando voltares ao clube de origem."
                        j.ofertas.isNotEmpty() -> "Tens ${j.ofertas.size} ${if (j.ofertas.size == 1) "proposta" else "propostas"}. Aceitar muda-te de clube já."
                        j.mercadoAberto -> "Nenhum clube apresentou proposta nesta janela."
                        else -> "O mercado está fechado. As propostas aparecem em julho, agosto e janeiro, conforme o teu nível, o teu estatuto e a reputação dos clubes."
                    },
                    Modifier.padding(top = 4.dp), fontSize = 13.sp, color = secundaria,
                )
                if (j.pedidoSaida.isNotEmpty()) Text("Pediste ${if (j.pedidoSaida == "empréstimo") "um" else "uma"} ${j.pedidoSaida}: as propostas contam com isso na próxima janela.", Modifier.padding(top = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        items(j.ofertas, key = { it.id }) { o -> CartaoOferta(o, ativo, jogo) }
        item {
            Bloco {
                Rotulo("Querer sair")
                Text("Se não estás satisfeito, pede ao clube para sair. O pedido tem efeito quando o mercado estiver aberto (e deixa o treinador um pouco desiludido).", Modifier.padding(vertical = 6.dp), fontSize = 13.sp, color = secundaria)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { jogo.pedirSaida(false) }, enabled = ativo && !j.emprestado, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Pedir transferência", fontSize = 14.sp) }
                    OutlinedButton(onClick = { jogo.pedirSaida(true) }, enabled = ativo && !j.emprestado, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Pedir empréstimo", fontSize = 14.sp) }
                }
            }
        }
    }
}

@Composable
private fun CartaoOferta(o: OfertaUi, ativo: Boolean, jogo: JogoViewModel) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    Bloco {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Emblema(o.emblema, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(o.clube, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(o.detalhes, fontSize = 12.sp, color = secundaria, maxLines = 2)
            }
            if (o.emprestimo) {
                Surface(color = MaterialTheme.colorScheme.tertiary, shape = MaterialTheme.shapes.small) {
                    Text("Empréstimo", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiary)
                }
            }
        }
        Text(o.condicoes, Modifier.padding(top = 8.dp), fontSize = 14.sp)
        Text(o.estatuto, Modifier.padding(top = 2.dp), fontSize = 13.sp, color = secundaria)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { jogo.aceitarOferta(o.id) }, enabled = ativo, modifier = Modifier.weight(1f)) { Text("Aceitar") }
            TextButton(onClick = { jogo.recusarOferta(o.id) }, enabled = ativo, modifier = Modifier.weight(1f)) { Text("Recusar") }
        }
    }
}

@Composable
private fun Premios(j: JogadorCarreiraUi) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Bloco {
                Rotulo("Os teus prémios")
                if (j.premios.isEmpty()) Text("Ainda não ganhaste prémios individuais. Fica entre os melhores da liga e chega à Bola de Ouro.", Modifier.padding(top = 4.dp), fontSize = 13.sp, color = secundaria)
                for (p in j.premios) LinhaConquista(p)
            }
        }
        item {
            Bloco {
                Rotulo("Troféus")
                if (j.trofeus.isEmpty()) Text("Ainda sem troféus. Ganha campeonatos, taças e competições europeias com o teu clube.", Modifier.padding(top = 4.dp), fontSize = 13.sp, color = secundaria)
                for (p in j.trofeus) LinhaConquista(p)
            }
        }
        if (j.premiosDaLiga.isNotEmpty()) {
            item {
                Bloco {
                    Rotulo("Prémios da liga")
                    Text(j.tituloPremiosDaLiga, Modifier.padding(top = 2.dp), fontSize = 12.sp, color = secundaria)
                    for ((titulo, vencedor) in j.premiosDaLiga) {
                        Row(Modifier.padding(top = 5.dp)) {
                            Text(titulo, Modifier.width(120.dp), fontSize = 13.sp, color = secundaria)
                            Text(vencedor, Modifier.weight(1f), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        item {
            Bloco {
                Rotulo("Bola de Ouro")
                if (j.bolaDeOuro.isEmpty()) Text("Ainda não houve nenhuma entrega.", Modifier.padding(top = 4.dp), fontSize = 13.sp, color = secundaria)
                for ((epoca, vencedor) in j.bolaDeOuro) {
                    Row(Modifier.padding(top = 5.dp)) {
                        Text(epoca, Modifier.width(70.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(vencedor, Modifier.weight(1f), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaConquista(p: ConquistaUi) {
    Row(Modifier.padding(top = 6.dp)) {
        Text(p.epoca, Modifier.width(70.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f)) {
            Text(p.titulo, fontSize = 14.sp)
            if (p.detalhe.isNotEmpty()) Text(p.detalhe, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
