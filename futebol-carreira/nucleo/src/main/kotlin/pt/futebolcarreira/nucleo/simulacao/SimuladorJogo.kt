package pt.futebolcarreira.nucleo.simulacao

import pt.futebolcarreira.nucleo.modelos.Equipa
import kotlin.math.exp
import kotlin.random.Random

/**
 * Resultado final de um jogo ("relatorio" só existe nos jogos simulados em detalhe).
 * Nas eliminatórias, "golosCasa"/"golosFora" incluem o prolongamento (a.p.); os penáltis (g.p.)
 * ficam à parte, em "penaltisCasa"/"penaltisFora".
 */
class Resultado(
    val casa: String, val fora: String, val golosCasa: Int, val golosFora: Int, val relatorio: RelatorioJogo? = null,
    val prolongamento: Boolean = false, val penaltisCasa: Int = 0, val penaltisFora: Int = 0,
) {
    /** true se o jogo foi decidido nos penáltis. */
    val penaltis: Boolean get() = penaltisCasa != 0 || penaltisFora != 0
}

/**
 * Simulação rápida de um jogo (só o resultado), para quando o detalhe não interessa.
 * A simulação detalhada (marcadores, cartões, lesões...) está em MotorJogo.
 */
object SimuladorJogo {
    /** Média de golos da equipa da casa entre equipas iguais. */
    const val GOLOS_BASE_CASA = 1.50
    /** Média de golos da equipa visitante. */
    const val GOLOS_BASE_FORA = 1.15
    /** Quanto maior, menos pesa a diferença de qualidade. */
    const val ESCALA = 20.0
    const val MAX_GOLOS = 9

    fun simular(casa: Equipa, fora: Equipa, rng: Random): Resultado {
        val fc = casa.forca()
        val ff = fora.forca()
        // Golos esperados: o ataque de uma equipa contra a defesa da outra.
        val esperadosCasa = GOLOS_BASE_CASA * exp((fc.ataque - ff.defesa) / ESCALA)
        val esperadosFora = GOLOS_BASE_FORA * exp((ff.ataque - fc.defesa) / ESCALA)
        return Resultado(casa.id, fora.id, rng.poisson(esperadosCasa, MAX_GOLOS), rng.poisson(esperadosFora, MAX_GOLOS))
    }
}
