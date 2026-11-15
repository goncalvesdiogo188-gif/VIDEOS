package pt.futebolcarreira.nucleo

import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.simulacao.GeradorCalendario
import pt.futebolcarreira.nucleo.simulacao.GestorEpocas
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes automáticos da Fase 1. Correm no computador, sem telemóvel:
 *   ./gradlew :nucleo:test
 */
class Fase1Test {
    private val pastaDados = File("../app/src/main/assets/dados")

    private fun carregar() = BaseDados().also { bd ->
        val ok = bd.carregar { File(pastaDados, it).takeIf(File::exists)?.readText() }
        assertTrue(ok, "carregar sem erros: ${bd.erros.take(3)}")
    }

    @Test
    fun `base de dados carrega e esta completa`() {
        val bd = carregar()
        assertEquals(6, bd.paises.size)
        assertEquals(12, bd.competicoes.size)
        assertEquals(234, bd.equipas.size)
        for (liga in bd.competicoes.values) {
            val n = bd.equipasDaLiga(liga.id).size
            assertTrue(n >= 2 && n % 2 == 0, "liga ${liga.id} com número par de equipas ($n)")
        }
        for (e in bd.equipas.values) {
            assertTrue(e.plantel.size >= 18, "plantel de ${e.id}")
            assertTrue(e.melhorOnze().all { it.jogador != null && it.nota > 30 }, "onze de ${e.id} completo")
        }
        for (j in bd.jogadores.values) {
            assertTrue(j.geral() in 25..j.potencial, "geral/potencial válidos em ${j.nome}")
        }
        println("${bd.equipas.size} equipas, ${bd.jogadores.size} jogadores")
    }

    @Test
    fun `gravar e voltar a ler da os mesmos dados`() {
        val bd = carregar()
        val ficheiros = bd.paraJson()
        for ((caminho, texto) in ficheiros) {
            assertEquals(File(pastaDados, caminho).readText(), texto, "conteúdo de $caminho")
        }
    }

    @Test
    fun `calendario - todos jogam com todos em casa e fora`() {
        val rng = Random(1)
        for (n in listOf(2, 4, 7, 18, 20)) {
            val ids = (0 until n).map { "E$it" }
            val calendario = GeradorCalendario.gerar(ids, 2, rng)
            assertEquals((if (n % 2 == 0) n - 1 else n) * 2, calendario.size, "$n equipas: jornadas")
            for (jornada in calendario) {
                val vistos = jornada.flatMap { listOf(it.casa, it.fora) }
                assertEquals(vistos.size, vistos.toSet().size, "$n equipas: ninguém joga 2 vezes na mesma jornada")
            }
            val jogos = calendario.flatten()
            assertEquals(n * (n - 1), jogos.toSet().size, "$n equipas: todos os confrontos casa/fora")
            assertEquals(n * (n - 1), jogos.size, "$n equipas: sem jogos repetidos")
            for (id in ids) assertEquals(n - 1, jogos.count { it.casa == id }, "$n equipas: jogos em casa de $id")
        }
    }

    @Test
    fun `cinco epocas com subidas e descidas`() {
        val bd = carregar()
        val gestor = GestorEpocas(bd, semente = 12345)
        val inicio = System.currentTimeMillis()
        repeat(5) {
            gestor.simularAteAoFim()
            assertTrue(gestor.epocaTerminada())
            for ((ligaId, estado) in gestor.estado) {
                val ordem = estado.tabela.ordenada()
                val n = ordem.size
                for (linha in ordem) {
                    assertEquals((n - 1) * 2, linha.j, "$ligaId: jogos de ${linha.id}")
                    assertEquals(linha.v * 3 + linha.e, linha.pts, "$ligaId: pontos")
                }
                assertEquals(ordem.sumOf { it.gm }, ordem.sumOf { it.gs }, "$ligaId: golos marcados = sofridos")
                val media = ordem.sumOf { it.gm }.toDouble() / (n * (n - 1))
                assertTrue(media in 2.0..3.6, "$ligaId: média de golos realista ($media)")
            }
            val resumo = gestor.historico.last()
            val p1 = resumo.ligas.getValue("POR1")
            val p2 = resumo.ligas.getValue("POR2")
            assertEquals(2, p1.descem.size)
            assertEquals(2, p2.sobem.size)
            println("${gestor.nomeEpoca()}  campeão POR: ${bd.equipas.getValue(p1.campeao).nome} (${p1.pontos} pts)" +
                " | desceram: ${p1.descem.map { bd.equipas.getValue(it).abreviatura }}" +
                " | subiram: ${p2.sobem.map { bd.equipas.getValue(it).abreviatura }}")
            gestor.avancarParaEpocaSeguinte()
            p1.descem.forEach { assertEquals("POR2", bd.equipas.getValue(it).liga) }
            p2.sobem.forEach { assertEquals("POR1", bd.equipas.getValue(it).liga) }
            for (liga in bd.competicoes.values) {
                assertEquals(gestor.estado.getValue(liga.id).tabela.linhas.size, bd.equipasDaLiga(liga.id).size, "${liga.id} mantém o número de equipas")
            }
            assertTrue(bd.equipas.values.all { it.plantel.size == 24 }, "plantéis mantêm 24 jogadores")
        }
        assertEquals(2031, gestor.ano)
        println("5 épocas simuladas em ${System.currentTimeMillis() - inicio} ms")
    }
}
