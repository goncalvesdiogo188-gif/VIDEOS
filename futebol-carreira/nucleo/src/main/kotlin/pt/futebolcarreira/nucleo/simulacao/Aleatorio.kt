package pt.futebolcarreira.nucleo.simulacao

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** Número ao acaso à volta de uma média (curva "de sino"). */
fun Random.gauss(media: Double, desvio: Double): Double {
    val u1 = 1.0 - nextDouble()
    val u2 = nextDouble()
    return media + desvio * sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
}

/** Sorteia um número de golos à volta de uma média (distribuição de Poisson). */
fun Random.poisson(media: Double, maximo: Int): Int {
    val limite = exp(-media.coerceIn(0.05, 6.0))
    var golos = 0
    var p = nextDouble()
    while (p > limite && golos < maximo) {
        golos++
        p *= nextDouble()
    }
    return golos
}
