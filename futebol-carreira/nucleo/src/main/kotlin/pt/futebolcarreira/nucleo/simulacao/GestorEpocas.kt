package pt.futebolcarreira.nucleo.simulacao

import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.dados.GeradorJogadores
import kotlin.random.Random

/** Estado de uma liga durante a época. */
class EstadoLiga(val calendario: List<List<Jogo>>, val tabela: TabelaClassificativa) {
    /** Resultados já jogados, jornada a jornada. */
    val resultados = mutableListOf<List<Resultado>>()
    val jornada get() = resultados.size
    val totalJornadas get() = calendario.size
}

/** Resumo de uma liga numa época terminada. */
class ResumoLiga(val campeao: String, val pontos: Int) {
    val sobem = mutableListOf<String>()
    val descem = mutableListOf<String>()
}

/** Resumo de uma época terminada (id da liga -> resumo). */
class ResumoEpoca(val ano: Int, val ligas: Map<String, ResumoLiga>)

/**
 * Conduz as épocas: cria calendários, simula jornadas, fecha a época
 * (campeões, subidas e descidas) e prepara a seguinte.
 *
 * "semente" igual dá sempre os mesmos resultados (útil para testes); null = ao acaso.
 */
class GestorEpocas(val bd: BaseDados, semente: Long? = null, anoInicial: Int = 2026) {
    private val rng: Random = if (semente != null) Random(semente) else Random.Default
    private val gerador = GeradorJogadores(bd.nomes, rng)
    private var epocaFechada = false

    var ano = anoInicial
        private set

    /** Estado de cada liga nesta época (id da liga -> estado). */
    val estado = LinkedHashMap<String, EstadoLiga>()

    /** Uma entrada por época terminada. */
    val historico = mutableListOf<ResumoEpoca>()

    init {
        iniciarEpoca()
    }

    /** Nome da época, por exemplo "2026/27". */
    fun nomeEpoca(anoEpoca: Int = ano): String = "%d/%02d".format(anoEpoca, (anoEpoca + 1) % 100)

    /** Cria calendários e tabelas novas para todas as ligas. */
    private fun iniciarEpoca() {
        estado.clear()
        epocaFechada = false
        for (liga in bd.competicoes.values) {
            val ids = bd.equipasDaLiga(liga.id).map { it.id }.sorted()
            estado[liga.id] = EstadoLiga(
                GeradorCalendario.gerar(ids, liga.voltas, rng),
                TabelaClassificativa(ids, liga.pontosVitoria, liga.pontosEmpate),
            )
        }
    }

    fun epocaTerminada(): Boolean = estado.values.all { it.jornada >= it.totalJornadas }

    /** Simula a jornada seguinte de todas as ligas. Devolve false se já não havia jogos. */
    fun simularJornada(): Boolean {
        var houveJogos = false
        for (e in estado.values) {
            if (e.jornada >= e.totalJornadas) continue
            e.resultados += e.calendario[e.jornada].map { jogo ->
                SimuladorJogo.simular(bd.equipas.getValue(jogo.casa), bd.equipas.getValue(jogo.fora), rng).also {
                    e.tabela.registar(it.casa, it.fora, it.golosCasa, it.golosFora)
                }
            }
            houveJogos = true
        }
        if (epocaTerminada() && !epocaFechada) fecharEpoca()
        return houveJogos
    }

    fun simularAteAoFim() {
        while (simularJornada()) Unit
    }

    /** Regista campeões, subidas e descidas no histórico (ainda sem mexer nas equipas). */
    private fun fecharEpoca() {
        epocaFechada = true
        val resumos = LinkedHashMap<String, ResumoLiga>()
        for ((ligaId, e) in estado) {
            val primeiro = e.tabela.ordenada().firstOrNull() ?: continue
            resumos[ligaId] = ResumoLiga(primeiro.id, primeiro.pts)
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

    /** Aplica subidas/descidas, faz evoluir os jogadores e começa a época seguinte. */
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
