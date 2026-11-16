package pt.futebolcarreira.ecras

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.EmblemaUi
import pt.futebolcarreira.JogadorUi

// B2: cartão de jogador ao estilo de uma carta: nível geral grande, posição, camisola, forma e estado.

/** Uma carta de jogador. A cor de fundo depende do nível (dourada, azulada ou neutra). */
@Composable
internal fun CartaJogador(j: JogadorUi, emblema: EmblemaUi, aoTocar: () -> Unit, modifier: Modifier = Modifier) {
    val cores = MaterialTheme.colorScheme
    val fundo = when {
        j.geral >= 80 -> cores.tertiaryContainer
        j.geral >= 70 -> cores.primaryContainer
        else -> cores.surfaceContainerHigh
    }
    val sobre = when {
        j.geral >= 80 -> cores.onTertiaryContainer
        j.geral >= 70 -> cores.onPrimaryContainer
        else -> cores.onSurface
    }
    Surface(
        color = fundo, contentColor = sobre, shape = MaterialTheme.shapes.medium,
        border = if (j.meu) BorderStroke(2.dp, cores.primary) else null,
        modifier = modifier.clickable(onClick = aoTocar),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column {
                    Text(j.geral.toString(), fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                    Text(j.posicao, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Camisola(emblema, 46.dp)
            }
            Text(
                j.nome, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text("${j.idade} anos · pot. ${j.potencial}", fontSize = 11.sp, maxLines = 1, color = sobre.copy(alpha = 0.8f))
            Row(Modifier.padding(top = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Dado("J", j.jogos.toString())
                Dado("G", j.golos.toString())
                Dado("A", j.assistencias.toString())
                Dado("Nota", j.nota)
            }
            // Forma (só no clube do utilizador) e estado (lesão ou castigo).
            if (j.forma.isNotEmpty()) Text(j.forma, fontSize = 11.sp, lineHeight = 13.sp, color = sobre.copy(alpha = 0.8f), modifier = Modifier.padding(top = 5.dp))
            if (j.estado.isNotEmpty()) {
                Surface(color = cores.error, contentColor = cores.onError, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.padding(top = 5.dp)) {
                    Text(j.estado, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun Dado(rotulo: String, valor: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(rotulo, fontSize = 10.sp, maxLines = 1, color = LocalContentColor.current.copy(alpha = 0.7f))
    }
}

