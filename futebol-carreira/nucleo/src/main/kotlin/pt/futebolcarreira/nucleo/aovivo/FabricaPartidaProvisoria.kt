package pt.futebolcarreira.nucleo.aovivo

import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.simulacao.RelatorioJogo
import kotlin.random.Random

// SUBSTITUÍDO PELA PARTE A NA JUNÇÃO: apagar este ficheiro e PartidaFalsa.kt; a parte A traz a FabricaPartida verdadeira.
object FabricaPartida {
    fun criar(casa: Equipa, fora: Equipa, utilizadorCasa: Boolean, rng: Random, duracaoReal: Float = 480f): PartidaAoVivo =
        PartidaFalsa(casa, fora, utilizadorCasa, rng, duracaoReal)

    fun relatorio(partida: PartidaAoVivo): RelatorioJogo =
        throw UnsupportedOperationException("Provisório: o relatório vem da parte A")
}
