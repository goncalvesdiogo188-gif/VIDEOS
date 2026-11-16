package pt.futebolcarreira.aovivo

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Botão de teste das Opções: abre o protótipo do ecrã em Godot (fase C0). */
@Composable
fun BotaoPrototipoGodot() {
    val contexto = LocalContext.current
    OutlinedButton(
        onClick = { EcraGodot.abrir(contexto) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
    ) { Text("Protótipo Godot", fontSize = 15.sp) }
    Text(
        "Teste: abre um campo 3D feito no Godot. Toca e arrasta para mover o jogador.",
        Modifier.padding(top = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
