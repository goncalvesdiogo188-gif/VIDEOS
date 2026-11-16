package pt.futebolcarreira.nucleo.simulacao

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.Equipa
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

/** As duas competições europeias de clubes. */
enum class TipoEuropa(val id: String, val nome: String) {
    CAMPEOES("UCL", "Liga dos Campeões"),
    EUROPA("UEL", "Liga Europa"),
}

/** Nomes das fases. */
object FasesEuropa {
    const val LIGA = "Fase de liga"
    const val PLAYOFF = "Play-off"
    const val OITAVOS = "Oitavos de final"
    const val QUARTOS = "Quartos de final"
    const val MEIAS = "Meias-finais"
    const val FINAL = "Final"
}

/** Clubes apurados para as duas competições. */
class Qualificados(val campeoes: List<String>, val europa: List<String>) {
    fun de(tipo: TipoEuropa) = if (tipo == TipoEuropa.CAMPEOES) campeoes else europa
}

/** Quem se apura: lugares 1–4 para a Liga dos Campeões e 5–8 para a Liga Europa em cada 1.ª divisão. */
object QualificacaoEuropa {
    /**
     * "tabelas" é ligaId -> ids dos clubes por ordem de classificação final da época anterior.
     * Ligas sem tabela (primeira época) usam a ordem por reputação.
     */
    fun qualificar(bd: BaseDados, tabelas: Map<String, List<String>> = emptyMap()): Qualificados {
        val campeoes = mutableListOf<String>()
        val europa = mutableListOf<String>()
        for (liga in bd.competicoes.values.filter { it.nivel == 1 }) {
            val ordem = tabelas[liga.id]?.filter { it in bd.equipas }?.takeIf { it.isNotEmpty() }
                ?: bd.equipasDaLiga(liga.id).sortedWith(compareByDescending<Equipa> { it.reputacao }.thenBy { it.id }).map { it.id }
            campeoes += ordem.take(4)
            europa += ordem.drop(4).take(4)
        }
        return Qualificados(campeoes, europa)
    }
}

/** Todas as datas de uma competição numa época. */
class DatasEuropa(
    val liga: List<LocalDate>,
    val playoff: List<LocalDate>,
    val oitavos: List<LocalDate>,
    val quartos: List<LocalDate>,
    val meias: List<LocalDate>,
    val final: LocalDate,
) {
    fun todas(): List<LocalDate> = liga + playoff + oitavos + quartos + meias + final
}

object CalendarioEuropa {
    /** Último sábado de campeonato (as ligas jogam aos sábados até cerca de 24 de maio). */
    fun fimDasLigas(ano: Int): LocalDate = LocalDate.of(ano + 1, 5, 24).with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))

    /** Datas da época que começa em "ano". Terças (Campeões) e quintas (Liga Europa); nunca ao sábado, exceto a final dos Campeões (primeira semana de junho). */
    fun datas(ano: Int, tipo: TipoEuropa): DatasEuropa {
        val terca = TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY)
        // Dias de jogos das seleções (quinta, domingo e quarta de cada paragem): a Liga Europa, às quintas, foge-lhes.
        val dasSelecoes = (0..3).flatMap { listOf(CalendarioSelecoes.quinta(ano, it), CalendarioSelecoes.domingo(ano, it), CalendarioSelecoes.quarta(ano, it)) }.toSet()
        fun dia(mes: Int, d: Int, anoDia: Int): LocalDate {
            val t = LocalDate.of(anoDia, mes, d).with(terca)
            var r = if (tipo == TipoEuropa.CAMPEOES) t else t.plusDays(2)
            while (r in dasSelecoes) r = r.plusWeeks(1)
            return r
        }
        val seguinte = ano + 1
        val liga = listOf(dia(9, 15, ano), dia(9, 29, ano), dia(10, 20, ano), dia(11, 10, ano), dia(12, 8, ano), dia(1, 21, seguinte))
        val ultimoSabadoMaio = LocalDate.of(seguinte, 5, 31).with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
        // A final dos Campeões é ao sábado a seguir ao da final da taça nacional (que é o sábado depois da última
        // jornada das ligas): assim nenhum clube pode ter as duas finais no mesmo dia. A da Liga Europa é à quarta-feira
        // seguinte à final da taça, depois das ligas, e antes da final dos Campeões.
        val finalCampeoes = CalendarioDatas.finalDaTaca(ano).plusWeeks(1)
        return DatasEuropa(
            liga = liga,
            playoff = listOf(dia(2, 10, seguinte), dia(2, 17, seguinte)),
            oitavos = listOf(dia(3, 10, seguinte), dia(3, 17, seguinte)),
            quartos = listOf(dia(4, 7, seguinte), dia(4, 14, seguinte)),
            meias = listOf(dia(4, 28, seguinte), dia(5, 5, seguinte)),
            final = if (tipo == TipoEuropa.CAMPEOES) finalCampeoes else finalCampeoes.minusDays(3),
        )
    }
}

/** Prémios em euros. */
object PremiosEuropa {
    class Tabela(
        val participacao: Long, val vitoria: Long, val empate: Long,
        val playoff: Long, val oitavos: Long, val quartos: Long, val meias: Long, val final: Long, val campeao: Long,
    ) {
        fun chegarA(fase: String): Long = when (fase) {
            FasesEuropa.PLAYOFF -> playoff
            FasesEuropa.OITAVOS -> oitavos
            FasesEuropa.QUARTOS -> quartos
            FasesEuropa.MEIAS -> meias
            FasesEuropa.FINAL -> final
            else -> 0L
        }
    }

    // Campeão a ganhar tudo sem play-off: cerca de 22 M€ (Campeões) e 8,5 M€ (Liga Europa).
    val CAMPEOES = Tabela(3_000_000, 700_000, 250_000, 400_000, 1_200_000, 2_000_000, 3_000_000, 4_000_000, 5_500_000)
    val EUROPA = Tabela(1_000_000, 250_000, 100_000, 150_000, 500_000, 900_000, 1_300_000, 1_800_000, 2_000_000)

    fun de(tipo: TipoEuropa) = if (tipo == TipoEuropa.CAMPEOES) CAMPEOES else EUROPA
}

/** Um jogo europeu. Os golos incluem o prolongamento; "penaltis*" só existe se houve desempate por penáltis. */
@Serializable
class JogoEuropa(
    val fase: String,
    /** Jornada da fase de liga (1–6) ou perna da eliminatória (1 ou 2; 1 na final). */
    val numero: Int,
    /** Data em formato ISO (aaaa-mm-dd). */
    val data: String,
    val casa: String,
    val fora: String,
    var jogado: Boolean = false,
    var golosCasa: Int = 0,
    var golosFora: Int = 0,
    var prolongamento: Boolean = false,
    var penaltisCasa: Int = -1,
    var penaltisFora: Int = -1,
    /** Campo neutro (a final). */
    val neutro: Boolean = false,
) {
    /** Relatório completo, só nos jogos que o chamador pediu para manter (não é gravado). */
    @Transient
    var relatorio: RelatorioJogo? = null

    val dia: LocalDate get() = LocalDate.parse(data)
    val foiAPenaltis get() = penaltisCasa >= 0
}

/** Eliminatória a duas mãos (ou jogo único na final). "a" é o cabeça de série: joga a segunda mão em casa. */
@Serializable
class Eliminatoria(
    val fase: String,
    val a: String,
    val b: String,
    val jogos: List<JogoEuropa>,
    var vencedor: String = "",
) {
    fun golos(id: String): Int = jogos.sumOf { (if (it.casa == id) it.golosCasa else 0) + (if (it.fora == id) it.golosFora else 0) }
    val terminada get() = vencedor.isNotEmpty()
}

/** Cópia gravável do estado de uma competição. */
@Serializable
class DadosEuropa(
    val tipo: String,
    val ano: Int,
    val clubes: List<String>,
    val jogosLiga: List<JogoEuropa>,
    val eliminatorias: List<Eliminatoria>,
    val campeao: String = "",
    val premios: Map<String, Long> = emptyMap(),
)

/**
 * Estado de uma competição europeia numa época.
 *
 * Criar com [criar] (sorteio da fase de liga), avançar com [jogarAte] e gravar com [dados].
 */
class EstadoEuropa private constructor(
    val tipo: TipoEuropa,
    val ano: Int,
    val clubes: List<String>,
    val jogosLiga: List<JogoEuropa>,
    private val ronda: MutableList<Eliminatoria>,
) {
    val datas: DatasEuropa = CalendarioEuropa.datas(ano, tipo)
    private val tabela = TabelaClassificativa(clubes)
    private val tabelaPremios = PremiosEuropa.de(tipo)
    private val premios = LinkedHashMap<String, Long>()
    var campeao: String = ""
        private set

    /** Fases de eliminatórias já sorteadas, por ordem. */
    val eliminatorias: List<Eliminatoria> get() = ronda

    val terminou: Boolean get() = campeao.isNotEmpty()

    /** Dinheiro de prémios já pago a um clube nesta competição. */
    fun premiosDe(id: String): Long = premios[id] ?: 0L

    /** Classificação da fase de liga, por ordem. */
    fun tabelaLiga(): List<LinhaTabela> = tabela.ordenada()

    private fun todosOsJogos(): List<JogoEuropa> = jogosLiga + ronda.flatMap { it.jogos }

    /** Todos os jogos (feitos ou por fazer) marcados para um dia. */
    fun jogosNoDia(dia: LocalDate): List<JogoEuropa> = todosOsJogos().filter { it.dia == dia }

    /** Todos os jogos de um clube nesta competição (os já sorteados). */
    fun jogosDoClube(id: String): List<JogoEuropa> = todosOsJogos().filter { it.casa == id || it.fora == id }.sortedBy { it.data }

    /** Próxima data com jogos por fazer (null se acabou). */
    fun proximaData(): LocalDate? = todosOsJogos().filter { !it.jogado }.minOfOrNull { it.dia }

    /** Clube ainda em prova (na fase de liga, todos; depois, só quem não foi eliminado). */
    fun emProva(id: String): Boolean {
        if (id !in clubes) return false
        if (campeao.isNotEmpty()) return id == campeao
        val ultima = ronda.lastOrNull() ?: return true
        return if (ultima.terminada) ultima.vencedor == id else ultima.a == id || ultima.b == id
    }

    /**
     * Joga todos os jogos por fazer até ao dia "ate" (inclusive), por ordem de data.
     * Aplica o MotorJogo (golos, cartões, lesões) e paga bilheteira e prémios.
     * "manter" são ids de clubes cujos relatórios completos se guardam. Devolve os jogos feitos.
     */
    /** Jogo do utilizador jogado ao vivo (Fase 11): usado no lugar da simulação quando chega o jogo entre estas duas equipas. */
    internal var relatorioAoVivo: RelatorioJogo? = null

    fun jogarAte(
        ate: LocalDate, bd: BaseDados, rng: Random, manter: Set<String> = emptySet(),
        aoJogar: ((JogoEuropa, RelatorioJogo) -> Unit)? = null,
    ): List<JogoEuropa> {
        val feitos = mutableListOf<JogoEuropa>()
        while (true) {
            val dia = proximaData() ?: break
            if (dia > ate) break
            for (jogo in todosOsJogos().filter { !it.jogado && it.dia == dia }) {
                jogar(jogo, bd, rng, jogo.casa in manter || jogo.fora in manter, aoJogar)
                feitos += jogo
            }
            avancar(bd, rng)
        }
        return feitos
    }

    /** Joga a competição toda (útil nos testes e para simular quando o utilizador não participa). */
    fun jogarTudo(bd: BaseDados, rng: Random, manter: Set<String> = emptySet()): List<JogoEuropa> = jogarAte(LocalDate.MAX, bd, rng, manter)

    private fun pagar(e: Equipa, valor: Long) {
        if (valor <= 0) return
        e.contas.premios += valor
        e.orcamento += valor
        premios.merge(e.id, valor, Long::plus)
    }

    /**
     * Joga um jogo com o motor detalhado. Nas eliminatórias, o último jogo é decisivo: se o agregado estiver
     * empatado há prolongamento (dentro do relatório) e, se continuar, penáltis (os mesmos do resto do jogo).
     * Todos os jogos europeus contam para as estatísticas "de taça" (a tabela de marcadores da liga fica intacta).
     */
    private fun jogar(jogo: JogoEuropa, bd: BaseDados, rng: Random, guardar: Boolean, aoJogar: ((JogoEuropa, RelatorioJogo) -> Unit)?) {
        val casa = bd.equipas.getValue(jogo.casa)
        val fora = bd.equipas.getValue(jogo.fora)
        val tie = if (jogo.fase == FasesEuropa.LIGA) null else ronda.first { t -> t.jogos.any { it === jogo } }
        val decisivo = tie != null && tie.jogos.last() === jogo
        val antesCasa = tie?.golos(jogo.casa) ?: 0
        val antesFora = tie?.golos(jogo.fora) ?: 0
        val vivo = relatorioAoVivo?.takeIf { it.casa.equipa == jogo.casa && it.fora.equipa == jogo.fora }
        if (vivo != null) relatorioAoVivo = null
        val rel = vivo ?: MotorJogo.simular(casa, fora, rng, jogo.neutro, if (decisivo) { gc, gf -> gc + antesCasa == gf + antesFora } else null)
        MotorJogo.aplicar(rel, taca = true)
        aoJogar?.invoke(jogo, rel)
        jogo.golosCasa = rel.golosCasa
        jogo.golosFora = rel.golosFora
        jogo.prolongamento = rel.prolongamento
        jogo.jogado = true
        if (guardar) jogo.relatorio = rel
        if (tie != null && decisivo && tie.golos(tie.a) == tie.golos(tie.b)) {
            val (pc, pf) = Penaltis.disputar(casa, fora, rng)
            jogo.penaltisCasa = pc
            jogo.penaltisFora = pf
        }
        // Bilheteira: o anfitrião fica com tudo; na final cada finalista fica com metade.
        if (jogo.neutro) {
            val metade = Financas.bilheteira(casa) / 2
            for (e in listOf(casa, fora)) { e.contas.bilheteira += metade; e.orcamento += metade }
        } else {
            val b = Financas.bilheteira(casa)
            casa.contas.bilheteira += b; casa.orcamento += b
        }
        if (tie == null) {
            // A participação paga-se no primeiro jogo de cada clube.
            for (e in listOf(casa, fora)) if (tabela.linhas.getValue(e.id).j == 0) pagar(e, tabelaPremios.participacao)
            tabela.registar(jogo.casa, jogo.fora, jogo.golosCasa, jogo.golosFora)
            when {
                jogo.golosCasa > jogo.golosFora -> pagar(casa, tabelaPremios.vitoria)
                jogo.golosCasa < jogo.golosFora -> pagar(fora, tabelaPremios.vitoria)
                else -> { pagar(casa, tabelaPremios.empate); pagar(fora, tabelaPremios.empate) }
            }
        } else if (tie.jogos.all { it.jogado }) {
            tie.vencedor = when {
                tie.golos(tie.a) > tie.golos(tie.b) -> tie.a
                tie.golos(tie.b) > tie.golos(tie.a) -> tie.b
                else -> if (jogo.penaltisCasa > jogo.penaltisFora) jogo.casa else jogo.fora
            }
        }
    }

    /** Sorteia a fase seguinte quando a anterior acaba. */
    private fun avancar(bd: BaseDados, rng: Random) {
        if (campeao.isNotEmpty()) return
        val ultima = ronda.lastOrNull()
        if (ultima == null) {
            if (jogosLiga.any { !it.jogado }) return
            val ordemLiga = tabela.ordenada().map { it.id }
            // Play-off: 9–16 (cabeças de série) contra 17–24.
            val sorteio = ordemLiga.drop(16).shuffled(rng)
            criarFase(FasesEuropa.PLAYOFF, ordemLiga.subList(8, 16).zip(sorteio), datas.playoff, bd)
            return
        }
        val atual = ronda.filter { it.fase == ultima.fase }
        if (atual.any { !it.terminada }) return
        val vencedores = atual.map { it.vencedor }
        when (ultima.fase) {
            FasesEuropa.PLAYOFF -> {
                val directos = tabela.ordenada().take(8).map { it.id }
                criarFase(FasesEuropa.OITAVOS, directos.zip(vencedores.shuffled(rng)), datas.oitavos, bd)
            }
            FasesEuropa.OITAVOS -> sortear(FasesEuropa.QUARTOS, vencedores, datas.quartos, bd, rng)
            FasesEuropa.QUARTOS -> sortear(FasesEuropa.MEIAS, vencedores, datas.meias, bd, rng)
            FasesEuropa.MEIAS -> {
                val duas = vencedores.shuffled(rng)
                criarFase(FasesEuropa.FINAL, listOf(duas[0] to duas[1]), listOf(datas.final), bd)
            }
            FasesEuropa.FINAL -> {
                campeao = atual.single().vencedor
                val e = bd.equipas.getValue(campeao)
                pagar(e, tabelaPremios.campeao)
            }
        }
    }

    private fun sortear(fase: String, vencedores: List<String>, dias: List<LocalDate>, bd: BaseDados, rng: Random) {
        // Os de melhor reputação são cabeças de série.
        val ordem = vencedores.sortedByDescending { bd.equipas.getValue(it).reputacao }
        val seeds = ordem.take(ordem.size / 2)
        val outros = ordem.drop(ordem.size / 2).shuffled(rng)
        criarFase(fase, seeds.zip(outros), dias, bd)
    }

    /** Cria as eliminatórias de uma fase (a primeira mão é em casa do "b") e paga a chegada à fase. */
    private fun criarFase(fase: String, pares: List<Pair<String, String>>, dias: List<LocalDate>, bd: BaseDados) {
        for ((a, b) in pares) {
            val jogos = if (fase == FasesEuropa.FINAL) {
                listOf(JogoEuropa(fase, 1, dias[0].toString(), a, b, neutro = true))
            } else {
                listOf(
                    JogoEuropa(fase, 1, dias[0].toString(), b, a),
                    JogoEuropa(fase, 2, dias[1].toString(), a, b),
                )
            }
            ronda += Eliminatoria(fase, a, b, jogos)
            for (id in listOf(a, b)) pagar(bd.equipas.getValue(id), tabelaPremios.chegarA(fase))
        }
    }

    /** Cópia para gravar. */
    fun dados(): DadosEuropa = DadosEuropa(tipo.name, ano, clubes, jogosLiga, ronda.toList(), campeao, premios.toMap())

    companion object {
        /** Sorteia a fase de liga: 6 jogos por clube (3 em casa, 3 fora), sem repetir adversários e evitando o mesmo país. */
        fun criar(tipo: TipoEuropa, bd: BaseDados, ano: Int, clubes: List<String>, rng: Random): EstadoEuropa {
            require(clubes.size == 24 && clubes.toSet().size == 24) { "São precisos 24 clubes diferentes" }
            val datas = CalendarioEuropa.datas(ano, tipo)
            val jornadas = sortearJornadas(clubes.map { bd.equipas.getValue(it).pais }, rng)
            val jogos = jornadas.flatMapIndexed { i, jogosDia ->
                jogosDia.map { (c, f) -> JogoEuropa(FasesEuropa.LIGA, i + 1, datas.liga[i].toString(), clubes[c], clubes[f]) }
            }
            return EstadoEuropa(tipo, ano, clubes, jogos, mutableListOf())
        }

        /** Reconstrói o estado a partir de uma cópia gravada. */
        fun restaurar(d: DadosEuropa, @Suppress("UNUSED_PARAMETER") bd: BaseDados): EstadoEuropa {
            val estado = EstadoEuropa(TipoEuropa.valueOf(d.tipo), d.ano, d.clubes, d.jogosLiga, d.eliminatorias.toMutableList())
            for (j in d.jogosLiga.filter { it.jogado }) estado.tabela.registar(j.casa, j.fora, j.golosCasa, j.golosFora)
            estado.campeao = d.campeao
            estado.premios.putAll(d.premios)
            return estado
        }

        /** Seis jornadas, cada uma com 12 jogos (pares de índices casa/fora). */
        private fun sortearJornadas(paises: List<String>, rng: Random): List<List<Pair<Int, Int>>> {
            val n = paises.size
            for (tentativa in 0 until 400) {
                val evitarPais = tentativa < 200
                val usados = HashSet<Int>()
                val dias = mutableListOf<List<Pair<Int, Int>>>()
                var bem = true
                for (d in 0 until 6) {
                    val dia = emparelhar(n, paises, evitarPais, usados, rng)
                    if (dia == null) { bem = false; break }
                    for ((a, b) in dia) usados += minOf(a, b) * n + maxOf(a, b)
                    dias += dia
                }
                if (bem) return orientar(n, dias)
            }
            error("Não foi possível sortear a fase de liga")
        }

        /** Emparelha os 24 clubes num dia (retrocedendo se preciso). */
        private fun emparelhar(n: Int, paises: List<String>, evitarPais: Boolean, usados: Set<Int>, rng: Random): List<Pair<Int, Int>>? {
            val livre = BooleanArray(n) { true }
            val par = mutableListOf<Pair<Int, Int>>()
            var passos = 0
            fun resolver(): Boolean {
                if (++passos > 4000) return false
                val a = livre.indexOfFirst { it }
                if (a < 0) return true
                livre[a] = false
                for (b in (a + 1 until n).filter { livre[it] }.shuffled(rng)) {
                    if (a * n + b in usados) continue
                    if (evitarPais && paises[a] == paises[b]) continue
                    livre[b] = false
                    par += a to b
                    if (resolver()) return true
                    par.removeAt(par.lastIndex)
                    livre[b] = true
                }
                livre[a] = true
                return false
            }
            return if (resolver()) par.toList() else null
        }

        /**
         * Decide quem joga em casa: o grafo é regular de grau 6, por isso percorrem-se
         * circuitos fechados e orienta-se cada jogo no sentido do percurso (3 em casa, 3 fora).
         */
        private fun orientar(n: Int, dias: List<List<Pair<Int, Int>>>): List<List<Pair<Int, Int>>> {
            val arestas = dias.flatMap { it }
            val adj = Array(n) { mutableListOf<Int>() }
            arestas.forEachIndexed { i, (a, b) -> adj[a] += i; adj[b] += i }
            val usada = BooleanArray(arestas.size)
            val orientada = arrayOfNulls<Pair<Int, Int>>(arestas.size)
            for (inicio in 0 until n) {
                while (true) {
                    var v = inicio
                    var andou = false
                    while (true) {
                        val i = adj[v].firstOrNull { !usada[it] } ?: break
                        usada[i] = true
                        val (a, b) = arestas[i]
                        val w = if (a == v) b else a
                        orientada[i] = v to w   // v joga em casa
                        v = w
                        andou = true
                    }
                    if (!andou) break
                }
            }
            var k = 0
            return dias.map { dia -> dia.map { orientada[k++]!! } }
        }
    }
}

/** Atalho: as duas competições da época, geridas em conjunto. */
class EuropaDaEpoca(val campeoes: EstadoEuropa, val europa: EstadoEuropa) {
    fun estado(tipo: TipoEuropa) = if (tipo == TipoEuropa.CAMPEOES) campeoes else europa

    val terminou get() = campeoes.terminou && europa.terminou

    fun proximaData(): LocalDate? = listOfNotNull(campeoes.proximaData(), europa.proximaData()).minOrNull()

    fun jogarAte(
        ate: LocalDate, bd: BaseDados, rng: Random, manter: Set<String> = emptySet(),
        aoJogar: ((JogoEuropa, RelatorioJogo) -> Unit)? = null,
    ): List<JogoEuropa> = campeoes.jogarAte(ate, bd, rng, manter, aoJogar) + europa.jogarAte(ate, bd, rng, manter, aoJogar)

    fun jogosNoDia(dia: LocalDate): List<JogoEuropa> = campeoes.jogosNoDia(dia) + europa.jogosNoDia(dia)

    fun dados(): List<DadosEuropa> = listOf(campeoes.dados(), europa.dados())

    companion object {
        fun criar(bd: BaseDados, ano: Int, tabelas: Map<String, List<String>>, rng: Random): EuropaDaEpoca {
            val q = QualificacaoEuropa.qualificar(bd, tabelas)
            return EuropaDaEpoca(
                EstadoEuropa.criar(TipoEuropa.CAMPEOES, bd, ano, q.campeoes, rng),
                EstadoEuropa.criar(TipoEuropa.EUROPA, bd, ano, q.europa, rng),
            )
        }

        fun restaurar(dados: List<DadosEuropa>, bd: BaseDados): EuropaDaEpoca {
            val porTipo = dados.associateBy { it.tipo }
            return EuropaDaEpoca(
                EstadoEuropa.restaurar(porTipo.getValue(TipoEuropa.CAMPEOES.name), bd),
                EstadoEuropa.restaurar(porTipo.getValue(TipoEuropa.EUROPA.name), bd),
            )
        }
    }
}
