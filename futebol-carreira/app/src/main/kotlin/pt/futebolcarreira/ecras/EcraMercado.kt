package pt.futebolcarreira.ecras

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.AcaoMercado
import pt.futebolcarreira.FichaJogadorUi
import pt.futebolcarreira.FiltroMercado
import pt.futebolcarreira.FinancasUi
import pt.futebolcarreira.MercadoUi
import pt.futebolcarreira.nucleo.modelos.Jogador

private enum class FiltroIdade(val nome: String, val min: Int, val max: Int) {
    TODAS("Todas as idades", 0, 99), JOVENS("Até 21", 0, 21), MEIO("22 a 29", 22, 29), VETERANOS("30 ou mais", 30, 99)
}

private enum class FiltroPreco(val nome: String, val max: Long) {
    TODOS("Qualquer preço", Long.MAX_VALUE), ATE_1M("Até 1 M€", 1_000_000L), ATE_5M("Até 5 M€", 5_000_000L), ATE_20M("Até 20 M€", 20_000_000L)
}

/** Separador «Mercado»: procurar jogadores de outros clubes ou sem clube. Tocar num jogador abre a ficha. */
@Composable
internal fun SeparadorMercado(mercado: MercadoUi, aoFiltrar: (String?, FiltroMercado) -> Unit, aoAbrirJogador: (Int) -> Unit) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    var idade by rememberSaveable { mutableStateOf(FiltroIdade.TODAS) }
    var preco by rememberSaveable { mutableStateOf(FiltroPreco.TODOS) }
    // Idade e preço filtram a lista já carregada (os 60 melhores com a posição e o filtro de cima).
    val visiveis = mercado.jogadores.filter { j ->
        j.idade in idade.min..idade.max && (preco == FiltroPreco.TODOS || j.preco == "livre" || j.valor <= preco.max)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Bloco(Modifier.padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Rotulo("Dinheiro em caixa")
                    Text(mercado.caixa, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Surface(
                    color = if (mercado.aberto) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        if (mercado.aberto) "Aberto" else "Fechado",
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (mercado.aberto) MaterialTheme.colorScheme.onTertiary else secundaria,
                    )
                }
            }
            if (!mercado.aberto) Text("Só se podem contratar jogadores sem clube.", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = secundaria)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (f in FiltroMercado.entries) FilterChip(mercado.filtro == f, { aoFiltrar(mercado.posicao, f) }, { Text(f.nome) })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(mercado.posicao == null, { aoFiltrar(null, mercado.filtro) }, { Text("Todas") })
            for (p in Jogador.POSICOES) FilterChip(mercado.posicao == p, { aoFiltrar(p, mercado.filtro) }, { Text(p) })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (f in FiltroIdade.entries) FilterChip(idade == f, { idade = f }, { Text(f.nome) })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (f in FiltroPreco.entries) FilterChip(preco == f, { preco = f }, { Text(f.nome) })
        }
        Text("${visiveis.size} jogadores", Modifier.padding(vertical = 4.dp), fontSize = 12.sp, color = secundaria)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (visiveis.isEmpty()) item { Text("Nenhum jogador com estes filtros.", Modifier.padding(top = 8.dp), color = secundaria) }
            items(visiveis, key = { it.id }) { j ->
                Bloco(aoTocar = { aoAbrirJogador(j.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                            Text(j.posicao, Modifier.width(44.dp).padding(vertical = 6.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(j.nome, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${j.clube} · ${j.idade} anos", fontSize = 12.sp, color = secundaria, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${j.geral}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            Text(j.preco, fontSize = 12.sp, color = secundaria, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Ficha de um jogador, por cima de tudo: atributos, contrato e botões de negócio. */
@Composable
internal fun EcraJogador(f: FichaJogadorUi, ativo: Boolean, aoNegociar: (AcaoMercado) -> Unit, aoFechar: () -> Unit) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    BackHandler(onBack = aoFechar)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.systemBarsPadding().padding(horizontal = 14.dp)) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                f.emblema?.let { Emblema(it, 46.dp) }
                Column(Modifier.weight(1f)) {
                    Text(f.nome, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(f.linha1, fontSize = 13.sp, color = secundaria)
                    Text(f.clube, fontSize = 13.sp, color = secundaria)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${f.geral}", fontSize = 30.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Text("pot. ${f.potencial}", fontSize = 12.sp, color = secundaria)
                }
            }
            if (!ativo) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Rotulo("Atributos", Modifier.padding(top = 14.dp, bottom = 4.dp))
                for ((nome, valor) in f.atributos) {
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(nome, Modifier.width(110.dp), fontSize = 14.sp)
                        LinearProgressIndicator(progress = { valor / 99f }, modifier = Modifier.weight(1f).height(8.dp))
                        Celula("$valor", 36.dp, negrito = true)
                    }
                }
                Rotulo("Contrato e época", Modifier.padding(top = 14.dp, bottom = 4.dp))
                for ((titulo, valor) in f.detalhes) {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(titulo, Modifier.width(150.dp), fontSize = 14.sp, color = secundaria)
                        Text(valor, Modifier.weight(1f), fontSize = 14.sp)
                    }
                }
                if (f.botoes.isNotEmpty()) Rotulo("Mercado", Modifier.padding(top = 14.dp, bottom = 4.dp))
                if (f.nota.isNotEmpty()) Text(f.nota, Modifier.padding(top = 12.dp), fontSize = 13.sp, color = secundaria)
                f.botoes.forEachIndexed { i, b ->
                    val forma = Modifier.fillMaxWidth().padding(vertical = 3.dp).heightIn(min = 48.dp)
                    if (i == 0) Button(onClick = { aoNegociar(b.acao) }, enabled = ativo, modifier = forma) { Text(b.texto, textAlign = TextAlign.Center) }
                    else OutlinedButton(onClick = { aoNegociar(b.acao) }, enabled = ativo, modifier = forma) { Text(b.texto, textAlign = TextAlign.Center) }
                }
            }
            OutlinedButton(onClick = aoFechar, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("Fechar") }
        }
    }
}

/** Finanças do clube: caixa, receitas e despesas da época, e obras no estádio. */
@Composable
internal fun SeparadorFinancas(f: FinancasUi, ativo: Boolean, aoAmpliar: () -> Unit) {
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Bloco {
                Rotulo("Dinheiro em caixa")
                Text(f.caixa, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (f.caixaNegativa) COR_DERROTA else MaterialTheme.colorScheme.onSurface)
                Text("Salários do plantel: ${f.folhaSalarial} por ano", fontSize = 13.sp, color = secundaria)
                if (f.caixaNegativa) Text("O clube está a dever dinheiro: a direção perde confiança em ti a cada jornada.", fontSize = 13.sp, color = COR_DERROTA)
            }
        }
        item {
            Bloco {
                Rotulo("Receitas desta época")
                for ((nome, valor) in f.receitas) LinhaConta(nome, valor)
                Rotulo("Despesas desta época", Modifier.padding(top = 10.dp))
                for ((nome, valor) in f.despesas) LinhaConta(nome, valor)
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                LinhaConta("Saldo da época", f.saldo, negrito = true)
            }
        }
        item {
            Bloco {
                Rotulo("Estádio")
                Text(f.estadio, Modifier.padding(bottom = 8.dp), fontSize = 14.sp)
                OutlinedButton(onClick = aoAmpliar, enabled = ativo && f.podeAmpliar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(f.obras) }
            }
        }
    }
}

@Composable
private fun LinhaConta(nome: String, valor: String, negrito: Boolean = false) {
    val peso = if (negrito) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(nome, Modifier.weight(1f), fontSize = 14.sp, fontWeight = peso)
        Text(valor, fontSize = 14.sp, fontWeight = peso)
    }
}
