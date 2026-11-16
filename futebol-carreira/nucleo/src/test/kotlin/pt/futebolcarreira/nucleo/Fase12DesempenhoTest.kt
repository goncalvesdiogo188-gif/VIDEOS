package pt.futebolcarreira.nucleo

import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Gravador
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.dados.BaseDados
import java.io.File
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Fase 12: medições de desempenho do núcleo (ordem de grandeza para um telemóvel de gama média).
 * Os limites são generosos de propósito: servem para apanhar desastres (ordens de grandeza), não para medir ms exatos.
 */
class Fase12DesempenhoTest {
    private val pastaDados = File("../app/src/main/assets/dados")

    private fun carregarBd(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar { File(pastaDados, it).takeIf(File::exists)?.readText() }, "carregar: ${bd.erros.take(3)}")
        return bd
    }

    private fun novaCarreira(bd: BaseDados): Carreira {
        val clube = bd.equipasDaLiga("POR1").maxBy { it.reputacao }
        return Carreira.nova(bd, "d", "D", ModoCarreira.TREINADOR, clube.id, semente = 1)
    }

    @Test
    fun `carregar a base de dados`() {
        var bd: BaseDados? = null
        val ms = measureTimeMillis { bd = carregarBd() }
        val base = bd!!
        println("Desempenho: carregar a base de dados ${ms} ms (${base.jogadores.size} jogadores, ${base.equipas.size} equipas)")
        assertTrue(ms < 15_000, "carregar demorou ${ms} ms")
    }

    @Test
    fun `criar carreira`() {
        val bd = carregarBd()
        val clube = bd.equipasDaLiga("POR1").maxBy { it.reputacao }
        var c: Carreira? = null
        val ms = measureTimeMillis {
            c = Carreira.nova(bd, "d", "D", ModoCarreira.TREINADOR, clube.id, semente = 1)
        }
        println("Desempenho: criar carreira ${ms} ms")
        assertTrue(c != null)
        assertTrue(ms < 20_000, "criar carreira demorou ${ms} ms")
    }

    @Test
    fun `uma jornada`() {
        val c = novaCarreira(carregarBd())
        val ms = measureTimeMillis {
            repeat(10) { c.avancar() }
        }
        val media = ms / 10.0
        println("Desempenho: uma jornada, média de 10 jornadas ${"%.1f".format(media)} ms (total ${ms} ms)")
        assertTrue(media < 5_000, "média por jornada ${media} ms")
    }

    @Test
    fun `uma epoca completa`() {
        val c = novaCarreira(carregarBd())
        val ms = measureTimeMillis {
            c.simularEpoca()
            c.novaEpoca()
        }
        println("Desempenho: uma época completa (simularEpoca + novaEpoca) ${ms} ms")
        assertTrue(ms < 120_000, "época completa demorou ${ms} ms")
    }

    @Test
    fun `gravar`() {
        val c = novaCarreira(carregarBd())
        repeat(10) { c.avancar() }
        var jornada = ""
        var base = ""
        val msJornada = measureTimeMillis { jornada = Gravador.paraTexto(c) }
        val msBase = measureTimeMillis { base = Gravador.baseDadosParaTexto(c) }
        println("Desempenho: gravar jornada ${msJornada} ms, ${jornada.length / 1024} KB")
        println("Desempenho: gravar base de dados ${msBase} ms, ${base.length / 1024} KB")
        assertTrue(jornada.length < 20L * 1024 * 1024, "gravação por jornada com ${jornada.length} caracteres")
        assertTrue(base.length < 60L * 1024 * 1024, "gravação da base com ${base.length} caracteres")
    }

    @Test
    fun `carregar gravacao`() {
        val c = novaCarreira(carregarBd())
        repeat(10) { c.avancar() }
        val jornada = Gravador.paraTexto(c)
        val base = Gravador.baseDadosParaTexto(c)
        var carregada: Carreira? = null
        val ms = measureTimeMillis { carregada = Gravador.deTexto(jornada, base) }
        println("Desempenho: carregar gravação ${ms} ms")
        assertTrue(carregada != null)
        assertTrue(ms < 30_000, "carregar gravação demorou ${ms} ms")
    }

    @Test
    fun `memoria`() {
        val c = novaCarreira(carregarBd())
        c.simularEpoca()
        c.novaEpoca()
        System.gc()
        val rt = Runtime.getRuntime()
        val usadaMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        println("Desempenho: memória usada após uma época ${usadaMb} MB (máximo ${rt.maxMemory() / (1024 * 1024)} MB)")
    }
}
