package pt.futebolcarreira.nucleo.carreira

import kotlinx.serialization.Serializable
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Jogador
import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Fase 13 (G3): contratos e negociações do clube do utilizador.
 *
 *  - Renovações e contratações de livres negociadas: salário, anos, luvas, prémios por golo e por objetivo, cláusula
 *    de rescisão e comissão do agente.
 *  - Propostas dos outros clubes pelos teus jogadores (compra, compra a prestações, empréstimo com opção de compra
 *    e cláusulas de rescisão ativadas).
 *  - Empréstimos com opção de compra (nos dois sentidos) e pagamentos a prestações.
 */
enum class TipoOferta(val nome: String) { COMPRA("Compra"), EMPRESTIMO_COM_OPCAO("Empréstimo com opção de compra") }

/** Condições combinadas além do salário e da duração. Sem entrada = tudo por omissão. */
@Serializable
class CondicoesContrato(
    /** Euros por cada golo marcado (nas competições todas), pagos no fim da época. */
    var premioGolo: Int = 0,
    /** Euros pagos no fim da época se o clube cumprir o objetivo da direção. */
    var premioObjetivo: Int = 0,
    /** Cláusula de rescisão combinada (0 = a normal, três vezes o valor de mercado). */
    var clausula: Long = 0,
)

/** Um pagamento mensal a prestações: "recebe" = true quando é o clube do utilizador que recebe. */
@Serializable
class Prestacao(val descricao: String, val valorMensal: Long, var meses: Int, val recebe: Boolean, val clube: String)

/** Uma proposta de outro clube por um jogador do utilizador. */
@Serializable
class OfertaRecebida(
    val id: Int,
    val jogadorId: Int,
    val clubeId: String,
    val tipo: TipoOferta,
    var valor: Long,
    /** Nas compras: em quantas prestações mensais o resto é pago (0 = tudo de uma vez). */
    val meses: Int = 0,
    /** Nos empréstimos: o que o clube paga pelo empréstimo e o preço da opção de compra. */
    val taxa: Long = 0,
    val opcao: Long = 0,
    val expira: String,
    var contraposta: Boolean = false,
)

/** Opção de compra de um jogador emprestado ao fim da época: "minha" = o utilizador pode comprar; senão é o outro clube. */
@Serializable
class OpcaoCompra(val jogadorId: Int, val clubeId: String, val preco: Long, val minha: Boolean)

@Serializable
class ConversaContrato(var paciencia: Int = 3, var bloqueadoAte: String = "")

enum class PoliticaRenovacao(val nome: String, val descricao: String) {
    AUTOMATICA("Automática", "Os contratos que acabam renovam-se sozinhos, com as condições habituais."),
    MANUAL("Negociada", "Só ficam os jogadores cujo contrato renovares; os outros saem livres no verão."),
}

@Serializable
class EstadoNegociacoes(
    val condicoes: MutableMap<Int, CondicoesContrato> = mutableMapOf(),
    val ofertas: MutableList<OfertaRecebida> = mutableListOf(),
    val prestacoes: MutableList<Prestacao> = mutableListOf(),
    val opcoes: MutableList<OpcaoCompra> = mutableListOf(),
    val conversas: MutableMap<Int, ConversaContrato> = mutableMapOf(),
    var proximaOferta: Int = 1,
    var renovacao: PoliticaRenovacao = PoliticaRenovacao.AUTOMATICA,
    /** Último mês ("ano-mês") em que se avisou dos contratos a acabar. */
    var avisoContratos: String = "",
)

/** O que se propõe a um jogador (ou o que ele pede). */
data class PropostaContrato(
    val salario: Int,
    val anos: Int,
    val luvas: Long = 0,
    val premioGolo: Int = 0,
    val premioObjetivo: Int = 0,
    /** 0 = a cláusula normal. */
    val clausula: Long = 0,
)

enum class ResNeg { ACEITE, CONTRAPROPOSTA, RECUSADA, SEM_PACIENCIA, NAO_PERMITIDO, ORCAMENTO, SEM_DINHEIRO }

class ResultadoNegociacao(val resultado: ResNeg, val texto: String, val contraproposta: PropostaContrato? = null)

class Negociacoes(private val c: Carreira, private val g: GestaoClube) {
    private val e get() = g.estado.negociacoes
    private val meu get() = c.equipa
    private val bd get() = c.bd
    private val ano get() = c.gestor.ano

    fun condicoes(j: Jogador): CondicoesContrato = e.condicoes[j.id] ?: CondicoesContrato()

    /** A cláusula de rescisão em vigor (a combinada ou a normal). */
    fun clausula(j: Jogador): Long = condicoes(j).clausula.takeIf { it > 0 } ?: j.clausula()

    // ------------------------------------------------------------ renovações

    /** Jogadores do plantel cujo contrato acaba já neste verão, os mais valiosos primeiro. */
    fun aTerminar(): List<Jogador> = meu.plantel.filter { it.contratoAte <= ano + 1 && it.emprestadoPor.isEmpty() }.sortedByDescending { it.geral() }

    /** Pode-se negociar a renovação? (contrato a acabar nesta ou na próxima época). */
    fun renovavel(j: Jogador): Boolean = j.equipa == meu.id && !j.academia && j.emprestadoPor.isEmpty() && j.contratoAte <= ano + 2

    private fun golosEsperados(j: Jogador): Int = maxOf(1, when (j.posicao) {
        "PL" -> 10; "ED", "EE", "MOC" -> 6; "MC" -> 3; else -> 1
    })

    /** O que o jogador pede, conforme a qualidade, a idade, o tempo de jogo e a moral. */
    fun exigencia(j: Jogador): PropostaContrato {
        val titular = j.equipa == meu.id && meu.onzeParaJogo().any { it.jogador === j }
        var f = 0.0
        if (titular) f += 0.08
        f += ((50 - j.moral) / 200.0).coerceIn(-0.10, 0.15)
        f += when {
            j.idade >= 33 -> -0.15
            j.idade >= 31 -> -0.05
            j.idade <= 22 && j.potencial - j.geral() >= 8 -> 0.10
            else -> 0.0
        }
        if (j.geral() > meu.forca().geral + 4) f += 0.08
        val base = maxOf(j.salarioJusto().toDouble(), j.salario * 0.9)
        val anos = when { j.idade <= 29 -> 4; j.idade <= 32 -> 2; else -> 1 }
        val salario = ((base * (1.0 + f)) / 1000.0).roundToInt().coerceAtLeast(12) * 1000
        return PropostaContrato(salario, anos, 0, 0, 0, j.clausula())
    }

    /** Quanto vale a proposta para o jogador, em euros por ano. */
    fun valorParaOJogador(j: Jogador, p: PropostaContrato): Double =
        p.salario + p.luvas / p.anos.coerceAtLeast(1).toDouble() + p.premioGolo * golosEsperados(j).toDouble() + p.premioObjetivo * 0.5

    /** Salário que o jogador exige, ajustado a uma cláusula alta (quer mais para ficar preso). */
    private fun exigido(j: Jogador, p: PropostaContrato): Double {
        val padrao = j.clausula().toDouble().coerceAtLeast(1.0)
        val clausula = if (p.clausula > 0) p.clausula.toDouble() else padrao
        return exigencia(j).salario * (1.0 + 0.08 * (clausula / padrao - 1.0).coerceIn(-0.3, 0.8))
    }

    /** A comissão do agente: 6% do salário de todos os anos. */
    fun comissao(p: PropostaContrato): Long = (0.06 * p.salario * p.anos).toLong() / 1000 * 1000

    /** Renegociar o contrato de um jogador do clube (a renovação). */
    fun renovar(j: Jogador, p: PropostaContrato): ResultadoNegociacao {
        if (!g.podeGerir || !renovavel(j)) return ResultadoNegociacao(ResNeg.NAO_PERMITIDO, "Não podes negociar a renovação deste jogador.")
        return propor(j, p, livre = false)
    }

    /** Contratar um jogador sem clube, negociando as condições. */
    fun contratarLivre(j: Jogador, p: PropostaContrato): ResultadoNegociacao {
        if (!g.podeGerir || j.equipa.isNotEmpty()) return ResultadoNegociacao(ResNeg.NAO_PERMITIDO, "Não podes contratar este jogador.")
        if (meu.plantel.size >= MAX_PLANTEL) return ResultadoNegociacao(ResNeg.NAO_PERMITIDO, Resposta.PLANTEL_CHEIO.frase)
        return propor(j, p, livre = true)
    }

    private fun propor(j: Jogador, p: PropostaContrato, livre: Boolean): ResultadoNegociacao {
        if (p.anos !in 1..5 || p.salario < 12_000 || p.luvas < 0 || p.premioGolo < 0 || p.premioObjetivo < 0) {
            return ResultadoNegociacao(ResNeg.NAO_PERMITIDO, "Condições inválidas: de 1 a 5 anos e um salário mínimo de 12 mil €.")
        }
        val conversa = e.conversas.getOrPut(j.id) { ConversaContrato() }
        if (conversa.bloqueadoAte.isNotEmpty() && c.gestor.data < LocalDate.parse(conversa.bloqueadoAte)) {
            return ResultadoNegociacao(ResNeg.SEM_PACIENCIA, "${j.nome} já não quer falar contigo por agora. Tenta mais tarde.")
        }
        val comissao = comissao(p)
        if (p.luvas + comissao > meu.orcamento) return ResultadoNegociacao(ResNeg.SEM_DINHEIRO, "O clube não tem em caixa as luvas e a comissão (${Mercado.euros(p.luvas + comissao)}).")
        val acrescimo = if (livre) p.salario.toLong() else (p.salario - j.salario).toLong().coerceAtLeast(0)
        g.bloqueioSalarios(acrescimo)?.let { return ResultadoNegociacao(ResNeg.ORCAMENTO, it.frase) }
        val exigido = exigido(j, p)
        val valor = valorParaOJogador(j, p)
        val razao = valor / exigido
        if (razao >= 1.0) {
            conversa.paciencia = 3
            if (livre) {
                val r = c.mercado.contratarLivre(j)
                if (r != Resposta.ACEITE) return ResultadoNegociacao(ResNeg.NAO_PERMITIDO, r.frase)
            }
            aplicar(j, p, comissao, livre)
            return ResultadoNegociacao(ResNeg.ACEITE, "${j.nome} aceitou: ${Mercado.euros(p.salario.toLong())} por ano até ${j.contratoAte}.")
        }
        conversa.paciencia--
        if (conversa.paciencia <= 0) {
            conversa.paciencia = 3
            conversa.bloqueadoAte = c.gestor.data.plusDays(30).toString()
            return ResultadoNegociacao(ResNeg.SEM_PACIENCIA, "${j.nome} perdeu a paciência e fechou a porta por um mês.")
        }
        if (razao >= 0.85) {
            val falta = exigido - valor
            val salario = (((p.salario + falta) / 1000.0).toInt() + 1) * 1000
            return ResultadoNegociacao(ResNeg.CONTRAPROPOSTA, "${j.nome} só aceita com um salário de ${Mercado.euros(salario.toLong())} por ano.", p.copy(salario = salario))
        }
        return ResultadoNegociacao(ResNeg.RECUSADA, "${j.nome} recusou: a proposta fica muito abaixo do que pede (${Mercado.euros(exigencia(j).salario.toLong())} por ano).")
    }

    private fun aplicar(j: Jogador, p: PropostaContrato, comissao: Long, livre: Boolean) {
        j.salario = p.salario
        j.contratoAte = ano + 1 + p.anos
        val padrao = j.clausula()
        e.condicoes[j.id] = CondicoesContrato(p.premioGolo, p.premioObjetivo, if (p.clausula > 0 && p.clausula != padrao) p.clausula else 0)
        e.conversas.remove(j.id)
        meu.orcamento -= p.luvas + comissao
        meu.contas.salarios += p.luvas
        meu.contas.comissoes += comissao
        c.baseDadosPorGravar = true
        c.mensagem(
            if (livre) "Contratação: ${j.nome}" else "Renovação: ${j.nome}",
            "${j.nome} ${if (livre) "assinou" else "renovou"} até ${j.contratoAte} por ${Mercado.euros(p.salario.toLong())} por ano" +
                (if (p.luvas > 0) ", com ${Mercado.euros(p.luvas)} de luvas" else "") +
                (if (p.premioGolo > 0) " e ${Mercado.euros(p.premioGolo.toLong())} por golo" else "") +
                ". Comissão do agente: ${Mercado.euros(comissao)}.",
        )
    }

    // ------------------------------------------------------------ fim de contrato

    /** O clube renova este jogador sozinho no fim do contrato? (só conta na via de treinador e com a política «Automática») */
    fun renovaSozinho(j: Jogador): Boolean =
        c.info.modo != ModoCarreira.TREINADOR || c.info.despedido || e.renovacao == PoliticaRenovacao.AUTOMATICA

    fun definirPolitica(p: PoliticaRenovacao) { e.renovacao = p }

    /** Mensagem mensal (março a maio) a lembrar os contratos que vão acabar. */
    fun avisarContratos() {
        val data = c.gestor.data
        if (data.monthValue !in 3..5 || e.renovacao != PoliticaRenovacao.MANUAL) return
        val chave = "${data.year}-${data.monthValue}"
        if (e.avisoContratos == chave) return
        val lista = aTerminar()
        e.avisoContratos = chave
        if (lista.isEmpty()) return
        c.mensagem(
            "Contratos a terminar",
            "Acaba o contrato no verão: " + lista.take(6).joinToString { "${it.nome} (${it.geral()})" } + (if (lista.size > 6) " e mais ${lista.size - 6}" else "") +
                ". Se não renovares (Mais › Gestão › Contratos), saem livres.",
        )
    }

    /** No fim da época: prémios combinados com os jogadores (golos e objetivo). */
    fun pagarPremios() {
        var total = 0L
        val objetivo = c.posicao() in 1..c.info.objetivoPosicao
        for (j in meu.plantel) {
            val cond = e.condicoes[j.id] ?: continue
            val golos = j.epoca.golos + j.epocaTaca.golos
            total += cond.premioGolo.toLong() * golos + (if (objetivo) cond.premioObjetivo.toLong() else 0L)
        }
        if (total <= 0) return
        meu.orcamento -= total
        meu.contas.premiosJogadores += total
        c.mensagem("Prémios aos jogadores", "Pagaste ${Mercado.euros(total)} em prémios combinados nos contratos (golos${if (objetivo) " e objetivo cumprido" else ""}).")
    }

    /** Limpa as condições de quem já não é do clube. */
    fun limpar() {
        e.condicoes.keys.retainAll { id -> bd.jogadores[id]?.let { it.equipa == meu.id || it.emprestadoPor == meu.id } == true }
        e.conversas.keys.retainAll { id -> bd.jogadores[id] != null }
    }

    // ------------------------------------------------------------ prestações

    /** Valor ainda por pagar em prestações de compras. */
    fun dividaEmPrestacoes(): Long = e.prestacoes.filter { !it.recebe }.sumOf { it.valorMensal * it.meses }

    fun aReceber(): Long = e.prestacoes.filter { it.recebe }.sumOf { it.valorMensal * it.meses }

    /** O utilizador compra "valor" em "meses" prestações mensais: paga já a entrada e o resto vai saindo todos os meses. */
    internal fun pagarEmPrestacoes(vendedor: Equipa, valor: Long, meses: Int, descricao: String) {
        val mensal = (valor - valor / 4) / meses
        val entrada = valor - mensal * meses
        c.mercado.pagar(meu, vendedor, entrada)
        e.prestacoes += Prestacao(descricao, mensal, meses, false, vendedor.id)
    }

    /** Todos os meses, as prestações em curso são pagas e recebidas. */
    fun processarMes() {
        val feitas = mutableListOf<Prestacao>()
        for (p in e.prestacoes) {
            val outro = bd.equipas[p.clube]
            if (p.recebe) {
                meu.orcamento += p.valorMensal
                meu.contas.vendas += p.valorMensal
                outro?.let { it.orcamento -= p.valorMensal; it.contas.compras += p.valorMensal }
            } else {
                meu.orcamento -= p.valorMensal
                meu.contas.compras += p.valorMensal
                outro?.let { it.orcamento += p.valorMensal; it.contas.vendas += p.valorMensal }
            }
            p.meses--
            if (p.meses <= 0) feitas += p
        }
        e.prestacoes.removeAll(feitas)
    }

    // ------------------------------------------------------------ propostas dos outros clubes

    val ofertas: List<OfertaRecebida> get() = e.ofertas

    private fun arredondar(v: Long): Long = v / 10_000 * 10_000

    /** Enquanto o mercado está aberto, os outros clubes fazem propostas pelos teus jogadores (usa um gerador próprio, que não mexe na simulação). */
    fun gerarOfertas() {
        val hoje = c.gestor.data
        e.ofertas.removeAll { LocalDate.parse(it.expira) <= hoje || bd.jogadores[it.jogadorId]?.equipa != meu.id || !c.mercado.aberto }
        if (!c.mercado.aberto || e.ofertas.size >= MAX_OFERTAS) return
        val rng = g.rng(17)
        if (rng.nextDouble() > 0.35) return
        val candidatos = meu.plantel.filter { !it.academia && it.emprestadoPor.isEmpty() && e.ofertas.none { o -> o.jogadorId == it.id } && it.id != c.info.jogadorId }
        if (candidatos.size <= MIN_PLANTEL) return
        val j = candidatos.sortedByDescending { it.geral() }.take(12).random(rng)
        val vm = j.valorMercado().toLong()
        val compradores = bd.equipas.values.filter {
            it !== meu && it.plantel.size < MAX_PLANTEL && j.geral() >= it.forca().geral - 4 && it.orcamento >= vm * 8 / 10 && it.reputacao >= meu.reputacao - 25
        }
        val comprador = compradores.randomOrNull(rng) ?: return
        val valor = arredondar(minOf((vm * (0.85 + rng.nextDouble() * 0.5)).toLong(), comprador.orcamento))
        if (valor <= 0) return
        // Uma cláusula combinada baixa pode ser paga logo, sem pedir licença.
        if (clausula(j) <= vm * 22 / 10 && rng.nextDouble() < 0.3 && comprador.orcamento >= clausula(j) && podeSair(j)) {
            venderPor(j, comprador, clausula(j), 0, "Cláusula de rescisão ativada")
            c.mensagem("Cláusula de rescisão paga", "${comprador.nome} pagou a cláusula de ${Mercado.euros(clausula(j))} e levou ${j.nome}.")
            return
        }
        val expira = hoje.plusDays(21).toString()
        val oferta = if (rng.nextDouble() < 0.3 && comprador.orcamento >= vm) {
            OfertaRecebida(e.proximaOferta++, j.id, comprador.id, TipoOferta.EMPRESTIMO_COM_OPCAO, 0, 0, arredondar(vm * 5 / 100), arredondar(vm * 115 / 100), expira)
        } else {
            val meses = if (rng.nextDouble() < 0.4) listOf(12, 24).random(rng) else 0
            OfertaRecebida(e.proximaOferta++, j.id, comprador.id, TipoOferta.COMPRA, valor, meses, 0, 0, expira)
        }
        e.ofertas += oferta
        val nome = comprador.nome
        c.mensagem(
            "Proposta por ${j.nome}",
            when (oferta.tipo) {
                TipoOferta.COMPRA -> "$nome oferece ${Mercado.euros(oferta.valor)}" + (if (oferta.meses > 0) " em ${oferta.meses} prestações mensais" else "") + " por ${j.nome}."
                TipoOferta.EMPRESTIMO_COM_OPCAO -> "$nome quer ${j.nome} emprestado até ao fim da época (${Mercado.euros(oferta.taxa)} pelo empréstimo) com opção de compra de ${Mercado.euros(oferta.opcao)}."
            } + " Decide em Mais › Gestão › Contratos.",
        )
    }

    private fun podeSair(j: Jogador): Boolean = meu.plantel.size > MIN_PLANTEL && (j.posicao != "GR" || meu.plantel.count { it.posicao == "GR" } > 2)

    /** Vende o jogador a um clube (a prestações, se "meses" > 0). */
    private fun venderPor(j: Jogador, comprador: Equipa, valor: Long, meses: Int, titulo: String) {
        if (meses > 0) {
            val mensal = (valor - valor / 4) / meses
            val entrada = valor - mensal * meses
            c.mercado.pagar(comprador, meu, entrada)
            e.prestacoes += Prestacao("${j.nome} (${comprador.abreviatura})", mensal, meses, true, comprador.id)
        } else {
            c.mercado.pagar(comprador, meu, valor)
        }
        c.mercado.mudar(j, comprador)
        e.condicoes.remove(j.id)
        e.ofertas.removeAll { it.jogadorId == j.id }
        c.mensagem("Venda: ${j.nome}", "${j.nome} foi vendido a ${comprador.nome} por ${Mercado.euros(valor)}" + (if (meses > 0) " (em $meses prestações)" else "") + ". ($titulo)")
    }

    fun aceitar(id: Int): Resposta {
        val o = e.ofertas.firstOrNull { it.id == id } ?: return Resposta.NAO_PERMITIDO
        val j = bd.jogadores[o.jogadorId]
        val comprador = bd.equipas[o.clubeId]
        if (!g.podeGerir || j == null || comprador == null || j.equipa != meu.id) return Resposta.NAO_PERMITIDO
        if (!podeSair(j)) return Resposta.PLANTEL_CURTO
        when (o.tipo) {
            TipoOferta.COMPRA -> {
                val entrada = if (o.meses > 0) o.valor / 4 else o.valor
                if (comprador.orcamento < entrada) { e.ofertas.remove(o); return Resposta.SEM_INTERESSADOS }
                venderPor(j, comprador, o.valor, o.meses, "Proposta aceite")
            }
            TipoOferta.EMPRESTIMO_COM_OPCAO -> {
                if (comprador.plantel.size >= MAX_PLANTEL) { e.ofertas.remove(o); return Resposta.SEM_INTERESSADOS }
                c.mercado.pagar(comprador, meu, o.taxa)
                j.emprestadoPor = meu.id
                bd.mudarDeEquipa(j, comprador.id)
                e.opcoes += OpcaoCompra(j.id, comprador.id, o.opcao, false)
                e.ofertas.removeAll { it.jogadorId == j.id }
                c.mensagem("Empréstimo: ${j.nome}", "${j.nome} segue para ${comprador.nome} até ao fim da época (${Mercado.euros(o.taxa)}). O clube tem opção de compra por ${Mercado.euros(o.opcao)}.")
            }
        }
        c.baseDadosPorGravar = true
        return Resposta.ACEITE
    }

    fun recusar(id: Int): Resposta {
        if (!e.ofertas.removeAll { it.id == id }) return Resposta.NAO_PERMITIDO
        return Resposta.ACEITE
    }

    /** Pede 15% mais: o clube pode aceitar (e fica a proposta nova) ou retirar a oferta. Só se pode contrapor uma vez. */
    fun contrapor(id: Int): ResultadoNegociacao {
        val o = e.ofertas.firstOrNull { it.id == id } ?: return ResultadoNegociacao(ResNeg.NAO_PERMITIDO, "Esta proposta já não existe.")
        val j = bd.jogadores[o.jogadorId] ?: return ResultadoNegociacao(ResNeg.NAO_PERMITIDO, "O jogador já não está no clube.")
        if (o.contraposta) return ResultadoNegociacao(ResNeg.NAO_PERMITIDO, "Já fizeste uma contraproposta a este clube.")
        val clube = bd.equipas[o.clubeId]?.nome ?: "O clube"
        val base = if (o.tipo == TipoOferta.COMPRA) o.valor else o.taxa
        val pedido = base * 115 / 100
        val vm = j.valorMercado().toLong()
        val referencia = if (o.tipo == TipoOferta.COMPRA) vm * 125 / 100 else vm * 7 / 100
        val sorte = g.rng(o.id * 13 + 5).nextDouble()
        val aceita = sorte < (if (pedido <= referencia) 0.55 else 0.25)
        if (!aceita) {
            e.ofertas.remove(o)
            return ResultadoNegociacao(ResNeg.RECUSADA, "$clube não aceitou pagar mais e retirou a proposta.")
        }
        o.contraposta = true
        if (o.tipo == TipoOferta.COMPRA) o.valor = arredondar(pedido) else {
            // As ofertas são imutáveis nos campos de empréstimo: substitui-se por uma nova.
            e.ofertas.remove(o)
            e.ofertas += OfertaRecebida(o.id, o.jogadorId, o.clubeId, o.tipo, 0, 0, arredondar(pedido), o.opcao, o.expira, true)
        }
        return ResultadoNegociacao(ResNeg.ACEITE, "$clube aceitou a contraproposta de ${Mercado.euros(arredondar(pedido))}. A proposta fica à tua espera.")
    }

    // ------------------------------------------------------------ empréstimos com opção de compra

    /** O preço da opção e a taxa de empréstimo de um jogador de outro clube. */
    fun taxaEmprestimo(j: Jogador): Long = arredondar(j.valorMercado() * 6L / 100)
    fun precoOpcao(j: Jogador): Long = arredondar(j.valorMercado() * 115L / 100)

    /** Pede um jogador emprestado até ao fim da época, pagando uma taxa e ficando com a opção de o comprar. */
    fun pedirEmprestimoComOpcao(j: Jogador): Resposta {
        if (!g.podeGerir) return Resposta.NAO_PERMITIDO
        val dono = bd.equipas[j.equipa] ?: return Resposta.CLUBE_NAO_CEDE
        val taxa = taxaEmprestimo(j)
        if (taxa > meu.orcamento) return Resposta.SEM_DINHEIRO
        g.bloqueioTransferencias(taxa)?.let { return it }
        val preco = precoOpcao(j)
        val r = c.mercado.pedirEmprestimo(j)
        if (r != Resposta.ACEITE) return r
        c.mercado.pagar(meu, dono, taxa)
        e.opcoes += OpcaoCompra(j.id, dono.id, preco, true)
        c.mensagem("Opção de compra: ${j.nome}", "Pagaste ${Mercado.euros(taxa)} pelo empréstimo. Podes comprar ${j.nome} por ${Mercado.euros(preco)} até ao fim da época (Mais › Gestão › Contratos).")
        return Resposta.ACEITE
    }

    /** As opções de compra em vigor (dos dois lados) que ainda fazem sentido. */
    fun opcoes(): List<OpcaoCompra> = e.opcoes.filter { o ->
        val j = bd.jogadores[o.jogadorId] ?: return@filter false
        if (o.minha) j.equipa == meu.id && j.emprestadoPor == o.clubeId else j.equipa == o.clubeId && j.emprestadoPor == meu.id
    }

    /** Exerce a opção de comprar um jogador emprestado (a prestações, se "meses" > 0). */
    fun exercerOpcao(jogadorId: Int, meses: Int = 0): Resposta {
        val o = opcoes().firstOrNull { it.jogadorId == jogadorId && it.minha } ?: return Resposta.NAO_PERMITIDO
        val j = bd.jogadores[jogadorId] ?: return Resposta.NAO_PERMITIDO
        val dono = bd.equipas[o.clubeId] ?: return Resposta.NAO_PERMITIDO
        if (!g.podeGerir) return Resposta.NAO_PERMITIDO
        if ((if (meses > 0) o.preco / 4 else o.preco) > meu.orcamento) return Resposta.SEM_DINHEIRO
        g.bloqueioTransferencias(o.preco)?.let { return it }
        g.bloqueioSalarios(0)?.let { return it }
        if (meses > 0) pagarEmPrestacoes(dono, o.preco, meses, "${j.nome} (${dono.abreviatura})") else c.mercado.pagar(meu, dono, o.preco)
        j.emprestadoPor = ""
        c.mercado.mudar(j, meu)
        e.opcoes.remove(o)
        c.mensagem("Compra: ${j.nome}", "Exerceste a opção de compra de ${j.nome} por ${Mercado.euros(o.preco)}. Contrato até ${j.contratoAte}.")
        return Resposta.ACEITE
    }

    /**
     * Antes de os emprestados voltarem ao dono (mudança de época): os clubes decidem se exercem a opção de compra
     * dos teus jogadores, e as opções que ficaram por exercer perdem-se.
     */
    fun resolverOpcoes() {
        val rng = g.rng(29)
        for (o in e.opcoes.toList()) {
            val j = bd.jogadores[o.jogadorId]
            val outro = bd.equipas[o.clubeId]
            if (j != null && outro != null) {
                if (o.minha && j.equipa == meu.id && j.emprestadoPor == o.clubeId) {
                    c.mensagem("Opção de compra perdida", "Não exerceste a opção de compra de ${j.nome}: regressa a ${outro.nome}.")
                } else if (!o.minha && j.equipa == outro.id && j.emprestadoPor == meu.id) {
                    val interessa = j.geral() >= outro.forca().geral - 2 && rng.nextDouble() < 0.6
                    if (interessa && outro.orcamento >= o.preco / 4) {
                        val meses = if (outro.orcamento >= o.preco) 0 else 12
                        if (meses > 0) {
                            val mensal = (o.preco - o.preco / 4) / meses
                            c.mercado.pagar(outro, meu, o.preco - mensal * meses)
                            e.prestacoes += Prestacao("${j.nome} (${outro.abreviatura})", mensal, meses, true, outro.id)
                        } else c.mercado.pagar(outro, meu, o.preco)
                        j.emprestadoPor = ""
                        c.mercado.mudar(j, outro)
                        e.condicoes.remove(j.id)
                        c.mensagem("Opção de compra exercida", "${outro.nome} exerceu a opção e comprou ${j.nome} por ${Mercado.euros(o.preco)}.")
                    } else {
                        c.mensagem("Opção de compra recusada", "${outro.nome} não exerceu a opção de compra de ${j.nome}: regressa ao clube.")
                    }
                }
            }
        }
        e.opcoes.clear()
        e.ofertas.clear()
    }

    companion object {
        const val MAX_OFERTAS = 3
    }
}
