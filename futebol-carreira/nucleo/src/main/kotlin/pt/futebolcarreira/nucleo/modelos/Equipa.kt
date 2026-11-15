package pt.futebolcarreira.nucleo.modelos

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Um lugar do onze inicial: quem joga lá e que nota tem nesse lugar. */
class LugarOnze(val lugar: String, val jogador: Jogador?, val nota: Int)

/** Força de uma equipa (escala 1 a 99). */
class Forca(val ataque: Double, val defesa: Double, val geral: Double)

/** Um clube: dados gerais e plantel. */
@Serializable
class Equipa(
    var id: String = "",
    var nome: String = "",
    var abreviatura: String = "",
    var pais: String = "",
    /** Id da liga onde joga esta época. */
    var liga: String = "",
    var cidade: String = "",
    var estadio: String = "",
    var capacidade: Int = 5000,
    /** 1 a 99. */
    var reputacao: Int = 50,
    var orcamento: Long = 1_000_000,
    var cores: List<String> = listOf("#ffffff", "#000000"),
) {
    @Transient
    val plantel: MutableList<Jogador> = mutableListOf()

    /** Campeonatos ganhos durante o jogo. */
    @Transient
    var titulos: Int = 0

    @Transient
    private var forcaGuardada: Forca? = null

    /** Deve ser chamado sempre que o plantel ou os atributos mudam. */
    fun invalidarForca() {
        forcaGuardada = null
    }

    /** Escolhe os 11 melhores jogadores para a formação (4-3-3). */
    fun melhorOnze(): List<LugarOnze> {
        val usados = HashSet<Int>()
        return FORMACAO.map { lugar ->
            val melhor = plantel.filter { it.id !in usados }.maxByOrNull { notaNoLugar(it, lugar) }
            if (melhor == null) {
                // Plantel com menos de 11 jogadores: o lugar fica muito fraco.
                LugarOnze(lugar, null, 20)
            } else {
                usados += melhor.id
                LugarOnze(lugar, melhor, notaNoLugar(melhor, lugar))
            }
        }
    }

    /** Nota de um jogador num certo lugar (penaliza quem joga fora de posição). */
    private fun notaNoLugar(j: Jogador, lugar: String): Int = when {
        j.posicao == lugar -> j.geral()
        lugar == "GR" || j.posicao == "GR" -> maxOf(1, j.geral() - 40)
        j.posicao in POSICOES_PARECIDAS.getValue(lugar) -> maxOf(1, j.geral() - 5)
        else -> maxOf(1, j.geral() - 12)
    }

    /** Força da equipa, calculada a partir do melhor onze. */
    fun forca(): Forca {
        forcaGuardada?.let { return it }
        var somaAtaque = 0.0
        var pesoAtaque = 0.0
        var somaDefesa = 0.0
        var pesoDefesa = 0.0
        var soma = 0.0
        for (entrada in melhorOnze()) {
            val pa = PESO_ATAQUE.getValue(entrada.lugar)
            val pd = PESO_DEFESA.getValue(entrada.lugar)
            somaAtaque += entrada.nota * pa
            pesoAtaque += pa
            somaDefesa += entrada.nota * pd
            pesoDefesa += pd
            soma += entrada.nota
        }
        return Forca(somaAtaque / pesoAtaque, somaDefesa / pesoDefesa, soma / FORMACAO.size).also { forcaGuardada = it }
    }

    companion object {
        /** Tática base usada para escolher o melhor onze (4-3-3). */
        val FORMACAO = listOf("GR", "DD", "DC", "DC", "DE", "MDC", "MC", "MC", "ED", "EE", "PL")

        /** Posições parecidas: um jogador pode tapar um buraco com pequena penalização. */
        val POSICOES_PARECIDAS = mapOf(
            "DD" to listOf("DE", "DC"), "DE" to listOf("DD", "DC"), "DC" to listOf("MDC", "DD", "DE"),
            "MDC" to listOf("MC", "DC"), "MC" to listOf("MOC", "MDC"), "MOC" to listOf("MC", "ED", "EE"),
            "ED" to listOf("EE", "MOC"), "EE" to listOf("ED", "MOC"), "PL" to listOf("ED", "EE", "MOC"),
            "GR" to emptyList(),
        )

        /** Quanto cada lugar do onze conta para o ataque e para a defesa. */
        val PESO_ATAQUE = mapOf("GR" to 0.0, "DD" to 0.5, "DE" to 0.5, "DC" to 0.0, "MDC" to 0.5, "MC" to 1.5, "ED" to 2.0, "EE" to 2.0, "PL" to 3.0)
        val PESO_DEFESA = mapOf("GR" to 3.0, "DD" to 1.5, "DE" to 1.5, "DC" to 2.0, "MDC" to 1.5, "MC" to 0.5, "ED" to 0.0, "EE" to 0.0, "PL" to 0.0)
    }
}
