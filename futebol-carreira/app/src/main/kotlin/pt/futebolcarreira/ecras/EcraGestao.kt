package pt.futebolcarreira.ecras

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.CarreiraUi
import pt.futebolcarreira.JogoViewModel
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.carreira.Resposta

/**
 * Fase 13 (G2 a G5), em «Mais › Gestão»: equipa técnica, contratos e negociações, orçamento e finanças, treino detalhado.
 * Cada parte tem o seu ficheiro (EcraEquipaTecnica, EcraContratos, EcraFinancasOrcamento, EcraTreinoPlanos); a lógica
 * está no núcleo (carreira/GestaoClube.kt e ficheiros ao lado).
 */
private val PARTES = listOf("Equipa técnica", "Contratos", "Orçamento", "Treino")

@Composable
internal fun SeparadorGestao(ui: CarreiraUi, ativo: Boolean, jogo: JogoViewModel, parteInicial: Int = 0) {
    val c = jogo.carreiraAberta
    if (c == null || ui.modo != ModoCarreira.TREINADOR) {
        Text(
            "Na via de jogador é o treinador que gere a equipa técnica, os contratos, o orçamento e o treino.",
            Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    var parte by rememberSaveable { mutableIntStateOf(parteInicial) }
    val pode = ativo && !ui.despedido
    // As ações mudam o núcleo sem mudar o CarreiraUi: a versão faz os ecrãs refazerem-se.
    val versao = jogo.gestaoVersao * 1_000_003 + System.identityHashCode(ui)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PARTES.forEachIndexed { i, nome -> ChipToque(parte == i, { parte = i }, { Text(nome, fontSize = 14.sp, maxLines = 1) }) }
        }
        when (parte) {
            0 -> EcraEquipaTecnica(c, pode, jogo, versao)
            1 -> EcraContratos(c, pode, jogo, versao)
            2 -> EcraFinancasOrcamento(c, pode, jogo, versao)
            else -> EcraTreinoPlanos(c, pode, jogo, versao)
        }
    }
}

/** Texto a mostrar depois de uma ação: o sucesso por extenso ou o motivo da recusa. */
internal fun frase(r: Resposta, sucesso: String): String = if (r == Resposta.ACEITE) sucesso else r.frase

/** Um valor com «−» e «+» para o ajustar aos poucos (salários, preços, durações...). */
@Composable
internal fun Ajustador(titulo: String, valor: String, aoMenos: () -> Unit, aoMais: () -> Unit, ativo: Boolean = true, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, Modifier.weight(1f), fontSize = 14.sp)
        OutlinedButton(onClick = aoMenos, enabled = ativo, modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("−", fontSize = 20.sp) }
        Text(valor, Modifier.widthIn(min = 96.dp).padding(horizontal = 4.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
        OutlinedButton(onClick = aoMais, enabled = ativo, modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("+", fontSize = 20.sp) }
    }
}

/** Duas colunas de texto: a descrição à esquerda e o valor à direita. */
@Composable
internal fun LinhaValor(nome: String, valor: String, negrito: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(nome, Modifier.weight(1f), fontSize = 14.sp, fontWeight = if (negrito) FontWeight.Bold else FontWeight.Normal)
        Text(valor, fontSize = 14.sp, fontWeight = if (negrito) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Botão secundário alto (alvo de toque de 48 dp). */
@Composable
internal fun BotaoSecundario(texto: String, ativo: Boolean = true, modifier: Modifier = Modifier, aoTocar: () -> Unit) {
    OutlinedButton(onClick = aoTocar, enabled = ativo, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(texto, fontSize = 14.sp, textAlign = TextAlign.Center) }
}

/** Estrelas de nível (1 a 5). */
internal fun estrelas(nivel: Int): String = "★".repeat(nivel.coerceIn(0, 5)) + "☆".repeat(5 - nivel.coerceIn(0, 5))

/** Atalho para o núcleo: devolve a carreira dentro de uma lembrança que se refaz com a versão. */
@Composable
internal fun <T> rememberGestao(c: Carreira, versao: Int, calcular: (Carreira) -> T): T = remember(c, versao) { calcular(c) }
