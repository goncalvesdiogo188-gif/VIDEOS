package pt.futebolcarreira.ecras

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.GestaoClubeUi
import pt.futebolcarreira.JogoViewModel
import pt.futebolcarreira.nucleo.modelos.FocoTreino
import pt.futebolcarreira.nucleo.modelos.Intensidade
import pt.futebolcarreira.nucleo.modelos.Jogador

/** Separador «Clube» (em «Mais»): treino, instalações, academia e olheiros. */
@Composable
internal fun SeparadorClube(g: GestaoClubeUi?, ativo: Boolean, jogo: JogoViewModel) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    if (g == null) {
        Text(
            "Na via de jogador é o treinador que decide o treino, a academia e os olheiros.",
            Modifier.padding(16.dp), color = secundaria,
        )
        return
    }
    var pais by rememberSaveable { mutableStateOf(g.paises.firstOrNull()?.id ?: "") }
    var posicao by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ---- Treino ----
        item {
            Bloco {
                Rotulo("Treino")
                Text("O que se treina mais: esses atributos evoluem mais depressa.", fontSize = 13.sp, color = secundaria)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (f in FocoTreino.entries) FilterChip(g.foco == f, { jogo.mudarFoco(f) }, { Text(f.nome) }, enabled = ativo)
                }
                Text("Intensidade: mais carga faz evoluir mais, mas traz mais lesões.", fontSize = 13.sp, color = secundaria, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in Intensidade.entries) FilterChip(g.intensidade == i, { jogo.mudarIntensidade(i) }, { Text(i.nome) }, enabled = ativo)
                }
                Text(
                    "Os jogadores evoluem um pouco todos os meses. Quem joga evolui mais. A forma e a moral contam em cada jogo.",
                    fontSize = 12.sp, color = secundaria, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // ---- Instalações ----
        item {
            Bloco {
                Rotulo("Instalações · caixa ${g.caixa}")
                for (i in g.instalacoes) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${i.nome}: ${"★".repeat(i.nivel)}${"☆".repeat(5 - i.nivel)}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(i.efeito, fontSize = 12.sp, color = secundaria)
                        }
                    }
                    OutlinedButton(
                        onClick = { jogo.melhorarInstalacao(i.instalacao) }, enabled = i.podeMelhorar,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 44.dp),
                    ) { Text(i.botao) }
                }
            }
        }

        // ---- Academia ----
        item {
            Bloco {
                Rotulo("Academia · ${g.academia.size} jovens")
                Text("Toca num jovem para o subir ao plantel ou dispensar. Todos os verões entra uma geração nova.", fontSize = 12.sp, color = secundaria)
                Row(Modifier.padding(top = 6.dp, bottom = 2.dp)) {
                    Celula("Pos", 40.dp, negrito = true)
                    Text("Nome", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Celula("Id.", 28.dp, negrito = true)
                    Celula("Ger", negrito = true)
                    Celula("Pot", 34.dp, negrito = true)
                }
                if (g.academia.isEmpty()) Text("A academia está vazia.", color = secundaria)
                for (j in g.academia) {
                    Row(Modifier.fillMaxWidth().clickable { jogo.abrirJogador(j.id) }.padding(vertical = 7.dp)) {
                        Celula(j.posicao, 40.dp)
                        Text(j.nome, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Celula("${j.idade}", 28.dp)
                        Celula("${j.geral}", negrito = true)
                        Celula("${j.potencial}", 34.dp, cor = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // ---- Olheiros ----
        item {
            Bloco {
                Rotulo("Olheiros")
                Text(
                    "Dos jovens de outros clubes só sabes o potencial aproximado. Manda o olheiro a um país e, em 4 semanas, ele traz-te os melhores jovens.",
                    fontSize = 12.sp, color = secundaria,
                )
                if (g.olheiroEmViagem.isNotEmpty()) {
                    Text(g.olheiroEmViagem, Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
                } else {
                    Text("País", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (p in g.paises) FilterChip(pais == p.id, { pais = p.id }, { Text(p.nome) })
                    }
                    Text("Posição", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(posicao.isEmpty(), { posicao = "" }, { Text("Todas") })
                        for (p in Jogador.POSICOES) FilterChip(posicao == p, { posicao = p }, { Text(p) })
                    }
                    val custo = g.paises.firstOrNull { it.id == pais }?.custo ?: ""
                    BotaoGrande("Enviar olheiro ($custo)", Modifier.padding(top = 6.dp), ativo = ativo && pais.isNotEmpty()) { jogo.enviarOlheiro(pais, posicao) }
                }
                if (g.relatorio.isNotEmpty()) {
                    Rotulo("Último relatório · toca para ver a ficha", Modifier.padding(top = 12.dp, bottom = 2.dp))
                    for (j in g.relatorio) {
                        Row(Modifier.fillMaxWidth().clickable { jogo.abrirJogador(j.id) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Celula(j.posicao, 40.dp)
                            Column(Modifier.weight(1f)) {
                                Text(j.nome, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${j.clube} · ${j.idade} anos", fontSize = 12.sp, color = secundaria)
                            }
                            Celula("${j.geral}", negrito = true)
                            Celula(j.preco, 60.dp, cor = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
