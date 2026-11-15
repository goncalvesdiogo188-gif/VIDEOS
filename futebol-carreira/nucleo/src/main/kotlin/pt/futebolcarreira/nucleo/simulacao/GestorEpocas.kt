package pt.futebolcarreira.nucleo.simulacao

import kotlinx.serialization.Serializable
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.dados.GeradorJogadores
import pt.futebolcarreira.nucleo.modelos.Jogador
import java.time.LocalDate
import kotlin.random.Random

/** Estado de uma liga durante a época. "datas" tem o dia de cada jornada. */
class EstadoLiga(val calendario: List<List<Jogo>>, val tabela: TabelaClassificativa, val datas: List<LocalDate>) {
    /** Resultados já jogados, jornada a jornada. */
    val resultados = mutableListOf<List<Resultado>>()
    val jornada get() = resultados.size
    val totalJornadas get() = calendario.size

    /** Dia da próxima jornada (null se a liga já acabou). */
    val proximaData: LocalDate? get() = datas.getOrNull(jornada)
}

/** Resumo de uma liga numa época terminada. */
@Serializable
class ResumoLiga(
    val campeao: String,
    val pontos: Int,
    val sobem: MutableList<String> = mutableListOf(),
    val descem: MutableList<String> = mutableListOf(),
    /** Melhor marcador da liga: nome, sigla do clube e golos. */
    var melhorMarcador: String = "",
    var clubeMarcador: String = "",
    var golosMarcador: Int = 0,
)

/** Resumo de uma época terminada (id da liga -> resumo). */
@Serializable
class ResumoEpoca(val ano: Int, val ligas: Map<String, ResumoLiga>)

/**
 * Conduz as épocas: cria calendários com datas, simula os jogos dia a dia,
 * fecha a época (campeões, subidas e descidas) e prepara a seguinte.
 *
 * "semente" igual dá sempre os mesmos resultados (útil para testes); null = ao acaso.
 * "iniciar" = false cria o gestor vazio, para ser preenchido a partir de uma gravação.
 */
class GestorEpocas(val bd: BaseDados, semente: Long? = null, anoInicial: Int = 2026, iniciar: Boolean = true) {
    private val rng: Random = if (semente != null) Random(semente) else Random.Default
    internal val gerador = GeradorJogadores(bd.nomes, rng)
    internal var epocaFechada = false

    /** Ano em que a época começou (2026 = época 2026/27). */
    var ano = anoInicial
        internal set

    /** Dia em que o jogo vai. */
    var data: LocalDate = CalendarioDatas.inicioDaEpoca(anoInicial)
        internal set

    /** Estado de cada liga nesta época (id da liga -> estado). */
    val estado = LinkedHashMap<String, EstadoLiga>()

    /** Uma entrada por época terminada. */
    val historico = mutableListOf<ResumoEpoca>()

    init {
        if (iniciar) iniciarEpoca()
    }

    /** Nome da época, por exemplo "2026/27". */
    fun nomeEpoca(anoEpoca: Int = ano): String = "%d/%02d".format(anoEpoca, (anoEpoca + 1) % 100)

    /** Cria calendários e tabelas novas para todas as ligas. */
    private fun iniciarEpoca() {
        estado.clear()
        epocaFechada = false
        data = CalendarioDatas.inicioDaEpoca(ano)
        // Época nova: estatísticas a zero, todos recuperados, e cada treinador escolhe a formação.
        for (equipa in bd.equipas.values) {
            for (j in equipa.plantel) {
                j.epoca.limpar()
                j.lesao = 0
                j.suspensao = 0
            }
            equipa.escolherFormacao()
        }
        for (liga in bd.competicoes.values) {
            val ids = bd.equipasDaLiga(liga.id).map { it.id }.sorted()
            val calendario = GeradorCalendario.gerar(ids, liga.voltas, rng)
            estado[liga.id] = EstadoLiga(
                calendario,
                TabelaClassificativa(ids, liga.pontosVitoria, liga.pontosEmpate),
                CalendarioDatas.jornadas(ano, calendario.size),
            )
        }
    }

    fun epocaTerminada(): Boolean = estado.values.all { it.jornada >= it.totalJornadas }

    /** O próximo dia com jogos, em qualquer liga (null se a época acabou). */
    fun proximaData(): LocalDate? = estado.values.mapNotNull { it.proximaData }.minOrNull()

    /** Joga todas as jornadas marcadas até ao dia "dia" (inclusive) e põe o jogo nesse dia. */
    fun simularAte(dia: LocalDate) {
        for (e in estado.values) {
            while (true) {
                val proxima = e.proximaData ?: break
                if (proxima > dia) break
                jogarJornada(e)
            }
        }
        if (dia > data) data = dia
        if (epocaTerminada() && !epocaFechada) fecharEpoca()
    }

    private fun jogarJornada(e: EstadoLiga) {
        e.resultados += e.calendario[e.jornada].map { jogo ->
            val relatorio = MotorJogo.simular(bd.equipas.getValue(jogo.casa), bd.equipas.getValue(jogo.fora), rng)
            MotorJogo.aplicar(relatorio)
            e.tabela.registar(jogo.casa, jogo.fora, relatorio.golosCasa, relatorio.golosFora)
            Resultado(jogo.casa, jogo.fora, relatorio.golosCasa, relatorio.golosFora, relatorio)
        }
    }

    /** Avança para o próximo dia com jogos e joga-os. Devolve false se já não havia jogos. */
    fun simularJornada(): Boolean {
        simularAte(proximaData() ?: return false)
        return true
    }

    /** Jogadores da liga que já jogaram esta época. */
    fun jogadoresDaLiga(ligaId: String): List<Jogador> =
        bd.equipasDaLiga(ligaId).flatMap { it.plantel }.filter { it.epoca.jogos > 0 }

    /**
     * Os "quantos" melhores jogadores da liga segundo um critério (golos, assistências, nota...).
     * Só entra quem tem pelo menos "minimoJogos" e um valor acima de zero.
     */
    fun melhores(ligaId: String, quantos: Int, minimoJogos: Int = 1, criterio: (Jogador) -> Double): List<Jogador> =
        jogadoresDaLiga(ligaId)
            .filter { it.epoca.jogos >= minimoJogos && criterio(it) > 0.0 }
            .sortedWith(compareByDescending<Jogador> { criterio(it) }.thenBy { it.epoca.jogos }.thenBy { it.id })
            .take(quantos)

    fun simularAteAoFim() {
        while (simularJornada()) Unit
    }

    /** Regista campeões, subidas e descidas no histórico (ainda sem mexer nas equipas). */
    private fun fecharEpoca() {
        epocaFechada = true
        val resumos = LinkedHashMap<String, ResumoLiga>()
        for ((ligaId, e) in estado) {
            val primeiro = e.tabela.ordenada().firstOrNull() ?: continue
            resumos[ligaId] = ResumoLiga(primeiro.id, primeiro.pts).also { resumo ->
                melhores(ligaId, 1) { it.epoca.golos.toDouble() }.firstOrNull()?.let { j ->
                    resumo.melhorMarcador = j.nome
                    resumo.clubeMarcador = bd.equipas[j.equipa]?.abreviatura ?: ""
                    resumo.golosMarcador = j.epoca.golos
                }
            }
        }
        for (liga in bd.competicoes.values) {
            val cima = resumos[liga.id] ?: continue
            val baixo = resumos[liga.descePara] ?: continue
            if (liga.descidas <= 0) continue
            val tabelaCima = estado.getValue(liga.id).tabela.ordenada()
            val tabelaBaixo = estado.getValue(liga.descePara).tabela.ordenada()
            val quantas = minOf(liga.descidas, minOf(tabelaCima.size, tabelaBaixo.size) / 2)
            cima.descem += tabelaCima.takeLast(quantas).reversed().map { it.id }
            baixo.sobem += tabelaBaixo.take(quantas).map { it.id }
        }
        for ((ligaId, resumo) in resumos) {
            if (bd.competicoes.getValue(ligaId).nivel == 1) bd.equipas.getValue(resumo.campeao).titulos++
        }
        historico += ResumoEpoca(ano, resumos)
    }

    /** Aplica subidas/descidas, faz evoluir os jogadores e começa a época seguinte (a 1 de julho). */
    fun avancarParaEpocaSeguinte() {
        if (!epocaTerminada()) return
        for ((ligaId, resumo) in historico.last().ligas) {
            val liga = bd.competicoes.getValue(ligaId)
            bd.equipas.getValue(resumo.campeao).let { it.reputacao = minOf(99, it.reputacao + 1) }
            for (id in resumo.descem) {
                val e = bd.equipas.getValue(id)
                e.liga = liga.descePara
                e.reputacao = maxOf(1, e.reputacao - 2)
            }
            // Quem sobe vai para a liga que tem esta como "desce_para".
            val acima = bd.competicoes.values.firstOrNull { it.descePara == ligaId }
            if (acima != null) {
                for (id in resumo.sobem) {
                    val e = bd.equipas.getValue(id)
                    e.liga = acima.id
                    e.reputacao = minOf(99, e.reputacao + 1)
                }
            }
        }
        EvolucaoPlantel.aplicar(bd, gerador, rng)
        ano++
        iniciarEpoca()
    }
}
