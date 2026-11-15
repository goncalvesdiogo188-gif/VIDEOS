package pt.futebolcarreira.nucleo.simulacao

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Um jogo marcado: ids da equipa da casa e da equipa visitante. */
@Serializable
data class Jogo(val casa: String, val fora: String)

/**
 * Cria o calendário de uma liga (todos contra todos).
 *
 * Usa o "método do círculo": uma equipa fica fixa e as outras rodam à volta,
 * de forma que em cada jornada todas as equipas jogam exatamente uma vez.
 */
object GeradorCalendario {
    /**
     * Devolve a lista de jornadas; cada jornada é uma lista de jogos.
     * Com 2 voltas, a segunda repete a primeira com casa/fora trocados.
     */
    fun gerar(idsEquipas: List<String>, voltas: Int, rng: Random): List<List<Jogo>> {
        // Baralha para o calendário ser diferente todas as épocas.
        val lista: MutableList<String?> = idsEquipas.shuffled(rng).toMutableList()
        // Número ímpar de equipas: junta-se uma "folga" (quem calha com ela descansa).
        if (lista.size % 2 == 1) lista += null
        val n = lista.size
        if (n < 2) return emptyList()

        val primeiraVolta = mutableListOf<List<Jogo>>()
        for (ronda in 0 until n - 1) {
            val jornada = mutableListOf<Jogo>()
            for (i in 0 until n / 2) {
                val a = lista[i] ?: continue
                val b = lista[n - 1 - i] ?: continue
                // Alterna quem joga em casa para ficar equilibrado.
                val troca = if (i == 0) ronda % 2 == 1 else i % 2 == 1
                jornada += if (troca) Jogo(b, a) else Jogo(a, b)
            }
            primeiraVolta += jornada
            // Roda todas as equipas menos a primeira.
            lista.add(1, lista.removeAt(n - 1))
        }

        val calendario = mutableListOf<List<Jogo>>()
        for (volta in 0 until voltas) {
            for (jornada in primeiraVolta) {
                calendario += if (volta % 2 == 1) jornada.map { Jogo(it.fora, it.casa) } else jornada
            }
        }
        return calendario
    }
}
