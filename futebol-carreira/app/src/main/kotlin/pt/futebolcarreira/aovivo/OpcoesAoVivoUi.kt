package pt.futebolcarreira.aovivo

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pt.futebolcarreira.ecras.Bloco
import pt.futebolcarreira.ecras.Rotulo
import pt.futebolcarreira.nucleo.aovivo.DuracaoJogo
import pt.futebolcarreira.nucleo.aovivo.NivelMira

/** Opções do jogo ao vivo (em Mais → Opções): duração do jogo, ajuda de mira e botões. Guarda nas SharedPreferences "opcoes". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlocoOpcoesAoVivo() {
    val prefs = OpcoesAoVivo.prefs(LocalContext.current)
    var duracao by remember { mutableStateOf(OpcoesAoVivo.duracaoMinutos(prefs)) }
    var mira by remember { mutableStateOf(OpcoesAoVivo.miraEscolhida(prefs)) }
    var editar by remember { mutableStateOf(false) }
    var estilo by remember { mutableStateOf(OpcoesAoVivo.estilo(prefs)) }
    var dificuldade by remember { mutableStateOf(pt.futebolcarreira.nucleo.aovivo.Dificuldade.deTexto(prefs.getString("dificuldade_3d", null))) }
    Bloco {
        Rotulo("Jogo ao vivo: gráficos")
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (e in EstiloGraficos.entries) FilterChip(
                selected = estilo == e, onClick = { estilo = e; OpcoesAoVivo.definirEstilo(prefs, e) },
                label = { Text(e.nome.replace("PlayStation", "PS"), fontSize = 14.sp, maxLines = 1) }, modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        Text("PS2: imagem suave com relva, público e luzes. PS1: pixelizado. Vale para o próximo jogo.", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Rotulo("Jogo ao vivo: dificuldade", Modifier.padding(top = 10.dp))
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in pt.futebolcarreira.nucleo.aovivo.Dificuldade.entries) FilterChip(
                selected = dificuldade == d, onClick = { dificuldade = d; prefs.edit().putString("dificuldade_3d", d.name).apply() },
                label = { Text(d.nome, fontSize = 14.sp, maxLines = 1) }, modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        Text("Muda a defesa e a pressão do adversário.", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Rotulo("Jogo ao vivo: duração", Modifier.padding(top = 10.dp))
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in DuracaoJogo.OPCOES_MINUTOS) FilterChip(
                selected = duracao == m, onClick = { duracao = m; OpcoesAoVivo.definirDuracao(prefs, m) },
                label = { Text(DuracaoJogo.texto(m), fontSize = 15.sp) }, modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        Text("Tempo real de um jogo de 90 minutos.", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Rotulo("Ajuda de mira", Modifier.padding(top = 10.dp))
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = mira == null, onClick = { mira = null; OpcoesAoVivo.definirMira(prefs, null) },
                label = { Text("Auto", fontSize = 14.sp, maxLines = 1) }, modifier = Modifier.heightIn(min = 48.dp),
            )
            for (n in NivelMira.entries) FilterChip(
                selected = mira == n, onClick = { mira = n; OpcoesAoVivo.definirMira(prefs, n) },
                label = { Text(n.nome, fontSize = 14.sp, maxLines = 1) }, modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        Text(
            "Puxa os passes e os remates para o colega ou o canto certo. «Auto» segue a dificuldade: forte no Fácil, nenhuma no Lendário.",
            Modifier.padding(top = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { editar = true }, Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp)) { Text("Personalizar botões", fontSize = 15.sp) }
    }
    if (editar) EditorBotoes(prefs) { editar = false }
}

/** Ecrã para arrastar, ligar/desligar e mudar o tamanho de cada botão do jogo 3D. */
@Composable
internal fun EditorBotoes(prefs: android.content.SharedPreferences, aoFechar: () -> Unit) {
    var disp by remember { mutableStateOf(OpcoesAoVivo.disposicao(prefs)) }
    fun mudar(nova: DisposicaoBotoes) { disp = nova; OpcoesAoVivo.guardarDisposicao(prefs, nova) }
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Botões do jogo 3D", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(onClick = aoFechar, Modifier.heightIn(min = 44.dp)) { Text("Feito", fontSize = 15.sp) }
                }
                Text("Arrasta os botões para onde fica melhor para os teus dedos. A zona à esquerda é do joystick.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PreviaBotoes(disp, ::mudar)
                for (b in disp.botoes) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.id.nome, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(b.id.descricao, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = b.ativo, onCheckedChange = { mudar(disp.com(b.copy(ativo = it))) })
                        }
                        if (b.ativo) Slider(
                            value = b.escala, onValueChange = { mudar(disp.com(b.copy(escala = it))) },
                            valueRange = DisposicaoBotoes.ESCALA_MIN..DisposicaoBotoes.ESCALA_MAX,
                        )
                    }
                }
                OutlinedButton(onClick = { OpcoesAoVivo.repor(prefs); disp = DisposicaoBotoes() }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Repor a disposição normal", fontSize = 15.sp) }
            }
        }
    }
}

/** Um ecrã de jogo em miniatura, com os botões que se arrastam. */
@Composable
private fun PreviaBotoes(disp: DisposicaoBotoes, aoMudar: (DisposicaoBotoes) -> Unit) {
    val atual by rememberUpdatedState(disp)
    val aMudar by rememberUpdatedState(aoMudar)
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(914f / 411f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2E7D32))) {
        val escala = maxWidth / 914.dp
        val larguraPx = constraints.maxWidth.toFloat()
        val alturaPx = constraints.maxHeight.toFloat()
        // Joystick (só para dar ideia do espaço).
        Box(Modifier.align(Alignment.BottomStart).padding(start = (30 * escala).dp, bottom = (20 * escala).dp).size((124 * escala).dp).clip(CircleShape).background(Color(0x33FFFFFF)))
        for (b in disp.ativos()) {
            val d = (b.diametroDp * escala).dp
            Box(
                Modifier.offset(maxWidth * b.x - d / 2, maxHeight * b.y - d / 2).size(d).clip(CircleShape)
                    .background(corDoBotao(b.id).copy(alpha = 0.85f))
                    .pointerInput(b.id) {
                        detectDragGestures { mudanca, arrasto ->
                            mudanca.consume()
                            val c = atual[b.id]
                            aMudar(atual.com(c.copy(x = c.x + arrasto.x / larguraPx, y = c.y + arrasto.y / alturaPx)))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text(b.id.rotulo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (9f + 8f * escala).coerceIn(7f, 13f).sp, maxLines = 1, softWrap = false, textAlign = TextAlign.Center) }
        }
    }
}
