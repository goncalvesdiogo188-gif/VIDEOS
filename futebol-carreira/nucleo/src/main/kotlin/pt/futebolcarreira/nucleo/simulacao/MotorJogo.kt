package pt.futebolcarreira.nucleo.simulacao

import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.modelos.Mentalidade
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Simulação detalhada de um jogo, minuto a minuto: remates, golos com marcador
 * e assistência, cartões, lesões, substituições, cansaço e táticas.
 *
 * A simulação só calcula o relatório; quem o aplica aos jogadores
 * (estatísticas, lesões, castigos) é a função "aplicar".
 */
object MotorJogo {
    // ---- Números que afinam o jogo (mexer aqui muda o "sabor" da simulação) ----
    /**
     * Acerto dos golos: como os favoritos atacam e quem perde arrisca,
     * sem este travão haveria golos a mais.
     */
    const val RITMO_GOLOS = 0.88
    /** Remates por jogo de uma equipa média. */
    const val REMATES_BASE = 11.5
    /** Faltas por jogo de uma equipa. */
    const val FALTAS_BASE = 11.5
    /** Probabilidade de uma falta dar cartão amarelo / vermelho direto. */
    const val PROB_AMARELO = 0.15
    const val PROB_VERMELHO = 0.004
    /** Probabilidade, por minuto e por equipa, de um jogador se lesionar. */
    const val PROB_LESAO = 0.0007
    /** Parte dos golos que são de penálti. */
    const val PROB_PENALTI = 0.08
    const val SUBSTITUICOES = 5
    const val SUPLENTES = 7
    /** Pontos de força que a equipa perde por cada jogador a menos. */
    const val CUSTO_JOGADOR_A_MENOS = 5.0
    /** De quantos em quantos amarelos na época há um jogo de castigo. */
    const val AMARELOS_PARA_CASTIGO = 5

    /** Um dos lados durante o jogo. */
    private class Lado(val equipa: Equipa, val casa: Boolean, val rng: Random, val neutro: Boolean = false) {
        val formacao: Formacao = equipa.formacao
        val lugares = formacao.lugares
        val emCampo = arrayOfNulls<FichaJogador>(11)
        val energia = DoubleArray(11) { 100.0 }
        /** Nota (sem cansaço) e gasto de energia por minuto de quem está em cada lugar. */
        val notaBase = DoubleArray(11)
        val gasto = DoubleArray(11)
        val banco = mutableListOf<Jogador>()
        val fichas = mutableListOf<FichaJogador>()
        val indisponiveis = equipa.plantel.filter { !it.disponivel }
        val estatisticas = EstatisticasEquipa()
        var substituicoes = SUBSTITUICOES
        var mentalidade = Mentalidade.EQUILIBRADA
        var mentalidadeInicial = Mentalidade.EQUILIBRADA
        var golos = 0
        var ataque = 0.0
        var defesa = 0.0
        var geral = 0.0

        init {
            val onze = equipa.onzeParaJogo()
            onze.forEachIndexed { i, lugar ->
                lugar.jogador?.let { j -> colocar(i, FichaJogador(j, lugar.lugar, true, 0).also { fichas += it }) }
            }
            banco += equipa.suplentesParaJogo(onze, SUPLENTES)
            recalcular()
        }

        /** Põe um jogador no lugar i. */
        fun colocar(i: Int, ficha: FichaJogador, energiaInicial: Double = 100.0) {
            emCampo[i] = ficha
            energia[i] = energiaInicial
            notaBase[i] = Equipa.notaNoLugar(ficha.jogador, lugares[i]) + ficha.jogador.bonusForma()
            // Quem tem melhor físico cansa-se menos; o guarda-redes quase não se cansa.
            gasto[i] = if (lugares[i] == "GR") 0.1 else 0.35 + (100 - ficha.jogador.atributo("fisico")) * 0.005
        }

        /** Nota atual de quem está no lugar i (o cansaço tira até 20%). */
        fun notaAtual(i: Int): Double =
            if (emCampo[i] == null) 0.0 else notaBase[i] * (0.80 + 0.20 * energia[i] / 100.0)

        /** Volta a calcular a força da equipa (depois de cansaço, trocas ou expulsões). */
        fun recalcular() {
            var somaAtaque = 0.0
            var pesoAtaque = 0.0
            var somaDefesa = 0.0
            var pesoDefesa = 0.0
            var soma = 0.0
            var presentes = 0
            for (i in 0 until 11) {
                if (emCampo[i] == null) continue
                val nota = notaAtual(i)
                val pa = Equipa.PESO_ATAQUE.getValue(lugares[i])
                val pd = Equipa.PESO_DEFESA.getValue(lugares[i])
                somaAtaque += nota * pa
                pesoAtaque += pa
                somaDefesa += nota * pd
                pesoDefesa += pd
                soma += nota
                presentes++
            }
            val castigo = (11 - presentes) * CUSTO_JOGADOR_A_MENOS
            ataque = (if (pesoAtaque > 0) somaAtaque / pesoAtaque else 20.0) + formacao.ataque - castigo
            defesa = (if (pesoDefesa > 0) somaDefesa / pesoDefesa else 20.0) + formacao.defesa - castigo
            geral = (if (presentes > 0) soma / presentes else 20.0) - castigo
        }

        /** Todos se cansam um pouco a cada minuto. */
        fun cansar() {
            for (i in 0 until 11) if (emCampo[i] != null) energia[i] = maxOf(0.0, energia[i] - gasto[i])
        }

        /** Sorteia um lugar ocupado; "peso" diz quanto conta cada lugar. null se o peso total for zero. */
        fun sortear(excluir: Int = -1, peso: (Int) -> Double): Int? {
            var total = 0.0
            for (i in 0 until 11) if (emCampo[i] != null && i != excluir) total += peso(i)
            if (total <= 0.0) return null
            var r = rng.nextDouble() * total
            for (i in 0 until 11) {
                if (emCampo[i] == null || i == excluir) continue
                r -= peso(i)
                if (r <= 0.0) return i
            }
            return (0 until 11).lastOrNull { emCampo[it] != null && it != excluir }
        }

        /** O melhor suplente para um lugar (null se não houver nenhum que sirva). */
        fun melhorSuplente(lugar: String): Jogador? {
            val candidatos = if (lugar == "GR") banco.filter { it.posicao == "GR" }.ifEmpty { banco } else banco.filter { it.posicao != "GR" }
            return candidatos.maxByOrNull { Equipa.notaNoLugar(it, lugar) }
        }

        /** Troca quem está no lugar i por um suplente. */
        fun substituir(i: Int, entra: Jogador, minuto: Int, eventos: MutableList<Evento>) {
            val sai = emCampo[i] ?: return
            sai.saiu = minuto
            banco -= entra
            colocar(i, FichaJogador(entra, lugares[i], false, minuto).also { fichas += it })
            substituicoes--
            eventos += Evento(minuto, TipoEvento.SUBSTITUICAO, casa, sai.jogador, entra)
        }

        /** Tira do campo quem está no lugar i (expulsão ou lesão sem substituições). */
        fun retirar(i: Int, minuto: Int, eventos: MutableList<Evento>) {
            emCampo[i]?.saiu = minuto
            emCampo[i] = null
            if (lugares[i] == "GR") porGuardaRedes(minuto, eventos)
        }

        /** A baliza ficou vazia: entra o guarda-redes suplente ou vai um jogador de campo para lá. */
        private fun porGuardaRedes(minuto: Int, eventos: MutableList<Evento>) {
            // O jogador de campo mais fraco é quem sai (ou quem vai para a baliza).
            val sacrificado = (1 until 11).filter { emCampo[it] != null }.minByOrNull { notaAtual(it) } ?: return
            val suplente = banco.firstOrNull { it.posicao == "GR" }
            if (suplente != null && substituicoes > 0) {
                val sai = emCampo[sacrificado]!!
                sai.saiu = minuto
                banco -= suplente
                colocar(0, FichaJogador(suplente, "GR", false, minuto).also { fichas += it })
                substituicoes--
                eventos += Evento(minuto, TipoEvento.SUBSTITUICAO, casa, sai.jogador, suplente)
            } else {
                colocar(0, emCampo[sacrificado]!!, energia[sacrificado])
            }
            emCampo[sacrificado] = null
        }

        /** Trocas feitas pelo treinador: saem os mais cansados se houver suplente à altura. */
        fun trocasDoTreinador(quantas: Int, minuto: Int, eventos: MutableList<Evento>) {
            repeat(quantas) {
                if (substituicoes <= 0) return
                val cansados = (1 until 11).filter { emCampo[it] != null }.sortedBy { energia[it] }
                for (i in cansados) {
                    val entra = melhorSuplente(lugares[i]) ?: continue
                    if (Equipa.notaNoLugar(entra, lugares[i]) >= notaAtual(i) - 1.0) {
                        substituir(i, entra, minuto, eventos)
                        break
                    }
                }
            }
        }
    }

    /**
     * Simula um jogo e devolve o relatório. Não altera os jogadores.
     *
     * "neutro" = campo neutro (ninguém joga em casa).
     * "precisaProlongamento" (só nas eliminatórias): recebe os golos da casa e da fora aos 90 minutos e diz
     * se é preciso prolongamento (30 minutos a mais). Assim serve para jogos únicos e para a segunda mão,
     * onde conta o resultado agregado. Os penáltis não são daqui: ver "Penaltis".
     */
    fun simular(
        casa: Equipa, fora: Equipa, rng: Random, neutro: Boolean = false,
        precisaProlongamento: ((Int, Int) -> Boolean)? = null,
    ): RelatorioJogo {
        val c = Lado(casa, true, rng, neutro)
        val f = Lado(fora, false, rng, neutro)
        val eventos = mutableListOf<Evento>()
        escolherMentalidadeInicial(c, f)
        escolherMentalidadeInicial(f, c)
        val geralInicialCasa = c.geral
        val geralInicialFora = f.geral

        val descontos = rng.nextInt(1, 7)
        var prolongamento = false
        fun jogar(minuto: Int) {
            if (minuto % 5 == 0) {
                c.recalcular()
                f.recalcular()
            }
            // Momentos em que os treinadores mexem na equipa (no prolongamento ainda há uma troca extra).
            val trocas = when {
                minuto == 46 -> 1; minuto == 60 -> 2; minuto == 70 -> 1; minuto == 80 -> 1
                prolongamento && (minuto == 91 || minuto == 106) -> 1
                else -> 0
            }
            if (trocas > 0) {
                if (minuto == 91) { c.substituicoes++; f.substituicoes++ }   // no prolongamento há uma troca a mais
                for (lado in listOf(c, f)) {
                    // Ao intervalo só há troca de vez em quando.
                    if (minuto != 46 || rng.nextDouble() < 0.3) lado.trocasDoTreinador(trocas, minuto, eventos)
                    lado.recalcular()
                }
            }
            if (minuto == 70 || minuto == 82) {
                ajustarMentalidade(c, f, minuto)
                ajustarMentalidade(f, c, minuto)
            }
            jogarMinuto(c, f, minuto, eventos)
            jogarMinuto(f, c, minuto, eventos)
            c.cansar()
            f.cansar()
        }

        var duracao = if (precisaProlongamento == null) 90 + descontos else 90
        for (minuto in 1..duracao) jogar(minuto)
        if (precisaProlongamento != null) {
            if (precisaProlongamento(c.golos, f.golos)) {
                prolongamento = true
                for (minuto in 91..120) jogar(minuto)
                duracao = 120 + rng.nextInt(1, 4)
                for (minuto in 121..duracao) jogar(minuto)
            } else {
                duracao = 90 + descontos
                for (minuto in 91..duracao) jogar(minuto)
            }
        }

        // Posse de bola: depende da qualidade das equipas e de jogar em casa.
        val posse = (51.0 + (geralInicialCasa - geralInicialFora) * 0.9 + rng.gauss(0.0, 4.0)).roundToInt().coerceIn(25, 75)
        c.estatisticas.posse = posse
        f.estatisticas.posse = 100 - posse
        darNotas(c, f, rng)
        darNotas(f, c, rng)
        return RelatorioJogo(
            LadoRelatorio(casa.id, c.formacao, c.mentalidadeInicial, c.fichas, c.estatisticas, c.indisponiveis),
            LadoRelatorio(fora.id, f.formacao, f.mentalidadeInicial, f.fichas, f.estatisticas, f.indisponiveis),
            c.golos, f.golos, duracao, eventos, prolongamento,
        )
    }

    /** Antes do jogo: o favorito ataca, a equipa muito mais fraca fecha-se (a não ser que a mentalidade esteja fixa). */
    private fun escolherMentalidadeInicial(lado: Lado, adversario: Lado) {
        val diferenca = lado.geral - adversario.geral
        lado.mentalidade = lado.equipa.mentalidade ?: when {
            diferenca >= 5.0 -> Mentalidade.OFENSIVA
            diferenca <= (if (lado.casa) -9.0 else -6.0) -> Mentalidade.DEFENSIVA
            else -> Mentalidade.EQUILIBRADA
        }
        lado.mentalidadeInicial = lado.mentalidade
    }

    /** Perto do fim: quem perde arrisca, quem ganha por pouco defende. */
    private fun ajustarMentalidade(lado: Lado, adversario: Lado, minuto: Int) {
        if (lado.equipa.mentalidade != null) return
        val diferenca = lado.golos - adversario.golos
        lado.mentalidade = when {
            diferenca < 0 -> Mentalidade.OFENSIVA
            diferenca == 1 && minuto >= 82 -> Mentalidade.DEFENSIVA
            else -> lado.mentalidade
        }
    }

    /** Um minuto de jogo visto do lado de uma equipa: remates, faltas e lesões. */
    private fun jogarMinuto(lado: Lado, adversario: Lado, minuto: Int, eventos: MutableList<Evento>) {
        val rng = lado.rng
        val est = lado.estatisticas

        // ---- Remates e golos ----
        val base = when {
            lado.neutro -> (SimuladorJogo.GOLOS_BASE_CASA + SimuladorJogo.GOLOS_BASE_FORA) / 2   // sem vantagem de campo
            lado.casa -> SimuladorJogo.GOLOS_BASE_CASA
            else -> SimuladorJogo.GOLOS_BASE_FORA
        }
        val ataque = lado.ataque + lado.mentalidade.ataque
        val defesa = adversario.defesa + adversario.mentalidade.defesa
        // Golos que a equipa marcaria, em média, num jogo inteiro a este ritmo.
        val golosEsperados = (RITMO_GOLOS * base * exp((ataque - defesa) / SimuladorJogo.ESCALA)).coerceIn(0.05, 6.0)
        val rematesEsperados = (REMATES_BASE * (golosEsperados / 1.3).pow(0.6)).coerceIn(4.0, 24.0)
        if (rng.nextDouble() < rematesEsperados / 90.0) {
            est.remates++
            val golo = rng.nextDouble() < golosEsperados / rematesEsperados
            val penalti = golo && rng.nextDouble() < PROB_PENALTI
            // Quem remata: os avançados rematam mais, e quem remata bem ainda mais.
            val rematador = if (penalti) {
                (1 until 11).filter { lado.emCampo[it] != null }.maxByOrNull { lado.emCampo[it]!!.jogador.atributo("remate") }
            } else {
                lado.sortear { i -> (Equipa.PESO_ATAQUE.getValue(lado.lugares[i]) + 0.15) * lado.emCampo[i]!!.jogador.atributo("remate").toDouble().pow(2) }
            }
            val ficha = rematador?.let { lado.emCampo[it] }
            if (ficha != null) {
                val guardaRedes = adversario.emCampo[0]
                when {
                    golo -> {
                        est.rematesBaliza++
                        lado.golos++
                        ficha.golos++
                        // Três em cada quatro golos têm assistência.
                        val assistente = if (!penalti && rng.nextDouble() < 0.75) {
                            lado.sortear(excluir = rematador) { i -> (Equipa.PESO_ATAQUE.getValue(lado.lugares[i]) + 0.5) * lado.emCampo[i]!!.jogador.atributo("passe") }
                                ?.let { lado.emCampo[it] }
                        } else null
                        assistente?.let { it.assistencias++ }
                        for (g in adversario.emCampo) g?.let { it.golosSofridos++ }
                        val (gc, gf) = if (lado.casa) lado.golos to adversario.golos else adversario.golos to lado.golos
                        eventos += Evento(minuto, TipoEvento.GOLO, lado.casa, ficha.jogador, assistente?.jogador, penalti, gc, gf)
                    }
                    rng.nextDouble() < 0.30 -> {
                        est.rematesBaliza++
                        guardaRedes?.let { it.defesas++ }
                        if (rng.nextDouble() < 0.4) est.cantos++
                        eventos += Evento(minuto, TipoEvento.DEFESA, lado.casa, ficha.jogador, guardaRedes?.jogador)
                    }
                    else -> {
                        if (rng.nextDouble() < 0.25) est.cantos++
                        // Só alguns remates para fora entram no relato, para não o encher.
                        if (rng.nextDouble() < 0.35) eventos += Evento(minuto, TipoEvento.REMATE_FORA, lado.casa, ficha.jogador)
                    }
                }
            }
        }

        // ---- Faltas e cartões (quem defende faz mais faltas) ----
        val faltas = FALTAS_BASE * (if (lado.mentalidade == Mentalidade.DEFENSIVA) 1.1 else 1.0)
        if (rng.nextDouble() < faltas / 90.0) {
            est.faltas++
            val sorte = rng.nextDouble()
            if (sorte < PROB_AMARELO + PROB_VERMELHO) {
                val i = lado.sortear { i -> if (lado.lugares[i] == "GR") 0.1 else Equipa.PESO_DEFESA.getValue(lado.lugares[i]) + 0.7 }
                val ficha = i?.let { lado.emCampo[it] }
                if (i != null && ficha != null) {
                    if (sorte < PROB_VERMELHO) {
                        ficha.vermelho = true
                        ficha.vermelhoDireto = true
                        est.vermelhos++
                        eventos += Evento(minuto, TipoEvento.VERMELHO, lado.casa, ficha.jogador)
                        lado.retirar(i, minuto, eventos)
                        lado.recalcular()
                    } else if (ficha.amarelos == 0) {
                        ficha.amarelos = 1
                        est.amarelos++
                        eventos += Evento(minuto, TipoEvento.AMARELO, lado.casa, ficha.jogador)
                    } else if (rng.nextDouble() < 0.35) {
                        // Quem já tem amarelo tem mais cuidado: só às vezes vê o segundo.
                        ficha.amarelos = 2
                        ficha.vermelho = true
                        est.amarelos++
                        est.vermelhos++
                        eventos += Evento(minuto, TipoEvento.SEGUNDO_AMARELO, lado.casa, ficha.jogador)
                        lado.retirar(i, minuto, eventos)
                        lado.recalcular()
                    }
                }
            }
        }

        // ---- Lesões ----
        if (rng.nextDouble() < PROB_LESAO) {
            val i = lado.sortear { 1.0 }
            val ficha = i?.let { lado.emCampo[it] }
            if (i != null && ficha != null) {
                // Quase todas as lesões são curtas; algumas são longas.
                ficha.lesao = (1 + (-ln(1.0 - rng.nextDouble()) * 3.0).toInt()).coerceAtMost(25)
                eventos += Evento(minuto, TipoEvento.LESAO, lado.casa, ficha.jogador)
                val entra = if (lado.substituicoes > 0) lado.melhorSuplente(lado.lugares[i]) else null
                if (entra != null) lado.substituir(i, entra, minuto, eventos) else lado.retirar(i, minuto, eventos)
                lado.recalcular()
            }
        }
    }

    /** Nota (1 a 10) de cada jogador que participou. */
    private fun darNotas(lado: Lado, adversario: Lado, rng: Random) {
        val resultado = when {
            lado.golos > adversario.golos -> 0.35
            lado.golos < adversario.golos -> -0.25
            else -> 0.0
        }
        for (ficha in lado.fichas) {
            var nota = 6.2 + rng.gauss(0.0, 0.45) + resultado
            nota += ficha.golos * 0.9 + ficha.assistencias * 0.5 + ficha.defesas * 0.15
            nota -= ficha.amarelos * 0.2
            if (ficha.vermelho) nota -= 1.2
            // Quem defende é avaliado pelos golos sofridos enquanto esteve em campo.
            if (Equipa.PESO_DEFESA.getValue(ficha.lugar) >= 1.5) {
                nota += if (ficha.golosSofridos == 0) 0.5 else -minOf(0.8, ficha.golosSofridos * 0.2)
            }
            // Quem jogou pouco tempo fica perto da nota neutra.
            val minutos = (if (ficha.saiu > 0) ficha.saiu else 90) - ficha.entrou
            if (minutos < 20) nota = 6.0 + (nota - 6.0) * 0.5
            ficha.nota = (nota.coerceIn(3.0, 10.0) * 10).roundToInt() / 10.0
        }
    }

    /**
     * Passa o relatório para os jogadores: estatísticas da época, lesões e castigos.
     * "taca" = jogo de taça: jogos, golos e notas vão para "epocaTaca" (a liga fica só com os seus números),
     * mas os cartões e as lesões contam sempre (os castigos valem em todas as competições).
     * Quem estava de fora (lesionado ou castigado) fica um jogo mais perto de voltar.
     */
    fun aplicar(relatorio: RelatorioJogo, taca: Boolean = false) {
        for (lado in listOf(relatorio.casa, relatorio.fora)) {
            for (j in lado.indisponiveis) {
                if (j.lesao > 0) j.lesao--
                if (j.suspensao > 0) j.suspensao--
            }
            for (ficha in lado.fichas) {
                val j = ficha.jogador
                val e = j.epoca
                val n = if (taca) j.epocaTaca else e
                n.jogos++
                n.golos += ficha.golos
                n.assistencias += ficha.assistencias
                n.somaNotas += ficha.nota
                if (ficha.vermelho) {
                    e.vermelhos++
                    j.suspensao += if (ficha.vermelhoDireto) 2 else 1
                }
                // Os amarelos contam para castigo (o segundo amarelo de uma expulsão já foi castigado).
                val amarelos = if (ficha.amarelos == 2) 1 else ficha.amarelos
                repeat(amarelos) {
                    e.amarelos++
                    if (e.amarelos % AMARELOS_PARA_CASTIGO == 0) j.suspensao++
                }
                if (ficha.lesao > 0) j.lesao = ficha.lesao
            }
        }
    }
}
