package pt.futebolcarreira.nucleo

import pt.futebolcarreira.nucleo.aovivo.AdaptadorAcoes
import pt.futebolcarreira.nucleo.aovivo.Bola
import pt.futebolcarreira.nucleo.aovivo.Comando
import pt.futebolcarreira.nucleo.aovivo.ComandoExtra
import pt.futebolcarreira.nucleo.aovivo.ControloTatico
import pt.futebolcarreira.nucleo.aovivo.DetetorMomentos
import pt.futebolcarreira.nucleo.aovivo.DuracaoJogo
import pt.futebolcarreira.nucleo.aovivo.EstadoPartida
import pt.futebolcarreira.nucleo.aovivo.FabricaPartida
import pt.futebolcarreira.nucleo.aovivo.FaseJogo
import pt.futebolcarreira.nucleo.aovivo.GestorSubstituicoes
import pt.futebolcarreira.nucleo.aovivo.JogadorTatico
import pt.futebolcarreira.nucleo.aovivo.Mira
import pt.futebolcarreira.nucleo.aovivo.NivelMira
import pt.futebolcarreira.nucleo.aovivo.Palestra
import pt.futebolcarreira.nucleo.aovivo.ResultadoSubstituicao
import pt.futebolcarreira.nucleo.aovivo.TipoPasse
import pt.futebolcarreira.nucleo.aovivo.TipoRemate
import pt.futebolcarreira.nucleo.aovivo.controloTatico
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.Mentalidade
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Fase E (E2, E4, E5): controlos avançados, táticas ao vivo e modos de jogo. */
class FaseETaticasModosTest {
    private val pastaDados = File("../app/src/main/assets/dados")
    private val ler = { caminho: String -> File(pastaDados, caminho).takeIf(File::exists)?.readText() }

    private fun estado(fase: FaseJogo = FaseJogo.A_JOGAR, bx: Float = 0f, by: Float = 0f, minuto: Int = 30, gc: Int = 0, gf: Int = 0) = EstadoPartida(
        minuto, 0f, gc, gf, fase, false, emptyList(), Bola(bx, by, 0f), 0xFFFF0000L to 0xFFFFFFFFL, 0xFF0000FFL to 0xFFFFFFFFL, "AAA", "BBB", null,
    )

    // ------------------------------------------------------------------ E2

    @Test
    fun `passe dirigido e profundidade viram direcao e forca no comando basico`() {
        val base = Comando(movX = 0.2f, movY = 0.2f, passe = true, forca = 0.3f)
        val dir = AdaptadorAcoes.paraBasico(base, ComandoExtra(TipoPasse.DIRIGIDO, anguloPasse = PI.toFloat() / 2), 1)
        assertEquals(0f, dir.movX, 0.001f); assertEquals(1f, dir.movY, 0.001f)
        val prof = AdaptadorAcoes.paraBasico(base, ComandoExtra(TipoPasse.PROFUNDIDADE), -1)
        assertEquals(-1f, prof.movX, 0.001f)
        assertTrue(prof.forca >= AdaptadorAcoes.FORCA_PROFUNDIDADE)
        val cruz = AdaptadorAcoes.paraBasico(base, ComandoExtra(TipoPasse.CRUZAMENTO), 1)
        assertEquals(1f, cruz.forca, 0.001f)
    }

    @Test
    fun `remate colocado limita a forca e por cima usa forca maxima`() {
        val base = Comando(remate = true, forca = 1f)
        val col = AdaptadorAcoes.paraBasico(base, ComandoExtra(tipoRemate = TipoRemate.COLOCADO, anguloRemate = -0.3f), 1)
        assertEquals(AdaptadorAcoes.FORCA_COLOCADO_MAX, col.forca, 0.001f)
        assertTrue(col.movY < 0f)
        val fraco = AdaptadorAcoes.paraBasico(Comando(remate = true, forca = 0.1f), ComandoExtra(tipoRemate = TipoRemate.COLOCADO), 1)
        assertEquals(AdaptadorAcoes.FORCA_COLOCADO_MIN, fraco.forca, 0.001f)
        val alto = AdaptadorAcoes.paraBasico(Comando(remate = true, forca = 0.2f), ComandoExtra(tipoRemate = TipoRemate.POR_CIMA), 1)
        assertEquals(1f, alto.forca, 0.001f)
    }

    @Test
    fun `finta roda o movimento e proteger abranda`() {
        val f = AdaptadorAcoes.paraBasico(Comando(movX = 1f), ComandoExtra(finta = true, fintaLado = 1), 1)
        assertTrue(f.sprint); assertTrue(f.movY > 0.9f); assertTrue(abs(f.movX) < 0.4f)
        val p = AdaptadorAcoes.paraBasico(Comando(movX = 1f, sprint = true), ComandoExtra(proteger = true), 1)
        assertFalse(p.sprint); assertEquals(AdaptadorAcoes.ESCALA_PROTEGER, p.movX, 0.001f)
    }

    @Test
    fun `ajuda de mira puxa para o colega dentro do cone e respeita o nivel`() {
        val colega = listOf(Mira.Alvo(20f, 6f))   // ~16,7 graus
        val ang = atan2(6f, 20f)
        val sem = 0f
        assertEquals(sem, Mira.ajustarPasse(sem, colega, NivelMira.NENHUMA), 0f)
        val forte = Mira.ajustarPasse(sem, colega, NivelMira.FORTE)
        val media = Mira.ajustarPasse(sem, colega, NivelMira.MEDIA)
        assertTrue(forte > media && media > 0f && forte <= ang)
        // Fora do cone do nível fraco (16 graus): não mexe.
        assertEquals(sem, Mira.ajustarPasse(sem, colega, NivelMira.FRACA), 0f)
        // Colegas ao pé ou longe de mais são ignorados.
        assertEquals(sem, Mira.ajustarPasse(sem, listOf(Mira.Alvo(2f, 0.5f), Mira.Alvo(80f, 1f)), NivelMira.FORTE), 0f)
        // Ângulos em torno de PI não dão saltos.
        val volta = Mira.ajustarPasse(PI.toFloat() - 0.05f, listOf(Mira.Alvo(-20f, -2f)), NivelMira.FORTE)
        assertTrue(abs(Mira.difAngulo(volta, PI.toFloat())) < 0.2f)
    }

    @Test
    fun `nivel de mira segue a dificuldade`() {
        assertEquals(NivelMira.FORTE, NivelMira.daDificuldade("Fácil"))
        assertEquals(NivelMira.FORTE, NivelMira.daDificuldade("FACIL"))
        assertEquals(NivelMira.MEDIA, NivelMira.daDificuldade("NORMAL"))
        assertEquals(NivelMira.MEDIA, NivelMira.daDificuldade(null))
        assertEquals(NivelMira.FRACA, NivelMira.daDificuldade("DIFICIL"))
        assertEquals(NivelMira.NENHUMA, NivelMira.daDificuldade("LENDARIO"))
    }

    @Test
    fun `canto da baliza fica longe do guarda-redes`() {
        val a = Mira.anguloCanto(30f, 0f, 52.5f, grY = 1.5f)
        assertTrue(a < 0f)   // GR ao lado positivo: o remate vai para o lado negativo
        val b = Mira.anguloCanto(30f, 0f, 52.5f, grY = -1.5f)
        assertTrue(b > 0f)
        assertTrue(Mira.anguloCanto(30f, 0f, 52.5f, null, lado = 1) > 0f)
        val c = Mira.anguloCruzamento(40f, 25f, 52.5f)
        assertTrue(c < -PI.toFloat() / 2 || c in -2f..-0.5f)
        assertTrue(Mira.anguloProfundidade(-1, 1f) < PI.toFloat() + 1f && abs(Mira.anguloProfundidade(1, 0f)) < 0.001f)
    }

    // ------------------------------------------------------------------ E4

    private fun tatico(id: Int, lugar: String, energia: Int = 100, lesionado: Boolean = false, geral: Int = 60, posicao: String = lugar) =
        JogadorTatico(id, "J$id", posicao, lugar, id, energia, lesionado, 0, geral)

    @Test
    fun `substituicoes tem maximo de cinco e regra do guarda-redes`() {
        assertEquals(ResultadoSubstituicao.FEITA, GestorSubstituicoes.validar(4, "MC", "MC"))
        assertEquals(ResultadoSubstituicao.SEM_SUBSTITUICOES, GestorSubstituicoes.validar(5, "MC", "MC"))
        assertEquals(ResultadoSubstituicao.GR_INCOMPATIVEL, GestorSubstituicoes.validar(0, "GR", "MC"))
        assertEquals(ResultadoSubstituicao.GR_INCOMPATIVEL, GestorSubstituicoes.validar(0, "MC", "GR"))
        assertEquals(ResultadoSubstituicao.JOGADOR_INVALIDO, GestorSubstituicoes.validar(0, "MC", "MC", entraDisponivel = false))
        assertEquals(ResultadoSubstituicao.JOGADOR_INVALIDO, GestorSubstituicoes.validar(0, null, "MC"))
    }

    @Test
    fun `cansaco e lesao ordenam quem deve sair e quem entra`() {
        val onze = listOf(tatico(1, "GR"), tatico(2, "DC", energia = 40), tatico(3, "MC", energia = 20), tatico(4, "PL", energia = 80), tatico(5, "DD", lesionado = true, energia = 90))
        val ordem = GestorSubstituicoes.candidatosASair(onze).map { it.id }
        assertEquals(listOf(5, 3, 2), ordem)
        assertTrue(onze[2].muitoCansado && onze[1].cansado && !onze[3].cansado)
        val banco = listOf(tatico(20, "", geral = 70, posicao = "MC", lesionado = true), tatico(21, "", geral = 65, posicao = "MC"), tatico(22, "", geral = 80, posicao = "DC"), tatico(23, "", posicao = "GR"))
        assertEquals(21, GestorSubstituicoes.melhorEntrada("MC", banco)?.id)   // o do lugar, sem lesionados
        assertEquals(23, GestorSubstituicoes.melhorEntrada("GR", banco)?.id)
    }

    @Test
    fun `palestra tem efeito pequeno e depende do resultado`() {
        for (p in Palestra.entries) for (d in -3..3) assertTrue(p.efeitoMoral(d, 0) in -2..4)
        assertTrue(Palestra.MOTIVAR.efeitoMoral(0, 1) > Palestra.MOTIVAR.efeitoMoral(1, 0) - 1)
        assertTrue(Palestra.PRESSIONAR.efeitoMoral(0, 2) > 0)
        assertTrue(Palestra.PRESSIONAR.efeitoMoral(2, 0) < 0)
        assertEquals(0, Palestra.NADA.efeitoMoral(0, 0))
        assertEquals(Palestra.ACALMAR, Palestra.deNome("ACALMAR"))
    }

    @Test
    fun `controlo tatico por omissao nao faz nada`() {
        val c = ControloTatico.NENHUM
        assertFalse(c.suportado)
        assertEquals(ResultadoSubstituicao.NAO_SUPORTADO, c.substituir(true, 1, 2))
        assertEquals(5, c.substituicoesRestantes(true))
        assertEquals(0, c.aplicarPalestra(true, Palestra.MOTIVAR))
    }

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar(ler), "carregar: ${bd.erros.take(3)}")
        return bd
    }

    @Test
    fun `motor real aceita substituicoes formacao mentalidade e palestra`() {
        val equipas = carregar().equipas.values.filter { it.plantel.size >= 20 }.sortedBy { it.id }
        val casa = equipas[0]; val fora = equipas[1]
        val p = FabricaPartida.criar(casa, fora, true, Random(4), 120f)
        val t = p.controloTatico()
        assertTrue(t.suportado)
        repeat(300) { p.passo(0.05f, Comando()) }
        val onze = t.onzeEmCampo(true)
        assertEquals(11, onze.size)
        assertTrue(t.banco(true).isNotEmpty())
        // GR por campo: recusado.
        val gr = onze.first { it.lugar == "GR" }
        val campo = t.banco(true).first { it.posicao != "GR" }
        assertEquals(ResultadoSubstituicao.GR_INCOMPATIVEL, t.substituir(true, gr.id, campo.id))
        // Troca válida e contagem.
        val sai = onze.first { it.lugar != "GR" }
        val entra = t.banco(true).first { it.posicao != "GR" }
        assertEquals(ResultadoSubstituicao.FEITA, t.substituir(true, sai.id, entra.id))
        assertEquals(1, t.substituicoesFeitas(true))
        assertTrue(t.onzeEmCampo(true).any { it.id == entra.id } && t.onzeEmCampo(true).none { it.id == sai.id })
        assertEquals(100, t.onzeEmCampo(true).first { it.id == entra.id }.energia)
        // Quem saiu não volta.
        assertEquals(ResultadoSubstituicao.JOGADOR_INVALIDO, t.substituir(true, entra.id, sai.id))
        // Até ao máximo.
        var feitas = 1
        while (feitas < 5) {
            val s = t.onzeEmCampo(true).first { it.lugar != "GR" }
            val e = t.banco(true).firstOrNull { it.posicao != "GR" && !it.lesionado } ?: break
            assertEquals(ResultadoSubstituicao.FEITA, t.substituir(true, s.id, e.id)); feitas++
        }
        if (feitas == 5) {
            val s = t.onzeEmCampo(true).first { it.lugar != "GR" }
            val e = t.banco(true).firstOrNull { it.posicao != "GR" }
            if (e != null) assertEquals(ResultadoSubstituicao.SEM_SUBSTITUICOES, t.substituir(true, s.id, e.id))
        }
        // Formação e mentalidade.
        for (f in Formacao.entries) {
            assertTrue(t.mudarFormacao(true, f))
            assertEquals(f, t.formacaoAtual(true))
            val lugares = t.onzeEmCampo(true).map { it.lugar }.sorted()
            assertEquals(f.lugares.sorted(), lugares, "lugares da formação ${f.nome}")
            repeat(60) { p.passo(0.05f, Comando()) }
        }
        assertTrue(t.mudarMentalidade(true, Mentalidade.OFENSIVA))
        assertEquals(Mentalidade.OFENSIVA, t.mentalidadeAtual(true))
        // Palestra mexe pouco na moral.
        val antes = casa.plantel.first { j -> t.onzeEmCampo(true).any { it.id == j.id } }.moral
        val delta = t.aplicarPalestra(true, Palestra.MOTIVAR)
        assertTrue(delta in 1..4)
        val depois = casa.plantel.first { j -> t.onzeEmCampo(true).any { it.id == j.id } }.moral
        assertTrue(depois >= antes)
        // O jogo continua a funcionar até ao fim e dá relatório com as substituições.
        var n = 0
        while (!p.terminou && n++ < 30_000) p.passo(0.05f, Comando())
        assertTrue(p.terminou)
        val rel = FabricaPartida.relatorio(p)
        assertTrue(rel.eventos.count { it.tipo == pt.futebolcarreira.nucleo.simulacao.TipoEvento.SUBSTITUICAO && it.casa } >= feitas)
        assertNotNull(rel)
    }

    // ------------------------------------------------------------------ E5

    @Test
    fun `duracao do jogo valida opcoes`() {
        assertEquals(listOf(4, 8, 12), DuracaoJogo.OPCOES_MINUTOS)
        assertEquals(240f, DuracaoJogo.segundos(4)); assertEquals(720f, DuracaoJogo.segundos(12))
        assertEquals(8, DuracaoJogo.valida(7)); assertEquals(12, DuracaoJogo.valida(99)); assertEquals(4, DuracaoJogo.valida(-3))
    }

    @Test
    fun `detetor de momentos-chave junta golos bolas paradas e zonas de perigo`() {
        assertFalse(DetetorMomentos.ehMomento(estado(bx = 0f)))
        assertTrue(DetetorMomentos.ehMomento(estado(bx = 40f, by = 3f)))
        assertTrue(DetetorMomentos.ehMomento(estado(bx = -40f, by = -3f)))   // também a defender
        assertFalse(DetetorMomentos.ehMomento(estado(bx = 40f, by = 32f)))   // junto à linha lateral
        assertTrue(DetetorMomentos.ehMomento(estado(FaseJogo.GOLO)))
        assertTrue(DetetorMomentos.ehMomento(estado(FaseJogo.PENALTI)))
        assertTrue(DetetorMomentos.ehMomento(estado(FaseJogo.CANTO, bx = 52f, by = 33f)))
        assertTrue(DetetorMomentos.ehMomento(estado(FaseJogo.LIVRE, bx = 30f, by = 5f)))
        assertFalse(DetetorMomentos.ehMomento(estado(FaseJogo.LIVRE, bx = 0f, by = 5f)))
        assertFalse(DetetorMomentos.ehMomento(estado(FaseJogo.LANCAMENTO, bx = 0f, by = 33f)))
        assertTrue(DetetorMomentos.ehMomento(estado(minuto = 89, gc = 1, gf = 1)))
        assertFalse(DetetorMomentos.ehMomento(estado(minuto = 89, gc = 4, gf = 0)))
    }

    @Test
    fun `um momento dura um pouco depois de acabar`() {
        val d = DetetorMomentos(retencao = 3f)
        assertFalse(d.atualizar(estado(), 0.1f))
        assertTrue(d.atualizar(estado(bx = 40f), 0.1f))
        assertTrue(d.atualizar(estado(), 1f))
        assertTrue(d.atualizar(estado(), 1f))
        assertFalse(d.atualizar(estado(), 1.5f))
        d.reiniciar()
        assertFalse(d.emMomento)
    }
}
