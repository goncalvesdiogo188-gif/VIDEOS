package pt.futebolcarreira.nucleo.dados

import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.modelos.ListaNomes
import pt.futebolcarreira.nucleo.simulacao.gauss
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Cria jogadores fictícios (para a base de dados inicial e para os jovens
 * que aparecem todas as épocas).
 */
class GeradorJogadores(private val nomes: Map<String, ListaNomes>, private val rng: Random) {
    private val nomesUsados = HashSet<String>()

    /** Cria o plantel completo de uma equipa. */
    fun criarPlantel(equipa: Equipa): List<Jogador> {
        val plantel = mutableListOf<Jogador>()
        val nivel = nivelDaReputacao(equipa.reputacao)
        for ((posicao, quantidade) in PLANTEL_TIPO) {
            repeat(quantidade) { i ->
                val j = when {
                    i < TITULARES.getValue(posicao) -> criar(equipa.pais, posicao, nivel + rng.gauss(1.0, 2.5), rng.nextInt(22, 33))
                    rng.nextDouble() < 0.5 -> criar(equipa.pais, posicao, nivel - 8.0 + rng.gauss(0.0, 3.0), rng.nextInt(17, 22))
                    else -> criar(equipa.pais, posicao, nivel - 5.0 + rng.gauss(0.0, 3.0), rng.nextInt(22, 35))
                }
                j.equipa = equipa.id
                plantel += j
            }
        }
        return plantel
    }

    /** Cria um jovem para substituir um jogador que se reformou. */
    fun criarJovem(equipa: Equipa, posicao: String): Jogador {
        val nivel = nivelDaReputacao(equipa.reputacao) - 11.0 + rng.gauss(0.0, 4.0)
        return criar(equipa.pais, posicao, nivel, rng.nextInt(17, 20)).also { it.equipa = equipa.id }
    }

    /** Cria um jogador com um certo nível (geral) e idade. */
    fun criar(paisClube: String, posicao: String, nivel: Double, idade: Int): Jogador {
        val j = Jogador(posicao = posicao, idade = idade)
        // A maioria é do país do clube; alguns são estrangeiros.
        j.nacionalidade = if (rng.nextDouble() < 0.22 && nomes.size > 1) nomes.keys.random(rng) else paisClube
        j.nome = criarNome(j.nacionalidade)
        j.pe = escolherPe(posicao)

        val alvo = nivel.roundToInt().coerceIn(25, 95)
        val pesos = Jogador.PESOS.getValue(posicao)
        for (atributo in Jogador.ATRIBUTOS) {
            val valor = when {
                atributo in pesos -> alvo + rng.gauss(0.0, 4.0)
                atributo == "guarda_redes" -> rng.nextInt(5, 16).toDouble()
                else -> alvo - 22.0 + rng.gauss(0.0, 6.0)
            }
            j.atributos[atributo] = valor.roundToInt().coerceIn(1, 99)
        }
        // Acerta os atributos principais para o geral bater com o nível pedido.
        repeat(3) {
            val diferenca = alvo - j.geral()
            if (diferenca != 0) j.ajustarAtributos(diferenca)
        }

        val margem = when {
            idade <= 21 -> rng.nextInt(4, 17)
            idade <= 25 -> rng.nextInt(1, 8)
            idade <= 28 -> rng.nextInt(0, 3)
            else -> 0
        }
        j.potencial = minOf(97, j.geral() + margem)
        return j
    }

    private fun criarNome(pais: String): String {
        val lista = nomes[pais]
        val proprios = lista?.nomes?.takeIf { it.isNotEmpty() } ?: listOf("Jogador")
        val apelidos = lista?.apelidos?.takeIf { it.isNotEmpty() } ?: listOf("Anónimo")
        var nome = ""
        // Tenta evitar nomes repetidos.
        for (tentativa in 0 until 12) {
            nome = "${proprios.random(rng)} ${apelidos.random(rng)}"
            if (nome !in nomesUsados) break
        }
        nomesUsados += nome
        return nome
    }

    private fun escolherPe(posicao: String): String {
        val sorte = rng.nextDouble()
        return if (posicao == "DE" || posicao == "EE") {
            if (sorte < 0.7) "esquerdo" else if (sorte < 0.95) "direito" else "ambos"
        } else {
            if (sorte < 0.75) "direito" else if (sorte < 0.95) "esquerdo" else "ambos"
        }
    }

    companion object {
        /** Plantel-tipo de 24 jogadores: posição -> quantidade. */
        val PLANTEL_TIPO = linkedMapOf("GR" to 3, "DD" to 2, "DC" to 4, "DE" to 2, "MDC" to 2, "MC" to 3, "MOC" to 2, "ED" to 2, "EE" to 2, "PL" to 2)

        /** Quantos de cada posição são titulares (os restantes são suplentes). */
        val TITULARES = mapOf("GR" to 1, "DD" to 1, "DC" to 2, "DE" to 1, "MDC" to 1, "MC" to 2, "MOC" to 1, "ED" to 1, "EE" to 1, "PL" to 1)

        /** Nível médio (geral) esperado para um titular de uma equipa com esta reputação. */
        fun nivelDaReputacao(reputacao: Int): Double = 30.0 + 0.58 * reputacao
    }
}
