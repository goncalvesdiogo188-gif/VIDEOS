package pt.futebolcarreira.ecras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.JogoViewModel
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Mercado
import pt.futebolcarreira.nucleo.carreira.PerfilPatrocinio
import pt.futebolcarreira.nucleo.carreira.Saude
import pt.futebolcarreira.nucleo.carreira.SlotPatrocinio
import pt.futebolcarreira.nucleo.simulacao.Financas

private class ResumoFinancas(
    val saude: Saude, val caixa: Long, val racio: Double, val divida: Long, val receita: Long, val embargo: Boolean,
    val massa: Long, val limiteSalarios: Long, val sugestaoSalarios: Long, val maximoSalarios: Long,
    val limiteTransf: Long, val disponivelTransf: Long?, val sugestaoTransf: Long,
    val preco: Double, val precoNormal: Int, val procura: Double, val receitaJogo: Pair<Long, Long>, val assistencia: Int,
    val limiteCredito: Long, val juros: Double, val regras: List<String>, val resultados: List<Long>,
)

/** Separador «Orçamento»: orçamentos de salários e transferências, bilhetes, patrocinadores, dívida e regras de equilíbrio. */
@Composable
internal fun EcraFinancasOrcamento(c: Carreira, ativo: Boolean, jogo: JogoViewModel, versao: Int) {
    val o = c.gestaoClube.orcamento
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    val r = rememberGestao(c, versao) {
        val e = c.gestaoClube.estado.financas
        ResumoFinancas(
            o.saude(), c.equipa.orcamento, o.racioSalarial(), o.divida(), o.receitaPrevista(), o.embargoAtivo(),
            o.massaSalarial(), e.orcamentoSalarios, o.sugestaoSalarios(), o.maximoSalarios(),
            e.orcamentoTransferencias, o.disponivelTransferencias(), o.sugestaoTransferencias(),
            e.precoBilhete, (12.0 + c.equipa.reputacao * 0.35).toInt(), o.procura(), o.bilheteira(false, 1.0), (Financas.assistencia(c.equipa) * o.procura()).toInt().coerceAtMost(c.equipa.capacidade),
            o.limiteCredito(), o.jurosAnuais(), o.regras(), e.resultados.toList(),
        )
    }
    val patrocinios = rememberGestao(c, versao) { SlotPatrocinio.entries.map { s -> Triple(s, o.contrato(s), o.propostas(s)) } }
    val corSaude = when (r.saude) {
        Saude.ESTAVEL -> COR_VITORIA
        Saude.ATENCAO -> MaterialTheme.colorScheme.tertiary
        Saude.CRITICA -> COR_DERROTA
    }
    val passoSalarios = maxOf(10_000L, r.maximoSalarios / 50 / 10_000 * 10_000)
    val passoTransf = maxOf(10_000L, maxOf(r.sugestaoTransf, 1_000_000L) / 10 / 10_000 * 10_000)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ---- Saúde financeira ----
        item {
            Bloco {
                Rotulo("Saúde financeira")
                Text(r.saude.nome, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = corSaude)
                LinhaValor("Caixa", Mercado.euros(r.caixa), negrito = r.caixa < 0)
                LinhaValor("Receita prevista da época", Mercado.euros(r.receita))
                LinhaValor("Salários sobre a receita", "${(r.racio * 100).toInt()}%")
                LinhaValor("Dívida ao banco", Mercado.euros(r.divida))
                if (r.resultados.isNotEmpty()) LinhaValor("Resultados das últimas épocas", r.resultados.joinToString(" · ") { Mercado.euros(it) })
                if (r.embargo) Text("O clube está impedido de contratar (regras de equilíbrio financeiro).", Modifier.padding(top = 4.dp), fontSize = 13.sp, color = COR_DERROTA, fontWeight = FontWeight.Bold)
                Text("Regras", Modifier.padding(top = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = secundaria)
                for (regra in r.regras) Text("· $regra", fontSize = 12.sp, color = secundaria)
            }
        }
        // ---- Orçamentos ----
        item {
            Bloco {
                Rotulo("Orçamento de salários")
                Text("Teto para a massa salarial do plantel e da equipa técnica. Contratações e renovações que o ultrapassem são recusadas.", fontSize = 12.sp, color = secundaria)
                LinhaValor("Salários de hoje (por ano)", Mercado.euros(r.massa))
                LinhaValor("Orçamento", if (r.limiteSalarios > 0) Mercado.euros(r.limiteSalarios) else "Sem limite")
                LinhaValor("A direção sugere", Mercado.euros(r.sugestaoSalarios))
                val base = if (r.limiteSalarios > 0) r.limiteSalarios else r.massa / 10_000 * 10_000
                Ajustador(
                    "Definir", if (r.limiteSalarios > 0) Mercado.euros(r.limiteSalarios) else "—",
                    { jogo.gerir { frase(o.definirOrcamentoSalarios(maxOf(passoSalarios, base - passoSalarios)), "Orçamento de salários atualizado.") } },
                    { jogo.gerir { frase(o.definirOrcamentoSalarios(base + passoSalarios), "Orçamento de salários atualizado.") } }, ativo,
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BotaoSecundario("Seguir a direção", ativo, Modifier.weight(1f)) { jogo.gerir { frase(o.definirOrcamentoSalarios(minOf(r.sugestaoSalarios, r.maximoSalarios)), "Orçamento de salários atualizado.") } }
                    BotaoSecundario("Sem limite", ativo, Modifier.weight(1f)) { jogo.gerir { frase(o.definirOrcamentoSalarios(0), "Sem limite de salários.") } }
                }
                Text("Máximo que a direção aceita: ${Mercado.euros(r.maximoSalarios)}.", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = secundaria)
            }
        }
        item {
            Bloco {
                Rotulo("Orçamento de transferências")
                Text("Quanto podes gastar em compras esta época (metade do que venderes também conta).", fontSize = 12.sp, color = secundaria)
                LinhaValor("Orçamento", if (r.limiteTransf > 0) Mercado.euros(r.limiteTransf) else "Sem limite")
                if (r.disponivelTransf != null) LinhaValor("Ainda disponível", Mercado.euros(r.disponivelTransf), negrito = true)
                LinhaValor("A direção sugere", Mercado.euros(r.sugestaoTransf))
                Ajustador(
                    "Definir", if (r.limiteTransf > 0) Mercado.euros(r.limiteTransf) else "—",
                    { jogo.gerir { frase(o.definirOrcamentoTransferencias(maxOf(passoTransf, r.limiteTransf - passoTransf)), "Orçamento de transferências atualizado.") } },
                    { jogo.gerir { frase(o.definirOrcamentoTransferencias(maxOf(r.sugestaoTransf, r.limiteTransf) + passoTransf), "Orçamento de transferências atualizado.") } }, ativo,
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BotaoSecundario("Seguir a direção", ativo, Modifier.weight(1f)) { jogo.gerir { frase(o.definirOrcamentoTransferencias(r.sugestaoTransf), "Orçamento de transferências atualizado.") } }
                    BotaoSecundario("Sem limite", ativo, Modifier.weight(1f)) { jogo.gerir { frase(o.definirOrcamentoTransferencias(0), "Sem limite de transferências.") } }
                }
            }
        }
        // ---- Bilhetes ----
        item {
            Bloco {
                Rotulo("Preço dos bilhetes")
                Text("Mais barato enche mais o estádio e agrada aos adeptos; muito caro afasta-os. O preço normal está no meio.", fontSize = 12.sp, color = secundaria)
                Ajustador(
                    "Preço", "×${"%.2f".format(r.preco)} (${(r.precoNormal * r.preco).toInt()} €)",
                    { jogo.gerir { frase(o.definirPrecoBilhete(r.preco - 0.05), "Preço atualizado.") } },
                    { jogo.gerir { frase(o.definirPrecoBilhete(r.preco + 0.05), "Preço atualizado.") } }, ativo,
                )
                LinhaValor("Procura em relação ao normal", "${(r.procura * 100).toInt()}%")
                LinhaValor("Espectadores esperados", "${r.assistencia}")
                LinhaValor("Receita por jogo em casa", Mercado.euros(r.receitaJogo.first) + "  (normal " + Mercado.euros(r.receitaJogo.second) + ")")
                if (r.preco >= 1.3) Text("Preços tão altos fazem os adeptos perder confiança em ti a cada jogo em casa.", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = COR_DERROTA)
                if (r.preco <= 0.85) Text("Preços baixos agradam aos adeptos: a confiança sobe a cada jogo em casa.", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = COR_VITORIA)
            }
        }
        // ---- Patrocinadores ----
        for ((slot, contrato, propostas) in patrocinios) {
            item(key = slot.name) {
                Bloco {
                    Rotulo("Patrocinador · ${slot.nome}")
                    if (contrato != null) {
                        Text(contrato.nome, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${contrato.perfil.nome}: ${Mercado.euros(contrato.fixo)} fixos por ano" +
                                (if (contrato.porVitoria > 0) " · ${Mercado.euros(contrato.porVitoria)} por vitória na liga" else "") +
                                (if (contrato.porTitulo > 0) " · ${Mercado.euros(contrato.porTitulo)} pelo título" else "") +
                                " · ${contrato.anosRestantes} ${if (contrato.anosRestantes == 1) "época" else "épocas"} por cumprir",
                            fontSize = 13.sp, color = secundaria,
                        )
                        BotaoSecundario("Rescindir (paga ${Mercado.euros(contrato.fixo * 3 / 10)})", ativo, Modifier.padding(top = 6.dp)) { jogo.gerir { frase(o.rescindir(slot), "Contrato rescindido.") } }
                    } else {
                        Text("O patrocínio normal do clube mantém-se até escolheres. Cada proposta troca uma parte dele por esta.", fontSize = 12.sp, color = secundaria)
                        for (p in propostas) {
                            HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Text("${p.nome} · ${p.perfil.nome}", Modifier.padding(top = 8.dp), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${Mercado.euros(p.fixo)} fixos (o normal seria ${Mercado.euros(p.base)})" +
                                    (if (p.porVitoria > 0) " · ${Mercado.euros(p.porVitoria)} por vitória" else "") +
                                    (if (p.porTitulo > 0) " · ${Mercado.euros(p.porTitulo)} pelo título" else "") +
                                    " · ${p.anos} ${if (p.anos == 1) "época" else "épocas"}",
                                fontSize = 12.sp, color = secundaria,
                            )
                            Text(p.perfil.descricao, fontSize = 12.sp, color = secundaria)
                            BotaoSecundario("Assinar com ${p.nome}", ativo, Modifier.padding(top = 4.dp)) { jogo.gerir { frase(o.assinar(slot, p.perfil), "Novo patrocinador: ${p.nome}.") } }
                        }
                    }
                }
            }
        }
        // ---- Crédito ----
        item {
            Bloco {
                Rotulo("Dívida e crédito")
                Text("O banco empresta até 40% da receita prevista (menos a dívida). Paga-se em 24 meses com juros; não há crédito durante um embargo.", fontSize = 12.sp, color = secundaria)
                LinhaValor("Dívida", Mercado.euros(r.divida))
                LinhaValor("Juros", "${(r.juros * 100).toInt()}% ao ano")
                LinhaValor("Pode pedir até", Mercado.euros(r.limiteCredito), negrito = true)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (parte in listOf(25, 50, 100)) {
                        val valor = r.limiteCredito * parte / 100 / 10_000 * 10_000
                        BotaoSecundario("Pedir ${Mercado.euros(valor)}", ativo && valor >= 100_000, Modifier.weight(1f)) { jogo.gerir { frase(o.pedirCredito(valor), "Crédito aprovado: ${Mercado.euros(valor)}.") } }
                    }
                }
                BotaoSecundario("Pagar já a dívida (o que a caixa permitir)", ativo && r.divida > 0, Modifier.padding(top = 6.dp)) { jogo.gerir { frase(o.amortizar(r.divida), "Dívida amortizada.") } }
            }
        }
    }
}
