package pt.futebolcarreira.nucleo

import kotlinx.serialization.json.Json
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.simulacao.CalendarioDatas
import pt.futebolcarreira.nucleo.simulacao.CalendarioEuropa
import pt.futebolcarreira.nucleo.simulacao.DadosEuropa
import pt.futebolcarreira.nucleo.simulacao.EstadoEuropa
import pt.futebolcarreira.nucleo.simulacao.EuropaDaEpoca
import pt.futebolcarreira.nucleo.simulacao.FasesEuropa
import pt.futebolcarreira.nucleo.simulacao.QualificacaoEuropa
import pt.futebolcarreira.nucleo.simulacao.TipoEuropa
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Testes da Fase 7 (parte B): Liga dos Campeões e Liga Europa. */
class Fase7EuropaTest {
    private val pastaDados = File("../app/src/main/assets/dados")

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar { File(pastaDados, it).takeIf(File::exists)?.readText() }, "carregar: ${bd.erros.take(3)}")
        return bd
    }

    private fun primeiras(bd: BaseDados) = bd.competicoes.values.filter { it.nivel == 1 }

    @Test
    fun `apuramento por reputacao e por tabelas`() {
        val bd = carregar()
        val q = QualificacaoEuropa.qualificar(bd)
        assertEquals(24, q.campeoes.size); assertEquals(24, q.europa.size)
        assertEquals(48, (q.campeoes + q.europa).toSet().size)
        for (liga in primeiras(bd)) {
            val ordem = bd.equipasDaLiga(liga.id).sortedWith(compareByDescending<pt.futebolcarreira.nucleo.modelos.Equipa> { it.reputacao }.thenBy { it.id }).map { it.id }
            assertEquals(ordem.take(4), q.campeoes.filter { bd.equipas.getValue(it).liga == liga.id })
            assertEquals(ordem.subList(4, 8), q.europa.filter { bd.equipas.getValue(it).liga == liga.id })
        }
        // Com tabelas: a ordem invertida da liga portuguesa.
        val inversa = bd.equipasDaLiga("POR1").map { it.id }.reversed()
        val q2 = QualificacaoEuropa.qualificar(bd, mapOf("POR1" to inversa))
        assertEquals(inversa.take(4), q2.campeoes.filter { bd.equipas.getValue(it).liga == "POR1" })
        assertEquals(inversa.subList(4, 8), q2.europa.filter { bd.equipas.getValue(it).liga == "POR1" })
        assertEquals(24, q2.campeoes.size)
    }

    @Test
    fun `fase de liga tem 6 jogos por clube 3 em casa sem repetir adversarios`() {
        val bd = carregar()
        val q = QualificacaoEuropa.qualificar(bd)
        for (seed in 1..5) for (tipo in TipoEuropa.values()) {
            val e = EstadoEuropa.criar(tipo, bd, 2026, q.de(tipo), Random(seed))
            assertEquals(72, e.jogosLiga.size)
            for (id in e.clubes) {
                val jogos = e.jogosLiga.filter { it.casa == id || it.fora == id }
                assertEquals(6, jogos.size, id)
                assertEquals(3, jogos.count { it.casa == id }, "em casa $id")
                val adv = jogos.map { if (it.casa == id) it.fora else it.casa }
                assertEquals(6, adv.toSet().size, "adversários repetidos de $id")
                assertTrue(adv.none { bd.equipas.getValue(it).pais == bd.equipas.getValue(id).pais }, "mesmo país: $id")
                assertEquals(6, jogos.map { it.numero }.toSet().size, "um jogo por jornada")
            }
        }
    }

    @Test
    fun `datas nao caem ao sabado exceto a final dos Campeoes`() {
        for (ano in 2026..2040) {
            val c = CalendarioEuropa.datas(ano, TipoEuropa.CAMPEOES)
            val l = CalendarioEuropa.datas(ano, TipoEuropa.EUROPA)
            assertTrue(c.todas().dropLast(1).all { it.dayOfWeek == DayOfWeek.TUESDAY }, "terças $ano")
            assertTrue(l.todas().dropLast(1).all { it.dayOfWeek == DayOfWeek.THURSDAY }, "quintas $ano")
            assertEquals(DayOfWeek.SATURDAY, c.final.dayOfWeek)
            assertEquals(DayOfWeek.WEDNESDAY, l.final.dayOfWeek)
            assertEquals(c.final.minusDays(3), l.final)
            for (jornadas in listOf(30, 34, 38)) {
                val ligas = CalendarioDatas.jornadas(ano, jornadas)
                assertTrue(c.final > ligas.max(), "final dos Campeões depois das ligas ($ano, $jornadas)")
                val sabados = ligas.filter { it.dayOfWeek == DayOfWeek.SATURDAY }.toSet()
                assertTrue((c.todas().dropLast(1) + l.todas()).none { it in sabados })
            }
            assertTrue(c.liga.first() >= LocalDate.of(ano, 9, 15) && c.liga.last().monthValue == 1)
            assertTrue(c.liga.zipWithNext().all { (a, b) -> a < b })
            assertTrue(c.final.monthValue in 5..6, "final entre fim de maio e inícios de junho")
        }
    }

    private fun jogarEmTudo(bd: BaseDados, tipo: TipoEuropa, seed: Int): EstadoEuropa {
        val q = QualificacaoEuropa.qualificar(bd)
        val e = EstadoEuropa.criar(tipo, bd, 2026, q.de(tipo), Random(seed))
        e.jogarTudo(bd, Random(seed + 100))
        return e
    }

    @Test
    fun `a competicao chega a um unico vencedor e o dinheiro chega aos clubes`() {
        val bd = carregar()
        val q = QualificacaoEuropa.qualificar(bd)
        val antes = bd.equipas.mapValues { it.value.orcamento }
        for (tipo in TipoEuropa.values()) {
            val e = EstadoEuropa.criar(tipo, bd, 2026, q.de(tipo), Random(5))
            val rel = q.de(tipo).first()
            val jogos = e.jogarTudo(bd, Random(6), setOf(rel))
            assertTrue(e.terminou && e.campeao in e.clubes)
            assertEquals(117, jogos.size)
            assertTrue(jogos.all { it.jogado })
            assertEquals(listOf(FasesEuropa.PLAYOFF, FasesEuropa.OITAVOS, FasesEuropa.QUARTOS, FasesEuropa.MEIAS, FasesEuropa.FINAL), e.eliminatorias.map { it.fase }.distinct())
            assertEquals(listOf(8, 8, 4, 2, 1), e.eliminatorias.groupBy { it.fase }.values.map { it.size })
            assertTrue(e.eliminatorias.all { it.terminada && it.vencedor in listOf(it.a, it.b) })
            assertEquals(e.campeao, e.eliminatorias.last().vencedor)
            assertTrue(e.eliminatorias.last().jogos.single().neutro)
            assertNotEquals(null, e.jogosDoClube(rel).firstOrNull { it.relatorio != null }, "relatório guardado")
            assertEquals(null, jogos.firstOrNull { it.casa != rel && it.fora != rel && it.relatorio != null })
            assertTrue(e.proximaData() == null)
            val ganho = e.premiosDe(e.campeao)
            println("${tipo.nome}: campeão ${bd.equipas.getValue(e.campeao).nome}, prémios totais $ganho")
            // Caixa e contas aumentaram para todos.
            assertTrue(e.clubes.all { bd.equipas.getValue(it).orcamento > antes.getValue(it) })
            assertTrue(e.clubes.all { bd.equipas.getValue(it).contas.premios > 0 })
            val total = e.clubes.sumOf { e.premiosDe(it) }
            assertEquals(total, e.clubes.sumOf { bd.equipas.getValue(it).contas.premios })
        }
    }

    @Test
    fun `premio do campeao tem ordem de grandeza certa`() {
        val bd = carregar()
        val c = jogarEmTudo(bd, TipoEuropa.CAMPEOES, 11)
        val l = jogarEmTudo(bd, TipoEuropa.EUROPA, 11)
        assertTrue(c.premiosDe(c.campeao) in 15_000_000L..30_000_000L, "Campeões ${c.premiosDe(c.campeao)}")
        assertTrue(l.premiosDe(l.campeao) in 5_000_000L..11_000_000L, "Liga Europa ${l.premiosDe(l.campeao)}")
    }

    @Test
    fun `desempate produz sempre vencedor e ha prolongamento e penaltis`() {
        var prolong = 0
        var penaltis = 0
        for (seed in 1..6) {
            val bd = carregar()
            val e = jogarEmTudo(bd, TipoEuropa.EUROPA, seed)
            assertTrue(e.terminou)
            for (t in e.eliminatorias) {
                assertTrue(t.terminada)
                val ultimo = t.jogos.last()
                if (ultimo.prolongamento) prolong++
                if (ultimo.foiAPenaltis) {
                    penaltis++
                    assertEquals(t.golos(t.a), t.golos(t.b))
                    assertNotEquals(ultimo.penaltisCasa, ultimo.penaltisFora)
                } else {
                    assertNotEquals(t.golos(t.a), t.golos(t.b))
                }
            }
        }
        println("Prolongamentos: $prolong, penáltis: $penaltis")
        assertTrue(prolong > 0 && penaltis > 0)
    }

    @Test
    fun `gravar e restaurar a meio da prova da o mesmo estado`() {
        val json = Json { encodeDefaults = true }
        val bd = carregar()
        val q = QualificacaoEuropa.qualificar(bd)
        val epoca = EuropaDaEpoca.criar(bd, 2026, emptyMap(), Random(3))
        epoca.jogarAte(LocalDate.of(2027, 2, 20), bd, Random(4))   // fase de liga acabada e play-off a meio/feito
        assertTrue(epoca.campeoes.eliminatorias.isNotEmpty() && !epoca.terminou)
        val texto = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(DadosEuropa.serializer()), epoca.dados())
        val lido = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(DadosEuropa.serializer()), texto)
        val copia = EuropaDaEpoca.restaurar(lido, bd)
        for (tipo in TipoEuropa.values()) {
            val a = epoca.estado(tipo)
            val b = copia.estado(tipo)
            assertEquals(a.proximaData(), b.proximaData())
            assertEquals(a.tabelaLiga().map { it.id to it.pts }, b.tabelaLiga().map { it.id to it.pts })
            assertEquals(a.eliminatorias.size, b.eliminatorias.size)
        }
        assertEquals(q.campeoes.toSet(), copia.campeoes.clubes.toSet())
        // Continuar os dois com a mesma semente dá o mesmo resultado (as equipas são partilhadas, por isso só se compara o resultado do jogo).
        assertEquals(texto, json.encodeToString(kotlinx.serialization.builtins.ListSerializer(DadosEuropa.serializer()), copia.dados()))
        copia.jogarAte(LocalDate.MAX, bd, Random(9))
        assertTrue(copia.terminou)
        assertEquals(2, copia.dados().size)
    }
}
