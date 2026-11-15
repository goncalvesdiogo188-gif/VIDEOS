package pt.futebolcarreira.nucleo.simulacao

import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.dados.GeradorJogadores
import kotlin.random.Random

/**
 * Evolução simples dos jogadores no fim da época: envelhecem, os jovens
 * melhoram, os veteranos pioram e alguns reformam-se (entra um jovem).
 * PROVISÓRIO: mais tarde é substituído pelo sistema completo de treino e academia.
 */
object EvolucaoPlantel {
    fun aplicar(bd: BaseDados, gerador: GeradorJogadores, rng: Random, anoNovo: Int = 2027) {
        for (equipa in bd.equipas.values) {
            for (j in equipa.plantel.toList()) {
                j.idade++
                val limiteReforma = if (j.posicao == "GR") 37 else 35
                if (j.idade >= limiteReforma && (j.idade >= limiteReforma + 3 || rng.nextDouble() < 0.5)) {
                    bd.removerJogador(j)
                    bd.adicionarJogador(gerador.criarJovem(equipa, j.posicao).also { it.contratoAte = anoNovo + 3 })
                    continue
                }
                val geral = j.geral()
                when {
                    j.idade <= 23 && geral < j.potencial -> j.ajustarAtributos(minOf(rng.nextInt(0, 5), j.potencial - geral))
                    j.idade <= 28 && geral < j.potencial -> j.ajustarAtributos(rng.nextInt(0, 2))
                    j.idade >= (if (j.posicao == "GR") 33 else 31) -> j.ajustarAtributos(-rng.nextInt(0, 3))
                }
            }
            equipa.invalidarForca()
        }
    }
}
