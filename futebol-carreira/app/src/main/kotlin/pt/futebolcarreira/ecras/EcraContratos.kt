package pt.futebolcarreira.ecras

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.JogoViewModel
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Mercado
import pt.futebolcarreira.nucleo.carreira.PoliticaRenovacao
import pt.futebolcarreira.nucleo.carreira.PropostaContrato
import pt.futebolcarreira.nucleo.carreira.ResNeg
import pt.futebolcarreira.nucleo.carreira.ResultadoNegociacao
import pt.futebolcarreira.nucleo.carreira.TipoOferta
import pt.futebolcarreira.nucleo.modelos.Jogador

private class LinhaContrato(val id: Int, val nome: String, val posicao: String, val idade: Int, val geral: Int, val salario: Int, val contratoAte: Int, val aTerminar: Boolean)

private fun linha(j: Jogador, ano: Int) = LinhaContrato(j.id, j.nome, j.posicao, j.idade, j.geral(), j.salario, j.contratoAte, j.contratoAte <= ano + 1)

/** Separador «Contratos»: renovações negociadas, livres, propostas dos outros clubes, opções de compra e prestações. */
@Composable
internal fun EcraContratos(c: Carreira, ativo: Boolean, jogo: JogoViewModel, versao: Int) {
    val n = c.gestaoClube.negociacoes
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    val ano = c.gestor.ano
    val politica = rememberGestao(c, versao) { c.gestaoClube.estado.negociacoes.renovacao }
    val renovaveis = rememberGestao(c, versao) { c.equipa.plantel.filter { n.renovavel(it) }.sortedWith(compareBy({ it.contratoAte }, { -it.geral() })).take(25).map { linha(it, ano) } }
    val livres = rememberGestao(c, versao) { c.bd.livres().sortedByDescending { it.geral() }.take(8).map { linha(it, ano) } }
    val ofertas = rememberGestao(c, versao) { n.ofertas.toList() }
    val opcoes = rememberGestao(c, versao) { n.opcoes() }
    val prestacoes = rememberGestao(c, versao) { c.gestaoClube.estado.negociacoes.prestacoes.toList() }
    val comprometido = rememberGestao(c, versao) { Mercado.euros(n.dividaEmPrestacoes()) to Mercado.euros(n.aReceber()) }
    var negociar by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }   // (jogador, é livre)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Bloco {
                Rotulo("Renovações dos contratos")
                Text(politica.descricao, fontSize = 13.sp, color = secundaria)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in PoliticaRenovacao.entries) ChipToque(politica == p, { jogo.gerir { n.definirPolitica(p); "Renovações: ${p.nome.lowercase()}." } }, { Text(p.nome) }, enabled = ativo)
                }
                if (politica == PoliticaRenovacao.MANUAL) {
                    Text("Atenção: quem tiver o contrato a acabar no fim da época e não renovares sai livre.", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            Bloco {
                Rotulo("Contratos a negociar")
                Text("Toca num jogador para negociar salário, anos, luvas, prémios e cláusula de rescisão.", fontSize = 12.sp, color = secundaria)
                if (renovaveis.isEmpty()) Text("Nenhum contrato a acabar nas próximas duas épocas.", Modifier.padding(top = 6.dp), color = secundaria)
                for (l in renovaveis) {
                    LinhaJogadorContrato(l, ativo) { negociar = l.id to false }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        if (ofertas.isNotEmpty()) item {
            Bloco {
                Rotulo("Propostas dos outros clubes")
                for (o in ofertas) {
                    val j = c.bd.jogadores[o.jogadorId] ?: continue
                    val clube = c.bd.equipas[o.clubeId]?.nome ?: "Um clube"
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("${j.nome} (${j.posicao}, ${j.idade} anos, geral ${j.geral()})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            when (o.tipo) {
                                TipoOferta.COMPRA -> "$clube oferece ${Mercado.euros(o.valor)}" + (if (o.meses > 0) " em ${o.meses} prestações mensais" else "")
                                TipoOferta.EMPRESTIMO_COM_OPCAO -> "$clube: empréstimo até ao fim da época por ${Mercado.euros(o.taxa)}, com opção de compra de ${Mercado.euros(o.opcao)}"
                            } + ". Valor de mercado: ${Mercado.euros(j.valorMercado().toLong())}. Válida até ${o.expira}.",
                            fontSize = 13.sp, color = secundaria,
                        )
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { jogo.gerir { frase(n.aceitar(o.id), "Negócio fechado: ${j.nome}.") } }, enabled = ativo, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Aceitar", fontSize = 13.sp) }
                            OutlinedButton(onClick = { jogo.gerir { n.contrapor(o.id).texto } }, enabled = ativo && !o.contraposta, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Pedir +15%", fontSize = 13.sp, maxLines = 1) }
                            OutlinedButton(onClick = { jogo.gerir { frase(n.recusar(o.id), "Proposta recusada.") } }, enabled = ativo, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Recusar", fontSize = 13.sp) }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        if (opcoes.isNotEmpty()) item {
            Bloco {
                Rotulo("Opções de compra")
                for (o in opcoes) {
                    val j = c.bd.jogadores[o.jogadorId] ?: continue
                    val clube = c.bd.equipas[o.clubeId]?.nome ?: "o outro clube"
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(j.nome, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (o.minha) {
                            Text("Emprestado por $clube. Podes comprá-lo por ${Mercado.euros(o.preco)} até ao fim da época.", fontSize = 13.sp, color = secundaria)
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { jogo.gerir { frase(n.exercerOpcao(j.id), "${j.nome} é teu.") } }, enabled = ativo, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Comprar já", fontSize = 13.sp) }
                                OutlinedButton(onClick = { jogo.gerir { frase(n.exercerOpcao(j.id, 12), "${j.nome} é teu (12 prestações).") } }, enabled = ativo, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("12 prestações", fontSize = 13.sp) }
                            }
                        } else {
                            Text("Emprestado a $clube, que tem opção de o comprar por ${Mercado.euros(o.preco)} no fim da época.", fontSize = 13.sp, color = secundaria)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        item {
            Bloco {
                Rotulo("Pagamentos a prestações")
                LinhaValor("Ainda a pagar", comprometido.first)
                LinhaValor("Ainda a receber", comprometido.second)
                for (p in prestacoes) {
                    Text(
                        (if (p.recebe) "Recebes " else "Pagas ") + "${Mercado.euros(p.valorMensal)} por mês durante mais ${p.meses} ${if (p.meses == 1) "mês" else "meses"}: ${p.descricao}",
                        Modifier.padding(top = 4.dp), fontSize = 12.sp, color = secundaria,
                    )
                }
                if (prestacoes.isEmpty()) Text("Sem prestações em curso. Podes comprar jogadores a prestações na ficha do mercado.", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = secundaria)
            }
        }
        item {
            Bloco {
                Rotulo("Jogadores livres · negociar")
                for (l in livres) {
                    LinhaJogadorContrato(l, ativo, livre = true) { negociar = l.id to true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (livres.isEmpty()) Text("Não há jogadores livres.", color = secundaria)
            }
        }
    }

    negociar?.let { (id, livre) -> DialogoNegociacao(c, id, livre, ativo, jogo) { negociar = null } }
}

@Composable
private fun LinhaJogadorContrato(l: LinhaContrato, ativo: Boolean, livre: Boolean = false, aoTocar: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = ativo, onClick = aoTocar).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Celula(l.posicao, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(l.nome, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (livre) "${l.idade} anos · sem clube" else "${l.idade} anos · ${Mercado.euros(l.salario.toLong())} por ano · até ${l.contratoAte}" + if (l.aTerminar) " (acaba no verão)" else "",
                fontSize = 12.sp, color = if (l.aTerminar && !livre) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Celula("${l.geral}", negrito = true)
        Text(if (livre) "Negociar" else "Renovar", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
    }
}

/** A janela de negociação de um contrato (renovação ou jogador livre): propõe-se, o jogador responde. */
@Composable
private fun DialogoNegociacao(c: Carreira, id: Int, livre: Boolean, ativo: Boolean, jogo: JogoViewModel, aoFechar: () -> Unit) {
    val n = c.gestaoClube.negociacoes
    val j = c.bd.jogadores[id]
    if (j == null) { aoFechar(); return }
    val pede = remember(id) { n.exigencia(j) }
    var salario by remember { mutableStateOf(pede.salario) }
    var anos by remember { mutableStateOf(pede.anos) }
    var luvas by remember { mutableStateOf(0L) }
    var premioGolo by remember { mutableStateOf(0) }
    var premioObjetivo by remember { mutableStateOf(0) }
    var clausulaX by remember { mutableStateOf(1.0) }
    var resposta by remember { mutableStateOf<ResultadoNegociacao?>(null) }
    val passo = maxOf(1000, (salario / 20) / 1000 * 1000)
    val proposta = PropostaContrato(salario, anos, luvas, premioGolo, premioObjetivo, if (clausulaX == 1.0) 0 else (j.clausula() * clausulaX).toLong() / 10_000 * 10_000)
    val comissao = n.comissao(proposta)
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (livre) "Contratar ${j.nome}" else "Renovar ${j.nome}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${j.posicao} · ${j.idade} anos · geral ${j.geral()} · moral ${j.moral}", fontSize = 13.sp, color = secundaria)
                Text("Pede cerca de ${Mercado.euros(pede.salario.toLong())} por ano, ${pede.anos} ${if (pede.anos == 1) "ano" else "anos"}.", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Ajustador("Salário por ano", Mercado.euros(salario.toLong()), { salario = maxOf(12_000, salario - passo) }, { salario += passo }, ativo)
                Ajustador("Duração", "$anos ${if (anos == 1) "ano" else "anos"}", { anos = maxOf(1, anos - 1) }, { anos = minOf(5, anos + 1) }, ativo)
                Ajustador("Luvas", Mercado.euros(luvas), { luvas = maxOf(0L, luvas - 50_000L) }, { luvas += 50_000L }, ativo)
                Ajustador("Prémio por golo", Mercado.euros(premioGolo.toLong()), { premioGolo = maxOf(0, premioGolo - 5_000) }, { premioGolo += 5_000 }, ativo)
                Ajustador("Prémio por objetivo", Mercado.euros(premioObjetivo.toLong()), { premioObjetivo = maxOf(0, premioObjetivo - 25_000) }, { premioObjetivo += 25_000 }, ativo)
                Ajustador(
                    "Cláusula de rescisão", Mercado.euros(if (clausulaX == 1.0) j.clausula() else (j.clausula() * clausulaX).toLong()),
                    { clausulaX = maxOf(0.5, clausulaX - 0.5) }, { clausulaX = minOf(3.0, clausulaX + 0.5) }, ativo,
                )
                Text("Cláusula alta: o jogador pede mais. Baixa: outro clube pode pagá-la e levá-lo sem pedir licença.", fontSize = 11.sp, color = secundaria)
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                LinhaValor("Comissão do agente (6%)", Mercado.euros(comissao))
                LinhaValor("Custo já (luvas e comissão)", Mercado.euros(luvas + comissao), negrito = true)
                LinhaValor("Caixa", Mercado.euros(c.equipa.orcamento))
                resposta?.let {
                    Text(it.texto, Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (it.resultado == ResNeg.ACEITE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ativo,
                onClick = {
                    var r: ResultadoNegociacao? = null
                    jogo.gerir(depois = {
                        resposta = r
                        r?.contraproposta?.let { salario = it.salario }
                        if (r?.resultado == ResNeg.ACEITE) aoFechar()
                    }) {
                        r = if (livre) n.contratarLivre(j, proposta) else n.renovar(j, proposta)
                        null
                    }
                },
            ) { Text("Propor") }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
    )
}
