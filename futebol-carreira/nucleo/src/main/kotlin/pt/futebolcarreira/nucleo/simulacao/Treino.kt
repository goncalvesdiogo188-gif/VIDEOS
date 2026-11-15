package pt.futebolcarreira.nucleo.simulacao

import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Jogador
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Treino e estado dos jogadores.
 *
 *  - Uma vez por mês faz-se a conta do treino: os jovens aproximam-se do seu potencial,
 *    atributo a atributo, e os veteranos vão perdendo qualidades.
 *  - Depois de cada jogo mexem a forma e a moral, há lesões no treino e o
 *    departamento médico pode encurtar as paragens.
 */
object Treino {
    /** Idade a partir da qual um jogador começa a perder qualidades. */
    fun idadeDeDeclinio(j: Jogador) = if (j.posicao == "GR") 33 else 31

    /**
     * Um mês de treino em todos os clubes. Devolve frases sobre os jogadores do
     * clube do utilizador que mudaram de "geral" (plantel e academia).
     */
    fun mensal(bd: BaseDados, rng: Random, clubeDoUtilizador: String = ""): List<String> {
        val frases = mutableListOf<String>()
        for (equipa in bd.equipas.values) {
            val meu = equipa.id == clubeDoUtilizador
            for (j in equipa.plantel) {
                val antes = j.geral()
                evoluir(j, equipa, rng, naAcademia = false)
                if (meu && j.geral() != antes) frases += "${j.nome} ($antes→${j.geral()})"
            }
            for (j in equipa.academia) {
                val antes = j.geral()
                evoluir(j, equipa, rng, naAcademia = true)
                if (meu && j.geral() != antes) frases += "${j.nome}, da academia ($antes→${j.geral()})"
            }
            equipa.invalidarForca()
        }
        return frases
    }

    /** Um mês de treino de um jogador. */
    fun evoluir(j: Jogador, equipa: Equipa, rng: Random, naAcademia: Boolean) {
        val pesos = Jogador.PESOS[j.posicao] ?: Jogador.PESOS.getValue("MC")
        val foco = equipa.focoTreino.atributos
        val jogosTotais = j.epoca.jogos + j.epocaTaca.jogos
        val temJogado = jogosTotais > j.jogosNoUltimoTreino
        j.jogosNoUltimoTreino = jogosTotais
        val geral = j.geral()

        if (j.idade <= 28 && geral < j.potencial) {
            // Probabilidade de cada atributo importante subir um ponto neste mês.
            val base = when {
                j.idade <= 20 -> 0.38
                j.idade <= 23 -> 0.26
                else -> 0.07
            }
            val margem = ((j.potencial - geral) / 8.0).coerceIn(0.35, 1.5)
            val instalacoes = 0.8 + 0.1 * (if (naAcademia) equipa.nivelAcademia else equipa.nivelTreino)
            // Quem joga evolui mais; os da academia têm os seus próprios jogos.
            val minutos = if (naAcademia) 1.0 else if (temJogado) 1.15 else 0.85
            val p = base * margem * instalacoes * minutos * equipa.intensidade.crescimento
            for (atributo in Jogador.ATRIBUTOS) {
                if (atributo == "guarda_redes" && j.posicao != "GR") continue
                var pa = if (atributo in pesos) p else p * 0.4
                if (foco.isNotEmpty()) pa *= if (atributo in foco) 1.5 else 0.8
                if (rng.nextDouble() < pa) {
                    j.atributos[atributo] = (j.atributo(atributo) + 1).coerceAtMost(99)
                    // Nunca passa do potencial.
                    if (j.geral() > j.potencial) j.atributos[atributo] = j.atributo(atributo) - 1
                }
            }
        } else if (j.idade >= idadeDeDeclinio(j)) {
            var q = 0.10 + (j.idade - idadeDeDeclinio(j)) * 0.03
            // Bom trabalho físico atrasa a quebra.
            if ("fisico" in foco) q *= 0.8
            for (atributo in pesos.keys) {
                val qa = if (atributo == "ritmo" || atributo == "fisico") q * 1.5 else q
                if (rng.nextDouble() < qa) j.atributos[atributo] = (j.atributo(atributo) - 1).coerceAtLeast(1)
            }
        }
    }

    /**
     * Depois de um jogo (já aplicado aos jogadores): forma e moral dos dois plantéis,
     * recuperação mais rápida com bom departamento médico e lesões no treino da semana.
     * As frases sobre o clube do utilizador vão para "noticias".
     */
    fun depoisDoJogo(bd: BaseDados, relatorio: RelatorioJogo, rng: Random, clubeDoUtilizador: String, noticias: MutableList<String>) {
        for (lado in listOf(relatorio.casa, relatorio.fora)) {
            val equipa = bd.equipas[lado.equipa] ?: continue
            val casa = lado === relatorio.casa
            val saldo = if (casa) relatorio.golosCasa - relatorio.golosFora else relatorio.golosFora - relatorio.golosCasa
            val resultado = if (saldo > 0) 3 else if (saldo < 0) -3 else 0
            val jogaram = HashMap<Int, FichaJogador>()
            for (ficha in lado.fichas) jogaram[ficha.jogador.id] = ficha

            for (j in equipa.plantel) {
                val ficha = jogaram[j.id]
                if (ficha != null) {
                    // Boas notas dão confiança; a forma tende sempre a voltar ao normal.
                    j.forma = (j.forma + ((ficha.nota - 6.5) * 6).roundToInt() + (50 - j.forma) / 10).coerceIn(0, 100)
                    j.moral = (j.moral + resultado).coerceIn(0, 100)
                    if (ficha.lesao > 0) j.forma = (j.forma - 15).coerceAtLeast(0)
                } else {
                    // Sem jogar perde-se ritmo e, aos poucos, a paciência.
                    if (j.forma > 42) j.forma--
                    if (j.disponivel) j.moral = (j.moral + resultado / 3 - 1).coerceIn(20, 100)
                }
            }

            // Departamento médico: cada nível acima do primeiro dá mais hipóteses de encurtar uma paragem.
            val medico = (equipa.nivelMedico - 1) * 0.08
            for (j in lado.indisponiveis) {
                if (j.lesao > 0 && rng.nextDouble() < medico) j.lesao--
            }

            // Lesões no treino: dependem da intensidade (e um bom departamento médico evita algumas).
            val risco = equipa.intensidade.riscoLesao * (1.0 - 0.1 * (equipa.nivelMedico - 1))
            for (j in equipa.plantel) {
                if (j.lesao == 0 && rng.nextDouble() < risco) {
                    j.lesao = rng.nextInt(1, 5)
                    j.forma = (j.forma - 10).coerceAtLeast(0)
                    if (equipa.id == clubeDoUtilizador) noticias += "${j.nome} lesionou-se no treino e falha ${if (j.lesao == 1) "1 jogo" else "${j.lesao} jogos"}."
                }
            }
        }
    }
}
