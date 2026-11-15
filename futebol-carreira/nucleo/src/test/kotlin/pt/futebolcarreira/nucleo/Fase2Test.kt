package pt.futebolcarreira.nucleo

import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.Mentalidade
import pt.futebolcarreira.nucleo.simulacao.GestorEpocas
import pt.futebolcarreira.nucleo.simulacao.MotorJogo
import pt.futebolcarreira.nucleo.simulacao.Relato
import pt.futebolcarreira.nucleo.simulacao.TabelaClassificativa
import pt.futebolcarreira.nucleo.simulacao.TipoEvento
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Testes da Fase 2: simulação detalhada dos jogos. */
class Fase2Test {
    private val pastaDados = File("../app/src/main/assets/dados")

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar { File(pastaDados, it).takeIf(File::exists)?.readText() }, "carregar: ${bd.erros.take(3)}")
        return bd
    }

    @Test
    fun `relatorio de jogo e coerente`() {
        val bd = carregar()
        val equipas = bd.equipasDaLiga("POR1")
        val rng = Random(7)
        var golos = 0
        var amarelos = 0
        var vermelhos = 0
        var lesoes = 0
        var remates = 0
        val jogos = 1500
        repeat(jogos) { n ->
            val casa = equipas[n % equipas.size]
            val fora = equipas[(n * 7 + 3) % equipas.size].let { if (it === casa) equipas[(n + 1) % equipas.size] else it }
            val r = MotorJogo.simular(casa, fora, rng)
            for ((lado, marcados) in listOf(r.casa to r.golosCasa, r.fora to r.golosFora)) {
                assertEquals(marcados, lado.fichas.sumOf { it.golos }, "golos das fichas = marcador")
                assertEquals(11, lado.fichas.count { it.titular }, "onze titulares")
                assertTrue(lado.fichas.size <= 11 + MotorJogo.SUBSTITUICOES, "no máximo 5 substituições")
                assertEquals(lado.fichas.size, lado.fichas.map { it.jogador.id }.toSet().size, "ninguém joga duas vezes")
                assertTrue(lado.fichas.all { it.nota in 3.0..10.0 }, "notas entre 3 e 10")
                assertTrue(lado.fichas.all { !it.vermelho || it.saiu > 0 }, "expulso sai do campo")
                assertTrue(lado.fichas.sumOf { it.assistencias } <= marcados, "assistências <= golos")
                assertTrue(lado.estatisticas.rematesBaliza in marcados..lado.estatisticas.remates, "remates à baliza")
                // Quem acaba o jogo em campo: 11 menos expulsos e lesionados sem substituto.
                assertTrue(lado.fichas.count { it.saiu == 0 } <= 11)
                amarelos += lado.estatisticas.amarelos
                vermelhos += lado.estatisticas.vermelhos
                remates += lado.estatisticas.remates
            }
            assertEquals(100, r.casa.estatisticas.posse + r.fora.estatisticas.posse)
            assertEquals(r.eventos.map { it.minuto }.sorted(), r.eventos.map { it.minuto }, "eventos por ordem")
            val ultimoGolo = r.eventos.lastOrNull { it.tipo == TipoEvento.GOLO }
            if (ultimoGolo != null) assertEquals(r.golosCasa to r.golosFora, ultimoGolo.golosCasa to ultimoGolo.golosFora)
            assertEquals(r.golosCasa + r.golosFora, r.eventos.count { it.tipo == TipoEvento.GOLO })
            for (e in r.eventos) assertTrue(Relato.frase(e, "Clube").isNotBlank())
            golos += r.golosCasa + r.golosFora
            lesoes += r.eventos.count { it.tipo == TipoEvento.LESAO }
        }
        val porJogo = { n: Int -> "%.2f".format(n.toDouble() / jogos) }
        println("Por jogo: ${porJogo(golos)} golos, ${porJogo(remates)} remates, ${porJogo(amarelos)} amarelos, ${porJogo(vermelhos)} vermelhos, ${porJogo(lesoes)} lesões")
        assertTrue(golos.toDouble() / jogos in 2.2..3.3, "média de golos")
        assertTrue(amarelos.toDouble() / jogos in 2.0..5.5, "média de amarelos")
        assertTrue(vermelhos.toDouble() / jogos in 0.05..0.5, "média de vermelhos")
    }

    @Test
    fun `taticas tem efeito no jogo`() {
        val bd = carregar()
        val (casa, fora) = bd.equipasDaLiga("POR1").sortedBy { it.id }.let { it[4] to it[5] }
        fun media(mentalidade: Mentalidade?, formacao: Formacao): Pair<Double, Double> {
            casa.mentalidade = mentalidade
            casa.formacaoAutomatica = false
            casa.formacao = formacao
            val rng = Random(99)
            var marcados = 0
            var sofridos = 0
            repeat(3000) {
                val r = MotorJogo.simular(casa, fora, rng)
                marcados += r.golosCasa
                sofridos += r.golosFora
            }
            return marcados / 3000.0 to sofridos / 3000.0
        }
        val equilibrada = media(Mentalidade.EQUILIBRADA, Formacao.F433)
        val ofensiva = media(Mentalidade.OFENSIVA, Formacao.F433)
        val defensiva = media(Mentalidade.DEFENSIVA, Formacao.F433)
        val cincoDefesas = media(Mentalidade.EQUILIBRADA, Formacao.F532)
        println("Golos marcados/sofridos — equilibrada $equilibrada, ofensiva $ofensiva, defensiva $defensiva, 5-3-2 $cincoDefesas")
        assertTrue(ofensiva.first > equilibrada.first && ofensiva.second > equilibrada.second, "ofensiva: mais golos nas duas balizas")
        assertTrue(defensiva.first < equilibrada.first && defensiva.second < equilibrada.second, "defensiva: menos golos nas duas balizas")
        assertTrue(cincoDefesas.second < equilibrada.second, "5-3-2 sofre menos golos")
    }

    @Test
    fun `expulsos e lesionados ficam de fora e depois voltam`() {
        val bd = carregar()
        val (casa, fora) = bd.equipasDaLiga("ESP1").let { it[0] to it[1] }
        val rng = Random(3)
        var vistos = 0
        repeat(400) {
            val r = MotorJogo.simular(casa, fora, rng)
            MotorJogo.aplicar(r)
            for (lado in listOf(r.casa, r.fora)) {
                // Quem estava indisponível não pode ter jogado.
                assertTrue(lado.indisponiveis.none { fora -> lado.fichas.any { it.jogador === fora } })
                for (ficha in lado.fichas) {
                    if (ficha.vermelho) {
                        vistos++
                        assertTrue(ficha.jogador.suspensao >= 1, "expulso fica castigado")
                    }
                    if (ficha.lesao > 0) {
                        vistos++
                        assertEquals(ficha.lesao, ficha.jogador.lesao)
                    }
                }
            }
        }
        assertTrue(vistos > 20, "houve expulsões e lesões ($vistos)")
        // Ninguém fica de fora para sempre: ao fim de 30 jogos sem novos casos, todos voltam.
        val tudo = casa.plantel + fora.plantel
        repeat(30) { for (j in tudo) { if (j.lesao > 0) j.lesao--; if (j.suspensao > 0) j.suspensao-- } }
        assertTrue(tudo.all { it.disponivel })
    }

    @Test
    fun `desempate por confronto direto`() {
        val t = TabelaClassificativa(listOf("A", "B", "C", "D"))
        // A e B acabam com 6 pontos; A tem melhor diferença de golos geral, mas B ganhou os dois jogos com A.
        t.registar("A", "B", 0, 1)
        t.registar("B", "A", 1, 0)
        t.registar("A", "C", 5, 0)
        t.registar("A", "D", 5, 0)
        t.registar("C", "B", 1, 0)
        t.registar("D", "B", 1, 0)
        assertEquals(listOf("B", "A", "C", "D"), t.ordenada().map { it.id })
        // Sem jogos entre si, decide a diferença de golos geral.
        val u = TabelaClassificativa(listOf("A", "B", "C", "D"))
        u.registar("A", "C", 3, 0)
        u.registar("B", "D", 1, 0)
        assertEquals(listOf("A", "B", "D", "C"), u.ordenada().map { it.id })
    }

    @Test
    fun `estatisticas da epoca`() {
        val bd = carregar()
        val gestor = GestorEpocas(bd, semente = 2024)
        val inicio = System.currentTimeMillis()
        gestor.simularAteAoFim()
        println("Época detalhada simulada em ${System.currentTimeMillis() - inicio} ms")
        for ((ligaId, estado) in gestor.estado) {
            val golosTabela = estado.tabela.ordenada().sumOf { it.gm }
            assertEquals(golosTabela, gestor.jogadoresDaLiga(ligaId).sumOf { it.epoca.golos }, "$ligaId: golos dos jogadores = golos da tabela")
            assertTrue(estado.resultados.all { jornada -> jornada.all { it.relatorio != null } })
            val resumo = gestor.historico.last().ligas.getValue(ligaId)
            assertTrue(resumo.melhorMarcador.isNotEmpty() && resumo.golosMarcador in 10..60, "$ligaId: melhor marcador (${resumo.golosMarcador})")
        }
        val marcadores = gestor.melhores("POR1", 3) { it.epoca.golos.toDouble() }
        val notas = gestor.melhores("POR1", 3, minimoJogos = 15) { it.epoca.notaMedia() }
        println("Marcadores POR1: " + marcadores.joinToString { "${it.nome} ${it.epoca.golos}" })
        println("Notas POR1: " + notas.joinToString { "${it.nome} %.2f".format(it.epoca.notaMedia()) })
        println("Formações: " + bd.equipas.values.groupingBy { it.formacao.nome }.eachCount())
        val jogos = gestor.estado.values.sumOf { e -> e.resultados.sumOf { it.size } }
        val casa = gestor.estado.values.sumOf { e -> e.resultados.sumOf { j -> j.count { it.golosCasa > it.golosFora } } }
        val empates = gestor.estado.values.sumOf { e -> e.resultados.sumOf { j -> j.count { it.golosCasa == it.golosFora } } }
        println("Golos por jogo: %.2f".format(gestor.estado.values.sumOf { e -> e.tabela.ordenada().sumOf { it.gm } }.toDouble() / jogos))
        println("Vitórias da casa %.0f%%, empates %.0f%%".format(100.0 * casa / jogos, 100.0 * empates / jogos))
        // Época seguinte: estatísticas voltam a zero.
        gestor.avancarParaEpocaSeguinte()
        assertTrue(bd.equipas.values.all { e -> e.plantel.all { it.epoca.jogos == 0 && it.disponivel } })
    }
}
