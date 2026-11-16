package pt.futebolcarreira.nucleo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Gravador
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.dados.Nacionalidades
import pt.futebolcarreira.nucleo.simulacao.CatalogoSelecoes
import pt.futebolcarreira.nucleo.simulacao.EstadoSelecoes
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Nacionalidades reais dos jogadores e a sua utilização nas seleções. */
class NacionalidadesTest {
    private val pastaDados = File("../app/src/main/assets/dados")
    private val ler: (String) -> String? = { File(pastaDados, it).takeIf(File::exists)?.readText() }

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar(ler), "carregar: ${bd.erros.take(3)}")
        return bd
    }

    private fun estado(bd: BaseDados = carregar(), epoca: Int = 2026) =
        EstadoSelecoes.criar(bd, assertNotNull(CatalogoSelecoes.carregar(ler)), epoca, 7L)

    @Test
    fun `todos os codigos de nacionalidade dos ficheiros tem nome`() {
        val nomes = Nacionalidades.deTexto(ler("nacionalidades.json"))
        val codigosBrutos = File(pastaDados, "jogadores").listFiles { f -> f.extension == "json" }!!.flatMap { f ->
            Regex("\"nacionalidade\":\"([A-Z]+)\"").findAll(f.readText()).map { it.groupValues[1] }.toList()
        }.toSet()
        assertTrue(codigosBrutos.size > 50)
        for (c in codigosBrutos) assertTrue(nomes.conhece(c), "sem nome: $c")
        val catalogo = assertNotNull(CatalogoSelecoes.carregar(ler))
        for (n in catalogo.nacoes) assertTrue(nomes.conhece(n.id), "seleção sem nome: ${n.id}")
        assertEquals("Brasil", nomes.nome("BRA"))
        assertEquals("Chéquia", nomes.nome("CZE"))
        assertEquals("XYZ", nomes.nome("XYZ"))
    }

    @Test
    fun `alias normalizados ao carregar os jogadores`() {
        val bd = carregar()
        val usados = bd.jogadores.values.map { it.nacionalidade }.toSet()
        for (alias in Nacionalidades.ALIAS.keys) assertTrue(alias !in usados, "alias por normalizar: $alias")
        assertEquals("RCH", Nacionalidades.normalizar("CZE"))
        assertEquals("ESC", Nacionalidades.normalizar("SCO"))
        assertEquals("EQU", Nacionalidades.normalizar("ECU"))
        assertEquals("CAM", Nacionalidades.normalizar("CMR"))
        assertEquals("GAN", Nacionalidades.normalizar("GHA"))
        assertEquals("CPV", Nacionalidades.normalizar("CAV"))
        assertEquals("RDC", Nacionalidades.normalizar("COD"))
        assertEquals("ALE", Nacionalidades.normalizar("GER"))
        assertEquals("POR", Nacionalidades.normalizar("POR"))
        // Os destinos dos alias são códigos das seleções ou nações com nome.
        val catalogo = assertNotNull(CatalogoSelecoes.carregar(ler)).nacoes.map { it.id }.toSet()
        for (destino in Nacionalidades.ALIAS.values) assertTrue(destino in catalogo || destino in setOf("CPV", "MLI", "BFA", "RDC", "GAN"), destino)
        assertEquals("Cabo Verde", bd.nomeNacionalidade("CAV"))
    }

    @Test
    fun `o Brasil convoca jogadores da base de dados com 23 e pelo menos 3 guarda-redes`() {
        val bd = carregar()
        val s = estado(bd)
        assertTrue(s.usaJogadoresReais("BRA"))
        val c = s.calcularConvocatoria("BRA")
        assertEquals(23, c.size)
        assertEquals(23, c.map { it.id }.toSet().size)
        assertTrue(c.count { it.posicao == "GR" } >= 3)
        assertTrue(c.all { it.id > 0 && it.nacionalidade == "BRA" && it.id in bd.jogadores }, "só reais")
        assertEquals(c.map { it.id }.toSet(), s.convocados("BRA").map { it.id }.toSet())
        // Outras nações fora das seis dos clubes.
        for (n in listOf("ARG", "NED", "BEL", "CRO", "MAR", "SEN", "URU", "COL", "DIN", "SUI")) {
            assertTrue(s.usaJogadoresReais(n), n)
            val conv = s.calcularConvocatoria(n)
            assertEquals(23, conv.size, n)
            assertTrue(conv.count { it.posicao == "GR" } >= 3, n)
            assertTrue(conv.count { it.id > 0 } >= 16, n)
        }
    }

    @Test
    fun `nacao com poucos jogadores reais continua a ter 23 convocados mistos ou ficticios`() {
        val bd = carregar()
        val s = estado(bd)
        val poucas = s.nacoes.values.filter { !it.baseDados && !s.usaJogadoresReais(it.id) }
        assertTrue(poucas.size >= 5, "nações com plantel fictício: ${poucas.size}")
        for (n in poucas) {
            val c = s.convocados(n.id)
            assertEquals(23, c.size, n.nome)
            assertTrue(c.count { it.posicao == "GR" } >= 3, n.nome)
            assertEquals(c.size, c.map { it.id }.toSet().size)
        }
        // Os fictícios nunca entram na base de dados.
        assertTrue(bd.jogadores.keys.all { it > 0 })
    }

    @Test
    fun `jogador de nacao fora da base de dados no clube do utilizador aparece nos convocados`() {
        val bd = carregar()
        // O par (clube, nação europeia fora da base de dados) com mais jogadores: joga a Liga das Nações já em setembro.
        val s0 = estado(bd)
        val europeias = s0.nacoes.values.filter { it.europeia && !it.baseDados && s0.usaJogadoresReais(it.id) }.map { it.id }
        val (clube, nacao) = bd.equipas.values.flatMap { e -> europeias.map { n -> e to n } }
            .maxBy { (e, n) -> e.plantel.count { it.nacionalidade == n } }
        assertTrue(clube.plantel.count { it.nacionalidade == nacao } >= 2, "$nacao em ${clube.id}")
        val c = Carreira.nova(bd, "nac-sel", "Ana", ModoCarreira.TREINADOR, clube.id, semente = 5)
        val s = assertNotNull(c.gestor.selecoes)
        val doClube = clube.plantel.filter { it.nacionalidade == nacao }
        val chamados = s.convocados(nacao)
        assertTrue(doClube.any { j -> chamados.any { it.id == j.id } }, "algum do clube é chamado")
        while (c.gestor.data < LocalDate.of(2026, 11, 20)) c.avancar()
        val janela = s.convocadosDaJanela()
        assertTrue(nacao in janela, "$nacao convocada na janela")
        val meus = janela.getValue(nacao).mapNotNull { bd.jogadores[it] }.filter { it.equipa == clube.id }
        assertTrue(meus.isNotEmpty())
        assertEquals(nacao, s.nacaoDoConvocado(meus.first().id))
        assertTrue(c.mensagens.any { it.titulo == "Convocatórias" || it.titulo == "Convocatória" })
    }

    @Test
    fun `gravar e carregar mantem convocatorias mistas e nomes das nacionalidades`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("POR1").maxByOrNull { it.reputacao }!!.id
        val c = Carreira.nova(bd, "nac-gravar", "Rui", ModoCarreira.TREINADOR, clube, semente = 3)
        while (c.gestor.data < LocalDate.of(2026, 11, 20)) c.avancar()
        val s = assertNotNull(c.gestor.selecoes)
        val ficheiros = Json.parseToJsonElement(Gravador.baseDadosParaTexto(c)).jsonObject
        assertTrue("nacionalidades.json" in ficheiros, "vai na base de dados gravada")
        val d = Gravador.deTexto(Gravador.paraTexto(c), Gravador.baseDadosParaTexto(c))
        val t = assertNotNull(d.gestor.selecoes)
        assertEquals(s.convocadosDaJanela(), t.convocadosDaJanela())
        assertEquals("Brasil", d.bd.nomeNacionalidade("BRA"))
        for (n in listOf("BRA", "ISL", "GEO")) assertEquals(s.convocatoriaAtual(n).map { it.id }, t.convocatoriaAtual(n).map { it.id })
        assertNotNull(d.avancar())
    }

    @Test
    fun `gravacao antiga sem nacionalidades e com codigos antigos carrega`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("POR1").maxByOrNull { it.reputacao }!!.id
        val c = Carreira.nova(bd, "nac-antiga", "Rui", ModoCarreira.TREINADOR, clube, semente = 3)
        while (c.gestor.data < LocalDate.of(2026, 10, 20)) c.avancar()
        val ficheiros = Json.parseToJsonElement(Gravador.baseDadosParaTexto(c)).jsonObject
        // Base de dados antiga: sem nacionalidades.json e com códigos antigos nos jogadores.
        val antigos = ficheiros.filterKeys { it != "nacionalidades.json" }.mapValues { (k, v) ->
            val texto = (v as kotlinx.serialization.json.JsonPrimitive).content
            kotlinx.serialization.json.JsonPrimitive(if (k.startsWith("jogadores/")) texto.replace("\"nacionalidade\":\"RCH\"", "\"nacionalidade\":\"CZE\"").replace("\"nacionalidade\":\"CPV\"", "\"nacionalidade\":\"CAV\"") else texto)
        }
        val antiga = JsonObject(antigos).toString()
        val sem = Gravador.deTexto(Gravador.paraTexto(c), antiga)
        assertEquals("CAV", sem.bd.nomeNacionalidade("CAV"), "sem ficheiro de nomes fica o código")
        assertTrue(sem.bd.jogadores.values.none { it.nacionalidade in Nacionalidades.ALIAS })
        val com = Gravador.deTexto(Gravador.paraTexto(c), antiga) { ler(it) }
        assertEquals("Cabo Verde", com.bd.nomeNacionalidade("CAV"))
        assertTrue(com.bd.jogadores.values.none { it.nacionalidade in Nacionalidades.ALIAS })
        assertNotNull(com.avancar())
    }
}
