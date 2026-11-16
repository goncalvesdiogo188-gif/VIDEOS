package pt.futebolcarreira.aovivo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.nucleo.aovivo.EstadoPartida
import pt.futebolcarreira.nucleo.aovivo.GestorSubstituicoes
import pt.futebolcarreira.nucleo.aovivo.JogadorTatico
import pt.futebolcarreira.nucleo.aovivo.Palestra
import pt.futebolcarreira.nucleo.aovivo.ResultadoSubstituicao
import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.Mentalidade

/** Cor da barra de energia: verde, amarelo e vermelho conforme o cansaço. */
internal fun corEnergia(energia: Int): Color = when {
    energia < GestorSubstituicoes.LIMITE_EXAUSTO -> Color(0xFFE53935)
    energia < GestorSubstituicoes.LIMITE_CANSADO -> Color(0xFFFFB300)
    else -> Color(0xFF43A047)
}

/** Painel aberto a partir da pausa (ou do intervalo): substituições, formação e mentalidade. O jogo está parado. */
@Composable
internal fun PainelTatico(sessao: SessaoAoVivo, aoFechar: () -> Unit) {
    val tatica = sessao.tatica
    val casa = sessao.utilizadorCasa
    var separador by remember { mutableIntStateOf(0) }
    var versao by remember { mutableIntStateOf(0) }
    var sai by remember { mutableStateOf<Int?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val titulos = listOf("Jogadores", "Formação", "Mentalidade")

    Box(Modifier.fillMaxSize().background(Color(0xB3000000)).pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }, contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Jogadores e tática", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    // "versao" força a releitura depois de cada troca.
                    val feitas = remember(versao) { tatica.substituicoesFeitas(casa) }
                    Text(GestorSubstituicoes.resumo(feitas), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = aoFechar, Modifier.heightIn(min = 44.dp)) { Text("Voltar ao jogo", fontSize = 14.sp) }
                }
                if (!tatica.suportado) {
                    Text("As táticas ao vivo não estão disponíveis neste jogo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    return@Column
                }
                TabRow(selectedTabIndex = separador) {
                    titulos.forEachIndexed { i, t -> Tab(selected = separador == i, onClick = { separador = i }, text = { Text(t, fontSize = 14.sp) }) }
                }
                aviso?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
                when (separador) {
                    0 -> SeparadorJogadores(sessao, versao, sai, { sai = it }) { r ->
                        aviso = r.texto
                        if (r == ResultadoSubstituicao.FEITA) { sai = null; versao++ }
                    }
                    1 -> SeparadorFormacao(sessao, versao) { versao++; aviso = "Formação mudada." }
                    else -> SeparadorMentalidade(sessao, versao) { versao++; aviso = "Mentalidade mudada." }
                }
            }
        }
    }
}

@Composable
private fun SeparadorJogadores(sessao: SessaoAoVivo, versao: Int, sai: Int?, aoEscolherSai: (Int?) -> Unit, aoResultado: (ResultadoSubstituicao) -> Unit) {
    val tatica = sessao.tatica
    val casa = sessao.utilizadorCasa
    val onze = remember(versao) { tatica.onzeEmCampo(casa) }
    val banco = remember(versao) { tatica.banco(casa) }
    val restantes = remember(versao) { tatica.substituicoesRestantes(casa) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Em campo (toca em quem sai)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (j in onze) LinhaJogador(j, selecionado = sai == j.id, sugerido = j.cansado) { aoEscolherSai(if (sai == j.id) null else j.id) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val saiJ = onze.firstOrNull { it.id == sai }
            Text(
                if (restantes <= 0) "Já não há substituições." else if (saiJ == null) "Banco (escolhe primeiro quem sai)" else "Banco: quem entra por ${saiJ.nome}?",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val sugestao = saiJ?.let { GestorSubstituicoes.melhorEntrada(it.lugar, banco)?.id }
            for (j in banco) LinhaJogador(j, selecionado = false, sugerido = j.id == sugestao, ativo = saiJ != null && restantes > 0 && !j.lesionado) {
                if (saiJ != null) aoResultado(tatica.substituir(casa, saiJ.id, j.id))
            }
            if (banco.isEmpty()) Text("Banco vazio.", fontSize = 13.sp)
        }
    }
}

@Composable
private fun LinhaJogador(j: JogadorTatico, selecionado: Boolean, sugerido: Boolean, ativo: Boolean = true, aoTocar: () -> Unit) {
    val fundo = when {
        selecionado -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(fundo).clickable(enabled = ativo, onClick = aoTocar).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${j.numero}", Modifier.width(26.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.End)
        Column(Modifier.weight(1f)) {
            Text(j.nome, fontSize = 14.sp, maxLines = 1, fontWeight = if (sugerido) FontWeight.Bold else FontWeight.Normal)
            val estado = buildList {
                add(if (j.lugar.isNotEmpty()) j.lugar else j.posicao)
                if (j.lesionado) add("lesionado")
                if (j.muitoCansado) add("exausto") else if (j.cansado) add("cansado")
                if (j.amarelos > 0) add("amarelo")
            }.joinToString(" · ")
            Text(estado, fontSize = 11.sp, color = if (j.lesionado || j.muitoCansado) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (j.lugar.isNotEmpty()) {
            Box(Modifier.width(56.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x33808080))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(j.energia / 100f).background(corEnergia(j.energia)))
            }
        } else Text("${j.geral}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SeparadorFormacao(sessao: SessaoAoVivo, versao: Int, aoMudar: () -> Unit) {
    val atual = remember(versao) { sessao.tatica.formacaoAtual(sessao.utilizadorCasa) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("A equipa reorganiza-se já: os jogadores em campo passam para os lugares da nova formação.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in Formacao.entries) FilterChip(
                selected = atual == f, onClick = { if (sessao.tatica.mudarFormacao(sessao.utilizadorCasa, f)) aoMudar() },
                label = { Text(f.nome, fontSize = 15.sp) }, modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun SeparadorMentalidade(sessao: SessaoAoVivo, versao: Int, aoMudar: () -> Unit) {
    val atual = remember(versao) { sessao.tatica.mentalidadeAtual(sessao.utilizadorCasa) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Defensiva: a equipa fica mais atrás. Ofensiva: sobe no campo e arrisca mais.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in Mentalidade.entries) FilterChip(
                selected = atual == m, onClick = { if (sessao.tatica.mudarMentalidade(sessao.utilizadorCasa, m)) aoMudar() },
                label = { Text(m.nome, fontSize = 15.sp) }, modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

/** Palestra ao intervalo: uma escolha simples, com um efeito pequeno na moral. O jogo só recomeça depois de escolher. */
@Composable
internal fun PalestraIntervalo(e: EstadoPartida, utilizadorCasa: Boolean, aoEscolher: (Palestra?) -> Unit, aoTatica: () -> Unit) {
    val meus = if (utilizadorCasa) e.golosCasa else e.golosFora
    val deles = if (utilizadorCasa) e.golosFora else e.golosCasa
    val situacao = when {
        meus > deles -> "A ganhar por $meus-$deles"
        meus < deles -> "A perder por $meus-$deles"
        else -> "Empate a $meus"
    }
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)).pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }, contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(20.dp).width(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Intervalo", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("$situacao. O que dizes ao balneário?", fontSize = 15.sp)
                for (p in Palestra.entries.filter { it != Palestra.NADA }) {
                    OutlinedButton(onClick = { aoEscolher(p) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(p.nome, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(p.descricao, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedButton(onClick = aoTatica, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Jogadores e tática", fontSize = 15.sp) }
                Button(onClick = { aoEscolher(null) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Recomeçar o jogo", fontSize = 16.sp) }
            }
        }
    }
}
