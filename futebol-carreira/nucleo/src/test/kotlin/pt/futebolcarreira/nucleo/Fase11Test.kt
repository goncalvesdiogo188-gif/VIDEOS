package pt.futebolcarreira.nucleo

import pt.futebolcarreira.nucleo.aovivo.Comando
import pt.futebolcarreira.nucleo.aovivo.EstadoPartida
import pt.futebolcarreira.nucleo.aovivo.FabricaPartida
import pt.futebolcarreira.nucleo.aovivo.FaseJogo
import pt.futebolcarreira.nucleo.aovivo.PartidaAoVivo
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Gravador
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.simulacao.MotorJogo
import pt.futebolcarreira.nucleo.simulacao.RelatorioJogo
import pt.futebolcarreira.nucleo.simulacao.TipoEvento
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Testes da Fase 11 (parte A): motor da partida ao vivo e a sua ligação à carreira, sem desenhar nada. */
class Fase11Test {
    private val pastaDados = File("../app/src/main/assets/dados")
    private val ler = { caminho: String -> File(pastaDados, caminho).takeIf(File::exists)?.readText() }

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar(ler), "carregar: ${bd.erros.take(3)}")
        return bd
    }

    private fun equipasValidas(bd: BaseDados) = bd.equipas.values.filter { it.plantel.size >= 16 }.sortedBy { it.id }

    /** Joga um jogo todo pela IA (sem utilizador); devolve a partida e o número de passos. */
    private fun jogarIA(casa: Equipa, fora: Equipa, semente: Int, duracao: Float = 480f, dt: Float = 1f / 30f): Pair<PartidaAoVivo, Int> {
        val p = FabricaPartida.criar(casa, fora, true, Random(semente), duracao, semUtilizador = true)
        var passos = 0
        val limite = (duracao * 1.3f / dt).toInt()
        while (!p.terminou && passos < limite) { p.passo(dt, Comando()); passos++ }
        return p to passos
    }

    /** Comando de quem corre para a baliza e remata quando está perto. */
    private fun guiao(e: EstadoPartida, passo: Int): Comando {
        val sentido = if (e.segundaParte) -1f else 1f
        val c = e.jogadores.firstOrNull { it.controlado } ?: return Comando()
        val perto = c.x * sentido > 25f
        return Comando(movX = sentido, movY = (-c.y / 30f).coerceIn(-0.5f, 0.5f), sprint = true, remate = perto && passo % 6 == 0, forca = 0.7f, trocar = passo % 40 == 0)
    }

    private fun jogarComGuiao(casa: Equipa, fora: Equipa, semente: Int, duracao: Float = 480f): Pair<PartidaAoVivo, Int> {
        val p = FabricaPartida.criar(casa, fora, true, Random(semente), duracao)
        var passos = 0
        while (!p.terminou && passos < (duracao * 1.3f * 30f).toInt()) { p.passo(1f / 30f, guiao(p.estado, passos)); passos++ }
        return p to passos
    }

    @Test
    fun `jogo sem comandos chega ao fim no tempo esperado`() {
        val bd = carregar()
        val t = equipasValidas(bd)
        val (p, passos) = jogarIA(t[0], t[1], 1)
        assertTrue(p.terminou, "o jogo não terminou")
        val e = p.estado
        assertEquals(FaseJogo.FIM, e.fase)
        assertTrue(e.segundaParte)
        assertTrue(e.minuto >= 90, "minuto final ${e.minuto}")
        // 480 s + intervalo (3 s) + um pouco de desconto, a 30 passos por segundo
        assertTrue(passos in 14_400..15_800, "passos = $passos")
        assertTrue(e.jogadores.size in 18..22)
    }

    @Test
    fun `duracao real mais curta tambem acaba`() {
        val bd = carregar()
        val t = equipasValidas(bd)
        val (p, passos) = jogarIA(t[2], t[3], 2, duracao = 120f)
        assertTrue(p.terminou)
        assertTrue(passos in 3_600..4_400, "passos = $passos")
    }

    @Test
    fun `estatisticas realistas em 30 jogos IA contra IA`() {
        val bd = carregar()
        val todas = equipasValidas(bd)
        val rnd = Random(11)
        var n = 0
        var golos = 0; var posseCasa = 0; var rematesCasa = 0; var rematesFora = 0
        val fases = HashSet<FaseJogo>()
        while (n < 30) {
            val a = todas[rnd.nextInt(todas.size)]; val b = todas[rnd.nextInt(todas.size)]
            if (a === b) continue
            val p = FabricaPartida.criar(a, b, true, Random(n + 500), semUtilizador = true)
            var passos = 0
            while (!p.terminou && passos < 20_000) { p.passo(1f / 30f, Comando()); fases += p.estado.fase; passos++ }
            val rel = FabricaPartida.relatorio(p)
            golos += rel.golosCasa + rel.golosFora
            posseCasa += rel.casa.estatisticas.posse
            rematesCasa += rel.casa.estatisticas.remates; rematesFora += rel.fora.estatisticas.remates
            assertEquals(100, rel.casa.estatisticas.posse + rel.fora.estatisticas.posse)
            n++
        }
        val mediaGolos = golos / n.toDouble()
        val mediaPosse = posseCasa / n.toDouble()
        println("F11 estatisticas: golos/jogo=$mediaGolos posseCasa=$mediaPosse remates casa=${rematesCasa / n.toDouble()} fora=${rematesFora / n.toDouble()}")
        assertTrue(mediaGolos in 1.5..4.5, "golos por jogo: $mediaGolos")
        assertTrue(mediaPosse in 35.0..65.0, "posse: $mediaPosse")
        assertTrue(rematesCasa / n.toDouble() > 5.0 && rematesFora / n.toDouble() > 5.0, "remates: $rematesCasa / $rematesFora")
        // Passou por intervalo, golos e lances parados
        for (f in listOf(FaseJogo.PONTAPE_SAIDA, FaseJogo.A_JOGAR, FaseJogo.GOLO, FaseJogo.INTERVALO, FaseJogo.FIM, FaseJogo.LANCAMENTO, FaseJogo.PONTAPE_BALIZA, FaseJogo.CANTO, FaseJogo.LIVRE)) {
            assertTrue(f in fases, "nunca houve $f")
        }
    }

    @Test
    fun `a equipa mais forte ganha mais vezes`() {
        val bd = carregar()
        val ordem = equipasValidas(bd).sortedBy { it.forca().geral }
        val fracos = ordem.take(10); val fortes = ordem.takeLast(10)
        var vitorias = 0; var derrotas = 0
        for (k in 0 until 20) {
            val f = fortes[k % 10]; val w = fracos[(k * 3) % 10]
            val (p, _) = if (k % 2 == 0) jogarIA(f, w, 100 + k) else jogarIA(w, f, 100 + k)
            val rel = FabricaPartida.relatorio(p)
            val gf = if (k % 2 == 0) rel.golosCasa else rel.golosFora
            val gw = if (k % 2 == 0) rel.golosFora else rel.golosCasa
            if (gf > gw) vitorias++ else if (gf < gw) derrotas++
        }
        println("F11 fortes vs fracos: V=$vitorias D=$derrotas")
        assertTrue(vitorias >= 11 && vitorias > derrotas * 3, "vitórias $vitorias, derrotas $derrotas")
    }

    @Test
    fun `a bola nunca sai do campo sem reinicio`() {
        val bd = carregar()
        val t = equipasValidas(bd)
        for (k in 0 until 4) {
            val p = FabricaPartida.criar(t[k], t[k + 5], true, Random(k), 240f, semUtilizador = k % 2 == 0)
            var passos = 0
            while (!p.terminou && passos < 12_000) {
                p.passo(1f / 30f, if (k % 2 == 0) Comando() else guiao(p.estado, passos))
                val e = p.estado
                val b = e.bola
                when (e.fase) {
                    FaseJogo.A_JOGAR, FaseJogo.LANCAMENTO, FaseJogo.CANTO, FaseJogo.PONTAPE_BALIZA, FaseJogo.LIVRE, FaseJogo.PENALTI, FaseJogo.PONTAPE_SAIDA ->
                        assertTrue(abs(b.x) <= 52.51f && abs(b.y) <= 34.01f, "bola fora em ${e.fase}: ${b.x}, ${b.y} (passo $passos)")
                    else -> assertTrue(abs(b.x) <= 56f && abs(b.y) <= 36f, "bola longe em ${e.fase}: ${b.x}, ${b.y}")
                }
                assertTrue(b.z >= 0f && b.z < 40f, "altura da bola ${b.z}")
                for (j in e.jogadores) assertTrue(abs(j.x) <= 56.6f && abs(j.y) <= 38.1f, "jogador fora: ${j.x}, ${j.y}")
                passos++
            }
            assertTrue(p.terminou)
        }
    }

    @Test
    fun `mesma semente e mesmos comandos dao o mesmo jogo`() {
        val bd = carregar()
        val t = equipasValidas(bd)
        fun jogar(semente: Int): Triple<List<EstadoPartida>, RelatorioJogo, Int> {
            val p = FabricaPartida.criar(t[4], t[9], true, Random(semente), 150f)
            val fotos = mutableListOf<EstadoPartida>()
            var passos = 0
            while (!p.terminou && passos < 9_000) {
                val dt = if (passos % 3 == 0) 1f / 25f else 1f / 40f
                p.passo(dt, guiao(p.estado, passos))
                if (passos % 150 == 0) fotos += p.estado
                passos++
            }
            return Triple(fotos, FabricaPartida.relatorio(p), passos)
        }
        val a = jogar(77); val b = jogar(77)
        assertEquals(a.third, b.third)
        assertEquals(a.first, b.first)
        assertEquals(a.second.golosCasa, b.second.golosCasa)
        assertEquals(a.second.golosFora, b.second.golosFora)
        assertEquals(a.second.eventos.map { it.minuto to it.tipo }, b.second.eventos.map { it.minuto to it.tipo })
        // Outra semente dá outro jogo
        val c = jogar(78)
        assertTrue(c.first != a.first, "sementes diferentes deviam dar jogos diferentes")
    }

    @Test
    fun `um guiao de correr para a baliza e rematar marca golos`() {
        val bd = carregar()
        val todas = equipasValidas(bd)
        val rnd = Random(3)
        var golosUtilizador = 0
        var jogos = 0
        for (k in 0 until 12) {
            val a = todas[rnd.nextInt(todas.size)]; val b = todas[rnd.nextInt(todas.size)]
            if (a === b) continue
            val (p, _) = jogarComGuiao(a, b, k + 1)
            val rel = FabricaPartida.relatorio(p)
            golosUtilizador += rel.golosCasa
            jogos++
            assertTrue(rel.casa.estatisticas.remates > 0, "o utilizador nunca rematou")
        }
        println("F11 guiao: $golosUtilizador golos em $jogos jogos")
        assertTrue(golosUtilizador >= 1, "o guiao nunca marcou")
    }

    @Test
    fun `os controlos do utilizador funcionam - passe e trocar`() {
        val bd = carregar()
        val t = equipasValidas(bd)
        var testouPasse = false
        var testouTrocar = false
        for (semente in 1..6) {
            val p = FabricaPartida.criar(t[1], t[6], true, Random(semente), 240f)
            var passos = 0
            while (!p.terminou && passos < 7000 && !(testouPasse && testouTrocar)) {
                val e = p.estado
                val c = e.jogadores.firstOrNull { it.controlado }
                if (c != null && e.fase == FaseJogo.A_JOGAR && !testouPasse) {
                    val d = hypot(e.bola.x - c.x, e.bola.y - c.y)
                    if (d < 1.0f && c.posicao != "GR") {
                        val antes = e.bola
                        p.passo(1f / 30f, Comando(movX = if (e.segundaParte) -1f else 1f, passe = true))
                        repeat(20) { p.passo(1f / 30f, Comando()) }
                        val dep = p.estado.bola
                        if (hypot(dep.x - antes.x, dep.y - antes.y) > 4f) testouPasse = true
                    }
                } else if (c != null && e.fase == FaseJogo.A_JOGAR && !testouTrocar) {
                    val d = hypot(e.bola.x - c.x, e.bola.y - c.y)
                    if (d > 12f) {
                        p.passo(1f / 30f, Comando(trocar = true))
                        p.passo(1f / 30f, Comando())
                        val novo = p.estado.jogadores.firstOrNull { it.controlado }
                        if (novo != null && novo.id != c.id) testouTrocar = true
                    }
                }
                p.passo(1f / 30f, Comando())
                passos++
            }
        }
        assertTrue(testouPasse, "o passe não mexeu a bola")
        assertTrue(testouTrocar, "trocar não mudou de jogador")
    }

    @Test
    fun `o jogador comandado segue o joystick`() {
        val bd = carregar()
        val t = equipasValidas(bd)
        val p = FabricaPartida.criar(t[1], t[6], true, Random(5), 240f)
        // Deixa passar o pontapé de saída
        var espera = 0
        while (p.estado.fase != FaseJogo.A_JOGAR && espera++ < 400) p.passo(1f / 30f, Comando(passe = true))
        assertEquals(FaseJogo.A_JOGAR, p.estado.fase)
        val c0 = p.estado.jogadores.first { it.controlado }
        repeat(30) { p.passo(1f / 30f, Comando(movX = 0f, movY = 1f, sprint = true)) }
        val c1 = p.estado.jogadores.first { it.id == c0.id }
        assertTrue(c1.y - c0.y > 3f, "o jogador não andou para +y: ${c0.y} -> ${c1.y}")
        assertTrue(abs(c1.x - c0.x) < 3f)
    }

    @Test
    fun `o relatorio e coerente e o motor de simulacao aceita-o`() {
        val bd = carregar()
        val t = equipasValidas(bd)
        for (k in 0 until 6) {
            val casa = t[k * 2]; val fora = t[k * 2 + 1]
            val (p, _) = jogarIA(casa, fora, 900 + k)
            val rel = FabricaPartida.relatorio(p)
            val e = p.estado
            assertEquals(e.golosCasa, rel.golosCasa)
            assertEquals(e.golosFora, rel.golosFora)
            assertEquals(rel.golosCasa, rel.eventos.count { it.tipo == TipoEvento.GOLO && it.casa })
            assertEquals(rel.golosFora, rel.eventos.count { it.tipo == TipoEvento.GOLO && !it.casa })
            assertEquals(rel.golosCasa, rel.casa.fichas.sumOf { it.golos })
            assertEquals(rel.golosFora, rel.fora.fichas.sumOf { it.golos })
            assertEquals(casa.id, rel.casa.equipa)
            assertEquals(fora.id, rel.fora.equipa)
            assertTrue(rel.casa.fichas.count { it.titular } == 11 && rel.fora.fichas.count { it.titular } == 11)
            assertEquals(100, rel.casa.estatisticas.posse + rel.fora.estatisticas.posse)
            assertTrue(rel.casa.estatisticas.rematesBaliza <= rel.casa.estatisticas.remates)
            assertTrue(rel.duracao >= 90)
            // Os acontecimentos vêm por ordem e o marcador cresce
            var anterior = 0
            var gc = 0; var gf = 0
            for (ev in rel.eventos) {
                assertTrue(ev.minuto >= anterior, "eventos fora de ordem")
                anterior = ev.minuto
                if (ev.tipo == TipoEvento.GOLO) {
                    if (ev.casa) gc++ else gf++
                    assertEquals(gc, ev.golosCasa); assertEquals(gf, ev.golosFora)
                    assertTrue(ev.jogador.equipa == (if (ev.casa) casa.id else fora.id))
                }
            }
            for (f in rel.casa.fichas + rel.fora.fichas) assertTrue(f.nota in 1.0..10.0, "nota ${f.nota}")
            assertNotNull(rel.melhorEmCampo)
            // O motor de simulação aplica-o como a um jogo simulado
            val jogosAntes = rel.casa.fichas.associate { it.jogador.id to it.jogador.epoca.jogos }
            val golosAntes = rel.casa.fichas.sumOf { it.jogador.epoca.golos }
            MotorJogo.aplicar(rel)
            for (f in rel.casa.fichas) assertEquals(jogosAntes.getValue(f.jogador.id) + 1, f.jogador.epoca.jogos)
            assertEquals(golosAntes + rel.golosCasa, rel.casa.fichas.sumOf { it.jogador.epoca.golos })
        }
    }

    @Test
    fun `nao ha relatorio antes do fim`() {
        val bd = carregar()
        val t = equipasValidas(bd)
        val p = FabricaPartida.criar(t[0], t[1], true, Random(1), 120f, semUtilizador = true)
        repeat(100) { p.passo(1f / 30f, Comando()) }
        assertFailsWith<IllegalStateException> { FabricaPartida.relatorio(p) }
    }

    @Test
    fun `uma carreira joga ao vivo um jogo de liga e o resto continua simulado`() {
        val bd = carregar()
        val c = Carreira.nova(bd, "f11", "Rui", ModoCarreira.TREINADOR, "POR_02", semente = 5)
        var guarda = 0
        while (c.proximoJogo()?.eLiga != true && guarda++ < 40) c.avancar()
        val jornadasAntes = c.estadoLiga.jornada
        val (casa, fora) = assertNotNull(c.prepararJogoAoVivo())
        val jogoAntes = assertNotNull(c.proximoJogo())
        assertEquals(c.equipa.id, (if (jogoAntes.emCasa) casa else fora).id)
        val nMensagens = c.mensagens.size
        val golosEpocaAntes = casa.plantel.sumOf { it.epoca.golos }
        val ptsAntes = c.estadoLiga.tabela.linhas.getValue(casa.id).pts
        val gmAntes = c.estadoLiga.tabela.linhas.getValue(casa.id).gm
        val (p, _) = jogarComGuiao(casa, fora, 9, duracao = 120f)
        val rel = FabricaPartida.relatorio(p)
        val jogado = assertNotNull(c.registarJogoAoVivo(rel))
        assertTrue(c.gestor.jogoAoVivo == null)
        assertTrue(jogado.eLiga)
        val r = assertNotNull(jogado.resultado)
        assertTrue(r.relatorio === rel, "o resultado devia usar o relatório ao vivo")
        assertEquals(rel.golosCasa, r.golosCasa)
        assertEquals(rel.golosFora, r.golosFora)
        // A tabela registou o jogo e os outros jogos da jornada foram simulados
        val e = c.estadoLiga
        assertEquals(jornadasAntes + 1, e.jornada)
        assertEquals(jornadasAntes + 1, e.tabela.linhas.getValue(casa.id).j)
        assertEquals(e.resultados[jornadasAntes].size, e.calendario[jornadasAntes].size)
        val ptsEsperados = when { rel.golosCasa > rel.golosFora -> 3; rel.golosCasa == rel.golosFora -> 1; else -> 0 }
        assertEquals(ptsEsperados, e.tabela.linhas.getValue(casa.id).pts - ptsAntes)
        assertEquals(rel.golosCasa, e.tabela.linhas.getValue(casa.id).gm - gmAntes)
        // Mensagem do jogo com o resultado
        assertTrue(c.mensagens.size > nMensagens)
        val marcador = "${jogado.golosMarcados}-${jogado.golosSofridos}"
        assertTrue(c.mensagens.take(6).any { it.titulo.contains(marcador) || it.titulo.contains("Empate a ${jogado.golosMarcados}") }, "sem mensagem do jogo: ${c.mensagens.take(4).map { it.titulo }}")
        // Estatísticas dos jogadores
        assertEquals(rel.golosCasa, casa.plantel.sumOf { it.epoca.golos } - golosEpocaAntes)
        // Grava e carrega
        val d = Gravador.deTexto(Gravador.paraTexto(c), Gravador.baseDadosParaTexto(c))
        assertEquals(c.posicao(), d.posicao())
        assertEquals(e.tabela.linhas.getValue(casa.id).pts, d.estadoLiga.tabela.linhas.getValue(casa.id).pts)
        // E o jogo seguinte continua a funcionar (simulado)
        assertNotNull(c.avancar())
    }

    @Test
    fun `uma carreira joga ao vivo um jogo de taca`() {
        val bd = carregar()
        val c = Carreira.nova(bd, "f11b", "Rui", ModoCarreira.TREINADOR, "POR_02", semente = 8)
        var guarda = 0
        while (c.proximoJogo()?.tipo != "taca" && guarda++ < 80) c.avancar()
        val proximo = assertNotNull(c.proximoJogo())
        assertEquals("taca", proximo.tipo)
        val (casa, fora) = assertNotNull(c.prepararJogoAoVivo())
        val (p, _) = jogarComGuiao(casa, fora, 21, duracao = 120f)
        val rel = FabricaPartida.relatorio(p)
        val jogado = assertNotNull(c.registarJogoAoVivo(rel))
        assertEquals("taca", jogado.tipo)
        val r = assertNotNull(jogado.resultado)
        assertTrue(r.relatorio === rel)
        assertEquals(rel.golosCasa, r.golosCasa)
        assertEquals(rel.golosFora, r.golosFora)
        assertTrue(c.gestor.jogoAoVivo == null)
        assertNotNull(jogado.confronto)
    }

    @Test
    fun `um relatorio de outro jogo e descartado e o jogo simula-se`() {
        val bd = carregar()
        val c = Carreira.nova(bd, "f11c", "Rui", ModoCarreira.TREINADOR, "POR_02", semente = 5)
        val t = equipasValidas(bd).filter { it.id != "POR_02" }
        val (p, _) = jogarIA(t[0], t[1], 3, duracao = 60f)
        val rel = FabricaPartida.relatorio(p)
        val jogado = assertNotNull(c.registarJogoAoVivo(rel))
        assertTrue(jogado.resultado?.relatorio !== rel)
        assertTrue(c.gestor.jogoAoVivo == null)
    }

    @Test
    fun `desempenho por minuto simulado`() {
        val bd = carregar()
        val t = equipasValidas(bd)
        // Aquecimento
        jogarIA(t[0], t[1], 1, duracao = 120f, dt = 1f / 60f)
        val ini = System.nanoTime()
        val (p, passos) = jogarIA(t[2], t[3], 2, duracao = 480f, dt = 1f / 60f)
        val ms = (System.nanoTime() - ini) / 1e6
        assertTrue(p.terminou)
        println("F11 desempenho: ${"%.1f".format(ms)} ms para o jogo todo ($passos passos de 1/60 s) = ${"%.2f".format(ms / 90.0)} ms por minuto de jogo, ${"%.3f".format(ms / passos)} ms por passo")
        assertTrue(ms / passos < 2.0, "demasiado lento: ${ms / passos} ms por passo")
    }
}
