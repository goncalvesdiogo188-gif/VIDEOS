package pt.futebolcarreira.nucleo

import pt.futebolcarreira.nucleo.aovivo.AcaoParada
import pt.futebolcarreira.nucleo.aovivo.Comando
import pt.futebolcarreira.nucleo.aovivo.ComandoParada
import pt.futebolcarreira.nucleo.aovivo.Desempate
import pt.futebolcarreira.nucleo.aovivo.Dificuldade
import pt.futebolcarreira.nucleo.aovivo.ExigenciaVencedor
import pt.futebolcarreira.nucleo.aovivo.FabricaPartida
import pt.futebolcarreira.nucleo.aovivo.FaseJogo
import pt.futebolcarreira.nucleo.aovivo.MotorAoVivo
import pt.futebolcarreira.nucleo.aovivo.PartidaAoVivo
import pt.futebolcarreira.nucleo.aovivo.TipoLance
import pt.futebolcarreira.nucleo.aovivo.TipoParada
import pt.futebolcarreira.nucleo.aovivo.aoReceber
import pt.futebolcarreira.nucleo.aovivo.calcularAlvos
import pt.futebolcarreira.nucleo.aovivo.exigenciaDoProximoJogo
import pt.futebolcarreira.nucleo.aovivo.falta
import pt.futebolcarreira.nucleo.aovivo.iniciarDesempate
import pt.futebolcarreira.nucleo.aovivo.passar
import pt.futebolcarreira.nucleo.aovivo.tickRegras
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.simulacao.TipoEvento
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Testes da Fase 13 (E1 e E3): regras do jogo ao vivo e inteligência/dificuldade. */
class Fase13RegrasTest {
    private val pastaDados = File("../app/src/main/assets/dados")
    private val ler = { caminho: String -> File(pastaDados, caminho).takeIf(File::exists)?.readText() }

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar(ler), "carregar: ${bd.erros.take(3)}")
        return bd
    }

    private fun equipasValidas(bd: BaseDados) = bd.equipas.values.filter { it.plantel.size >= 16 }.sortedBy { it.id }

    private fun motor(
        casa: Equipa, fora: Equipa, semente: Int, duracao: Float = 240f, semUtilizador: Boolean = false,
        dif: Dificuldade = Dificuldade.NORMAL, exigencia: ExigenciaVencedor? = null,
    ): MotorAoVivo = FabricaPartida.criar(casa, fora, true, Random(semente), duracao, semUtilizador, dif, exigencia) as MotorAoVivo

    private fun correr(p: PartidaAoVivo, segundos: Float, comando: Comando = Comando()) {
        repeat((segundos * 30f).toInt()) { p.passo(1f / 30f, comando) }
    }

    /** Joga um jogo todo; devolve o número de passos (e falha se ficar preso). */
    private fun jogarAteAoFim(p: PartidaAoVivo, limite: Int = 60_000, comando: (Int) -> Comando = { Comando() }): Int {
        var passos = 0
        var seguidos = 0
        var faseAnt: Pair<FaseJogo, Int>? = null
        while (!p.terminou && passos < limite) {
            p.passo(1f / 30f, comando(passos))
            val e = p.estado
            val f = e.fase
            // nos penáltis de desempate a fase é sempre PENALTI: conta o progresso pelos chutes já dados
            val chave = f to (e.desempate?.let { it.chutesCasa.size + it.chutesFora.size } ?: 0)
            if (chave == faseAnt && f != FaseJogo.A_JOGAR) seguidos++ else seguidos = 0
            faseAnt = chave
            assertTrue(seguidos < 30 * 16, "ficou preso na fase $f durante 16 s (passo $passos)")
            passos++
        }
        assertTrue(p.terminou, "o jogo não terminou em $limite passos")
        return passos
    }

    // ---------------------------------------------------------------- cenários montados à mão

    private fun aJogar(m: MotorAoVivo) {
        m.fase = FaseJogo.A_JOGAR
        m.tFase = 0f
        m.dono = -1
        m.cobrador = -1
        for (i in 0 until 22) { m.vx[i] = 0f; m.vy[i] = 0f; m.atordoado[i] = 0f; m.carrinho[i] = 0f; m.cd[i] = 0f }
    }

    /** A casa ataca para +x. Adversários perto de x = 10, o penúltimo a 22 e o guarda-redes a 50. O passador (6) tem a bola. */
    private fun cenarioForaDeJogo(m: MotorAoVivo) {
        aJogar(m)
        assertEquals(0, m.papel[0]); assertEquals(0, m.papel[11])
        for (i in 0 until 22) { m.px[i] = if (i < 11) -5f else 10f; m.py[i] = (i % 11 - 5) * 3f }
        m.px[0] = -50f; m.py[0] = 0f
        m.px[11] = 50f; m.py[11] = 0f
        m.px[12] = 22f; m.py[12] = 0f
        m.px[6] = 5f; m.py[6] = 0f
        m.px[10] = 30f; m.py[10] = 8f     // adiantado: fora de jogo
        m.px[9] = 20f; m.py[9] = -8f      // em jogo
        m.dono = 6
        m.bx = 5.6f; m.by = 0f; m.bz = 0f
    }

    @Test
    fun `fora de jogo e assinalado a quem recebe adiantado`() {
        val bd = carregar(); val t = equipasValidas(bd)
        val m = motor(t[0], t[1], 1, semUtilizador = true)
        cenarioForaDeJogo(m)
        m.passar(6, 10, false)
        assertTrue(m.reg.foraPos[10], "o 10 devia estar em fora de jogo")
        assertFalse(m.reg.foraPos[9], "o 9 está em jogo")
        m.aoReceber(10)
        assertEquals(FaseJogo.LIVRE, m.estado.fase)
        assertEquals(1, m.eqReinicio, "o livre é do adversário")
        val l = m.lances.last { it.tipo == TipoLance.FORA_DE_JOGO }
        assertEquals(false, l.casa, "o livre favorece a equipa de fora")
        assertTrue(m.estado.mensagem!!.contains("Fora de jogo"))
        assertTrue(kotlin.math.abs(m.bx - 30f) < 1f && kotlin.math.abs(m.by - 8f) < 1f, "o livre é no sítio do fora de jogo: ${m.bx}, ${m.by}")
    }

    @Test
    fun `quem recebe em jogo nao e apitado`() {
        val bd = carregar(); val t = equipasValidas(bd)
        val m = motor(t[0], t[1], 2, semUtilizador = true)
        cenarioForaDeJogo(m)
        m.passar(6, 9, false)
        m.aoReceber(9)
        assertEquals(FaseJogo.A_JOGAR, m.estado.fase)
        assertEquals(9, m.dono)
        assertTrue(m.lances.none { it.tipo == TipoLance.FORA_DE_JOGO })
    }

    @Test
    fun `nao ha fora de jogo num pontape de baliza nem num adversario a tocar na bola`() {
        val bd = carregar(); val t = equipasValidas(bd)
        // Pontapé de baliza
        val m = motor(t[0], t[1], 3, semUtilizador = true)
        cenarioForaDeJogo(m)
        m.iniciarReinicio(FaseJogo.PONTAPE_BALIZA, 0, -47f, 9f)
        val c = m.cobrador
        m.px[c] = -47f; m.py[c] = 9f
        m.bx = -47f; m.by = 9f
        m.passar(c, 10, true)
        assertTrue(m.reg.foraPos.none { it }, "no pontapé de baliza não há fora de jogo")
        // Um adversário toca na bola: o passe deixa de contar
        val n = motor(t[0], t[1], 4, semUtilizador = true)
        cenarioForaDeJogo(n)
        n.passar(6, 10, false)
        assertTrue(n.reg.foraPos[10])
        n.aoReceber(13)               // um adversário fica com a bola
        n.aoReceber(10)               // ... e perde-a para o 10, que já não está em fora de jogo
        assertEquals(FaseJogo.A_JOGAR, n.estado.fase)
    }

    @Test
    fun `a IA respeita o fora de jogo conforme a dificuldade`() {
        val bd = carregar(); val t = equipasValidas(bd)
        fun contar(d: Dificuldade): Double {
            var n = 0
            for (k in 0 until 14) {
                val m = motor(t[k % 8], t[8 + k % 8], 600 + k, 150f, semUtilizador = true, dif = d)
                jogarAteAoFim(m)
                n += m.lances.count { it.tipo == TipoLance.FORA_DE_JOGO }
            }
            return n / 14.0
        }
        val facil = contar(Dificuldade.FACIL); val lendario = contar(Dificuldade.LENDARIO)
        println("F13 fora de jogo por jogo: facil=$facil lendario=$lendario")
        assertTrue(facil > lendario, "o fora de jogo devia ser mais frequente no Fácil ($facil) do que no Lendário ($lendario)")
        assertTrue(facil > 0.4, "no Fácil devia haver fora de jogo ($facil)")
    }

    // ---------------------------------------------------------------- vantagem

    private fun cenarioFalta(m: MotorAoVivo, x: Float, y: Float) {
        aJogar(m)
        for (i in 0 until 22) { m.px[i] = if (i < 11) 0f else -12f; m.py[i] = (i % 11 - 5) * 4f }
        m.px[0] = -50f; m.px[11] = 50f; m.py[0] = 0f; m.py[11] = 0f
        m.px[8] = x; m.py[8] = y            // portador da casa
        m.dono = 8
        m.bx = x + 0.6f; m.by = y; m.bz = 0f
        for (i in 0 until 22) m.prot[i] = 0f
    }

    @Test
    fun `o arbitro deixa seguir a vantagem e volta a falta se a posse se perde`() {
        val bd = carregar(); val t = equipasValidas(bd)
        var visto = 0
        for (semente in 1..40) {
            if (visto >= 2) break
            val m = motor(t[0], t[1], semente, semUtilizador = true)
            cenarioFalta(m, 25f, 0f)
            m.falta(15, 8, false)
            if (m.reg.vantagemEq != 0) {
                // sem vantagem: o jogo parou para o livre
                assertEquals(FaseJogo.LIVRE, m.estado.fase)
                continue
            }
            visto++
            assertEquals(FaseJogo.A_JOGAR, m.estado.fase, "com vantagem o jogo continua")
            assertTrue(m.estado.vantagem)
            assertTrue(m.lances.any { it.tipo == TipoLance.VANTAGEM })
            if (visto == 1) {
                // A posse perde-se: volta-se à falta, no sítio onde foi cometida
                m.dono = 15
                m.tickRegras(0.1f)
                assertEquals(FaseJogo.LIVRE, m.estado.fase)
                assertEquals(0, m.eqReinicio)
                assertTrue(m.lances.any { it.tipo == TipoLance.VANTAGEM_ANULADA })
                assertTrue(kotlin.math.abs(m.bx - 25.6f) < 1.5f, "o livre é onde foi a falta: ${m.bx}")
            } else {
                // A equipa mantém a bola: a vantagem cumpre-se
                repeat(40) { m.tickRegras(0.1f) }
                assertEquals(FaseJogo.A_JOGAR, m.estado.fase)
                assertFalse(m.estado.vantagem)
                assertTrue(m.lances.none { it.tipo == TipoLance.VANTAGEM_ANULADA })
            }
        }
        assertEquals(2, visto, "nunca houve vantagem")
    }

    @Test
    fun `falta na area da penalti e nao ha vantagem`() {
        val bd = carregar(); val t = equipasValidas(bd)
        for (semente in 1..10) {
            val m = motor(t[0], t[1], semente, semUtilizador = true)
            cenarioFalta(m, 45f, 5f)
            m.falta(15, 8, false)
            assertEquals(FaseJogo.PENALTI, m.estado.fase, "falta na área = penálti")
            assertEquals(0, m.eqReinicio)
            assertTrue(m.lances.any { it.tipo == TipoLance.PENALTI })
        }
    }

    // ---------------------------------------------------------------- bolas paradas

    @Test
    fun `o lancamento lateral joga-se com direcao e forca`() {
        val bd = carregar(); val t = equipasValidas(bd)
        val m = motor(t[0], t[1], 5)
        correr(m, 1f)
        m.iniciarReinicio(FaseJogo.LANCAMENTO, 0, 10f, -34f)
        correr(m, 0.6f)
        val bp = assertNotNull(m.estado.bolaParada)
        assertEquals(TipoParada.LANCAMENTO, bp.tipo)
        assertTrue(bp.doUtilizador && bp.casa)
        assertTrue(bp.tempoRestante > 5f)
        assertEquals(m.estado.jogadores.first { it.controlado }.id, bp.cobradorId)
        assertTrue(m.executarParada(ComandoParada(direcao = (Math.PI / 2).toFloat(), forca = 0.4f)))
        correr(m, 0.8f)
        assertEquals(FaseJogo.A_JOGAR, m.estado.fase)
        assertNull(m.estado.bolaParada)
        assertTrue(m.estado.bola.y > -32f, "a bola devia ir para dentro do campo: ${m.estado.bola.y}")
        assertFalse(m.executarParada(ComandoParada()), "já não há lance parado")
    }

    @Test
    fun `o canto cruza-se pelo ar`() {
        val bd = carregar(); val t = equipasValidas(bd)
        val m = motor(t[2], t[3], 6)
        correr(m, 1f)
        m.iniciarReinicio(FaseJogo.CANTO, 0, 52.5f, 34f)
        correr(m, 0.6f)
        assertEquals(TipoParada.CANTO, assertNotNull(m.estado.bolaParada).tipo)
        assertTrue(m.executarParada(ComandoParada(forca = 0.7f)))
        var maxZ = 0f
        repeat(40) { m.passo(1f / 30f, Comando()); maxZ = maxOf(maxZ, m.estado.bola.z) }
        assertTrue(maxZ > 1.5f, "o cruzamento devia subir: $maxZ")
        assertTrue(m.estado.bola.x < 50f, "a bola devia afastar-se da bandeira: ${m.estado.bola.x}")
    }

    @Test
    fun `o livre perto da baliza tem barreira e pode rematar`() {
        val bd = carregar(); val t = equipasValidas(bd)
        val m = motor(t[4], t[5], 7)
        correr(m, 1f)
        m.iniciarReinicio(FaseJogo.LIVRE, 0, 35f, 6f)
        correr(m, 3f)
        val bp = assertNotNull(m.estado.bolaParada)
        assertTrue(bp.podeRematar && bp.barreira)
        assertTrue(bp.distanciaBaliza < 25f)
        val naBarreira = m.estado.jogadores.filter { !it.casa && kotlin.math.hypot(it.x - 35f, it.y - 6f) in 7.5f..11.5f }
        assertTrue(naBarreira.size >= 2, "devia haver barreira: ${naBarreira.size}")
        val antes = m.remates[0]
        assertTrue(m.executarParada(ComandoParada(acao = AcaoParada.REMATAR, mira = 0.6f, altura = 0.3f, forca = 0.9f)))
        assertEquals(antes + 1, m.remates[0])
        correr(m, 2f)
    }

    @Test
    fun `lances parados do adversario nao se comandam`() {
        val bd = carregar(); val t = equipasValidas(bd)
        val m = motor(t[0], t[1], 8)
        correr(m, 1f)
        m.iniciarReinicio(FaseJogo.LIVRE, 1, -20f, 0f)
        correr(m, 0.6f)
        val bp = assertNotNull(m.estado.bolaParada)
        assertFalse(bp.doUtilizador)
        assertEquals(-1f, bp.tempoRestante)
        assertFalse(m.executarParada(ComandoParada()))
    }

    @Test
    fun `se o utilizador nao cobra o lance parado a IA cobra por ele`() {
        val bd = carregar(); val t = equipasValidas(bd)
        val m = motor(t[0], t[1], 9)
        correr(m, 1f)
        m.iniciarReinicio(FaseJogo.CANTO, 0, 52.5f, -34f)
        correr(m, 9f)
        assertEquals(FaseJogo.CANTO, m.estado.fase, "ainda há tempo")
        correr(m, 2.5f)
        assertTrue(m.estado.fase != FaseJogo.CANTO, "a IA devia ter cobrado")
    }

    @Test
    fun `penalti do utilizador com mira e forca`() {
        val bd = carregar(); val t = equipasValidas(bd)
        var golosCanto = 0; var golosCentro = 0
        val n = 16
        for (k in 0 until n) {
            for (mira in listOf(0.85f, 0f)) {
                val m = motor(t[k % 10], t[10 + k % 10], 70 + k, semUtilizador = false)
                correr(m, 1f)
                val g0 = m.golos[0]
                m.iniciarReinicio(FaseJogo.PENALTI, 0, 41.5f, 0f)
                correr(m, 0.6f)
                val bp = assertNotNull(m.estado.bolaParada)
                assertEquals(TipoParada.PENALTI, bp.tipo)
                assertTrue(bp.doUtilizador)
                assertTrue(m.executarParada(ComandoParada(acao = AcaoParada.REMATAR, mira = if (k % 2 == 0) mira else -mira, altura = if (mira == 0f) 0.1f else 0.5f, forca = 0.55f)))
                correr(m, 4f)
                if (m.golos[0] > g0) { if (mira == 0f) golosCentro++ else golosCanto++ }
            }
        }
        println("F13 penaltis do utilizador: canto=$golosCanto/$n centro=$golosCentro/$n")
        assertTrue(golosCanto >= n * 0.5, "um penálti bem colocado devia entrar na maioria das vezes ($golosCanto de $n)")
        assertTrue(golosCanto > golosCentro, "o canto ($golosCanto) devia marcar mais do que o centro ($golosCentro)")
    }

    @Test
    fun `o guarda-redes do utilizador escolhe o mergulho`() {
        val bd = carregar(); val t = equipasValidas(bd)
        for (lado in listOf(-1, 0, 1)) {
            val m = motor(t[0], t[1], 10 + lado)
            correr(m, 1f)
            m.iniciarReinicio(FaseJogo.PENALTI, 1, -41.5f, 0f)
            val bp = assertNotNull(m.estado.bolaParada)
            assertTrue(bp.guardaRedesDoUtilizador && !bp.doUtilizador)
            m.escolherMergulho(lado)
            var passos = 0
            while (m.estado.fase == FaseJogo.PENALTI && passos++ < 300) m.passo(1f / 30f, Comando())
            assertEquals(lado * 2.3f, m.gkAlvoY[0], 0.001f, "o guarda-redes devia ir para o lado escolhido")
        }
    }

    // ---------------------------------------------------------------- prolongamento e penaltis

    @Test
    fun `a regra de decisao dos penaltis`() {
        fun d(c0: Int, m0: Int, c1: Int, m1: Int) = Desempate(1f, 0).also { it.chutes[0] = c0; it.marcados[0] = m0; it.chutes[1] = c1; it.marcados[1] = m1 }
        assertFalse(d(0, 0, 0, 0).estaDecidido())
        assertTrue(d(3, 3, 3, 0).estaDecidido(), "3-0 ao fim de 3 cada: já não há como virar")
        assertFalse(d(3, 2, 3, 0).estaDecidido(), "2-0 ao fim de 3: ainda pode virar")
        assertTrue(d(4, 3, 4, 1).estaDecidido())
        assertFalse(d(5, 3, 5, 3).estaDecidido())
        assertTrue(d(5, 4, 5, 3).estaDecidido())
        assertFalse(d(6, 4, 5, 4).estaDecidido(), "na morte súbita só se decide no fim da ronda")
        assertFalse(d(6, 4, 6, 4).estaDecidido())
        assertTrue(d(6, 5, 6, 4).estaDecidido())
        assertTrue(d(9, 7, 9, 8).estaDecidido())
    }

    @Test
    fun `desempate por penaltis ao vivo ate ao fim`() {
        val bd = carregar(); val t = equipasValidas(bd)
        var mortaSubita = 0; var cinco = 0
        for (k in 0 until 14) {
            val m = motor(t[k % 9], t[9 + k % 9], 200 + k, 120f, semUtilizador = true, exigencia = ExigenciaVencedor())
            correr(m, 5f)
            val golosAntes = m.golos.toList()
            m.iniciarDesempate()
            jogarAteAoFim(m, limite = 8_000)
            assertEquals(golosAntes, m.golos.toList(), "os penáltis de desempate não contam como golos")
            val e = assertNotNull(m.estado.desempate)
            assertTrue(e.decidido)
            assertTrue(e.golosCasa != e.golosFora, "os penáltis nunca acabam empatados")
            assertTrue(kotlin.math.abs(e.chutesCasa.size - e.chutesFora.size) <= 1)
            assertEquals(e.golosCasa, e.chutesCasa.count { it }); assertEquals(e.golosFora, e.chutesFora.count { it })
            if (e.mortaSubita) mortaSubita++ else cinco++
            val ls = m.lances
            assertEquals(e.chutesCasa.size + e.chutesFora.size, ls.count { it.tipo == TipoLance.PENALTI_MARCADO || it.tipo == TipoLance.PENALTI_DEFENDIDO || it.tipo == TipoLance.PENALTI_FORA })
            assertTrue(ls.any { it.tipo == TipoLance.INICIO_PENALTIS } && ls.any { it.tipo == TipoLance.FIM_PENALTIS })
            val rel = FabricaPartida.relatorio(m)
            assertEquals(e.golosCasa, rel.penaltisCasa); assertEquals(e.golosFora, rel.penaltisFora)
            assertEquals(golosAntes[0], rel.golosCasa)
            // As equipas só atiram até estar decidido: antes do último chute ainda havia dúvida
            if (!e.mortaSubita) assertTrue(e.chutesCasa.size >= 3 && e.chutesFora.size >= 3)
        }
        println("F13 desempates: serie de 5=$cinco morte subita=$mortaSubita")
    }

    @Test
    fun `o utilizador marca e defende penaltis de desempate`() {
        val bd = carregar(); val t = equipasValidas(bd)
        val m = motor(t[3], t[4], 33, 120f, exigencia = ExigenciaVencedor())
        correr(m, 3f)
        m.iniciarDesempate()
        var passos = 0
        var cobrou = 0; var mergulhou = 0
        while (!m.terminou && passos++ < 12_000) {
            val bp = m.estado.bolaParada
            if (bp != null && m.estado.fase == FaseJogo.PENALTI) {
                if (bp.guardaRedesDoUtilizador && mergulhou <= cobrou) { m.escolherMergulho(if (passos % 2 == 0) 1 else -1); mergulhou++ }
                if (bp.doUtilizador && m.executarParada(ComandoParada(acao = AcaoParada.REMATAR, mira = if (cobrou % 2 == 0) 0.8f else -0.8f, altura = 0.4f, forca = 0.6f))) cobrou++
            }
            m.passo(1f / 30f, Comando())
        }
        assertTrue(m.terminou)
        assertTrue(cobrou >= 3, "o utilizador devia ter cobrado ($cobrou)")
        val e = assertNotNull(m.estado.desempate)
        assertTrue(e.decidido && e.golosCasa != e.golosFora)
        assertTrue(e.chutesCasa.size >= 3 && e.chutesFora.size >= 3)
    }

    @Test
    fun `eliminatoria empatada vai a prolongamento e penaltis e o relatorio fica coerente`() {
        val bd = carregar(); val t = equipasValidas(bd)
        var prolongamentos = 0; var penaltis = 0; var decididosNoProlongamento = 0
        for (k in 0 until 90) {
            val m = motor(t[k % 12], t[12 + k % 12], 3000 + k, 60f, semUtilizador = true, exigencia = ExigenciaVencedor())
            jogarAteAoFim(m, limite = 20_000)
            val rel = FabricaPartida.relatorio(m)
            assertEquals(m.estado.golosCasa, rel.golosCasa)
            if (rel.prolongamento) {
                prolongamentos++
                assertTrue(rel.duracao >= 120, "duração com prolongamento: ${rel.duracao}")
                assertTrue(m.lances.any { it.tipo == TipoLance.FIM_DOS_90 } && m.lances.any { it.tipo == TipoLance.INICIO_PROLONGAMENTO })
                assertTrue(m.lances.any { it.tipo == TipoLance.INTERVALO_PROLONGAMENTO })
                // os golos do prolongamento estão nas fichas, nos eventos e no marcador, por ordem
                var anterior = 0
                for (ev in rel.eventos) { assertTrue(ev.minuto >= anterior, "eventos fora de ordem"); anterior = ev.minuto }
                assertEquals(rel.golosCasa, rel.eventos.count { it.tipo == TipoEvento.GOLO && it.casa })
                if (rel.penaltisCasa >= 0) {
                    penaltis++
                    assertTrue(rel.golosCasa == rel.golosFora && rel.penaltisCasa != rel.penaltisFora)
                } else {
                    decididosNoProlongamento++
                    assertTrue(rel.golosCasa != rel.golosFora, "sem penáltis o prolongamento tem de decidir")
                }
            } else {
                assertEquals(-1, rel.penaltisCasa)
                assertTrue(rel.golosCasa != rel.golosFora, "um jogo sem prolongamento não pode acabar empatado numa eliminatória")
                assertTrue(rel.duracao in 90..100, "duração ${rel.duracao}")
            }
        }
        println("F13 prolongamentos=$prolongamentos (decididos=$decididosNoProlongamento) penaltis=$penaltis de 90 jogos")
        assertTrue(prolongamentos >= 3, "devia haver prolongamentos ($prolongamentos)")
        assertTrue(penaltis >= 1, "devia haver penáltis ($penaltis)")
    }

    @Test
    fun `agregado conta para o prolongamento`() {
        val bd = carregar(); val t = equipasValidas(bd)
        var semProlongamentoEmZeroZero = 0; var comProlongamento = 0
        for (k in 0 until 60) {
            // A equipa de fora já ganhou 1-0 na primeira mão: o 0-0 da segunda mão decide, o 1-0 da casa empata a eliminatória
            val m = motor(t[k % 10], t[10 + k % 10], 4000 + k, 60f, semUtilizador = true, exigencia = ExigenciaVencedor(golosAntesCasa = 0, golosAntesFora = 1))
            jogarAteAoFim(m, limite = 20_000)
            val rel = FabricaPartida.relatorio(m)
            val agregadoCasa = rel.golosCasa; val agregadoFora = rel.golosFora + 1
            if (!rel.prolongamento) {
                assertTrue(agregadoCasa != agregadoFora, "sem prolongamento o agregado tem de estar decidido")
                assertEquals(-1, rel.penaltisCasa)
                if (rel.golosCasa == 0 && rel.golosFora == 0) semProlongamentoEmZeroZero++
            } else {
                comProlongamento++
                if (rel.penaltisCasa >= 0) assertEquals(agregadoCasa, agregadoFora)
                else assertTrue(agregadoCasa != agregadoFora)
            }
        }
        assertTrue(semProlongamentoEmZeroZero >= 1, "um 0-0 com desvantagem na primeira mão não leva a prolongamento")
        assertTrue(comProlongamento >= 1, "um empate no agregado devia levar a prolongamento")
    }

    @Test
    fun `um jogo sem exigencia de vencedor acaba aos 90 como antes`() {
        val bd = carregar(); val t = equipasValidas(bd)
        for (k in 0 until 6) {
            val m = motor(t[k], t[k + 6], 5000 + k, 60f, semUtilizador = true)
            jogarAteAoFim(m)
            val rel = FabricaPartida.relatorio(m)
            assertFalse(rel.prolongamento)
            assertEquals(-1, rel.penaltisCasa)
            assertTrue(m.estado.periodo <= 1)
        }
    }

    @Test
    fun `um jogo de liga nao exige vencedor`() {
        val bd = carregar()
        val c = Carreira.nova(bd, "f13b", "Rui", ModoCarreira.TREINADOR, "POR_02", semente = 5)
        var guarda = 0
        while (c.proximoJogo()?.eLiga != true && guarda++ < 40) c.avancar()
        assertNull(c.exigenciaDoProximoJogo())
    }

    @Test
    fun `a carreira usa o prolongamento e os penaltis jogados ao vivo`() {
        val bd = carregar()
        val c = Carreira.nova(bd, "f13a", "Rui", ModoCarreira.TREINADOR, "POR_02", semente = 8)
        var guarda = 0
        while (c.proximoJogo()?.tipo != "taca" && guarda++ < 80) c.avancar()
        val proximo = assertNotNull(c.proximoJogo())
        assertEquals("taca", proximo.tipo)
        val (casa, fora) = assertNotNull(c.prepararJogoAoVivo())
        val exigencia = c.exigenciaDoProximoJogo()
        val confrontoAntes = assertNotNull(proximo.confronto)
        assertEquals(!confrontoAntes.idaEVolta, exigencia != null && confrontoAntes.jogos.isEmpty(), "num jogo único a exigência existe")
        // Procura um jogo que acabe nos penáltis
        var rel: pt.futebolcarreira.nucleo.simulacao.RelatorioJogo? = null
        for (semente in 0 until 250) {
            val m = motor(casa, fora, 8000 + semente, 60f, semUtilizador = true, exigencia = exigencia ?: ExigenciaVencedor())
            jogarAteAoFim(m, limite = 20_000)
            val r = FabricaPartida.relatorio(m)
            if (r.penaltisCasa >= 0) { rel = r; break }
        }
        val relatorio = assertNotNull(rel, "nenhum jogo foi a penáltis")
        val jogado = assertNotNull(c.registarJogoAoVivo(relatorio))
        val r = assertNotNull(jogado.resultado)
        assertTrue(r.relatorio === relatorio)
        assertEquals(relatorio.golosCasa, r.golosCasa)
        assertEquals(relatorio.golosFora, r.golosFora)
        assertTrue(r.prolongamento)
        val confronto = assertNotNull(jogado.confronto)
        if (!confronto.idaEVolta) {
            assertTrue(r.penaltis, "o resultado devia trazer os penáltis")
            assertEquals(relatorio.penaltisCasa, r.penaltisCasa)
            assertEquals(relatorio.penaltisFora, r.penaltisFora)
            val ganhou = if (relatorio.penaltisCasa > relatorio.penaltisFora) casa.id else fora.id
            assertEquals(ganhou, confronto.vencedor)
        }
    }

    // ---------------------------------------------------------------- inteligência e dificuldade

    @Test
    fun `a dificuldade tem quatro niveis cada vez mais exigentes`() {
        val n = Dificuldade.entries
        assertEquals(listOf("Fácil", "Normal", "Difícil", "Lendário"), n.map { it.nome })
        for (i in 1 until n.size) {
            assertTrue(n[i].linhaDefensiva > n[i - 1].linhaDefensiva, "linha defensiva")
            assertTrue(n[i].raioPressao > n[i - 1].raioPressao, "pressão")
            assertTrue(n[i].marcacao > n[i - 1].marcacao, "marcação")
            assertTrue(n[i].erroPasse < n[i - 1].erroPasse, "erro de passe")
            assertTrue(n[i].erroRemate < n[i - 1].erroRemate, "erro de remate")
            assertTrue(n[i].tempoDecisao < n[i - 1].tempoDecisao)
            assertTrue(n[i].ajudaMira < n[i - 1].ajudaMira, "a ajuda de mira desce com a dificuldade")
            assertTrue(n[i].velocidade >= n[i - 1].velocidade)
        }
        assertEquals(Dificuldade.NORMAL, Dificuldade.deTexto(null))
        assertEquals(Dificuldade.LENDARIO, Dificuldade.deTexto("lendário"))
        assertEquals(Dificuldade.DIFICIL, Dificuldade.deTexto("DIFICIL"))
    }

    @Test
    fun `a linha defensiva sobe com a dificuldade e acompanha a bola`() {
        val bd = carregar(); val t = equipasValidas(bd)
        fun linha(d: Dificuldade, bolaX: Float): Float {
            val m = motor(t[0], t[1], 12, semUtilizador = true, dif = d)
            correr(m, 3f)
            aJogar(m)
            m.dono = -1; m.bx = bolaX; m.by = 0f
            m.ultimoEq = 0                    // a equipa 0 tem a bola: a 1 defende
            m.calcularAlvos()
            // linha defensiva da equipa 1 no seu eixo de ataque
            return m.reg.linhaDef[1]
        }
        val facil = linha(Dificuldade.FACIL, 0f); val normal = linha(Dificuldade.NORMAL, 0f); val lendario = linha(Dificuldade.LENDARIO, 0f)
        assertTrue(facil < normal && normal < lendario, "$facil < $normal < $lendario")
        assertTrue(lendario - facil > 10f)
        val recuada = linha(Dificuldade.NORMAL, 40f); val subida = linha(Dificuldade.NORMAL, -40f)
        assertTrue(subida > recuada + 10f, "a linha acompanha a bola: $recuada -> $subida")
    }

    @Test
    fun `quem defende marca adversarios e a equipa pressiona`() {
        val bd = carregar(); val t = equipasValidas(bd)
        val m = motor(t[0], t[1], 13, semUtilizador = true, dif = Dificuldade.DIFICIL)
        var comMarcacao = 0; var amostras = 0
        repeat(900) {
            m.passo(1f / 30f, Comando())
            if (it % 30 == 0 && m.estado.fase == FaseJogo.A_JOGAR) {
                val defensores = if (m.posseEqParaTeste() == 0) 11 until 22 else 0 until 11
                amostras++
                if (defensores.count { m.reg.marca[it] >= 0 } >= 3) comMarcacao++
            }
        }
        assertTrue(amostras > 5)
        assertTrue(comMarcacao >= amostras / 2, "devia haver marcações ($comMarcacao de $amostras)")
    }

    private fun MotorAoVivo.posseEqParaTeste(): Int = if (dono >= 0) eq(dono) else ultimoEq

    @Test
    fun `a dificuldade muda o jogo - um guiao marca menos contra o Lendario do que contra o Facil`() {
        val bd = carregar(); val todas = equipasValidas(bd)
        fun guiao(m: MotorAoVivo, passo: Int): Comando {
            val e = m.estado
            val sentido = if (e.segundaParte) -1f else 1f
            val c = e.jogadores.firstOrNull { it.controlado } ?: return Comando()
            val perto = c.x * sentido > 25f
            return Comando(movX = sentido, movY = (-c.y / 30f).coerceIn(-0.5f, 0.5f), sprint = true, remate = perto && passo % 6 == 0, forca = 0.7f, trocar = passo % 40 == 0)
        }
        fun golos(d: Dificuldade): Int {
            var g = 0
            val rnd = Random(5)
            for (k in 0 until 16) {
                val a = todas[rnd.nextInt(todas.size)]; val b = todas[rnd.nextInt(todas.size)]
                if (a === b) continue
                val m = motor(a, b, 900 + k, 150f, dif = d)
                var passos = 0
                while (!m.terminou && passos < 8_000) { m.passo(1f / 30f, guiao(m, passos)); passos++ }
                g += m.golos[0]
            }
            return g
        }
        val facil = golos(Dificuldade.FACIL); val normal = golos(Dificuldade.NORMAL); val lendario = golos(Dificuldade.LENDARIO)
        println("F13 golos do guião: facil=$facil normal=$normal lendario=$lendario")
        assertTrue(facil > lendario, "o guião devia marcar mais ao Fácil ($facil) do que ao Lendário ($lendario)")
        assertTrue(facil >= normal || normal >= lendario)
    }

    // ---------------------------------------------------------------- jogos completos

    @Test
    fun `jogos IA contra IA completos nao ficam presos em nenhuma dificuldade`() {
        val bd = carregar(); val t = equipasValidas(bd)
        var lances = HashSet<TipoLance>()
        var k = 0
        for (d in Dificuldade.entries) {
            for (j in 0 until 4) {
                val m = motor(t[(k * 3) % 20], t[(k * 3 + 1) % 20], 7000 + k, 90f, semUtilizador = true, dif = d, exigencia = if (j % 2 == 0) ExigenciaVencedor() else null)
                jogarAteAoFim(m, limite = 30_000)
                val rel = FabricaPartida.relatorio(m)
                assertEquals(rel.golosCasa, m.estado.golosCasa)
                assertEquals(m.lances.size, m.estado.numeroDeLances)
                assertEquals(d, m.estado.dificuldade)
                lances += m.lances.map { it.tipo }
                for (f in rel.casa.fichas + rel.fora.fichas) assertTrue(f.nota in 1.0..10.0)
                k++
            }
        }
        println("F13 lances vistos: $lances")
        for (l in listOf(TipoLance.FALTA, TipoLance.LANCAMENTO, TipoLance.LIVRE, TipoLance.VANTAGEM)) assertTrue(l in lances, "nunca houve $l")
    }

    @Test
    fun `os jogos continuam deterministas com as regras novas`() {
        val bd = carregar(); val t = equipasValidas(bd)
        fun jogar(): List<Any> {
            val m = motor(t[5], t[8], 77, 100f, semUtilizador = true, dif = Dificuldade.DIFICIL, exigencia = ExigenciaVencedor())
            jogarAteAoFim(m, limite = 30_000)
            val rel = FabricaPartida.relatorio(m)
            return listOf(rel.golosCasa, rel.golosFora, rel.penaltisCasa, rel.penaltisFora, m.lances.map { it.tipo to it.minuto })
        }
        assertEquals(jogar(), jogar())
    }

    @Test
    fun `um utilizador parado nunca prende o jogo`() {
        val bd = carregar(); val t = equipasValidas(bd)
        val m = motor(t[1], t[2], 21, 90f, exigencia = ExigenciaVencedor())
        jogarAteAoFim(m, limite = 30_000)
        assertTrue(m.terminou)
    }
}
