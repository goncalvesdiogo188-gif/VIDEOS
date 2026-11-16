package pt.futebolcarreira.aovivo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.futebolcarreira.nucleo.aovivo.PartidaFalsa
import pt.futebolcarreira.nucleo.modelos.Equipa
import kotlin.math.abs
import kotlin.random.Random

/** Testes sem OpenGL nem Robolectric: câmara, joystick, controlos, cenário e sessão. */
class MatematicaAoVivoTest {
    // ------------------------------------------------------------- câmara

    @Test
    fun camaraSegueABolaEFicaDentroDoCampo() {
        val c = CameraTransmissao()
        c.atualizar(10f, 5f, 0.016f)
        assertEquals(10f, c.alvoX, 0.001f)   // a primeira chamada salta para a bola
        // A bola vai para a linha de fundo: o alvo para antes (margem) e chega lá devagar.
        repeat(2) { c.atualizar(52f, 30f, 0.016f) }
        assertTrue("devagar", c.alvoX < 20f)
        repeat(600) { c.atualizar(52f, 30f, 0.016f) }
        assertEquals(52.5f - 15f, c.alvoX, 0.05f)
        assertEquals(30f * CameraTransmissao.SEGUE_Y, c.alvoY, 0.05f)
    }

    @Test
    fun suavizacaoNaoDependeDoPasso() {
        val a = CameraTransmissao(); val b = CameraTransmissao()
        a.atualizar(0f, 0f, 0.016f); b.atualizar(0f, 0f, 0.016f)
        repeat(60) { a.atualizar(20f, 0f, 1f / 60) }
        repeat(30) { b.atualizar(20f, 0f, 1f / 30) }
        assertEquals(a.alvoX, b.alvoX, 0.2f)
    }

    @Test
    fun bolaSempreNaVistaComCameraAssentada() {
        val proporcoes = listOf(16f / 9f, 2.1f)
        for (sentido in listOf(1, -1)) for (p in proporcoes) {
            var x = -52.5f
            while (x <= 52.5f) {
                var y = -34f
                while (y <= 34f) {
                    val c = CameraTransmissao()
                    c.atualizar(x, y, 0.016f, sentido)   // câmara já assente na bola
                    assertTrue("bola ($x,$y) sentido $sentido prop $p", c.naVista(x, y, 0f, p, margem = 0.05f))
                    y += 8.5f
                }
                x += 10.5f
            }
        }
    }

    @Test
    fun sentidoTrocaOLadoDaCameraEOCentroFicaNoEcra() {
        val a = CameraTransmissao()
        a.atualizar(0f, 0f, 0.016f, 1)
        val b = CameraTransmissao()
        b.atualizar(0f, 0f, 0.016f, -1)
        assertTrue(a.olhoY < 0f && b.olhoY > 0f)
        // Com sentido +1 o +x fica à direita; com -1 fica à esquerda.
        assertTrue(a.projetar(10f, 0f, 0f, 2f)!!.first > 0f)
        assertTrue(b.projetar(10f, 0f, 0f, 2f)!!.first < 0f)
        // O alvo está ao centro do ecrã.
        val p = a.projetar(a.alvoX, a.alvoY, 0f, 2f)!!
        assertEquals(0f, p.first, 0.001f)
        assertEquals(0f, p.second, 0.001f)
        // Atrás da câmara: não projeta.
        assertNull(a.projetar(0f, -200f, 0f, 2f))
    }

    // ----------------------------------------------------------- joystick

    @Test
    fun joystickZonaMorta() {
        assertEquals(0f to 0f, Joystick.paraCampo(0.1f, 0.05f, 1))
        assertEquals(0f to 0f, Joystick.paraCampo(0f, 0f, 1))
        val (x, y) = Joystick.paraCampo(0.2f, 0f, 1)
        assertTrue(x > 0f && x < 0.2f)   // a zona morta é retirada
        assertEquals(0f, y, 0.0001f)
    }

    @Test
    fun joystickEsticaAteUmEFicaLimitado() {
        val (x, y) = Joystick.paraCampo(1f, 0f, 1)
        assertEquals(1f, x, 0.001f); assertEquals(0f, y, 0.001f)
        val (x2, y2) = Joystick.paraCampo(3f, 4f, 1)   // fora do círculo: tem de dar no máximo 1
        assertEquals(1f, kotlin.math.hypot(x2, y2), 0.001f)
        assertEquals(0.6f, x2, 0.001f)
    }

    @Test
    fun joystickRelativoAoLadoDeAtaque() {
        // Para a frente (para a direita no ecrã) é +x quando se ataca para +x e -x quando se ataca para -x.
        assertEquals(1f, Joystick.paraCampo(1f, 0f, 1).first, 0.001f)
        assertEquals(-1f, Joystick.paraCampo(1f, 0f, -1).first, 0.001f)
        // Para cima no ecrã é +y num lado e -y no outro.
        assertEquals(1f, Joystick.paraCampo(0f, 1f, 1).second, 0.001f)
        assertEquals(-1f, Joystick.paraCampo(0f, 1f, -1).second, 0.001f)
    }

    // ----------------------------------------------------------- controlos

    private class Relogio { var ns = 0L; fun avanca(s: Float) { ns += (s * 1e9f).toLong() } }

    @Test
    fun passeCurtoELongo() {
        val r = Relogio()
        val c = ControloTactil { r.ns }
        c.passePremido(); r.avanca(0.1f); c.passeSolto()
        val curto = c.consumir(1)
        assertTrue(curto.passe); assertEquals(0.3f, curto.forca, 0.001f)
        // Só vale uma vez.
        assertFalse(c.consumir(1).passe)
        c.passePremido(); r.avanca(0.65f)
        assertTrue(c.forcaPasseAgora() in 0.6f..0.8f)
        r.avanca(5f); c.passeSolto()
        val longo = c.consumir(1)
        assertTrue(longo.passe); assertEquals(1f, longo.forca, 0.001f)
    }

    @Test
    fun remateTemForcaPeloTempoPremido() {
        val r = Relogio()
        val c = ControloTactil { r.ns }
        c.rematePremido(); r.avanca(0.45f); c.remateSolto()
        val med = c.consumir(1)
        assertTrue(med.remate); assertEquals(0.5f, med.forca, 0.01f)
        c.rematePremido(); r.avanca(3f); c.remateSolto()
        assertEquals(1f, c.consumir(1).forca, 0.001f)
        assertFalse(c.consumir(1).remate)
    }

    @Test
    fun sprintTrocarEJoystickNoComando() {
        val c = ControloTactil { 0L }
        c.joystick(1f, 0f); c.sprint(true); c.trocar()
        val a = c.consumir(-1)
        assertEquals(-1f, a.movX, 0.001f)
        assertTrue(a.sprint && a.trocar)
        val b = c.consumir(-1)
        assertFalse(b.trocar)
        assertTrue(b.sprint)   // sprint e joystick mantêm-se
        c.limpar()
        val d = c.consumir(1)
        assertEquals(0f, d.movX, 0f); assertFalse(d.sprint)
    }

    // ------------------------------------------------------------ cenário

    @Test
    fun cenarioDentroDosLimitesEComBalizas() {
        val chao = CenarioCampo.chao()
        assertTrue(chao.size > 25)
        // Linhas do campo: todas dentro do relvado.
        val linhas = chao.filter { it.cor == 0xFFF5F5F0.toInt() }
        assertTrue(linhas.size >= 20)
        for (p in linhas) assertTrue(abs(p.x) <= 52.6f && abs(p.y) <= 34.1f)
        val balizas = CenarioCampo.balizas()
        assertEquals(2, balizas.count { it.sy == CenarioCampo.BALIZA_LARGURA + 0.14f })   // as duas barras
        assertTrue(balizas.any { it.transparente })
        // As traves ficam em y = ±3,66.
        assertTrue(balizas.any { abs(abs(it.y) - 3.66f) < 0.001f })
        assertTrue(CenarioCampo.bancadas().isNotEmpty())
        assertEquals(64, CenarioCampo.circulo(32).size)
    }

    @Test
    fun malhasTemTriangulosCompletos() {
        val m = Malhas.criar()
        assertEquals(36, m.caixa.vertices)
        for (x in listOf(m.caixa, m.esfera, m.disco, m.anel, m.anelGrosso)) {
            assertEquals(0, x.vertices % 3)
            assertTrue(x.vertices > 0)
            assertTrue((x.inicio + x.vertices) * Malhas.FLOATS_POR_VERTICE <= m.dados.size)
        }
        assertEquals(m.dados.size, (m.anelGrosso.inicio + m.anelGrosso.vertices) * Malhas.FLOATS_POR_VERTICE)
    }

    // -------------------------------------------------------------- sessão

    private fun equipa(id: String, cores: List<String>) = Equipa(id = id, nome = "Clube $id", abreviatura = id.uppercase(), cores = cores)

    @Test
    fun sessaoJogaAteAoFimComOMotorFalso() {
        val p = PartidaFalsa(equipa("aaa", listOf("#ff0000", "#ffffff")), equipa("bbb", listOf("#0000ff", "#ffffff")), true, Random(3), 60f)
        val s = SessaoAoVivo(p, utilizadorCasa = true)
        assertEquals(22, p.estado.jogadores.size)
        assertEquals(1, p.estado.jogadores.count { it.controlado })
        assertEquals(1, s.sentido())
        var passos = 0
        // Primeira parte: a casa ataca para +x; na segunda inverte-se.
        while (!p.estado.segundaParte && passos++ < 10_000) s.avancar(0.05f)
        assertTrue(p.estado.segundaParte)
        assertEquals(-1, s.sentido())
        while (!p.terminou && passos++ < 20_000) s.avancar(0.05f)
        assertTrue(p.terminou)
        assertEquals(90, p.estado.minuto)
        // Depois do fim já não avança.
        val antes = p.estado
        s.avancar(0.05f)
        assertTrue(antes === p.estado)
    }

    @Test
    fun sentidoDoUtilizadorForaDeCasa() {
        val p = PartidaFalsa(equipa("aaa", listOf("#ff0000", "#ffffff")), equipa("bbb", listOf("#0000ff", "#ffffff")), false, Random(3), 60f)
        assertEquals(-1, SessaoAoVivo(p, utilizadorCasa = false).sentido())
    }

    @Test
    fun pausaEsimularRestoNaSessao() {
        val p = PartidaFalsa(equipa("aaa", listOf("#ff0000", "#ffffff")), equipa("bbb", listOf("#0000ff", "#ffffff")), true, Random(5), 60f)
        val s = SessaoAoVivo(p, true)
        s.pausado = true
        val m0 = p.estado.minuto
        repeat(100) { s.avancar(0.05f) }
        assertEquals(m0, p.estado.minuto)   // em pausa nada avança
        assertNotNull(p.estado)
        assertTrue(s.simularResto() > 0)
        assertTrue(p.terminou)
    }

    @Test
    fun aVerNaoEnviaComandos() {
        val p = PartidaFalsa(equipa("aaa", listOf("#ff0000", "#ffffff")), equipa("bbb", listOf("#0000ff", "#ffffff")), true, Random(5), 600f)
        val s = SessaoAoVivo(p, true, controlar = false)
        s.controlo.joystick(1f, 0f)
        val x0 = p.estado.jogadores.first { it.controlado }.x
        repeat(20) { s.avancar(0.05f) }
        // Sem comando o jogador não corre à vontade do joystick (a posição segue só a formação).
        assertTrue(abs(p.estado.jogadores.first { it.controlado }.x - x0) < 6f)
    }

    @Test
    fun modoDeJogosGuardaNome() {
        assertEquals(ModoJogos.SIMULAR, ModoJogos.deNome(null))
        assertEquals(ModoJogos.SIMULAR, ModoJogos.deNome("lixo"))
        assertEquals(ModoJogos.JOGAR_3D, ModoJogos.deNome("JOGAR_3D"))
    }
}
