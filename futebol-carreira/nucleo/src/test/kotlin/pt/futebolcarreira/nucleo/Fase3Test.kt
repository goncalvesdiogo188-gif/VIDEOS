package pt.futebolcarreira.nucleo

import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Gravador
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.simulacao.CalendarioDatas
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Testes da Fase 3: datas, carreira e gravação. */
class Fase3Test {
    private val pastaDados = File("../app/src/main/assets/dados")

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar { File(pastaDados, it).takeIf(File::exists)?.readText() }, "carregar: ${bd.erros.take(3)}")
        return bd
    }

    @Test
    fun `datas das jornadas`() {
        for (ano in 2026..2040) {
            for (quantas in listOf(30, 34, 38, 46)) {
                val datas = CalendarioDatas.jornadas(ano, quantas)
                assertEquals(quantas, datas.size, "$ano: número de jornadas")
                assertEquals(datas.sorted().distinct(), datas, "$ano/$quantas: datas por ordem e sem repetições")
                assertTrue(datas.first() >= LocalDate.of(ano, 8, 8) && datas.first() <= LocalDate.of(ano, 8, 14), "$ano: começa em agosto")
                assertTrue(datas.last() <= LocalDate.of(ano + 1, 5, 31), "$ano/$quantas: acaba em maio (${datas.last()})")
                // Até 34 jornadas joga-se só ao sábado; com 38 pode haver uma ou duas jornadas a meio da semana.
                val meioDaSemana = datas.count { it.dayOfWeek != DayOfWeek.SATURDAY }
                assertTrue(if (quantas <= 34) meioDaSemana == 0 else if (quantas == 38) meioDaSemana <= 2 else true, "$ano/$quantas: sábados")
            }
        }
        assertTrue(CalendarioDatas.mercadoAberto(LocalDate.of(2026, 7, 1)) && CalendarioDatas.mercadoAberto(LocalDate.of(2027, 1, 15)))
        assertTrue(!CalendarioDatas.mercadoAberto(LocalDate.of(2026, 10, 1)))
        assertEquals("sáb, 8 ago 2026", CalendarioDatas.texto(LocalDate.of(2026, 8, 8)))
    }

    @Test
    fun `carreira de treinador avanca jogo a jogo`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("POR1").first().id
        val c = Carreira.nova(bd, "teste", "Ana Treinadora", ModoCarreira.TREINADOR, clube, semente = 5)
        assertEquals(LocalDate.of(2026, 7, 1), c.gestor.data)
        assertEquals(34, c.jogosDoClube().size)
        assertNull(c.ultimoJogo())
        var jogos = 0
        while (!c.gestor.epocaTerminada()) {
            val proximo = assertNotNull(c.proximoJogo())
            val jogado = assertNotNull(c.avancar())
            jogos++
            assertEquals(proximo.jornada, jogado.jornada)
            assertEquals(jogos, jogado.jornada)
            assertNotNull(jogado.resultado?.relatorio)
            assertTrue(c.gestor.data >= jogado.data)
            // Nenhuma liga ficou para trás nem se adiantou.
            for (e in c.gestor.estado.values) {
                assertTrue(e.datas.take(e.jornada).all { it <= c.gestor.data } && e.datas.drop(e.jornada).all { it > c.gestor.data })
            }
            if (jogos == 34) break
        }
        // A liga portuguesa acaba antes das ligas de 20 equipas: o avanço seguinte fecha a época.
        if (!c.gestor.epocaTerminada()) assertNull(c.avancar())
        assertTrue(c.gestor.epocaTerminada())
        assertEquals(34, jogos)
        assertTrue(c.mensagens.any { it.titulo.startsWith("Fim da época") })
        assertTrue(c.mensagens.any { it.titulo == "Mercado aberto" } && c.mensagens.any { it.titulo == "Mercado fechado" })
        assertTrue(c.mensagens.size <= Carreira.MAX_MENSAGENS)
        println("Mensagens: " + c.mensagens.take(3).joinToString(" | ") { "${it.titulo}: ${it.texto}" })
        c.novaEpoca()
        assertEquals(LocalDate.of(2027, 7, 1), c.gestor.data)
        assertNotNull(c.proximoJogo())
    }

    @Test
    fun `gravar e carregar a meio da epoca`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("ING2")[3].id
        val c = Carreira.nova(bd, "gravar", "Rui Jogador", ModoCarreira.JOGADOR, clube, posicao = "MC", semente = 11)
        val eu = assertNotNull(c.jogador)
        assertEquals("Rui Jogador", eu.nome)
        assertEquals("MC", eu.posicao)
        assertEquals(17, eu.idade)
        assertTrue(eu.potencial >= 86 && eu in c.equipa.plantel)
        c.equipa.formacaoAutomatica = false
        c.equipa.formacao = pt.futebolcarreira.nucleo.modelos.Formacao.F532
        repeat(15) { c.avancar() }

        val inicio = System.currentTimeMillis()
        val texto = Gravador.paraTexto(c)
        val baseDados = Gravador.baseDadosParaTexto(c)
        val meio = System.currentTimeMillis()
        val d = Gravador.deTexto(texto, baseDados)
        println("Gravação: estado ${texto.length / 1024} KB + base de dados ${baseDados.length / 1024} KB; gravar ${meio - inicio} ms, carregar ${System.currentTimeMillis() - meio} ms")
        assertTrue(c.baseDadosPorGravar && !d.baseDadosPorGravar)

        assertEquals(c.gestor.data, d.gestor.data)
        assertEquals(c.gestor.ano, d.gestor.ano)
        assertEquals(c.info.jogadorId, d.info.jogadorId)
        assertEquals("Rui Jogador", d.jogador?.nome)
        assertEquals(c.equipa.formacao, d.equipa.formacao)
        assertEquals(false, d.equipa.formacaoAutomatica)
        assertEquals(c.mensagens.map { it.titulo }, d.mensagens.map { it.titulo })
        assertEquals(c.bd.jogadores.size, d.bd.jogadores.size)
        for ((ligaId, e) in c.gestor.estado) {
            val f = d.gestor.estado.getValue(ligaId)
            assertEquals(e.jornada, f.jornada, "$ligaId: jornada")
            assertEquals(e.datas, f.datas)
            assertEquals(e.calendario, f.calendario)
            assertEquals(e.tabela.ordenada().map { "${it.id} ${it.pts} ${it.gm} ${it.gs}" }, f.tabela.ordenada().map { "${it.id} ${it.pts} ${it.gm} ${it.gs}" }, "$ligaId: tabela")
        }
        for (j in c.bd.jogadores.values) {
            val k = d.bd.jogadores.getValue(j.id)
            assertEquals(listOf(j.nome, j.equipa, j.geral(), j.epoca.jogos, j.epoca.golos, j.epoca.amarelos, j.lesao, j.suspensao),
                listOf(k.nome, k.equipa, k.geral(), k.epoca.jogos, k.epoca.golos, k.epoca.amarelos, k.lesao, k.suspensao))
            assertEquals(j.epoca.somaNotas, k.epoca.somaNotas, 1e-9)
        }
        // Os relatos dos jogos do clube ficam gravados; os dos outros jogos não.
        val meus = d.jogosDoClube().filter { it.resultado != null }
        assertEquals(15, meus.size)
        assertTrue(meus.all { it.resultado?.relatorio != null })
        val original = c.ultimoJogo()!!.resultado!!.relatorio!!
        val lido = d.ultimoJogo()!!.resultado!!.relatorio!!
        assertEquals(original.eventos.map { "${it.minuto} ${it.tipo} ${it.jogador.id}" }, lido.eventos.map { "${it.minuto} ${it.tipo} ${it.jogador.id}" })
        assertEquals(original.casa.fichas.map { it.nota }, lido.casa.fichas.map { it.nota })
        assertTrue(d.gestor.estado.getValue("POR1").resultados.all { jornada -> jornada.all { it.relatorio == null } })

        // A carreira carregada continua a funcionar até à época seguinte, e volta a gravar-se.
        d.simularEpoca()
        assertTrue(d.gestor.epocaTerminada())
        val fim = Gravador.deTexto(Gravador.paraTexto(d), Gravador.baseDadosParaTexto(d))
        assertTrue(fim.gestor.epocaTerminada())
        assertEquals(1, fim.gestor.historico.size)
        fim.novaEpoca()
        assertTrue(fim.baseDadosPorGravar)
        assertEquals(2027, fim.gestor.ano)
        assertNotNull(fim.avancar())
        assertEquals(18, fim.jogador?.idade)

        val resumo = Gravador.resumoDeTexto(Gravador.resumoParaTexto(Gravador.resumo(c, 123)))
        assertEquals(c.equipa.nome, resumo?.clube)
        assertEquals("2026/27", resumo?.epoca)
        assertNull(Gravador.resumoDeTexto("isto não é JSON"))
    }
}
