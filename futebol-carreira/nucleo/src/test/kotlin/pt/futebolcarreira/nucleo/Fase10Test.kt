package pt.futebolcarreira.nucleo

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.dados.EditorDados
import pt.futebolcarreira.nucleo.dados.TipoImportacao
import pt.futebolcarreira.nucleo.dados.Validador
import pt.futebolcarreira.nucleo.dados.carregarComSubstituicoes
import pt.futebolcarreira.nucleo.modelos.Jogador
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Testes da Fase 10: validação e importação/exportação de JSON, e o editor de dados. */
class Fase10Test {
    private val pastaDados = File("../app/src/main/assets/dados")

    private fun lerAsset(caminho: String) = File(pastaDados, caminho).takeIf(File::exists)?.readText()

    private fun carregar() = BaseDados().also { bd ->
        assertTrue(bd.carregar(::lerAsset), "carregar sem erros: ${bd.erros.take(3)}")
    }

    /** Os ficheiros em bruto (como estão na pasta de dados), como se fossem um ficheiro exportado. */
    private fun ficheirosOriginais(): MutableMap<String, String> =
        carregar().paraJsonCompleto().keys.associateWith { lerAsset(it)!! }.toMutableMap()

    private fun comoTexto(mapa: Map<String, String>) =
        BaseDados.JSON.encodeToString(MapSerializer(String.serializer(), String.serializer()), mapa)

    @Test
    fun `validador aceita os dados que vem com o jogo`() {
        val bd = carregar()
        val r = Validador.validarMapa(ficheirosOriginais(), bd)
        assertTrue(r.ok, "sem erros: ${r.erros}")
        assertNotNull(r.ficheiros)
        val r2 = Validador.validarTexto(comoTexto(ficheirosOriginais()), bd)
        assertTrue(r2.ok, "sem erros: ${r2.erros}")
    }

    @Test
    fun `validador rejeita json estragado`() {
        val bd = carregar()
        val r = Validador.validarTexto("{ isto nao e json", bd)
        assertTrue(!r.ok)
        assertTrue(r.erros.first().contains("JSON"), r.erros.toString())
        // JSON bom mas um ficheiro lá dentro estragado.
        val f = ficheirosOriginais()
        f["equipas/POR.json"] = "[ { \"id\": "
        val r2 = Validador.validarTexto(comoTexto(f), bd)
        assertTrue(!r2.ok)
        assertTrue(r2.erros.any { it.contains("equipas/POR.json") }, r2.erros.toString())
    }

    @Test
    fun `validador rejeita clube em falta e liga com tamanho errado`() {
        val bd = carregar()
        val f = ficheirosOriginais()
        val copia = carregar()
        val sai = copia.equipasDaLiga("POR1").last()
        // Tira o clube do ficheiro de equipas e os seus jogadores.
        copia.equipas.remove(sai.id)
        for (j in sai.plantel + sai.academia) copia.jogadores.remove(j.id)
        val ficheiros = copia.paraJson()
        f["equipas/POR.json"] = ficheiros.getValue("equipas/POR.json")
        f["jogadores/POR.json"] = ficheiros.getValue("jogadores/POR.json")
        val r = Validador.validarMapa(f, bd)
        assertTrue(!r.ok)
        assertTrue(r.erros.any { it.contains("POR1") && it.contains("devia ter") }, r.erros.toString())
    }

    @Test
    fun `validador rejeita clube com 10 jogadores e sem guarda-redes`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("POR1").first()
        val pacote = Validador.exportarClube(bd, clube.id)
        // Sobram só 10 jogadores.
        val copia = carregar()
        val c = copia.equipas.getValue(clube.id)
        for (j in c.plantel.drop(10).toList()) copia.removerJogador(j)
        val r = Validador.validarTexto(Validador.exportarClube(copia, clube.id), bd)
        assertEquals(TipoImportacao.CLUBE, r.tipo)
        assertTrue(!r.ok)
        assertTrue(r.erros.any { it.contains("só 10 jogadores") }, r.erros.toString())
        // O mesmo clube sem guarda-redes.
        val sem = carregar()
        val c2 = sem.equipas.getValue(clube.id)
        for (j in c2.plantel.filter { it.posicao == "GR" }) j.posicao = "DC"
        val r2 = Validador.validarTexto(Validador.exportarClube(sem, clube.id), bd)
        assertTrue(r2.erros.any { it.contains("guarda-redes") }, r2.erros.toString())
        // E o original continua a passar.
        assertTrue(Validador.validarTexto(pacote, bd).ok)
    }

    @Test
    fun `validador rejeita atributos fora do intervalo`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("POR1").first()
        val copia = carregar()
        val j = copia.equipas.getValue(clube.id).plantel.first()
        j.atributos["remate"] = 150
        j.atributos["ritmo"] = 0
        val r = Validador.validarTexto(Validador.exportarClube(copia, clube.id), bd)
        assertTrue(!r.ok)
        assertTrue(r.erros.any { it.contains("'remate' = 150") }, r.erros.toString())
        assertTrue(r.erros.any { it.contains("'ritmo' = 0") }, r.erros.toString())
        // O mesmo na importação completa.
        val f = ficheirosOriginais()
        f["jogadores/POR.json"] = copia.paraJson().getValue("jogadores/POR.json")
        assertTrue(!Validador.validarMapa(f, bd).ok)
    }

    @Test
    fun `validador rejeita clube inexistente e clube que muda de liga`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("POR1").first()
        val copia = carregar()
        copia.equipas.getValue(clube.id).liga = "POR2"
        val r = Validador.validarTexto(Validador.exportarClube(copia, clube.id), bd)
        assertTrue(r.erros.any { it.contains("liga") }, r.erros.toString())
        val texto = Validador.exportarClube(bd, clube.id).replace("\"${clube.id}\"", "\"XXX_99\"")
        val r2 = Validador.validarTexto(texto, bd)
        assertTrue(r2.erros.any { it.contains("não existe") }, r2.erros.toString())
    }

    @Test
    fun `ids repetidos recebem ids novos e nacionalidades desconhecidas so avisam`() {
        val bd = carregar()
        val a = bd.equipasDaLiga("POR1")[0]
        val b = bd.equipasDaLiga("POR1")[1]
        val copia = carregar()
        val ja = copia.equipas.getValue(a.id).plantel.first()
        val jb = copia.equipas.getValue(b.id).plantel.first()
        val idDeB = jb.id
        ja.id = idDeB                       // repetido com um jogador de outro clube
        ja.nacionalidade = "ZZZ"            // código desconhecido
        val r = Validador.validarTexto(Validador.exportarClube(copia, a.id), bd)
        assertTrue(r.ok, r.erros.toString())
        assertTrue(r.avisos.any { it.contains("repetido") }, r.avisos.toString())
        assertTrue(r.avisos.any { it.contains("ZZZ") }, r.avisos.toString())
        val ids = r.jogadores.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(idDeB !in ids)
        assertTrue(ids.all { it !in bd.jogadores.keys || bd.jogadores.getValue(it).equipa == a.id })

        // Na importação completa: dois jogadores com o mesmo id no ficheiro.
        val f = ficheirosOriginais()
        val completa = carregar()
        val lista = completa.equipas.getValue(a.id).plantel
        lista[1].id = lista[0].id
        f["jogadores/POR.json"] = completa.paraJson().getValue("jogadores/POR.json")
        val r2 = Validador.validarMapa(f, bd)
        assertTrue(r2.ok, r2.erros.toString())
        assertTrue(r2.avisos.any { it.contains("repetido") })
        val novo = BaseDados().also { assertTrue(it.carregar { c -> r2.ficheiros!![c] }) }
        assertEquals(bd.jogadores.size, novo.jogadores.size)
    }

    @Test
    fun `exportar e importar da os mesmos dados`() {
        val bd = carregar()
        // Base completa.
        val r = Validador.validarTexto(Validador.exportarBase(bd), bd)
        assertTrue(r.ok, r.erros.toString())
        assertEquals(bd.paraJsonCompleto(), r.ficheiros)
        // Um país.
        val rp = Validador.validarTexto(Validador.exportarPais(bd, "POR"), bd)
        assertTrue(rp.ok, rp.erros.toString())
        assertEquals(bd.paraJsonCompleto(), rp.ficheiros)
        // Um clube.
        val clube = bd.equipasDaLiga("ESP1").first()
        val rc = Validador.validarTexto(Validador.exportarClube(bd, clube.id), bd)
        assertTrue(rc.ok, rc.erros.toString())
        assertEquals(TipoImportacao.CLUBE, rc.tipo)
        assertTrue(rc.avisos.isEmpty(), rc.avisos.toString())
        val antes = Validador.exportarClube(bd, clube.id)
        val destino = carregar()
        EditorDados(destino).aplicarClube(rc.equipa!!, rc.jogadores)
        assertEquals(antes, Validador.exportarClube(destino, clube.id))
        assertEquals(bd.paraJsonCompleto(), destino.paraJsonCompleto())
    }

    @Test
    fun `importar um clube editado muda so esse clube`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("POR1").first()
        val origem = carregar()
        val e = origem.equipas.getValue(clube.id)
        e.nome = "Clube Editado"
        e.plantel.first().nome = "Jogador Novo"
        val r = Validador.validarTexto(Validador.exportarClube(origem, clube.id), bd)
        assertTrue(r.ok, r.erros.toString())
        EditorDados(bd).aplicarClube(r.equipa!!, r.jogadores)
        assertEquals("Clube Editado", bd.equipas.getValue(clube.id).nome)
        assertTrue(bd.equipas.getValue(clube.id).plantel.any { it.nome == "Jogador Novo" })
        assertEquals(carregar().jogadores.size, bd.jogadores.size)
    }

    @Test
    fun `ficheiro editado tem prioridade sobre o original`() {
        val originais = ficheirosOriginais()
        val editado = carregar()
        val clube = editado.equipasDaLiga("POR1").first()
        editado.equipas.getValue(clube.id).nome = "Nome Editado"
        val substituicoes = editado.paraJson().filterKeys { it == "equipas/POR.json" }
        val bd = BaseDados()
        assertTrue(bd.carregarComSubstituicoes({ substituicoes[it] }, { originais[it] }), bd.erros.toString())
        assertEquals("Nome Editado", bd.equipas.getValue(clube.id).nome)
        // O resto vem dos originais.
        assertEquals(carregar().jogadores.size, bd.jogadores.size)
        assertEquals(carregar().equipas.getValue("ESP_01").nome, bd.equipas.getValue("ESP_01").nome)
        // Sem substituições, tudo igual ao original.
        val limpa = BaseDados()
        assertTrue(limpa.carregarComSubstituicoes({ null }, { originais[it] }))
        assertEquals(carregar().equipas.getValue(clube.id).nome, limpa.equipas.getValue(clube.id).nome)
    }

    @Test
    fun `editor respeita 18 jogadores e 2 guarda-redes`() {
        val bd = carregar()
        val editor = EditorDados(bd)
        val clube = bd.equipasDaLiga("POR1").first()
        val outro = bd.equipasDaLiga("POR1")[1]
        // Apagar até ao mínimo: o seguinte é recusado.
        val extras = clube.plantel.filter { it.posicao != "GR" }.take(clube.plantel.size - Validador.MIN_JOGADORES)
        for (j in extras) assertNull(editor.apagarJogador(j.id))
        assertEquals(Validador.MIN_JOGADORES, clube.plantel.size)
        val campo = clube.plantel.first { it.posicao != "GR" }
        assertNotNull(editor.apagarJogador(campo.id))
        assertNotNull(editor.moverJogador(campo.id, outro.id))
        // Depois de ganhar um jogador, já pode largar um.
        val novo = Jogador(0, "Teste Novo", "POR", 20, "MC", "direito", clube.id, 70, Jogador.ATRIBUTOS.associateWith { 60 }.toMutableMap())
        assertNull(editor.criarJogador(novo))
        assertTrue(novo.id > 0)
        assertNull(editor.moverJogador(campo.id, outro.id))
        // Guarda-redes: não se pode ficar com menos de 2.
        val grs = clube.plantel.filter { it.posicao == "GR" }
        if (grs.size == Validador.MIN_GUARDA_REDES) assertNotNull(editor.apagarJogador(grs.first().id))
    }

    @Test
    fun `editor valida clubes e jogadores e troca ligas`() {
        val bd = carregar()
        val editor = EditorDados(bd)
        val a = bd.equipasDaLiga("POR1").first()
        val b = bd.equipasDaLiga("POR2").first()
        val antesA = bd.equipasDaLiga("POR1").size
        assertNull(editor.trocarLiga(a.id, b.id))
        assertEquals("POR2", a.liga)
        assertEquals("POR1", b.liga)
        assertEquals(antesA, bd.equipasDaLiga("POR1").size)
        assertNotNull(editor.trocarLiga(a.id, bd.equipasDaLiga("ESP1").first().id))   // outro país
        assertNotNull(editor.editarClube(a.id, "", "ABC", "C", "E", 1000, 50, 1000L, listOf("#ffffff")))
        assertNotNull(editor.editarClube(a.id, "Nome", "ABC", "C", "E", 1000, 120, 1000L, listOf("#ffffff")))
        assertNotNull(editor.editarClube(a.id, "Nome", "ABC", "C", "E", 1000, 50, 1000L, listOf("vermelho")))
        assertNull(editor.editarClube(a.id, "Nome Novo", "nnn", "Cidade", "Estádio", 12345, 60, 5_000_000L, listOf("#112233", "#ffffff")))
        assertEquals("NNN", a.abreviatura)
        val j = a.plantel.first()
        val atributos = j.atributos.toMutableMap().also { it["remate"] = 100 }
        assertNotNull(editor.editarJogador(j.id, j.nome, j.nacionalidade, j.idade, j.posicao, j.pe, atributos, j.potencial, j.contratoAte, j.salario))
        atributos["remate"] = 80
        assertNull(editor.editarJogador(j.id, "Outro Nome", "BRA", 25, j.posicao, "ambos", atributos, 99, 2030, 1_000_000))
        assertEquals("Outro Nome", j.nome)
        assertEquals(80, j.atributos["remate"])
    }
}
