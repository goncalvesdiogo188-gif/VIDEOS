package pt.futebolcarreira.ecras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.BalnearioUi
import pt.futebolcarreira.CarreiraUi
import pt.futebolcarreira.JogoViewModel

// Fase 13 (F3 e F4): em Mais → Clube há três partes: Gestão (treino, instalações...), Balneário e História.

/** O separador «Clube» com o seletor Gestão · Balneário · História. */
@Composable
internal fun SeparadorClubeEmocao(ui: CarreiraUi, ativo: Boolean, jogo: JogoViewModel) {
    var parte by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Gestão", "Balneário", "História").forEachIndexed { i, nome ->
                FilterChip(parte == i, { parte = i }, { Text(nome, maxLines = 1, fontSize = 14.sp) }, Modifier.weight(1f).heightIn(min = 48.dp))
            }
        }
        when (parte) {
            0 -> SeparadorClube(ui.gestao, ativo, jogo)
            1 -> SeparadorBalneario(ui.emocao.balneario, ativo, jogo::nomearCapitao)
            else -> SeparadorHistoria(ui.emocao.historia)
        }
    }
}

/** Balneário: humor do grupo, capitão e líderes, promessas, voz das bancadas e o que se decidiu. */
@Composable
internal fun SeparadorBalneario(b: BalnearioUi?, ativo: Boolean, aoNomear: (Int) -> Unit) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    if (b == null) {
        Text("O balneário é do treinador. Na via de jogador, o que se passa à tua volta vê-se nas notícias.", Modifier.padding(16.dp), color = secundaria)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Bloco {
                Rotulo("O grupo")
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(b.humor, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Moral média ${b.moralMedia}", fontSize = 13.sp, color = secundaria)
                }
                Text("Capitão: ${b.capitao}", Modifier.padding(top = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Bloco {
                Rotulo("Capitão e líderes")
                Text("Os mais influentes do plantel (qualidade, idade e tempo de casa). Quem tem a braçadeira segura o grupo nas derrotas.", fontSize = 12.sp, color = secundaria)
                for (l in b.lideres) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                            Text(l.posicao, Modifier.width(44.dp).padding(vertical = 5.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(l.nome, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Influência ${l.influencia} · moral ${l.moral}", fontSize = 12.sp, color = secundaria)
                        }
                        if (l.capitao) {
                            Text("Capitão", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        } else {
                            OutlinedButton(onClick = { aoNomear(l.id) }, enabled = ativo, modifier = Modifier.heightIn(min = 44.dp)) { Text("Nomear", fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
        if (b.promessas.isNotEmpty()) {
            item {
                Bloco {
                    Rotulo("Promessas em curso")
                    for (p in b.promessas) Text(p, Modifier.padding(top = 6.dp), fontSize = 13.sp)
                }
            }
        }
        item {
            Bloco {
                Rotulo("Voz das bancadas")
                if (b.bancadas.isEmpty()) Text("As bancadas estão em silêncio. Os cânticos e os protestos aparecem aqui.", Modifier.padding(top = 4.dp), fontSize = 13.sp, color = secundaria)
                for (t in b.bancadas.take(8)) {
                    Text(t, Modifier.padding(top = 6.dp), fontSize = 13.sp)
                }
            }
        }
        item {
            Bloco {
                Rotulo("O que decidiste")
                if (b.registo.isEmpty()) Text("Ainda não houve decisões.", Modifier.padding(top = 4.dp), fontSize = 13.sp, color = secundaria)
                b.registo.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(t, Modifier.padding(top = 6.dp), fontSize = 12.sp, color = secundaria)
                }
            }
        }
    }
}
