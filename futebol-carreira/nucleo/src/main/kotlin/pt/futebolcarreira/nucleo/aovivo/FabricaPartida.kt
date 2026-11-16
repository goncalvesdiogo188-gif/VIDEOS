package pt.futebolcarreira.nucleo.aovivo

import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Mentalidade
import pt.futebolcarreira.nucleo.simulacao.EstatisticasEquipa
import pt.futebolcarreira.nucleo.simulacao.LadoRelatorio
import pt.futebolcarreira.nucleo.simulacao.RelatorioJogo
import pt.futebolcarreira.nucleo.simulacao.gauss
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** Cria jogos ao vivo e converte-os no relatório usado pelo resto do jogo. */
object FabricaPartida {
    /** "utilizadorCasa" = o utilizador comanda a equipa da casa (senão a de fora). duracaoReal em segundos (por defeito 8 min).
     * "semUtilizador" = as duas equipas jogam pela IA (para testes e para simular jogos sem comandos). */
    fun criar(
        casa: Equipa, fora: Equipa, utilizadorCasa: Boolean, rng: Random, duracaoReal: Float = 480f, semUtilizador: Boolean = false,
        dificuldade: Dificuldade = Dificuldade.NORMAL,
        /** Se não for null, a competição exige vencedor: empate no fim dos 90' leva a prolongamento e penáltis ao vivo. */
        exigencia: ExigenciaVencedor? = null,
    ): PartidaAoVivo = MotorAoVivo(casa, fora, utilizadorCasa, rng, duracaoReal, semUtilizador, dificuldade, exigencia)

    /** Converte o jogo terminado num relatório igual ao do motor de simulação (golos, marcadores, cartões, notas, lesões). */
    fun relatorio(partida: PartidaAoVivo): RelatorioJogo {
        val m = partida as? MotorAoVivo ?: throw IllegalArgumentException("só há relatório de jogos criados por FabricaPartida")
        check(m.terminou) { "o jogo ainda não terminou" }
        return m.construirRelatorio()
    }
}

/** Cores das camisolas: se as duas equipas se confundem, a de fora joga com as cores trocadas (ou de branco). */
internal object Cores {
    private fun ler(h: String?, padrao: Long): Long {
        val t = h?.trim()?.removePrefix("#") ?: return padrao
        return try { 0xFF000000L or t.toLong(16) } catch (_: NumberFormatException) { padrao }
    }

    private fun distancia(a: Long, b: Long): Int {
        val dr = ((a shr 16) and 0xFF).toInt() - ((b shr 16) and 0xFF).toInt()
        val dg = ((a shr 8) and 0xFF).toInt() - ((b shr 8) and 0xFF).toInt()
        val db = (a and 0xFF).toInt() - (b and 0xFF).toInt()
        return abs(dr) + abs(dg) + abs(db)
    }

    fun escolher(casa: Equipa, fora: Equipa): Pair<Pair<Long, Long>, Pair<Long, Long>> {
        val c = ler(casa.cores.getOrNull(0), 0xFFCC2222L) to ler(casa.cores.getOrNull(1), 0xFFFFFFFFL)
        var f = ler(fora.cores.getOrNull(0), 0xFF2244CCL) to ler(fora.cores.getOrNull(1), 0xFFFFFFFFL)
        if (distancia(c.first, f.first) < 120) {
            val troca = f.second to f.first
            f = if (distancia(c.first, troca.first) >= 120) troca else 0xFFFFFFFFL to 0xFF222222L
        }
        return c to f
    }
}

internal fun MotorAoVivo.construirRelatorio(): RelatorioJogo {
    val total = posseT[0] + posseT[1]
    val posse = if (total <= 0f) 50 else (posseT[0] * 100f / total).roundToInt().coerceIn(10, 90)
    val est = Array(2) { EstatisticasEquipa() }
    for (e in 0..1) {
        est[e].posse = if (e == 0) posse else 100 - posse
        est[e].remates = remates[e]; est[e].rematesBaliza = noAlvo[e]; est[e].cantos = cantos[e]
        est[e].faltas = faltas[e]; est[e].amarelos = cartAm[e]; est[e].vermelhos = cartVm[e]
    }
    darNotas()
    fun lado(e: Int, eq: Equipa) = LadoRelatorio(
        eq.id, eq.formacao, eq.mentalidade ?: Mentalidade.EQUILIBRADA,
        partes.filter { it.eq == e }.map { it.ficha }, est[e], indisponiveis[e],
    )
    val d = reg.desempate
    return RelatorioJogo(
        lado(0, casa), lado(1, fora), golos[0], golos[1], ultimoMinuto, eventos.toList(), reg.prolongamento,
        penaltisCasa = d?.marcados?.get(0) ?: -1, penaltisFora = d?.marcados?.get(1) ?: -1,
    )
}

/** Nota de 1 a 10 a partir do que cada um fez (golos, assistências, passes, desarmes, defesas...), como no motor de simulação. */
private fun MotorAoVivo.darNotas() {
    for (p in partes) {
        val f = p.ficha
        val e = p.eq
        val resultado = when {
            golos[e] > golos[1 - e] -> 0.35
            golos[e] < golos[1 - e] -> -0.25
            else -> 0.0
        }
        var nota = 6.2 + rng.gauss(0.0, 0.4) + resultado
        nota += f.golos * 0.9 + f.assistencias * 0.5 + f.defesas * 0.15
        nota += (p.passesOk * 0.01 - (p.passes - p.passesOk) * 0.02).coerceIn(-0.4, 0.4)
        nota += (p.desarmes * 0.06).coerceAtMost(0.4) + (p.noAlvo * 0.05).coerceAtMost(0.2)
        nota -= p.faltas * 0.08 + f.amarelos * 0.2
        if (f.vermelho) nota -= 1.2
        if (f.lugar in listOf("GR", "DD", "DE", "DC", "MDC")) nota += if (f.golosSofridos == 0) 0.5 else -minOf(0.8, f.golosSofridos * 0.2)
        // p.segundos conta tempo real: converte para minutos de jogo; quem jogou pouco fica perto da nota neutra.
        val minutosJogo = p.segundos * taxa / 60.0
        if (minutosJogo < 20.0) nota = 6.0 + (nota - 6.0) * 0.5
        f.nota = (nota.coerceIn(3.0, 10.0) * 10).roundToInt() / 10.0
    }
}
