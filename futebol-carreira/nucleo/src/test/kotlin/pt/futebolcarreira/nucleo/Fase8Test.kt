package pt.futebolcarreira.nucleo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import pt.futebolcarreira.nucleo.carreira.Ambiente
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.GeradorNoticias
import pt.futebolcarreira.nucleo.carreira.Gravador
import pt.futebolcarreira.nucleo.carreira.JogoDoClube
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.carreira.Noticia
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.simulacao.Financas
import pt.futebolcarreira.nucleo.simulacao.MotorJogo
import pt.futebolcarreira.nucleo.simulacao.Resultado
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Testes da Fase 8: rivalidades, notícias e ambiente. */
class Fase8Test {
    private val pastaDados = File("../app/src/main/assets/dados")
    private val ler = { caminho: String -> File(pastaDados, caminho).takeIf(File::exists)?.readText() }

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar(ler), "carregar: ${bd.erros.take(3)}")
        return bd
    }

    private fun benfica(bd: BaseDados, semente: Long = 5) = Carreira.nova(bd, "f8", "Rui", ModoCarreira.TREINADOR, "POR_02", semente = semente)

    @Test
    fun `rivalidades carregam e referem clubes que existem`() {
        val bd = carregar()
        val r = bd.rivalidades()
        assertTrue(r.lista.size >= 40, "poucas rivalidades: ${r.lista.size}")
        for (x in r.lista) {
            assertTrue(x.a in bd.equipas && x.b in bd.equipas, "clube em falta em ${x.nome}")
            assertTrue(x.a != x.b && x.intensidade in 1..3 && x.nome.isNotBlank(), "rivalidade inválida: ${x.nome}")
        }
        // Há derbies em todos os países.
        for (pais in bd.paises.keys) assertTrue(r.lista.any { bd.equipas.getValue(it.a).pais == pais }, "sem derbies em $pais")
        assertEquals("Derby de Lisboa", r.entre("POR_02", "POR_01")?.nome)
        assertEquals("Derby de Lisboa", r.entre("POR_01", "POR_02")?.nome)
        assertEquals(0, r.intensidade("POR_02", "POR_36"))
        // Sem ficheiro: não há derbies e nada falha.
        val sem = BaseDados()
        sem.carregar { if (it == "rivalidades.json") null else ler(it) }
        assertTrue(sem.rivalidades().lista.isEmpty())
        // Vai na base de dados gravada.
        assertTrue("rivalidades.json" in bd.paraJsonCompleto())
    }

    @Test
    fun `derbies aparecem no calendario do Benfica`() {
        val c = benfica(carregar())
        val derbies = c.jogosDoClube().filter { it.derby != null }
        assertTrue(derbies.any { it.derby == "Derby de Lisboa" && it.adversario.id == "POR_01" && it.intensidadeDerby == 3 })
        assertTrue(derbies.any { it.derby == "O Clássico" && it.adversario.id == "POR_03" })
        // Dois jogos de liga de cada derby (casa e fora).
        assertEquals(2, c.jogosDeLiga().count { it.derby == "Derby de Lisboa" })
        assertTrue(c.jogosDeLiga().filter { it.derby == null }.all { it.intensidadeDerby == 0 })
    }

    private fun jogoFalso(base: JogoDoClube, golosMeus: Int, golosDeles: Int, derby: Boolean): JogoDoClube {
        val r = if (base.emCasa) Resultado(base.clubeId, base.adversario.id, golosMeus, golosDeles) else Resultado(base.adversario.id, base.clubeId, golosDeles, golosMeus)
        return JogoDoClube(
            base.jornada, base.indice, base.data, base.adversario, base.emCasa, r, base.competicaoId, base.competicaoNome,
            clubeId = base.clubeId, derby = if (derby) "Derby de Lisboa" else null, intensidadeDerby = if (derby) 3 else 0,
        )
    }

    @Test
    fun `derby mexe mais na confianca do que um jogo normal`() {
        val c = benfica(carregar())
        val base = c.jogosDeLiga().first { it.derby == "Derby de Lisboa" }
        fun variacao(golosMeus: Int, golosDeles: Int, derby: Boolean): Pair<Int, Int> {
            c.info.confiancaDirecao = 50
            c.info.confiancaAdeptos = 50
            c.avaliarJogo(jogoFalso(base, golosMeus, golosDeles, derby))
            return (c.info.confiancaDirecao - 50) to (c.info.confiancaAdeptos - 50)
        }
        val vitoriaNormal = variacao(2, 0, false)
        val vitoriaDerby = variacao(2, 0, true)
        assertTrue(vitoriaDerby.first > vitoriaNormal.first && vitoriaDerby.second > vitoriaNormal.second, "$vitoriaNormal contra $vitoriaDerby")
        val derrotaNormal = variacao(0, 2, false)
        val derrotaDerby = variacao(0, 2, true)
        assertTrue(derrotaDerby.first < derrotaNormal.first && derrotaDerby.second < derrotaNormal.second, "$derrotaNormal contra $derrotaDerby")
        // Entre 1,5 e 2 vezes nos adeptos (4 -> 8 num clássico).
        assertEquals(vitoriaNormal.second * 2, vitoriaDerby.second)
    }

    @Test
    fun `derby tem mais cartoes e estadio cheio`() {
        val bd = carregar()
        val casa = bd.equipas.getValue("POR_02")
        val fora = bd.equipas.getValue("POR_01")
        fun cartoes(derby: Int): Int {
            val rng = Random(11)
            var total = 0
            repeat(400) {
                val rel = MotorJogo.simular(casa, fora, rng, derby = derby)
                total += rel.casa.estatisticas.amarelos + rel.fora.estatisticas.amarelos + 3 * (rel.casa.estatisticas.vermelhos + rel.fora.estatisticas.vermelhos)
            }
            return total
        }
        assertTrue(cartoes(3) > cartoes(0), "o derby devia ter mais cartões")
        assertEquals(casa.capacidade, Financas.assistencia(casa, derby = true))
        assertTrue(Financas.assistencia(casa, derby = true) >= Financas.assistencia(casa))
        assertTrue(Financas.bilheteira(casa, derby = true) > Financas.bilheteira(casa))
        // O ambiente mexe na assistência, sem passar da lotação.
        val pequeno = bd.equipas.values.minByOrNull { it.reputacao }!!
        assertTrue(Financas.assistencia(pequeno, fatorAmbiente = 1.06) > Financas.assistencia(pequeno, fatorAmbiente = 0.9))
        assertTrue(Financas.assistencia(casa, fatorAmbiente = 1.5) <= casa.capacidade)
    }

    @Test
    fun `ambiente segue os resultados e a confianca`() {
        assertEquals(Ambiente.EUFORICO, Ambiente.calcular(95, 90, 15, 5))
        assertEquals(Ambiente.BOM, Ambiente.calcular(70, 65, 9, 5))
        assertEquals(Ambiente.NORMAL, Ambiente.calcular(55, 55, 5, 5))
        assertEquals(Ambiente.TENSO, Ambiente.calcular(35, 40, 2, 5))
        assertEquals(Ambiente.HOSTIL, Ambiente.calcular(10, 20, 0, 5))
        // Sem jogos conta só a confiança; um derby acentua o que já se sente.
        assertEquals(Ambiente.NORMAL, Ambiente.calcular(60, 60, 0, 0))
        assertTrue(Ambiente.calcular(66, 66, 9, 5, derby = 3).ordinal <= Ambiente.calcular(66, 66, 9, 5).ordinal)
        assertTrue(Ambiente.calcular(30, 30, 1, 5, derby = 3).ordinal >= Ambiente.calcular(30, 30, 1, 5).ordinal)
        assertTrue(Ambiente.EUFORICO.fator > Ambiente.NORMAL.fator && Ambiente.NORMAL.fator > Ambiente.HOSTIL.fator)
        // Na carreira, a confiança dos adeptos e da direção muda o ambiente.
        val c = benfica(carregar())
        c.info.confiancaAdeptos = 98; c.info.confiancaDirecao = 95
        val alto = c.ambiente()
        c.info.confiancaAdeptos = 5; c.info.confiancaDirecao = 5
        val baixo = c.ambiente()
        assertTrue(alto.ordinal < baixo.ordinal, "$alto contra $baixo")
        assertEquals(Ambiente.HOSTIL, baixo)
    }

    /** Joga uma época inteira e junta as notícias que vão surgindo (sem repetir) e os máximos por jogo. */
    private fun epocaComNoticias(c: Carreira): List<Noticia> {
        val vistas = LinkedHashSet<Noticia>()
        val identidades = java.util.IdentityHashMap<Noticia, Boolean>()
        while (!c.gestor.epocaTerminada() && !c.info.despedido) {
            c.avancar()
            assertTrue(c.manchetes.size <= GeradorNoticias.MAX_NOTICIAS)
            val novas = c.manchetes.filter { identidades.put(it, true) == null }
            assertTrue(novas.size <= GeradorNoticias.MAX_POR_DIA, "demasiadas notícias de uma vez: ${novas.size}")
            vistas += novas
        }
        return vistas.toList()
    }

    @Test
    fun `noticias de uma epoca sao variadas do clube e dos outros e gravam-se`() {
        val c = benfica(carregar(), 3)
        val todas = epocaComNoticias(c)
        assertTrue(todas.size >= 40, "poucas notícias numa época: ${todas.size}")
        val tipos = todas.map { it.tipo }.toSet()
        assertTrue(tipos.size >= 6, "pouca variedade de tipos: $tipos")
        assertTrue(todas.any { it.doClube } && todas.any { !it.doClube }, "notícias só de um lado")
        assertTrue(todas.map { it.titulo }.toSet().size >= todas.size * 6 / 10, "títulos demasiado repetidos")
        assertTrue(todas.all { it.titulo.isNotBlank() && it.texto.isNotBlank() && it.data.length == 10 })
        assertTrue(todas.any { it.tipo == "derby" })
        // Só se guardam as últimas 80, mais recentes primeiro.
        assertTrue(c.manchetes.size <= GeradorNoticias.MAX_NOTICIAS)
        assertTrue(todas.containsAll(c.manchetes))
        // Gravar e carregar.
        val d = Gravador.deTexto(Gravador.paraTexto(c), Gravador.baseDadosParaTexto(c))
        assertEquals(c.manchetes.map { it.titulo to it.texto }, d.manchetes.map { it.titulo to it.texto })
        assertEquals(c.geradorNoticias.marcas, d.geradorNoticias.marcas)
        if (!d.info.despedido) { d.novaEpoca(); assertNotNull(d.avancar()) }
    }

    @Test
    fun `gravacao antiga sem noticias nem rivalidades carrega e continua`() {
        val bd = carregar()
        val c = benfica(bd, 4)
        repeat(12) { c.avancar() }
        assertTrue(c.manchetes.isNotEmpty())
        val estado = Json.parseToJsonElement(Gravador.paraTexto(c)).jsonObject
        assertTrue("noticias" in estado)
        val antigo = JsonObject(estado.filterKeys { it != "noticias" && it != "noticiasMarcas" }).toString()
        val ficheiros = Json.parseToJsonElement(Gravador.baseDadosParaTexto(c)).jsonObject
        assertTrue("rivalidades.json" in ficheiros)
        val antigaBd = JsonObject(ficheiros.filterKeys { it != "rivalidades.json" }).toString()
        // Com os dados da aplicação: derbies de volta, notícias vazias.
        val d = Gravador.deTexto(antigo, antigaBd, ler)
        assertTrue(d.manchetes.isEmpty())
        assertTrue(d.bd.rivalidades().lista.isNotEmpty())
        // Sem eles: sem derbies, e o jogo continua.
        val e = Gravador.deTexto(antigo, antigaBd)
        assertTrue(e.bd.rivalidades().lista.isEmpty())
        assertTrue(e.jogosDoClube().none { it.derby != null })
        for (carreira in listOf(d, e)) {
            repeat(8) { carreira.avancar() }
            assertTrue(carreira.manchetes.isNotEmpty(), "devia voltar a haver notícias")
        }
    }
}
