package pt.futebolcarreira.aovivo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.futebolcarreira.nucleo.aovivo.Comando
import pt.futebolcarreira.nucleo.aovivo.ComandoExtra
import pt.futebolcarreira.nucleo.aovivo.FaseJogo
import pt.futebolcarreira.nucleo.aovivo.ModoPartida
import pt.futebolcarreira.nucleo.aovivo.NivelMira
import pt.futebolcarreira.nucleo.aovivo.Palestra
import pt.futebolcarreira.nucleo.aovivo.TipoPasse
import pt.futebolcarreira.nucleo.aovivo.TipoRemate
import kotlin.math.PI
import kotlin.math.abs

/** Controlos avançados, disposição dos botões, modos de jogo e palestra, sem OpenGL nem Robolectric. */
class ControlosModosTest {
    private class Relogio { var ns = 0L; fun avanca(s: Float) { ns += (s * 1e9f).toLong() } }

    // ------------------------------------------------------------ controlos

    @Test
    fun passeEmProfundidadeECruzamentoViramTipos() {
        val r = Relogio()
        val c = ControloTactil { r.ns }
        c.premir(BotaoId.PROFUNDIDADE); r.avanca(0.1f); c.soltar(BotaoId.PROFUNDIDADE)
        val a = c.consumirCompleto(1)
        assertTrue(a.comando.passe); assertEquals(TipoPasse.PROFUNDIDADE, a.extra.tipoPasse)
        c.premir(BotaoId.CRUZAMENTO); r.avanca(0.8f); c.soltar(BotaoId.CRUZAMENTO)
        val b = c.consumirCompleto(1)
        assertEquals(TipoPasse.CRUZAMENTO, b.extra.tipoPasse)
        assertTrue(b.comando.forca > 0.6f)
        // Só vale uma vez.
        assertFalse(c.consumirCompleto(1).comando.passe)
        assertEquals(TipoPasse.NORMAL, c.consumirCompleto(1).extra.tipoPasse)
    }

    @Test
    fun setaDoPasseDaOAnguloNoCampoEInverteComOSentido() {
        val r = Relogio()
        val c = ControloTactil { r.ns }
        c.premir(BotaoId.PASSE); c.seta(0f, 1f); r.avanca(0.1f); c.soltar(BotaoId.PASSE)   // arrasto para cima no ecrã
        val a = c.consumirCompleto(1)
        assertEquals(TipoPasse.DIRIGIDO, a.extra.tipoPasse)
        assertEquals(PI.toFloat() / 2, a.extra.anguloPasse!!, 0.001f)
        c.premir(BotaoId.PASSE); c.seta(1f, 0f); c.soltar(BotaoId.PASSE)
        val b = c.consumirCompleto(-1)   // câmara virada: para a direita do ecrã é -x no campo
        assertEquals(PI.toFloat(), abs(b.extra.anguloPasse!!), 0.001f)
        // Um arrasto pequeno não conta como direção.
        c.premir(BotaoId.PASSE); c.seta(0.1f, 0.1f); c.soltar(BotaoId.PASSE)
        val d = c.consumirCompleto(1)
        assertEquals(TipoPasse.NORMAL, d.extra.tipoPasse); assertNull(d.extra.anguloPasse)
    }

    @Test
    fun rematesColocadoEPorCimaTemTipo() {
        val r = Relogio()
        val c = ControloTactil { r.ns }
        c.premir(BotaoId.COLOCADO); r.avanca(0.5f); c.soltar(BotaoId.COLOCADO)
        val a = c.consumirCompleto(1)
        assertTrue(a.comando.remate); assertEquals(TipoRemate.COLOCADO, a.extra.tipoRemate)
        c.premir(BotaoId.POR_CIMA); r.avanca(0.5f); c.soltar(BotaoId.POR_CIMA)
        assertEquals(TipoRemate.POR_CIMA, c.consumirCompleto(1).extra.tipoRemate)
        c.rematePremido(); r.avanca(0.5f); c.remateSolto()
        assertEquals(TipoRemate.NORMAL, c.consumirCompleto(1).extra.tipoRemate)
    }

    @Test
    fun fintaDuraUmPoucoEAlternaDeLadoEProtegerSegueOBotao() {
        val r = Relogio()
        val c = ControloTactil { r.ns }
        c.finta()
        val a = c.consumirCompleto(1)
        assertTrue(a.extra.finta)
        r.avanca(0.1f)
        assertTrue(c.consumirCompleto(1).extra.finta)
        r.avanca(0.5f)
        assertFalse(c.consumirCompleto(1).extra.finta)
        c.finta()
        assertTrue(c.consumirCompleto(1).extra.fintaLado != a.extra.fintaLado)
        c.premir(BotaoId.PROTEGER)
        assertTrue(c.consumirCompleto(1).extra.proteger); assertTrue(c.consumirCompleto(1).extra.proteger)
        c.soltar(BotaoId.PROTEGER)
        assertFalse(c.consumirCompleto(1).extra.proteger)
        c.premir(BotaoId.PROTEGER); c.finta(); c.limpar()
        val l = c.consumirCompleto(1)
        assertFalse(l.extra.proteger); assertFalse(l.extra.finta)
    }

    @Test
    fun consumirSimplesContinuaIgual() {
        val c = ControloTactil { 0L }
        c.joystick(1f, 0f)
        val a = c.consumir(1)
        assertTrue(a.movX > 0.9f); assertFalse(a.passe)
    }

    // ------------------------------------------------------------ disposição dos botões

    @Test
    fun disposicaoPadraoTemTodosOsBotoesDentroDoEcra() {
        val d = DisposicaoBotoes()
        assertEquals(BotaoId.entries.size, d.botoes.size)
        for (b in d.botoes) { assertTrue(b.x in 0.04f..0.96f && b.y in 0.08f..0.94f); assertTrue(b.ativo) }
        // Os botões não ficam na zona do joystick (45% da largura, em baixo).
        assertTrue(d.botoes.all { it.x > 0.5f })
    }

    @Test
    fun disposicaoGuardaELeSemPerdas() {
        val d = DisposicaoBotoes().com(ConfigBotao(BotaoId.FINTA, 0.6f, 0.4f, 1.3f, false)).com(ConfigBotao(BotaoId.REMATE, 0.9f, 0.9f, 0.8f, true))
        val l = DisposicaoBotoes.ler(d.serializar())
        assertEquals(d, l)
        assertEquals(false, l[BotaoId.FINTA].ativo)
        assertEquals(1.3f, l[BotaoId.FINTA].escala, 0.01f)
    }

    @Test
    fun disposicaoEstragadaOuAntigaVoltaAoPadrao() {
        assertEquals(DisposicaoBotoes(), DisposicaoBotoes.ler(null))
        assertEquals(DisposicaoBotoes(), DisposicaoBotoes.ler("lixo;;::"))
        assertEquals(DisposicaoBotoes(), DisposicaoBotoes.ler("REMATE:x:y:z:1;INEXISTENTE:0.5:0.5:1:1"))
        // Só um botão guardado: os outros ficam no padrão.
        val l = DisposicaoBotoes.ler("REMATE:0.700:0.500:1.00:1")
        assertEquals(0.7f, l[BotaoId.REMATE].x, 0.001f)
        assertEquals(DisposicaoBotoes.padraoDe(BotaoId.PASSE), l[BotaoId.PASSE])
        // Valores fora dos limites são cortados.
        val f = DisposicaoBotoes.ler("PASSE:5.000:-3.000:9.00:1")
        assertEquals(0.96f, f[BotaoId.PASSE].x, 0.001f); assertEquals(0.08f, f[BotaoId.PASSE].y, 0.001f)
        assertEquals(DisposicaoBotoes.ESCALA_MAX, f[BotaoId.PASSE].escala, 0.001f)
    }

    // ------------------------------------------------------------ sessão: mira e extras

    @Test
    fun sessaoRefinaPasseComAjudaDeMiraEProfundidade() {
        val p = PartidaTaticaFalsa()
        val forte = SessaoAoVivo(p, true, nivelMira = NivelMira.FORTE)
        // Joystick quase para o colega (20, 6): ~16,7 graus; o jogador aponta a 5 graus.
        val comando = Comando(movX = 0.996f, movY = 0.087f, passe = true)
        val e = forte.refinar(comando, ComandoExtra.SEM_EXTRA, p.estado)
        assertEquals(TipoPasse.DIRIGIDO, e.tipoPasse)
        assertTrue(e.anguloPasse!! > 0.087f)
        val nenhuma = SessaoAoVivo(p, true, nivelMira = NivelMira.NENHUMA)
        assertTrue(nenhuma.refinar(comando, ComandoExtra.SEM_EXTRA, p.estado).vazio)
        // Profundidade para a frente (sentido +1 = +x) e o colega à frente apanha o passe.
        val prof = forte.refinar(comando.copy(movX = 0f, movY = 0f), ComandoExtra(TipoPasse.PROFUNDIDADE), p.estado)
        assertNotNull(prof.anguloPasse)
        assertTrue(abs(prof.anguloPasse!!) < 0.4f)
        // Remate colocado: o GR rival está para o lado + (y=1,5), logo o canto é o -.
        val rem = forte.refinar(Comando(remate = true), ComandoExtra(tipoRemate = TipoRemate.COLOCADO), p.estado)
        assertTrue(rem.anguloRemate!! < 0f)
        // O joystick manda no lado.
        val lado = forte.refinar(Comando(remate = true, movY = 1f), ComandoExtra(tipoRemate = TipoRemate.COLOCADO), p.estado)
        assertTrue(lado.anguloRemate!! > 0f)
    }

    @Test
    fun sessaoEnviaExtrasAoMotorQueOsPercebe() {
        val p = PartidaTaticaFalsa()
        val s = SessaoAoVivo(p, true, nivelMira = NivelMira.NENHUMA)
        s.controlo.finta()
        s.avancar(0.05f)
        assertTrue(p.ultimoExtra!!.finta)
    }

    // ------------------------------------------------------------ modos de jogo

    @Test
    fun modosDeJogoLigamAoNucleo() {
        assertEquals(ModoPartida.MOMENTOS_CHAVE, ModoJogos.JOGAR_MOMENTOS.modoPartida)
        assertEquals(ModoPartida.COMPLETO, ModoJogos.JOGAR_3D.modoPartida)
        assertEquals(ModoPartida.VER, ModoJogos.VER_3D.modoPartida)
        assertEquals(ModoPartida.SIMULAR, ModoJogos.SIMULAR.modoPartida)
        assertEquals(ModoJogos.JOGAR_MOMENTOS, ModoJogos.deNome("JOGAR_MOMENTOS"))
        assertTrue(ModoPartida.MOMENTOS_CHAVE.saltaTempoMorto && ModoPartida.MOMENTOS_CHAVE.controla)
        assertFalse(ModoPartida.COMPLETO.saltaTempoMorto)
    }

    @Test
    fun momentosChaveSaltaOTempoMortoEJogaOsLancesPerigosos() {
        val p = PartidaTaticaFalsa()
        val s = SessaoAoVivo(p, true, momentosChave = true)
        // Bola a meio, sem perigo: avança vários passos por imagem, com a IA a jogar pelo utilizador.
        p.bx = 0f
        val antes = p.passos
        s.avancar(0.016f)
        assertTrue("devia avançar depressa", p.passos - antes > 1)
        assertTrue(s.avancandoDepressa)
        assertTrue(p.iaControlaUtilizador)
        // Bola perto da baliza: volta ao ritmo normal e ao comando do utilizador.
        p.bx = 42f; p.by = 2f
        val antes2 = p.passos
        s.avancar(0.016f)
        assertEquals(1, p.passos - antes2)
        assertFalse(s.avancandoDepressa)
        assertFalse(p.iaControlaUtilizador)
        // Um golo mantém-se em tempo normal.
        p.bx = 0f; p.fase = FaseJogo.GOLO
        val antes3 = p.passos
        s.avancar(0.016f)
        assertEquals(1, p.passos - antes3)
    }

    @Test
    fun jogoCompletoNuncaSalta() {
        val p = PartidaTaticaFalsa()
        val s = SessaoAoVivo(p, true)
        val antes = p.passos
        s.avancar(0.016f)
        assertEquals(1, p.passos - antes)
        assertFalse(s.avancandoDepressa)
    }

    // ------------------------------------------------------------ palestra e táticas

    @Test
    fun intervaloParaAteHaPalestraEAplicaOEfeito() {
        val p = PartidaTaticaFalsa()
        val s = SessaoAoVivo(p, true)
        p.fase = FaseJogo.INTERVALO
        s.avancar(0.05f)
        assertTrue(s.aguardaPalestra)
        val n = p.passos
        repeat(20) { s.avancar(0.05f) }
        assertEquals("em espera nada avança", n, p.passos)
        val d = s.resolverPalestra(Palestra.MOTIVAR)
        assertTrue(d > 0)
        assertEquals(listOf(Palestra.MOTIVAR), p.palestras)
        assertFalse(s.aguardaPalestra)
        repeat(5) { s.avancar(0.05f) }
        assertTrue(p.passos > n)
        // Só se pergunta uma vez.
        p.fase = FaseJogo.INTERVALO
        s.avancar(0.05f)
        assertFalse(s.aguardaPalestra)
    }

    @Test
    fun semTaticaOuSoAVerNaoHaPalestra() {
        val p = PartidaTaticaFalsa()
        val ver = SessaoAoVivo(p, true, controlar = false)
        p.fase = FaseJogo.INTERVALO
        ver.avancar(0.05f)
        assertFalse(ver.aguardaPalestra)
        val sem = SessaoAoVivo(pt.futebolcarreira.nucleo.aovivo.PartidaFalsa(
            pt.futebolcarreira.nucleo.modelos.Equipa(id = "a", nome = "A", abreviatura = "A"),
            pt.futebolcarreira.nucleo.modelos.Equipa(id = "b", nome = "B", abreviatura = "B"), true,
        ), true)
        assertFalse(sem.tatica.suportado)
        sem.avancar(0.05f)
        assertFalse(sem.aguardaPalestra)
    }

    @Test
    fun avisoDeCansacoSoComSubstituicoesPorUsar() {
        val p = PartidaTaticaFalsa()   // o "Cansado" tem 20 de energia
        val s = SessaoAoVivo(p, true)
        repeat(60) { s.avancar(0.05f) }
        assertTrue(s.avisoCansaco)
        p.feitas = 5
        repeat(60) { s.avancar(0.05f) }
        assertFalse(s.avisoCansaco)
    }
}
