package pt.futebolcarreira.ecras

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pt.futebolcarreira.PlantelUi
import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.Mentalidade

/**
 * Plantel de um clube: tática e lista de jogadores com as estatísticas da época.
 * Usado no separador «Plantel» (o clube do utilizador) e numa janela (os outros clubes).
 */
@Composable
internal fun ConteudoPlantel(
    plantel: PlantelUi, modifier: Modifier = Modifier,
    aoMudarFormacao: (Formacao?) -> Unit = {}, aoMudarMentalidade: (Mentalidade?) -> Unit = {},
    aoEscolherLugar: (Int) -> Unit = {}, aoOnzeAutomatico: () -> Unit = {}, aoAbrirJogador: (Int) -> Unit = {},
) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Emblema(plantel.emblema, 40.dp)
            Column {
                Text(plantel.titulo, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(plantel.detalhes, fontSize = 12.sp, lineHeight = 15.sp, color = secundaria)
            }
        }

        if (plantel.podeMudarTatica) {
            // "Auto" deixa a escolha ao treinador-adjunto (o computador).
            Text("Formação (em uso: ${plantel.formacaoEmUso})", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(plantel.formacao == null, { aoMudarFormacao(null) }, { Text("Auto") })
                for (f in Formacao.entries) FilterChip(plantel.formacao == f, { aoMudarFormacao(f) }, { Text(f.nome) })
            }
            Text("Mentalidade", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(plantel.mentalidade == null, { aoMudarMentalidade(null) }, { Text("Auto") })
                for (m in Mentalidade.entries) FilterChip(plantel.mentalidade == m, { aoMudarMentalidade(m) }, { Text(m.nome) })
            }
        } else {
            Text(
                "Formação ${plantel.formacaoEmUso} · Mentalidade: ${plantel.mentalidade?.nome ?: "decidida jogo a jogo"}",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp),
            )
        }

        LazyColumn(Modifier.weight(1f)) {
            // ---- Onze inicial: tocar num lugar para trocar o jogador ----
            item {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Rotulo("Onze para o próximo jogo", Modifier.weight(1f))
                    if (plantel.podeMudarTatica && plantel.onzeManual) {
                        TextButton(onClick = aoOnzeAutomatico) { Text("Escolha automática") }
                    }
                }
                if (plantel.podeMudarTatica) Text("Toca num lugar para trocar o jogador.", fontSize = 12.sp, color = secundaria)
            }
            items(plantel.onze) { l ->
                val toque = if (plantel.podeMudarTatica) Modifier.clickable { aoEscolherLugar(l.indice) } else Modifier
                Row(Modifier.fillMaxWidth().then(toque).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Celula(l.lugar, 44.dp, negrito = true, cor = MaterialTheme.colorScheme.primary)
                    Text(l.nome, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Avisa quando o jogador está fora da sua posição.
                    if (l.posicao.isNotEmpty() && l.posicao != l.lugar) Text("é ${l.posicao}", fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                    Celula(l.nota.toString(), 36.dp, negrito = true)
                }
            }
            item {
                Text("Suplentes: ${plantel.suplentes}", fontSize = 12.sp, color = secundaria, modifier = Modifier.padding(vertical = 6.dp))
                Rotulo("Plantel completo · toca num jogador para ver a ficha", Modifier.padding(top = 8.dp))
                CabecalhoPlantel()
            }
            items(plantel.jogadores) { j ->
                val cor = if (j.meu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                Column(Modifier.fillMaxWidth().clickable { aoAbrirJogador(j.id) }.padding(vertical = 5.dp)) {
                    Row {
                        Celula(j.posicao, 40.dp, cor = cor)
                        Text(
                            j.nome, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cor,
                            fontWeight = if (j.meu) FontWeight.Bold else FontWeight.Normal,
                        )
                        Celula(j.idade.toString(), 28.dp)
                        Celula(j.geral.toString(), negrito = true)
                        Celula(j.jogos.toString(), 26.dp)
                        Celula(j.golos.toString(), 26.dp)
                        Celula(j.assistencias.toString(), 26.dp)
                        Celula(j.nota, 38.dp)
                    }
                    Row(Modifier.padding(start = 40.dp)) {
                        Text("Potencial ${j.potencial} · ${j.valor}", fontSize = 12.sp, color = secundaria)
                        if (j.estado.isNotEmpty()) Text("  ·  ${j.estado}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun CabecalhoPlantel() {
    Row(Modifier.padding(top = 6.dp, bottom = 4.dp)) {
        Celula("Pos", 40.dp, negrito = true)
        Text("Nome", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Celula("Id.", 28.dp, negrito = true)
        Celula("Ger", negrito = true)
        Celula("J", 26.dp, negrito = true)
        Celula("G", 26.dp, negrito = true)
        Celula("A", 26.dp, negrito = true)
        Celula("Nota", 38.dp, negrito = true)
    }
}

/** Janela para escolher quem joga num lugar do onze: os melhores para esse lugar aparecem primeiro. */
@Composable
internal fun JanelaEscolha(escolha: pt.futebolcarreira.EscolhaUi, aoEscolher: (Int) -> Unit, aoFechar: () -> Unit) {
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f)) {
            Column(Modifier.padding(12.dp)) {
                Text("Quem joga a ${escolha.lugar}?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("O número é a nota do jogador neste lugar.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.weight(1f).padding(top = 6.dp)) {
                    items(escolha.candidatos) { c ->
                        val cor = if (c.apto) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        val toque = if (c.apto) Modifier.clickable { aoEscolher(c.jogadorId) } else Modifier
                        Row(Modifier.fillMaxWidth().then(toque).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Celula(c.posicao, 44.dp, cor = cor)
                            Column(Modifier.weight(1f)) {
                                Text(c.nome, fontSize = 14.sp, color = cor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (c.aviso.isNotEmpty()) Text(c.aviso, fontSize = 12.sp, color = if (c.apto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                            Celula(c.nota.toString(), 36.dp, negrito = true, cor = cor)
                        }
                    }
                }
                TextButton(onClick = aoFechar, modifier = Modifier.align(Alignment.End)) { Text("Cancelar") }
            }
        }
    }
}

/** Janela com o plantel de outro clube (só para ver). */
@Composable
internal fun JanelaPlantel(plantel: PlantelUi, aoFechar: () -> Unit, aoAbrirJogador: (Int) -> Unit) {
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f)) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                ConteudoPlantel(plantel, Modifier.weight(1f), aoAbrirJogador = aoAbrirJogador)
                TextButton(onClick = aoFechar, modifier = Modifier.align(Alignment.End)) { Text("Fechar") }
            }
        }
    }
}
