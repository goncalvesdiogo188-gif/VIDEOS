package pt.futebolcarreira.ecras

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.DecisaoUi

// Fase 13 (F1 e F2): a caixa «Decisões pendentes» do Início e o diálogo onde se responde
// (conferências de imprensa, palestras, conversas com jogadores, conflitos, protestos e homenagens).

/** Cartões das decisões à espera: tocar abre o diálogo com as respostas. */
@Composable
internal fun CaixaDecisoes(decisoes: List<DecisaoUi>, ativo: Boolean, aoEscolher: (id: Int, opcao: Int) -> Unit) {
    if (decisoes.isEmpty()) return
    var aberta by rememberSaveable { mutableIntStateOf(-1) }
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    Bloco {
        Rotulo("Decisões pendentes · ${decisoes.size}")
        Text("Responde antes do próximo jogo. Cada resposta tem efeitos pequenos mas reais.", Modifier.padding(top = 2.dp), fontSize = 12.sp, color = secundaria)
        decisoes.forEachIndexed { i, d ->
            if (i > 0) HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = ativo) { aberta = d.id }.padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EtiquetaDecisao(d.etiqueta)
                Column(Modifier.weight(1f)) {
                    Text(d.titulo, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(d.texto, fontSize = 12.sp, color = secundaria, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text("Responder", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
        }
    }
    val atual = decisoes.firstOrNull { it.id == aberta }
    if (atual != null) {
        AlertDialog(
            onDismissRequest = { aberta = -1 },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { aberta = -1 }) { Text("Responder mais tarde") } },
            title = { Text(atual.titulo, fontSize = 18.sp) },
            text = { PainelDecisao(atual) { opcao -> aberta = -1; aoEscolher(atual.id, opcao) } },
        )
    }
}

/** O que se mostra dentro do diálogo: a situação e uma resposta por botão. */
@Composable
internal fun PainelDecisao(d: DecisaoUi, aoEscolher: (Int) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EtiquetaDecisao(d.etiqueta)
        Text(d.texto, fontSize = 14.sp)
        d.opcoes.forEachIndexed { i, o ->
            OutlinedButton(onClick = { aoEscolher(i) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(o.rotulo, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (o.dica.isNotEmpty()) Text(o.dica, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EtiquetaDecisao(texto: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Text(
            texto, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1,
        )
    }
}
