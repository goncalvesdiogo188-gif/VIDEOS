package pt.futebolcarreira.nucleo.modelos

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Um jogador de futebol: identidade, posição e atributos.
 * Os campos têm os mesmos nomes que no ficheiro JSON.
 */
@Serializable
class Jogador(
    var id: Int = 0,
    var nome: String = "Sem Nome",
    var nacionalidade: String = "",
    var idade: Int = 20,
    var posicao: String = "MC",
    /** "direito", "esquerdo" ou "ambos". */
    var pe: String = "direito",
    /** Id da equipa ("" = sem clube). */
    var equipa: String = "",
    /** Geral máximo que pode atingir. */
    var potencial: Int = 0,
    /** Nome do atributo -> valor (1 a 99). */
    val atributos: MutableMap<String, Int> = mutableMapOf(),
) {
    /** Valor global do jogador na sua posição (1 a 99). */
    fun geral(): Int {
        val pesos = PESOS[posicao] ?: PESOS.getValue("MC")
        val soma = pesos.entries.sumOf { (atributo, peso) -> (atributos[atributo] ?: 1) * peso }
        return soma.roundToInt().coerceIn(1, 99)
    }

    /** Soma "delta" aos atributos que contam para a posição (usado na evolução). */
    fun ajustarAtributos(delta: Int) {
        val pesos = PESOS[posicao] ?: PESOS.getValue("MC")
        for (atributo in pesos.keys) {
            atributos[atributo] = ((atributos[atributo] ?: 1) + delta).coerceIn(1, 99)
        }
    }

    /** Valor de mercado estimado, em euros (provisório até à fase das transferências). */
    fun valorMercado(): Int {
        val base = 1.18.pow(geral() - 50) * 250_000.0
        val fatorIdade = when {
            idade <= 23 -> 1.0 + (potencial - geral()) * 0.04
            idade >= 30 -> maxOf(0.15, 1.0 - (idade - 29) * 0.15)
            else -> 1.0
        }
        return (base * fatorIdade / 10_000.0).roundToInt() * 10_000
    }

    /** Corrige valores em falta ou inválidos depois de ler do JSON. */
    fun normalizar() {
        if (posicao !in POSICOES) posicao = "MC"
        for (atributo in ATRIBUTOS) {
            atributos[atributo] = (atributos[atributo] ?: 30).coerceIn(1, 99)
        }
        potencial = (if (potencial <= 0) geral() else potencial).coerceIn(geral(), 99)
    }

    companion object {
        /**
         * Posições (siglas portuguesas): GR guarda-redes, DD/DE defesas laterais, DC defesa central,
         * MDC médio defensivo, MC médio centro, MOC médio ofensivo, ED/EE extremos, PL ponta de lança.
         */
        val POSICOES = listOf("GR", "DD", "DC", "DE", "MDC", "MC", "MOC", "ED", "EE", "PL")

        /** Atributos de cada jogador (valores de 1 a 99). */
        val ATRIBUTOS = listOf("ritmo", "remate", "passe", "drible", "defesa", "fisico", "guarda_redes")

        private val LATERAL = mapOf("defesa" to 0.35, "ritmo" to 0.25, "passe" to 0.15, "fisico" to 0.15, "drible" to 0.10)
        private val EXTREMO = mapOf("ritmo" to 0.30, "drible" to 0.30, "remate" to 0.20, "passe" to 0.15, "fisico" to 0.05)

        /** Peso de cada atributo no "geral", conforme a posição (cada linha soma 1). */
        val PESOS: Map<String, Map<String, Double>> = mapOf(
            "GR" to mapOf("guarda_redes" to 0.80, "fisico" to 0.12, "passe" to 0.08),
            "DC" to mapOf("defesa" to 0.55, "fisico" to 0.25, "ritmo" to 0.10, "passe" to 0.10),
            "DD" to LATERAL,
            "DE" to LATERAL,
            "MDC" to mapOf("defesa" to 0.35, "passe" to 0.30, "fisico" to 0.20, "drible" to 0.10, "ritmo" to 0.05),
            "MC" to mapOf("passe" to 0.40, "drible" to 0.20, "defesa" to 0.15, "fisico" to 0.10, "remate" to 0.10, "ritmo" to 0.05),
            "MOC" to mapOf("passe" to 0.30, "drible" to 0.30, "remate" to 0.20, "ritmo" to 0.15, "fisico" to 0.05),
            "ED" to EXTREMO,
            "EE" to EXTREMO,
            "PL" to mapOf("remate" to 0.45, "ritmo" to 0.20, "drible" to 0.15, "fisico" to 0.15, "passe" to 0.05),
        )
    }
}
