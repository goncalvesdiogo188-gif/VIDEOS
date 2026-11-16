package pt.futebolcarreira.nucleo.carreira

import kotlinx.serialization.Serializable
import pt.futebolcarreira.nucleo.modelos.AjustesTreino
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Fase 13: tudo o que o treinador gere a fundo no clube (equipa técnica, contratos, orçamento, treino) e que fica
 * gravado com a carreira. Todos os campos têm valor por omissão: as gravações antigas abrem com tudo normal.
 */
@Serializable
class EstadoGestao(
    /** O clube a que este estado pertence (ao mudar de clube começa-se do zero). */
    var clubeId: String = "",
    val equipaTecnica: EstadoEquipaTecnica = EstadoEquipaTecnica(),
    val negociacoes: EstadoNegociacoes = EstadoNegociacoes(),
    val financas: EstadoFinancas = EstadoFinancas(),
    val treino: EstadoTreino = EstadoTreino(),
    /** Época (ano) cujo fecho já foi feito (prémios, balanço financeiro). */
    var epocaFechada: Int = -1,
)

/**
 * Liga as quatro partes da gestão (equipa técnica, negociações, orçamento e treino) à carreira.
 * A carreira só chama [preparar], [aposAvancar], [fimDeEpoca], [antesDaNovaEpoca] e [depoisDaNovaEpoca].
 * Nada aqui usa o gerador aleatório da simulação, por isso a gestão não muda os resultados dos jogos.
 */
class GestaoClube(private val c: Carreira) {
    var estado = EstadoGestao()

    val equipaTecnica = EquipaTecnica(c, this)
    val negociacoes = Negociacoes(c, this)
    val orcamento = Orcamento(c, this)
    val treino = PlanosTreino(c, this)

    /** Só o treinador (e só enquanto tem emprego) gere estas coisas. */
    val podeGerir get() = c.info.modo == ModoCarreira.TREINADOR && !c.info.despedido

    private var clubeAplicado = ""
    private val individuaisAplicados = HashSet<Int>()

    /** Gerador de números aleatórios próprio, sempre igual para o mesmo dia e a mesma "semente" (não toca no da simulação). */
    fun rng(sal: Int): Random = Random(c.gestor.ano * 1_000_003L + c.gestor.data.toEpochDay() * 31L + sal)

    internal fun bloqueioSalarios(adicional: Long): Resposta? = if (podeGerir) orcamento.bloqueioSalarios(adicional) else null

    internal fun bloqueioTransferencias(valor: Long): Resposta? = if (podeGerir) orcamento.bloqueioTransferencias(valor) else null

    /** Carrega o que estava gravado (null = gravação antiga: tudo normal e os contratos que acabam renovam-se sozinhos). */
    fun carregar(gravado: EstadoGestao?) {
        estado = gravado ?: EstadoGestao()
        if (estado.clubeId.isEmpty()) estado.clubeId = c.info.equipaId
        clubeAplicado = ""
        preparar()
    }

    /**
     * Põe tudo a postos antes de avançar no tempo: política de renovações, efeitos no treino e na escolha do onze.
     * Se o treinador mudou de clube, o estado recomeça do zero e o clube antigo volta ao treino normal.
     */
    fun preparar() {
        if (estado.clubeId != c.info.equipaId) {
            val politica = estado.negociacoes.renovacao
            estado = EstadoGestao(clubeId = c.info.equipaId).also { it.negociacoes.renovacao = politica }
        }
        if (clubeAplicado.isNotEmpty() && clubeAplicado != c.info.equipaId) {
            c.bd.equipas[clubeAplicado]?.let { it.ajustes = AjustesTreino(); it.ajudaNaEscolha.clear() }
            for (id in individuaisAplicados) c.gestor.focosIndividuais.remove(id)
            individuaisAplicados.clear()
        }
        clubeAplicado = c.info.equipaId
        c.gestor.renovaClubeDoUtilizador = { j -> negociacoes.renovaSozinho(j) }
        aplicarAjustes()
    }

    /** Recalcula o que a equipa técnica, o plano e a fadiga mudam no treino do clube. */
    fun aplicarAjustes() {
        val a = AjustesTreino()
        if (podeGerir) {
            equipaTecnica.aplicarEm(a)
            treino.aplicarEm(a)
        }
        c.equipa.ajustes = a
        // Treino individual: só na via de treinador (na via de jogador esse foco é o do próprio jogador).
        for (id in individuaisAplicados) c.gestor.focosIndividuais.remove(id)
        individuaisAplicados.clear()
        if (podeGerir) {
            for ((id, foco) in treino.individuais()) { c.gestor.focosIndividuais[id] = foco; individuaisAplicados += id }
            treino.aplicarAjuda()
        }
    }

    /**
     * Chamado no fim de cada avanço no tempo (do dia "antes" até hoje): meses que passaram (prestações, juros,
     * treino de posição), fadiga, contas do jogo, propostas de outros clubes e relatórios dos olheiros.
     */
    fun aposAvancar(antes: LocalDate, jogado: JogoDoClube?) {
        if (!podeGerir) return
        val depois = c.gestor.data
        var mes = YearMonth.from(antes)
        while (mes < YearMonth.from(depois)) {
            mes = mes.plusMonths(1)
            negociacoes.processarMes()
            orcamento.processarMes()
            treino.processarMes()
        }
        treino.passarDias(ChronoUnit.DAYS.between(antes, depois).toInt())
        if (jogado != null) {
            val relatorio = jogado.resultado?.relatorio
            if (relatorio != null) treino.aposJogo(if (jogado.emCasa) relatorio.casa else relatorio.fora, relatorio.duracao)
            orcamento.aposJogo(jogado)
        }
        equipaTecnica.verificar()
        negociacoes.gerarOfertas()
        negociacoes.avisarContratos()
        negociacoes.limpar()
        aplicarAjustes()
    }

    /** A época acabou (chamado uma só vez): prémios aos jogadores, patrocinadores, balanço financeiro e contratos a terminar. */
    fun fimDeEpoca() {
        if (!podeGerir || estado.epocaFechada == c.gestor.ano) return
        estado.epocaFechada = c.gestor.ano
        negociacoes.pagarPremios()
        orcamento.fimDeEpoca()
        val saem = negociacoes.aTerminar()
        if (saem.isNotEmpty() && estado.negociacoes.renovacao == PoliticaRenovacao.MANUAL) {
            c.mensagem(
                "Contratos a terminar",
                "Ainda podes renovar antes de recomeçar a época: " + saem.take(8).joinToString { "${it.nome} (${it.geral()})" } + (if (saem.size > 8) " e mais ${saem.size - 8}" else "") + ". Quem ficar sem renovar sai livre.",
            )
        }
    }

    /** Antes de começar a época seguinte: as opções de compra dos emprestados decidem-se e o verão limpa a fadiga. */
    fun antesDaNovaEpoca() {
        if (!podeGerir) return
        negociacoes.resolverOpcoes()
        treino.verao()
    }

    fun depoisDaNovaEpoca() {
        if (!podeGerir) return
        orcamento.novaEpoca()
        negociacoes.limpar()
        aplicarAjustes()
    }
}
