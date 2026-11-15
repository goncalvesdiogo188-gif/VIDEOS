package pt.futebolcarreira.nucleo.simulacao

import pt.futebolcarreira.nucleo.modelos.Equipa
import java.time.LocalDate
import kotlin.random.Random

/** Desempate por penáltis (g.p.). Nunca acaba empatado. */
object Penaltis {
    /** Probabilidade de golo de um penálti, entre equipas iguais. */
    const val PROB_BASE = 0.76

    /** Sorteia uma marcação de penáltis: série de cinco e, se preciso, mortes súbitas. Devolve (casa, fora). */
    fun sortear(probCasa: Double, probFora: Double, rng: Random): Pair<Int, Int> {
        var c = 0
        var f = 0
        var chutesC = 0
        var chutesF = 0
        for (i in 1..5) {
            chutesC++
            if (rng.nextDouble() < probCasa) c++
            if (c > f + (5 - chutesF) || f > c + (5 - chutesC)) break   // já não há como virar
            chutesF++
            if (rng.nextDouble() < probFora) f++
            if (c > f + (5 - chutesF) || f > c + (5 - chutesC)) break
        }
        var extra = 0
        while (c == f && extra++ < 30) {
            if (rng.nextDouble() < probCasa) c++
            if (rng.nextDouble() < probFora) f++
        }
        // Caso quase impossível de empate eterno: decide a moeda ao ar.
        if (c == f) if (rng.nextBoolean()) c++ else f++
        return c to f
    }

    /** Como "sortear", mas as equipas melhores marcam um pouco mais. */
    fun disputar(casa: Equipa, fora: Equipa, rng: Random): Pair<Int, Int> {
        val dif = (casa.forca().geral - fora.forca().geral) * 0.004
        return sortear((PROB_BASE + dif).coerceIn(0.66, 0.86), (PROB_BASE - dif).coerceIn(0.66, 0.86), rng)
    }
}

/** Uma ronda de uma eliminatória: o nome, o dia de cada mão e se é em campo neutro. */
class DefRonda(val nome: String, val datas: List<LocalDate>, val neutro: Boolean = false)

/**
 * Um confronto entre duas equipas: jogo único (uma data) ou ida e volta (duas datas, golos fora não contam).
 * "a" joga em casa o jogo único e a primeira mão. Em campo neutro, "a" e "b" são só os nomes dos lados.
 * Se acabar empatado (no agregado) há prolongamento e, se continuar, penáltis na última mão.
 */
class Confronto(val a: String, val b: String, val datas: List<LocalDate>, val neutro: Boolean = false) {
    val jogos = mutableListOf<Resultado>()

    /** null enquanto não está decidido. */
    var vencedor: String? = null
        internal set

    val idaEVolta get() = datas.size > 1
    fun casaDoJogo(i: Int) = if (i % 2 == 0) a else b
    fun foraDoJogo(i: Int) = if (i % 2 == 0) b else a
    fun envolve(equipa: String) = equipa == a || equipa == b
    fun adversarioDe(equipa: String) = if (equipa == a) b else a

    /** Golos de uma equipa em todos os jogos já jogados (no prolongamento também; penáltis não). */
    fun golos(equipa: String): Int =
        jogos.sumOf { if (it.casa == equipa) it.golosCasa else if (it.fora == equipa) it.golosFora else 0 }

    val perdedor: String? get() = vencedor?.let { adversarioDe(it) }

    /** Dia do próximo jogo (null se já está decidido). */
    val proximaData: LocalDate? get() = if (vencedor != null) null else datas.getOrNull(jogos.size)

    val prolongamento get() = jogos.any { it.prolongamento }
    val penaltis get() = jogos.any { it.penaltis }

    /** Penáltis de cada lado (a, b), ou null se não houve. */
    val resultadoPenaltis: Pair<Int, Int>?
        get() = jogos.lastOrNull()?.takeIf { it.penaltis }?.let { if (it.casa == a) it.penaltisCasa to it.penaltisFora else it.penaltisFora to it.penaltisCasa }
}

/** Uma ronda já sorteada: os confrontos e as equipas que passaram sem jogar (isentas). */
class Ronda(val def: DefRonda, val confrontos: List<Confronto>, val isentos: List<String>) {
    val nome get() = def.nome
    val concluida get() = confrontos.all { it.vencedor != null }
}

/** Joga um jogo de uma eliminatória (quem chama decide como: motor detalhado, resultado rápido...). */
fun interface SimuladorEliminatoria {
    /**
     * "golosAntesCasa"/"golosAntesFora" são os golos que essas equipas já têm nesta eliminatória (primeira mão).
     * "decisivo" = a eliminatória tem de ficar decidida neste jogo: se estiver empatada no agregado há
     * prolongamento e, se continuar, penáltis (o Resultado devolvido traz-os preenchidos).
     */
    fun jogar(casa: String, fora: String, neutro: Boolean, golosAntesCasa: Int, golosAntesFora: Int, decisivo: Boolean): Resultado
}

/**
 * Uma competição a eliminar (taça nacional, supertaça, e mais tarde taças europeias e torneios de seleções).
 *
 * As rondas são sorteadas à medida que a anterior acaba. Se o número de equipas não for potência de 2,
 * as de maior reputação passam diretamente à segunda ronda ("isentas"). "categoria" é o escalão da equipa
 * (1 = primeira divisão): em jogo único, a equipa de escalão mais baixo joga em casa; no mesmo escalão sorteia-se.
 *
 * Só conhece identificadores de equipas: quem joga os jogos é o "SimuladorEliminatoria".
 */
class EstadoEliminatoria(
    val id: String,
    val nome: String,
    val participantes: List<String>,
    val defs: List<DefRonda>,
    private val reputacao: (String) -> Int,
    private val categoria: (String) -> Int,
    private val rng: Random,
    iniciar: Boolean = true,
) {
    val rondas = mutableListOf<Ronda>()

    var campeao: String? = null
        internal set
    var finalista: String? = null
        internal set

    /** true quando já há campeão (ou quando nem sequer havia equipas para jogar). */
    var concluida: Boolean = false
        internal set

    init {
        if (iniciar) {
            if (participantes.size < 2) {
                campeao = participantes.firstOrNull()
                concluida = true
            } else {
                require(defs.size >= numeroDeRondas(participantes.size)) { "$id: faltam datas de rondas" }
                sortear(participantes, true)
            }
        }
    }

    /** Dia do próximo jogo (null se a competição acabou). */
    val proximaData: LocalDate?
        get() = if (concluida) null else rondas.lastOrNull()?.confrontos?.mapNotNull { it.proximaData }?.minOrNull()

    /** Nome da ronda em curso (a última sorteada). */
    val rondaAtual: Ronda? get() = rondas.lastOrNull()

    /** Índice (0 = primeira ronda) da última ronda em que a equipa esteve (isenta conta), ou -1. */
    fun ultimaRonda(equipa: String): Int =
        rondas.indexOfLast { r -> equipa in r.isentos || r.confrontos.any { it.envolve(equipa) } }

    /** Confronto de uma equipa numa ronda (null se não jogou, por exemplo se foi isenta). */
    fun confrontoDe(equipa: String, ronda: Int): Confronto? = rondas.getOrNull(ronda)?.confrontos?.firstOrNull { it.envolve(equipa) }

    /**
     * Joga os jogos marcados até ao dia "dia" (inclusive), por ordem. "aoDecidir" é chamado quando
     * um confronto fica decidido, com o confronto e o índice da ronda (0 = primeira).
     */
    fun jogarDia(dia: LocalDate, simulador: SimuladorEliminatoria, aoDecidir: (Confronto, Int) -> Unit = { _, _ -> }) {
        while (!concluida) {
            val ronda = rondas.lastOrNull() ?: return
            val data = proximaData ?: return
            if (data > dia) return
            for (c in ronda.confrontos) {
                if (c.vencedor != null || c.proximaData != data) continue
                jogar(c, simulador)
                if (c.vencedor != null) aoDecidir(c, rondas.size - 1)
            }
            if (ronda.concluida) fecharRonda(ronda)
        }
    }

    private fun jogar(c: Confronto, simulador: SimuladorEliminatoria) {
        val i = c.jogos.size
        val casa = c.casaDoJogo(i)
        val fora = c.foraDoJogo(i)
        val ultimo = i == c.datas.size - 1
        var r = simulador.jogar(casa, fora, c.neutro, c.golos(casa), c.golos(fora), ultimo)
        if (ultimo) {
            // Rede de segurança: se o simulador deixou empatado, decidem os penáltis.
            if (c.golos(casa) + r.golosCasa == c.golos(fora) + r.golosFora && !r.penaltis) {
                val (pc, pf) = Penaltis.sortear(Penaltis.PROB_BASE, Penaltis.PROB_BASE, rng)
                r = Resultado(r.casa, r.fora, r.golosCasa, r.golosFora, r.relatorio, r.prolongamento, pc, pf)
            }
        }
        c.jogos += r
        if (ultimo) {
            val ga = c.golos(c.a)
            val gb = c.golos(c.b)
            c.vencedor = when {
                ga > gb -> c.a
                gb > ga -> c.b
                else -> if (r.penaltisCasa > r.penaltisFora) r.casa else r.fora
            }
        }
    }

    private fun fecharRonda(ronda: Ronda) {
        val passam = ronda.isentos + ronda.confrontos.map { it.vencedor!! }
        if (passam.size == 1) {
            campeao = passam[0]
            finalista = ronda.confrontos.singleOrNull()?.perdedor
            concluida = true
        } else {
            sortear(passam, false)
        }
    }

    /** Sorteia uma ronda com as equipas dadas. Na primeira, as de maior reputação ficam isentas se for preciso. */
    private fun sortear(equipas: List<String>, primeira: Boolean) {
        val def = defs[rondas.size]
        var jogam = equipas
        var isentos = emptyList<String>()
        if (primeira) {
            val isentas = potenciaDeDois(equipas.size) - equipas.size
            if (isentas > 0) {
                // Baralha primeiro para o desempate entre reputações iguais ser ao acaso.
                val ordem = equipas.shuffled(rng).sortedByDescending { reputacao(it) }
                isentos = ordem.take(isentas)
                jogam = ordem.drop(isentas)
            }
        }
        val confrontos = jogam.shuffled(rng).chunked(2).filter { it.size == 2 }.map { (x, y) ->
            // Jogo único: joga em casa o de escalão mais baixo (número maior); no mesmo escalão, o primeiro sorteado.
            val (a, b) = if (categoria(x) >= categoria(y) || def.neutro) x to y else y to x
            Confronto(a, b, def.datas, def.neutro)
        }
        rondas += Ronda(def, confrontos, isentos)
    }

    /** Repõe o estado de uma gravação (as rondas já sorteadas e quem ganhou). */
    internal fun restaurar(rondasGuardadas: List<Ronda>, campeao: String?, finalista: String?, concluida: Boolean) {
        rondas.clear()
        rondas += rondasGuardadas
        this.campeao = campeao
        this.finalista = finalista
        this.concluida = concluida
    }

    companion object {
        fun potenciaDeDois(n: Int): Int {
            var p = 1
            while (p < n) p *= 2
            return p
        }

        /** Quantas rondas tem uma eliminatória com "n" equipas (2 = só a final). */
        fun numeroDeRondas(n: Int): Int {
            var rondas = 0
            var p = 1
            while (p < n) { p *= 2; rondas++ }
            return rondas
        }

        /** Nomes das rondas: as quatro últimas têm nome próprio, as primeiras são "1.ª eliminatória"... */
        fun nomesDasRondas(total: Int): List<String> = List(total) { i ->
            when (total - 1 - i) {
                0 -> "Final"
                1 -> "Meias-finais"
                2 -> "Quartos de final"
                3 -> "Oitavos de final"
                else -> "${i + 1}.ª eliminatória"
            }
        }
    }
}
