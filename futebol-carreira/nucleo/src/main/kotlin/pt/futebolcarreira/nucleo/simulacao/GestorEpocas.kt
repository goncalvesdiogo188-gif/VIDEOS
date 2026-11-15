package pt.futebolcarreira.nucleo.simulacao

import kotlinx.serialization.Serializable
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.dados.GeradorJogadores
import pt.futebolcarreira.nucleo.modelos.Competicao
import pt.futebolcarreira.nucleo.modelos.ContasEpoca
import pt.futebolcarreira.nucleo.modelos.Jogador
import java.time.LocalDate
import java.time.YearMonth
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

/** Quem ganhou uma taça ou supertaça, e quem perdeu a final. */
@Serializable
class ResumoTaca(val vencedor: String, val finalista: String = "")

/**
 * Resumo de uma época terminada (id da liga -> resumo).
 * "tacas" e "supertacas": id da competição (por exemplo "POR_TACA") -> vencedor e finalista;
 * "europa": o mesmo para as competições europeias.
 */
@Serializable
class ResumoEpoca(
    val ano: Int,
    val ligas: Map<String, ResumoLiga>,
    val tacas: Map<String, ResumoTaca> = emptyMap(),
    val supertacas: Map<String, ResumoTaca> = emptyMap(),
    /** Competições europeias: "UCL" (Liga dos Campeões) e "UEL" (Liga Europa) -> vencedor e finalista. */
    val europa: Map<String, ResumoTaca> = emptyMap(),
)

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

    /** Taças e supertaças desta época (id da competição -> estado). Gravações antigas começam sem elas. */
    val eliminatorias = LinkedHashMap<String, EstadoEliminatoria>()

    /**
     * Ligas dos Campeões e Europa desta época. Gravações antigas (sem competições europeias) ficam com null
     * até à época seguinte, em que são criadas a partir das classificações finais.
     */
    var europa: EuropaDaEpoca? = null
        internal set

    /** A taça nacional ou a supertaça de um país nesta época (null se não existe). */
    fun tacaDoPais(pais: String, supertaca: Boolean = false): EstadoEliminatoria? =
        eliminatorias.values.firstOrNull { bd.competicoes[it.id]?.let { c -> c.pais == pais && (if (supertaca) c.eSupertaca else c.eTaca) } == true }

    /** Frases sobre o que aconteceu na mudança de época (empréstimos que acabam, renovações...). */
    val avisos = mutableListOf<String>()

    /** Frases sobre o treino do clube do utilizador (evolução, lesões); quem as lê deve esvaziar a lista. */
    val noticias = mutableListOf<String>()

    /** Id do clube do utilizador (para os avisos lhe dizerem respeito). */
    var clubeDoUtilizador: String = ""

    internal val aleatorio get() = rng

    /** Uma entrada por época terminada. */
    val historico = mutableListOf<ResumoEpoca>()

    init {
        if (iniciar) iniciarEpoca()
    }

    /** Nome da época, por exemplo "2026/27". */
    fun nomeEpoca(anoEpoca: Int = ano): String = "%d/%02d".format(anoEpoca, (anoEpoca + 1) % 100)

    /** Cria calendários e tabelas novas para todas as ligas. */
    private fun iniciarEpoca() {
        // Classificações finais da época que acaba (ligaId -> clubes por ordem), para o apuramento europeu.
        val tabelasAnteriores = estado.mapValues { (_, e) -> e.tabela.ordenada().map { it.id } }
        estado.clear()
        epocaFechada = false
        data = CalendarioDatas.inicioDaEpoca(ano)
        // Época nova: estatísticas a zero, todos recuperados, e cada treinador escolhe a formação.
        for (equipa in bd.equipas.values) {
            for (j in equipa.plantel + equipa.academia) {
                j.epoca.limpar()
                j.epocaTaca.limpar()
                j.lesao = 0
                j.suspensao = 0
                j.forma = 50
                j.moral = 50
                j.jogosNoUltimoTreino = 0
            }
            equipa.escolherFormacao()
            equipa.contas = ContasEpoca()
        }
        for (liga in bd.competicoes.values.filter { it.eLiga }) {
            val ids = bd.equipasDaLiga(liga.id).map { it.id }.sorted()
            val calendario = GeradorCalendario.gerar(ids, liga.voltas, rng)
            estado[liga.id] = EstadoLiga(
                calendario,
                TabelaClassificativa(ids, liga.pontosVitoria, liga.pontosEmpate),
                CalendarioDatas.jornadas(ano, calendario.size),
            )
        }
        iniciarTacas()
        iniciarEuropa(tabelasAnteriores)
    }

    /**
     * Cria a Liga dos Campeões e a Liga Europa: na primeira época apuram-se os clubes por reputação,
     * nas seguintes pelos lugares 1–4 e 5–8 das primeiras divisões na época anterior.
     * Sem 24 clubes em cada competição (bases de dados pequenas) não há competições europeias.
     */
    private fun iniciarEuropa(tabelas: Map<String, List<String>>) {
        val q = QualificacaoEuropa.qualificar(bd, tabelas)
        europa = if (q.campeoes.size == 24 && q.europa.size == 24 && q.campeoes.toSet().size == 24 && q.europa.toSet().size == 24) {
            EuropaDaEpoca.criar(bd, ano, tabelas, rng)
        } else null
    }

    /** Cria as taças e supertaças do país (as datas evitam os jogos de liga dos clubes). */
    private fun iniciarTacas() {
        eliminatorias.clear()
        val reputacao = { id: String -> bd.equipas[id]?.reputacao ?: 0 }
        val categoria = { id: String -> bd.competicoes[bd.equipas[id]?.liga]?.nivel ?: 1 }
        for (comp in bd.competicoes.values) {
            val ligas = bd.ligasDoPais(comp.pais)
            if (ligas.isEmpty()) continue
            if (comp.eTaca) {
                val participantes = ligas.flatMap { bd.equipasDaLiga(it.id) }.map { it.id }.sorted()
                if (participantes.size < 2) continue
                val rondas = EstadoEliminatoria.numeroDeRondas(participantes.size)
                val ocupadas = ligas.flatMap { estado[it.id]?.datas.orEmpty() }.toSet()
                val datas = CalendarioDatas.datasTaca(ano, rondas, comp.idaEVolta, ocupadas)
                val nomes = EstadoEliminatoria.nomesDasRondas(rondas)
                val defs = List(rondas) { DefRonda(nomes[it], datas[it], neutro = comp.finalNeutra && it == rondas - 1) }
                eliminatorias[comp.id] = EstadoEliminatoria(comp.id, comp.nome, participantes, defs, reputacao, categoria, rng)
            } else if (comp.eSupertaca) {
                val participantes = participantesDaSupertaca(comp, ligas.first())
                if (participantes.size < 2) continue
                val defs = listOf(DefRonda("Final", listOf(CalendarioDatas.supertaca(ano)), neutro = true))
                eliminatorias[comp.id] = EstadoEliminatoria(comp.id, comp.nome, participantes, defs, reputacao, categoria, rng)
            }
        }
    }

    /**
     * Campeão da liga da época passada contra o vencedor da taça (ou o finalista, se for o mesmo clube).
     * Sem época anterior (primeira época do jogo, ou gravação antiga sem taças) jogam os dois clubes de maior
     * reputação da primeira divisão; faltando só o vencedor da taça, joga o clube de maior reputação a seguir ao campeão.
     */
    private fun participantesDaSupertaca(comp: Competicao, primeiraDivisao: Competicao): List<String> {
        val maiores = bd.equipasDaLiga(primeiraDivisao.id).sortedByDescending { it.reputacao }.map { it.id }
        val anterior = historico.lastOrNull { it.ano == ano - 1 }
        val campeao = anterior?.ligas?.get(comp.ligaRef.ifEmpty { primeiraDivisao.id })?.campeao?.takeIf { it in bd.equipas }
            ?: return maiores.take(2)
        val taca = anterior.tacas[comp.tacaRef]
        val outro = listOfNotNull(taca?.vencedor, taca?.finalista.takeIf { !it.isNullOrEmpty() })
            .firstOrNull { it != campeao && it in bd.equipas }
            ?: maiores.firstOrNull { it != campeao }
        return listOfNotNull(campeao, outro)
    }

    /** Uma taça vazia, para ser preenchida a partir de uma gravação. */
    internal fun eliminatoriaVazia(comp: Competicao, participantes: List<String>, defs: List<DefRonda>) = EstadoEliminatoria(
        comp.id, comp.nome, participantes, defs, { bd.equipas[it]?.reputacao ?: 0 }, { bd.competicoes[bd.equipas[it]?.liga]?.nivel ?: 1 }, rng, iniciar = false,
    )

    /** A época acaba quando todas as ligas, taças e supertaças acabaram. */
    fun epocaTerminada(): Boolean =
        estado.values.all { it.jornada >= it.totalJornadas } && eliminatorias.values.all { it.concluida } && (europa?.terminou ?: true)

    /** O próximo dia com jogos, em qualquer competição (null se a época acabou). */
    fun proximaData(): LocalDate? =
        (estado.values.mapNotNull { it.proximaData } + eliminatorias.values.mapNotNull { it.proximaData } + listOfNotNull(europa?.proximaData())).minOrNull()

    /** Joga tudo o que está marcado até ao dia "dia" (inclusive), dia a dia e por ordem, e põe o jogo nesse dia. */
    fun simularAte(dia: LocalDate) {
        while (true) {
            val proxima = proximaData() ?: break
            if (proxima > dia) break
            for (e in estado.values) if (e.proximaData?.let { it <= proxima } == true) jogarJornada(e)
            for (t in eliminatorias.values) if (t.proximaData?.let { it <= proxima } == true) jogarEliminatoria(t, proxima)
            europa?.let { eu ->
                if (eu.proximaData()?.let { it <= proxima } == true) {
                    eu.jogarAte(proxima, bd, rng, setOf(clubeDoUtilizador)) { _, relatorio ->
                        Treino.depoisDoJogo(bd, relatorio, rng, clubeDoUtilizador, noticias)
                    }
                }
            }
        }
        // Sempre que se muda de mês faz-se a conta do treino.
        var mes = YearMonth.from(data)
        while (mes < YearMonth.from(dia)) {
            mes = mes.plusMonths(1)
            val evolucao = Treino.mensal(bd, rng, clubeDoUtilizador)
            if (evolucao.isNotEmpty()) noticias += "Treino de ${CalendarioDatas.mes(mes.minusMonths(1).monthValue)}: " + evolucao.take(8).joinToString() + (if (evolucao.size > 8) " e mais ${evolucao.size - 8}." else ".")
        }
        if (dia > data) data = dia
        if (epocaTerminada() && !epocaFechada) fecharEpoca()
    }

    private fun jogarJornada(e: EstadoLiga) {
        e.resultados += e.calendario[e.jornada].map { jogo ->
            val relatorio = MotorJogo.simular(bd.equipas.getValue(jogo.casa), bd.equipas.getValue(jogo.fora), rng)
            MotorJogo.aplicar(relatorio)
            Treino.depoisDoJogo(bd, relatorio, rng, clubeDoUtilizador, noticias)
            e.tabela.registar(jogo.casa, jogo.fora, relatorio.golosCasa, relatorio.golosFora)
            Financas.jornada(bd.equipas.getValue(jogo.casa), true, e.totalJornadas)
            Financas.jornada(bd.equipas.getValue(jogo.fora), false, e.totalJornadas)
            Resultado(jogo.casa, jogo.fora, relatorio.golosCasa, relatorio.golosFora, relatorio)
        }
    }

    private fun jogarEliminatoria(t: EstadoEliminatoria, dia: LocalDate) {
        val comp = bd.competicoes.getValue(t.id)
        t.jogarDia(dia, { casa, fora, neutro, antesCasa, antesFora, decisivo -> jogarJogoDeTaca(casa, fora, neutro, antesCasa, antesFora, decisivo) }) { confronto, ronda ->
            val vencedor = bd.equipas.getValue(confronto.vencedor!!)
            val vencido = bd.equipas.getValue(confronto.perdedor!!)
            if (comp.eSupertaca) {
                Financas.premioSupertaca(vencedor, true)
                Financas.premioSupertaca(vencido, false)
            } else {
                Financas.premioTaca(vencedor, ronda, ronda == t.defs.size - 1)
            }
        }
    }

    /** Um jogo de taça com o motor detalhado; em caso de empate decisivo há prolongamento e penáltis. */
    private fun jogarJogoDeTaca(casaId: String, foraId: String, neutro: Boolean, antesCasa: Int, antesFora: Int, decisivo: Boolean): Resultado {
        val casa = bd.equipas.getValue(casaId)
        val fora = bd.equipas.getValue(foraId)
        val relatorio = MotorJogo.simular(casa, fora, rng, neutro, if (decisivo) { gc, gf -> gc + antesCasa == gf + antesFora } else null)
        MotorJogo.aplicar(relatorio, taca = true)
        Treino.depoisDoJogo(bd, relatorio, rng, clubeDoUtilizador, noticias)
        Financas.bilheteiraTaca(casa, fora, neutro)
        var penaltisCasa = 0
        var penaltisFora = 0
        if (decisivo && relatorio.golosCasa + antesCasa == relatorio.golosFora + antesFora) {
            val (pc, pf) = Penaltis.disputar(casa, fora, rng)
            penaltisCasa = pc
            penaltisFora = pf
        }
        return Resultado(casaId, foraId, relatorio.golosCasa, relatorio.golosFora, relatorio, relatorio.prolongamento, penaltisCasa, penaltisFora)
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
        for (liga in bd.competicoes.values.filter { it.eLiga }) {
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
        val tacas = LinkedHashMap<String, ResumoTaca>()
        val supertacas = LinkedHashMap<String, ResumoTaca>()
        for ((id, t) in eliminatorias) {
            val campeao = t.campeao ?: continue
            val resumo = ResumoTaca(campeao, t.finalista ?: "")
            if (bd.competicoes.getValue(id).eSupertaca) {
                supertacas[id] = resumo
                bd.equipas.getValue(campeao).supertacas++
            } else {
                tacas[id] = resumo
                bd.equipas.getValue(campeao).tacas++
            }
        }
        val europeias = LinkedHashMap<String, ResumoTaca>()
        europa?.let { eu ->
            for (tipo in TipoEuropa.values()) {
                val estadoEuropeu = eu.estado(tipo)
                if (!estadoEuropeu.terminou) continue
                val final = estadoEuropeu.eliminatorias.last()
                europeias[tipo.id] = ResumoTaca(estadoEuropeu.campeao, if (final.vencedor == final.a) final.b else final.a)
                bd.equipas.getValue(estadoEuropeu.campeao).europeias++
            }
        }
        // Prémios de classificação.
        for ((ligaId, e) in estado) {
            val ordem = e.tabela.ordenada()
            ordem.forEachIndexed { i, linha ->
                Financas.premio(bd.equipas.getValue(linha.id), i + 1, ordem.size, bd.competicoes.getValue(ligaId).nivel)
            }
        }
        historico += ResumoEpoca(ano, resumos, tacas, supertacas, europeias)
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
        avisos.clear()
        FimDeEpoca.contratos(bd, ano, rng, avisos, clubeDoUtilizador)
        EvolucaoPlantel.aplicar(bd, gerador, rng, ano + 1, avisos, clubeDoUtilizador)
        FimDeEpoca.completarPlanteis(bd, gerador, ano + 1)
        ano++
        iniciarEpoca()
    }
}
