package pt.futebolcarreira.ecras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.HistoriaUi
import pt.futebolcarreira.LinhaHistoriaUi

/** A «sala de história» do clube: troféus, épocas, melhores marcadores, lendas, recordes e momentos. */
@Composable
internal fun SeparadorHistoria(h: HistoriaUi) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Bloco {
                Rotulo("Sala de história · ${h.clube}")
                if (h.trofeus.isEmpty()) {
                    Text("Ainda sem troféus na vitrine. O primeiro vai ficar aqui para sempre.", Modifier.padding(top = 4.dp), fontSize = 13.sp, color = secundaria)
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((nome, n) in h.trofeus) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$n", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                Text(nome, fontSize = 11.sp, color = secundaria, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
        item {
            Bloco {
                Rotulo("Melhores marcadores de sempre")
                if (h.marcadores.isEmpty()) Text("Ainda sem golos na história.", Modifier.padding(top = 4.dp), fontSize = 13.sp, color = secundaria)
                h.marcadores.forEachIndexed { i, l -> LinhaJogador(i + 1, l) }
            }
        }
        item {
            Bloco {
                Rotulo("Lendas do clube")
                Text("Quem chega a 120 jogos, 50 golos ou 6 épocas entra na galeria. Quando sai, o clube homenageia-o.", fontSize = 12.sp, color = secundaria)
                if (h.lendas.isEmpty()) Text("Ainda não há lendas.", Modifier.padding(top = 6.dp), fontSize = 13.sp, color = secundaria)
                h.lendas.forEachIndexed { i, l -> LinhaJogador(i + 1, l) }
            }
        }
        item {
            Bloco {
                Rotulo("Recordes")
                if (h.recordes.isEmpty()) Text("Os recordes aparecem com os jogos.", Modifier.padding(top = 4.dp), fontSize = 13.sp, color = secundaria)
                for ((titulo, valor) in h.recordes) {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(titulo, fontSize = 13.sp, color = secundaria)
                        Text(valor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Bloco {
                Rotulo("Momentos e prémios")
                if (h.momentos.isEmpty()) Text("Prémios, homenagens e recordes ficam registados aqui.", Modifier.padding(top = 4.dp), fontSize = 13.sp, color = secundaria)
                h.momentos.forEachIndexed { i, (data, texto) ->
                    if (i > 0) HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(data, fontSize = 12.sp, color = secundaria, maxLines = 1)
                        Text(texto, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Bloco {
                Rotulo("Época a época")
                if (h.epocas.isEmpty()) Text("A primeira época ainda não acabou.", Modifier.padding(top = 4.dp), fontSize = 13.sp, color = secundaria)
                for (e in h.epocas) Text(e, Modifier.padding(top = 6.dp), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun LinhaJogador(posicao: Int, l: LinhaHistoriaUi) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$posicao.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(l.nome + if (l.lenda) "  ★" else "", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(l.detalhe, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Text(l.valor, fontSize = 15.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}
