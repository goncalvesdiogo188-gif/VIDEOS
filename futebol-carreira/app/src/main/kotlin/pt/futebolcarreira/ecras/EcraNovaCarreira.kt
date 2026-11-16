package pt.futebolcarreira.ecras

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.NacaoUi
import pt.futebolcarreira.PaisUi
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.modelos.Jogador

/** Nova carreira: escolher a via (treinador ou jogador), o nome e o clube. */
@Composable
fun EcraNovaCarreira(
    paises: List<PaisUi>, nacoes: List<NacaoUi>, ocupado: Boolean,
    aoComecar: (ModoCarreira, String, String, String, String, String) -> Unit, aoVoltar: () -> Unit,
) {
    var modo by rememberSaveable { mutableStateOf(ModoCarreira.TREINADOR) }
    var nome by rememberSaveable { mutableStateOf("") }
    var posicao by rememberSaveable { mutableStateOf("PL") }
    var pais by rememberSaveable { mutableIntStateOf(0) }
    var liga by rememberSaveable { mutableIntStateOf(0) }
    var clube by rememberSaveable { mutableStateOf("") }
    // Só na via de jogador: nacionalidade ("" = a do país do clube) e pé preferido.
    var nacionalidade by rememberSaveable { mutableStateOf("") }
    var pe by rememberSaveable { mutableStateOf("direito") }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        BarraVoltar("Nova carreira", aoVoltar)
        if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Rotulo("1. Que carreira queres?")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Escolha("Treinador", "Escolhes a tática e levas o clube ao título.", modo == ModoCarreira.TREINADOR, Modifier.weight(1f)) { modo = ModoCarreira.TREINADOR }
                Escolha("Jogador", "Começas aos 17 anos e tentas chegar a lenda.", modo == ModoCarreira.JOGADOR, Modifier.weight(1f)) {
                    modo = ModoCarreira.JOGADOR
                    // Um clube do meio da tabela da primeira divisão, para quem não quiser escolher.
                    if (clube.isEmpty()) paises.firstOrNull()?.ligas?.firstOrNull()?.let { l -> l.clubes.getOrNull(l.clubes.size / 2)?.let { clube = it.id; pais = 0; liga = 0 } }
                }
            }

            Rotulo("2. O teu nome", Modifier.padding(top = 10.dp))
            OutlinedTextField(
                value = nome, onValueChange = { nome = it.take(24) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (modo == ModoCarreira.JOGADOR) "Nome do jogador" else "Nome do treinador") },
            )

            if (modo == ModoCarreira.JOGADOR) {
                Rotulo("Nacionalidade", Modifier.padding(top = 10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(nacionalidade.isEmpty(), { nacionalidade = "" }, { Text("Do país do clube") })
                    for (n in nacoes) FilterChip(nacionalidade == n.id, { nacionalidade = n.id }, { Text(n.nome) })
                }
                Text(
                    "As seleções de fora da Europa só jogam as qualificações e o Mundial (de quatro em quatro anos).",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Rotulo("Posição em campo", Modifier.padding(top = 10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in Jogador.POSICOES) FilterChip(posicao == p, { posicao = p }, { Text(p) })
                }
                Rotulo("Pé preferido", Modifier.padding(top = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((chave, nome) in listOf("direito" to "Direito", "esquerdo" to "Esquerdo", "ambos" to "Ambos")) {
                        FilterChip(pe == chave, { pe = chave }, { Text(nome) })
                    }
                }
            }

            Rotulo("3. O teu clube", Modifier.padding(top = 10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                paises.forEachIndexed { i, p -> FilterChip(pais == i, { pais = i; liga = 0; clube = "" }, { Text(p.nome) }) }
            }
            val ligas = paises.getOrNull(pais)?.ligas.orEmpty()
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ligas.forEachIndexed { i, l -> FilterChip(liga == i, { liga = i; clube = "" }, { Text(l.nome) }) }
            }
            for (c in ligas.getOrNull(liga)?.clubes.orEmpty()) {
                val escolhido = clube == c.id
                Surface(
                    color = if (escolhido) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { clube = c.id },
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Emblema(c.emblema, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(c.nome, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.cidade, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("★".repeat(c.estrelas) + "☆".repeat(5 - c.estrelas), color = MaterialTheme.colorScheme.tertiary, fontSize = 13.sp)
                            Text("Geral ${c.geral}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        val falta = when {
            nome.isBlank() -> "Escreve o teu nome"
            clube.isEmpty() -> "Escolhe um clube"
            else -> null
        }
        BotaoGrande(falta ?: "Começar carreira", Modifier.padding(12.dp), ativo = falta == null && !ocupado) {
            aoComecar(modo, nome, clube, posicao, if (modo == ModoCarreira.JOGADOR) nacionalidade else "", if (modo == ModoCarreira.JOGADOR) pe else "")
        }
    }
}

/** Cartão grande de escolha (treinador / jogador). */
@Composable
private fun Escolha(titulo: String, texto: String, escolhido: Boolean, modifier: Modifier, aoTocar: () -> Unit) {
    val cores = MaterialTheme.colorScheme
    Surface(
        color = if (escolhido) cores.primaryContainer else cores.surfaceContainerHigh,
        border = if (escolhido) BorderStroke(2.dp, cores.primary) else null,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = aoTocar),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(titulo, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(texto, fontSize = 13.sp, color = cores.onSurfaceVariant)
        }
    }
}
