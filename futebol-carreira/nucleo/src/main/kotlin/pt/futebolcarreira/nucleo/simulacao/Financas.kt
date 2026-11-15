package pt.futebolcarreira.nucleo.simulacao

import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.dados.GeradorJogadores
import pt.futebolcarreira.nucleo.modelos.Equipa
import kotlin.math.pow
import kotlin.random.Random

/**
 * Dinheiro dos clubes: bilheteira, patrocínios (inclui direitos de televisão),
 * salários e prémios. O dinheiro em caixa de cada clube é o campo "orcamento".
 */
object Financas {
    /** Patrocínios e televisão por época: clubes com mais reputação recebem muito mais. */
    fun patrocinioAnual(e: Equipa): Long {
        val nivel = GeradorJogadores.nivelDaReputacao(e.reputacao)
        return (1.17.pow(nivel - 50.0) * 60_000.0 * 15.5).toLong()
    }

    /** Espectadores num jogo em casa: estádios de clubes grandes enchem mais. */
    fun assistencia(e: Equipa): Int = (e.capacidade * (0.55 + e.reputacao / 250.0).coerceAtMost(0.97)).toInt()

    /** Receita de bilheteira de um jogo em casa. */
    fun bilheteira(e: Equipa): Long = (assistencia(e) * (12.0 + e.reputacao * 0.35)).toLong()

    /** Contas de uma jornada: uma fatia dos patrocínios e dos salários, e a bilheteira se jogou em casa. */
    fun jornada(e: Equipa, emCasa: Boolean, totalJornadas: Int) {
        val patrocinio = patrocinioAnual(e) / totalJornadas
        val salarios = e.folhaSalarial() / totalJornadas
        val bilhetes = if (emCasa) bilheteira(e) else 0L
        e.contas.patrocinios += patrocinio
        e.contas.salarios += salarios
        e.contas.bilheteira += bilhetes
        e.orcamento += patrocinio + bilhetes - salarios
    }

    /** Prémio de classificação no fim da época (quanto mais acima, maior). */
    fun premio(e: Equipa, posicao: Int, equipas: Int, nivel: Int) {
        val valor = (equipas - posicao + 1) * (if (nivel == 1) 400_000L else 80_000L)
        e.contas.premios += valor
        e.orcamento += valor
    }
}

/** O que acontece aos plantéis entre épocas: empréstimos, contratos e reforços. */
object FimDeEpoca {
    /**
     * Os emprestados voltam ao clube dono. Os contratos que acabam são renovados
     * (sempre no clube do utilizador; quase sempre nos outros) ou o jogador fica livre.
     * Quem esteve um ano inteiro sem clube deixa o futebol.
     */
    fun contratos(bd: BaseDados, ano: Int, rng: Random, avisos: MutableList<String>, clubeDoUtilizador: String = "") {
        val fim = ano + 1   // a época "ano" acaba no verão de ano + 1
        for (j in bd.livres()) if (j.contratoAte < fim) bd.removerJogador(j)
        for (j in bd.jogadores.values.toList()) {
            if (j.emprestadoPor.isNotEmpty()) {
                val dono = bd.equipas[j.emprestadoPor]
                if (dono != null) {
                    if (j.equipa == clubeDoUtilizador || dono.id == clubeDoUtilizador) avisos += "${j.nome} voltou do empréstimo (${dono.nome})."
                    bd.mudarDeEquipa(j, dono.id)
                }
                j.emprestadoPor = ""
            }
            // Os jovens da academia só assinam contrato a sério quando sobem ao plantel.
            if (j.equipa.isEmpty() || j.academia || j.contratoAte > fim) continue
            val renova = j.equipa == clubeDoUtilizador || rng.nextDouble() < 0.8 || j.idade >= 33
            if (renova) {
                j.contratoAte = fim + rng.nextInt(1, 4)
                j.salario = maxOf(j.salario, j.salarioJusto())
                if (j.equipa == clubeDoUtilizador) avisos += "${j.nome} renovou até ${j.contratoAte}."
            } else {
                bd.mudarDeEquipa(j, "")
                j.contratoAte = fim   // fica um ano à procura de clube
            }
        }
    }

    /** Clubes com poucos jogadores contratam jogadores livres ou promovem jovens da formação. */
    fun completarPlanteis(bd: BaseDados, gerador: GeradorJogadores, anoNovo: Int) {
        for (equipa in bd.equipas.values) {
            var guarda = 0
            while (equipa.plantel.size < 22 && guarda++ < 12) {
                // A posição que mais falta faz, em comparação com o plantel-tipo.
                val posicao = GeradorJogadores.PLANTEL_TIPO.maxByOrNull { (p, quantos) -> quantos - equipa.plantel.count { it.posicao == p } }!!.key
                val nivel = GeradorJogadores.nivelDaReputacao(equipa.reputacao)
                val livre = bd.livres().filter { it.posicao == posicao && it.geral() in (nivel - 9).toInt()..(nivel + 3).toInt() }.maxByOrNull { it.geral() }
                if (livre != null) {
                    bd.mudarDeEquipa(livre, equipa.id)
                    livre.contratoAte = anoNovo + 2
                    livre.salario = livre.salarioJusto()
                } else {
                    bd.adicionarJogador(gerador.criarJovem(equipa, posicao).also { it.contratoAte = anoNovo + 3 })
                }
            }
        }
    }
}
