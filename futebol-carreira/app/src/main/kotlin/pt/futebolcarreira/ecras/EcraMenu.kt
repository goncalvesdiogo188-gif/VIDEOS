package pt.futebolcarreira.ecras

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.nucleo.carreira.ResumoGravacao

/** Menu principal: continuar, nova carreira, carregar. */
@Composable
fun EcraMenu(gravacoes: List<ResumoGravacao>, ocupado: Boolean, aoContinuar: () -> Unit, aoNova: () -> Unit, aoCarregar: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("⚽", fontSize = 64.sp)
        Text("FUTEBOL", fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        Text("CARREIRA", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 8.sp)
        Text(
            "De jovem promessa a lenda, no relvado ou no banco.", Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))

        Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val ultima = gravacoes.firstOrNull()
            if (ultima != null) {
                BotaoGrande("Continuar\n${ultima.clube} · ${ultima.epoca}", ativo = !ocupado, aoTocar = aoContinuar)
            }
            if (ultima == null) {
                BotaoGrande("Nova carreira", ativo = !ocupado, aoTocar = aoNova)
            } else {
                OutlinedButton(onClick = aoNova, enabled = !ocupado, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Nova carreira", fontSize = 17.sp) }
                OutlinedButton(onClick = aoCarregar, enabled = !ocupado, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Carregar carreira", fontSize = 17.sp) }
            }
            if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(36.dp))
        Text("Versão de desenvolvimento 0.4 · Fase 4", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Lista das carreiras gravadas: tocar abre, «Apagar» apaga (depois de confirmar). */
@Composable
fun EcraCarregar(gravacoes: List<ResumoGravacao>, ocupado: Boolean, aoCarregar: (String) -> Unit, aoApagar: (String) -> Unit, aoVoltar: () -> Unit) {
    var paraApagar by remember { mutableStateOf<ResumoGravacao?>(null) }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        BarraVoltar("Carregar carreira", aoVoltar)
        if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (gravacoes.isEmpty()) {
            Text("Ainda não há carreiras gravadas.", Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(gravacoes, key = { it.id }) { g ->
                Bloco(aoTocar = if (ocupado) null else ({ aoCarregar(g.id) })) {
                    Text(g.clube, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${g.nome} · ${g.modo.nome}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Época ${g.epoca} · ${g.data}", Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { paraApagar = g }, enabled = !ocupado) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    paraApagar?.let { g ->
        AlertDialog(
            onDismissRequest = { paraApagar = null },
            title = { Text("Apagar carreira?") },
            text = { Text("A carreira de ${g.nome} (${g.clube}) vai ser apagada para sempre.") },
            confirmButton = { TextButton(onClick = { paraApagar = null; aoApagar(g.id) }) { Text("Apagar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { paraApagar = null }) { Text("Cancelar") } },
        )
    }
}

/** Barra de topo simples com seta para voltar atrás. */
@Composable
internal fun BarraVoltar(titulo: String, aoVoltar: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("◀", Modifier.clickable(onClick = aoVoltar).padding(horizontal = 16.dp, vertical = 12.dp), fontSize = 20.sp)
        Text(titulo, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
