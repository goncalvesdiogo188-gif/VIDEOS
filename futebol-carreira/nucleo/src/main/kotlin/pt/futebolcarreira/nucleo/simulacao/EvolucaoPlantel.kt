package pt.futebolcarreira.nucleo.simulacao

import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.dados.GeradorJogadores
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Jogador
import kotlin.random.Random

/**
 * O que muda nos plantéis de uma época para a outra: todos envelhecem um ano,
 * alguns veteranos reformam-se (entra um jovem da formação) e os jovens da
 * academia que chegam aos 20 anos sobem ao plantel ou saem do clube.
 * (A evolução dos atributos é feita mês a mês, em [Treino].)
 */
object EvolucaoPlantel {
    /** Idade com que um jovem tem de deixar a academia. */
    const val IDADE_MAXIMA_ACADEMIA = 20

    /** Um plantel não pode ter mais jogadores do que isto. */
    const val MAX_PLANTEL = 30

    fun aplicar(
        bd: BaseDados, gerador: GeradorJogadores, rng: Random, anoNovo: Int = 2027,
        avisos: MutableList<String> = mutableListOf(), clubeDoUtilizador: String = "",
    ) {
        for (equipa in bd.equipas.values) {
            val meu = equipa.id == clubeDoUtilizador
            for (j in equipa.plantel.toList()) {
                j.idade++
                val limiteReforma = if (j.posicao == "GR") 37 else 35
                if (j.idade >= limiteReforma && (j.idade >= limiteReforma + 3 || rng.nextDouble() < 0.5)) {
                    if (meu) avisos += "${j.nome} terminou a carreira aos ${j.idade} anos."
                    bd.removerJogador(j)
                    bd.adicionarJogador(gerador.criarJovem(equipa, j.posicao).also { it.contratoAte = anoNovo + 3 })
                }
            }
            for (j in equipa.academia.toList()) {
                j.idade++
                if (j.idade < IDADE_MAXIMA_ACADEMIA) continue
                if (equipa.plantel.size < MAX_PLANTEL) {
                    bd.promover(j)
                    j.contratoAte = anoNovo + 3
                    j.salario = j.salarioJusto()
                    if (meu) avisos += "${j.nome} fez ${j.idade} anos e subiu da academia ao plantel."
                } else {
                    bd.mudarDeEquipa(j, "")
                    j.contratoAte = anoNovo
                    if (meu) avisos += "${j.nome} fez ${j.idade} anos e saiu da academia: não havia lugar no plantel."
                }
            }
            equipa.invalidarForca()
        }
    }

    /**
     * Entram jovens novos na academia de um clube. Quanto melhor a academia,
     * mais jovens, com mais qualidade e mais potencial.
     */
    fun entradaNaAcademia(bd: BaseDados, gerador: GeradorJogadores, equipa: Equipa, rng: Random, ano: Int): List<Jogador> {
        val quantos = 3 + equipa.nivelAcademia / 2
        val posicoes = GeradorJogadores.PLANTEL_TIPO.flatMap { (posicao, n) -> List(n) { posicao } }
        return List(quantos) {
            val nivel = GeradorJogadores.nivelDaReputacao(equipa.reputacao) - 19.0 + equipa.nivelAcademia * 1.5 + rng.gauss(0.0, 3.5)
            val j = gerador.criar(equipa.pais, posicoes.random(rng), nivel, rng.nextInt(16, 18))
            j.nacionalidade = if (rng.nextDouble() < 0.9) equipa.pais else j.nacionalidade
            j.potencial = (j.geral() + rng.nextInt(8, 17 + 2 * equipa.nivelAcademia)).coerceAtMost(95)
            j.equipa = equipa.id
            j.academia = true
            j.salario = 12_000
            j.contratoAte = ano + 5
            bd.adicionarJogador(j)
            j
        }
    }
}
