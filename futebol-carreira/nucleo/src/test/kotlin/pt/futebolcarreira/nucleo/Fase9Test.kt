package pt.futebolcarreira.nucleo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.EstatutoNoClube
import pt.futebolcarreira.nucleo.carreira.Gravador
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.carreira.PedidoTreinador
import pt.futebolcarreira.nucleo.carreira.TipoPedido
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.FocoTreino
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.simulacao.Premios
import pt.futebolcarreira.nucleo.simulacao.ResumoEpoca
import pt.futebolcarreira.nucleo.simulacao.Treino
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Testes da Fase 9: carreira completa de jogador. */
class Fase9Test {
    private val pastaDados = File("../app/src/main/assets/dados")
    private val ler = { caminho: String -> File(pastaDados, caminho).takeIf(File::exists)?.readText() }

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar(ler), "carregar: ${bd.erros.take(3)}")
        return bd
    }

    private fun jogador(
        posicao: String = "MC", clube: String = "POR_10", semente: Long = 3, nacionalidade: String = "", pe: String = "",
    ) = Carreira.nova(carregar(), "f9", "Rui Craque", ModoCarreira.JOGADOR, clube, posicao, semente, nacionalidade, pe)

    /** Faz do jogador do utilizador uma estrela (todos os atributos a 95), para ele jogar sempre. */
    private fun estrela(c: Carreira, nivel: Int = 95) {
        val j = c.jogador!!
        for (a in Jogador.ATRIBUTOS) j.atributos[a] = nivel
        j.potencial = 99
        c.equipa.invalidarForca()
    }

    @Test
    fun `criacao com nacionalidade posicao pe e clube escolhidos`() {
        val c = jogador("ED", "ESP_05", 7, "BRA", "esquerdo")
        val j = assertNotNull(c.jogador)
        assertEquals("Rui Craque", j.nome)
        assertEquals("BRA", j.nacionalidade)
        assertEquals("ED", j.posicao)
        assertEquals("esquerdo", j.pe)
        assertEquals(17, j.idade)
        assertEquals("ESP_05", j.equipa)
        assertEquals("ESP_05", c.info.equipaId)
        assertTrue(j.potencial in 86..95)
        assertTrue(j.contratoAte >= c.gestor.ano + 2 && j.salario > 0)
        assertTrue(j in c.equipa.plantel)
        // O nível depende do clube: um grande começa mais alto do que um pequeno.
        val grande = jogador("MC", "POR_02", 7).jogador!!
        val pequeno = jogador("MC", "POR_18", 7).jogador!!
        assertTrue(grande.geral() > pequeno.geral(), "${grande.geral()} contra ${pequeno.geral()}")
        // Sem escolhas, fica a nacionalidade do clube e o pé que o gerador deu.
        val normal = jogador("MC", "POR_10", 7).jogador!!
        assertEquals("POR", normal.nacionalidade)
        assertTrue(normal.pe in listOf("direito", "esquerdo", "ambos"))
        // Nacionalidades à escolha: os seis países da base de dados e mais algumas.
        val lista = CarreiraJogadorAjuda.nacionalidades(carregar())
        assertTrue(lista.map { it.first }.containsAll(listOf("POR", "ESP", "ING", "ITA", "ALE", "FRA", "BRA", "ARG")))
    }

    @Test
    fun `estatuto no clube depende do lugar na posicao`() {
        val c = jogador("MC", "POR_10", 5)
        val j = c.jogador!!
        val cj = c.jogadorCarreira
        // Muito acima dos colegas: indiscutível.
        estrela(c)
        assertEquals(EstatutoNoClube.INDISCUTIVEL, cj.calcularEstatuto(j))
        // Muito abaixo: pouco utilizado.
        for (a in Jogador.ATRIBUTOS) j.atributos[a] = 20
        assertEquals(EstatutoNoClube.POUCO_UTILIZADO, cj.calcularEstatuto(j))
        // À entrada do onze: titular (ou rotação logo abaixo).
        val colegas = c.equipa.plantel.filter { it.posicao == "MC" && it.id != j.id }.map { it.geral() }.sortedDescending()
        val lugares = c.equipa.formacao.lugares.count { it == "MC" }.coerceAtLeast(1)
        val maisFraco = colegas[lugares - 1]
        for (a in Jogador.ATRIBUTOS) j.atributos[a] = maisFraco + 1
        assertTrue(cj.calcularEstatuto(j) <= EstatutoNoClube.TITULAR, "${cj.calcularEstatuto(j)}")
        for (a in Jogador.ATRIBUTOS) j.atributos[a] = maisFraco - 1
        assertTrue(cj.calcularEstatuto(j) in listOf(EstatutoNoClube.ROTACAO, EstatutoNoClube.SUPLENTE))
        // O estatuto aparece na primeira mensagem e fica guardado.
        assertNotNull(c.jogadorCarreira.estado.estatuto)
        assertTrue(c.mensagens.last().texto.contains("Estatuto"))
    }

    @Test
    fun `o treinador escolhe o onze tambem pela forma`() {
        val c = jogador("MC", "POR_10", 5)
        val e = c.equipa
        e.formacao = Formacao.F433
        val avancados = e.plantel.filter { it.posicao == "PL" }.sortedByDescending { it.geral() }
        val a = avancados[0]
        val b = avancados[1]
        for (atr in Jogador.ATRIBUTOS) b.atributos[atr] = a.atributo(atr)
        a.forma = 0; a.moral = 0
        b.forma = 100; b.moral = 100
        assertEquals(b.id, e.onzeParaJogo().first { it.lugar == "PL" }.jogador!!.id)
        a.forma = 100; a.moral = 100
        b.forma = 0; b.moral = 0
        assertEquals(a.id, e.onzeParaJogo().first { it.lugar == "PL" }.jogador!!.id)
        // A ajuda dada depois de pedir minutos também conta.
        a.forma = 50; a.moral = 50; b.forma = 50; b.moral = 50
        for (atr in Jogador.ATRIBUTOS) a.atributos[atr] = b.atributo(atr) + 2
        e.invalidarForca()
        assertEquals(a.id, e.onzeParaJogo().first { it.lugar == "PL" }.jogador!!.id)
        e.ajudaNaEscolha[b.id] = 4.0
        assertEquals(b.id, e.onzeParaJogo().first { it.lugar == "PL" }.jogador!!.id)
    }

    @Test
    fun `minutos e mensagens do jogo`() {
        val c = jogador("MC", "POR_10", 5)
        estrela(c)
        repeat(6) { c.avancar() }
        val e = c.jogadorCarreira.estado
        assertTrue(e.minutosEpoca > 300, "minutos ${e.minutosEpoca}")
        assertEquals(e.minutosEpoca, e.minutosCarreira)
        assertTrue(c.mensagens.any { it.texto.contains("Jogaste") && it.texto.contains("minutos") })
        val epoca = assertNotNull(c.jogadorCarreira.epocaAtual())
        assertTrue(epoca.jogos >= 5 && epoca.nota > 0.0)
        assertEquals(epoca.jogos, c.jogadorCarreira.totais().jogos)
    }

    @Test
    fun `mudanca de estatuto gera mensagem`() {
        val c = jogador("MC", "POR_10", 5)
        val j = c.jogador!!
        for (a in Jogador.ATRIBUTOS) j.atributos[a] = 20
        c.jogadorCarreira.estado.estatuto = c.jogadorCarreira.calcularEstatuto(j)
        assertEquals(EstatutoNoClube.POUCO_UTILIZADO, c.jogadorCarreira.estado.estatuto)
        estrela(c)
        c.avancar()
        assertEquals(EstatutoNoClube.INDISCUTIVEL, c.jogadorCarreira.estado.estatuto)
        assertTrue(c.mensagens.any { it.titulo.startsWith("Estatuto no clube") })
    }

    @Test
    fun `pedidos do treinador cumprem-se e falham`() {
        val c = jogador("MC", "POR_10", 5)
        estrela(c)
        val e = c.jogadorCarreira.estado
        // Um pedido fácil: nota média acima de 1.0 em 3 jogos.
        e.pedido = PedidoTreinador(TipoPedido.NOTA_MEDIA, 1.0, 3, "Teste fácil.")
        repeat(3) { c.avancar() }
        assertNull(e.pedido, "devia estar resolvido")
        assertEquals(1, e.pedidosCumpridos)
        assertTrue(e.ajuda > 0 && e.ajudaJogos > 0)
        assertTrue(c.mensagens.any { it.titulo == "Pedido cumprido" })
        // Um pedido impossível: nota média acima de 11.
        e.pedido = PedidoTreinador(TipoPedido.NOTA_MEDIA, 11.0, 3, "Teste impossível.")
        repeat(3) { c.avancar() }
        assertNull(e.pedido)
        assertEquals(1, e.pedidosFalhados)
        assertTrue(e.ajuda < 0)
        assertTrue(c.mensagens.any { it.titulo == "Pedido falhado" })
        // Golos: progresso a contar o que ele marca.
        e.pedido = PedidoTreinador(TipoPedido.GOLOS, 1000.0, 2, "Teste de golos.")
        repeat(2) { c.avancar() }
        assertNull(e.pedido)
        assertEquals(2, e.pedidosFalhados)
        // O treinador também oferece pedidos sozinho, mais ou menos de seis em seis jogos.
        val d = jogador("PL", "POR_10", 9)
        estrela(d)
        repeat(26) { d.avancar() }
        val ofereceu = d.mensagens.count { it.titulo == "Pedido do treinador" }
        assertTrue(ofereceu >= 1, "sem pedidos do treinador em 26 jogos")
        // Há pedidos para cada posição.
        for (pos in Jogador.POSICOES) {
            val x = jogador(pos, "POR_10", 4)
            val p = x.jogadorCarreira.novoPedido(x.jogador!!)
            assertTrue(p.texto.isNotBlank() && x.jogadorCarreira.progressoDoPedido(p).isNotBlank())
        }
    }

    @Test
    fun `pedir mais minutos ao treinador`() {
        val c = jogador("MC", "POR_10", 5)
        val j = c.jogador!!
        val cj = c.jogadorCarreira
        // Sem justificação: recusado e o estado fica sem ajuda.
        for (a in Jogador.ATRIBUTOS) j.atributos[a] = 20
        j.forma = 30
        cj.estado.estatuto = cj.calcularEstatuto(j)
        val resposta = cj.pedirMinutos()
        assertTrue(resposta.contains("ainda não mostraste"), resposta)
        assertEquals(0.0, cj.estado.ajuda)
        // Logo a seguir, fica a aguardar.
        assertTrue(cj.pedirMinutos().contains("Espera"))
        // Com boa forma e perto do titular: aceite.
        val colegas = c.equipa.plantel.filter { it.posicao == "MC" && it.id != j.id }.map { it.geral() }.sortedDescending()
        val lugares = c.equipa.formacao.lugares.count { it == "MC" }.coerceAtLeast(1)
        for (a in Jogador.ATRIBUTOS) j.atributos[a] = colegas[lugares - 1] - 3
        cj.estado.estatuto = cj.calcularEstatuto(j)
        j.forma = 80
        cj.estado.jogosDesdeConversa = 10
        val ok = cj.pedirMinutos()
        assertTrue(ok.startsWith("O treinador aceitou"), ok)
        assertTrue(cj.estado.ajuda >= 3.5 && cj.estado.ajudaJogos == 6)
        assertEquals(3.5, c.equipa.ajudaNaEscolha[j.id])
        // Um titular não precisa de pedir.
        estrela(c)
        assertTrue(cj.pedirMinutos().contains("Já és titular"))
    }

    @Test
    fun `foco de treino do jogador acelera o atributo escolhido`() {
        val c = jogador("MC", "POR_10", 5)
        val j = c.jogador!!
        val equipa = c.equipa
        c.jogadorCarreira.definirFoco(FocoTreino.FISICO)
        assertEquals(FocoTreino.FISICO, c.gestor.focosIndividuais[j.id])
        c.jogadorCarreira.definirFoco(FocoTreino.EQUILIBRADO)
        assertNull(c.gestor.focosIndividuais[j.id])
        // Estatística: com foco no físico sobe mais do que sem foco.
        fun ganho(foco: FocoTreino?): Double {
            val rng = Random(1)
            var total = 0
            repeat(1500) {
                j.atributos["fisico"] = 50
                j.potencial = 99
                Treino.evoluir(j, equipa, rng, false, foco)
                total += j.atributo("fisico") - 50
            }
            return total / 1500.0
        }
        val sem = ganho(null)
        val com = ganho(FocoTreino.FISICO)
        val outro = ganho(FocoTreino.ATAQUE)
        assertTrue(com > sem * 1.15, "com foco $com, sem foco $sem")
        assertTrue(outro < sem, "foco noutro grupo $outro, sem foco $sem")
    }

    @Test
    fun `aparecem propostas na janela e aceitar muda de clube com contrato novo`() {
        val c = jogador("MC", "POR_10", 5)
        estrela(c, 80)
        val j = c.jogador!!
        val e = c.jogadorCarreira.estado
        val antigo = c.equipa
        // A carreira começa a 1 de julho (mercado aberto): ao avançar aparecem propostas (e a janela fica registada).
        c.avancar()
        assertEquals("2026-verao", e.janelaOfertas)
        if (e.ofertas.isEmpty()) c.jogadorCarreira.gerarOfertas()
        assertTrue(e.ofertas.isNotEmpty(), "sem propostas para um jogador de 80")
        for (o in e.ofertas) {
            assertTrue(o.clubeId != antigo.id && o.clubeId in c.bd.equipas)
            assertTrue(o.salario >= 12_000 && o.anos >= 1 && o.preco >= 0)
        }
        val oferta = e.ofertas.first { !it.emprestimo }
        val destino = c.bd.equipas.getValue(oferta.clubeId)
        val caixaDestino = destino.orcamento
        val caixaOrigem = antigo.orcamento
        val texto = c.jogadorCarreira.aceitarOferta(oferta.id)
        assertTrue(texto.startsWith("Assinaste pelo"), texto)
        assertEquals(destino.id, j.equipa)
        assertEquals(destino.id, c.info.equipaId)
        assertEquals(destino.id, c.gestor.clubeDoUtilizador)
        assertEquals(oferta.salario, j.salario)
        assertEquals(c.gestor.ano + oferta.anos, j.contratoAte)
        assertTrue(j in destino.plantel && j !in antigo.plantel)
        assertEquals(caixaDestino - oferta.preco, destino.orcamento)
        assertEquals(caixaOrigem + oferta.preco, antigo.orcamento)
        assertTrue(e.ofertas.isEmpty() && e.estatuto != null)
        assertTrue(c.mensagens.any { it.titulo == "Novo clube: ${destino.nome}" })
        // Continua a jogar no clube novo.
        val jogo = assertNotNull(c.avancar())
        assertEquals(destino.id, jogo.clubeId)
        // Fora da janela as propostas caducam: avança até setembro.
        c.jogadorCarreira.gerarOfertas()
        repeat(4) { c.avancar() }
        assertTrue(!c.mercado.aberto)
        assertTrue(e.ofertas.isEmpty(), "as propostas deviam ter caducado")
    }

    @Test
    fun `emprestimo volta ao clube de origem e pedir saida gera propostas`() {
        val c = jogador("MC", "POR_10", 8)
        estrela(c, 75)
        val j = c.jogador!!
        val e = c.jogadorCarreira.estado
        val origem = c.equipa.id
        val resposta = c.jogadorCarreira.pedirSaida("emprestimo")
        assertTrue(resposta.contains("empréstimo"), resposta)
        // Se ninguém quis emprestá-lo, usa-se uma proposta feita à mão para o resto do teste.
        if (e.ofertas.none { it.emprestimo }) {
            e.ofertas.clear()
            e.ofertas += pt.futebolcarreira.nucleo.carreira.OfertaJogador(50, "POR_20", "emprestimo", j.salario, 1, 0, EstatutoNoClube.TITULAR)
        }
        val oferta = e.ofertas.first { it.emprestimo }
        val texto = c.jogadorCarreira.aceitarOferta(oferta.id)
        assertTrue(texto.startsWith("Foste emprestado"), texto)
        assertEquals(origem, j.emprestadoPor)
        assertEquals(oferta.clubeId, c.info.equipaId)
        c.simularEpoca()
        c.novaEpoca()
        // No fim da época volta ao clube de origem e o clube do utilizador volta a ser esse.
        assertEquals("", j.emprestadoPor)
        assertEquals(origem, j.equipa)
        assertEquals(origem, c.info.equipaId)
        assertTrue(c.mensagens.any { it.titulo == "Fim do empréstimo" })
        assertNotNull(c.avancar())
    }

    @Test
    fun `renovacao de contrato proposta pelo clube`() {
        val c = jogador("MC", "POR_10", 5)
        estrela(c, 80)
        val j = c.jogador!!
        val e = c.jogadorCarreira.estado
        j.contratoAte = c.gestor.ano + 1
        j.salario = 50_000
        e.janelaOfertas = ""
        c.jogadorCarreira.aoMercado()
        val r = assertNotNull(e.renovacao, "devia haver proposta de renovação")
        assertTrue(r.salario > 50_000 && r.anos >= 1)
        assertTrue(c.mensagens.any { it.titulo == "Renovação de contrato" })
        val texto = c.jogadorCarreira.aceitarRenovacao()
        assertTrue(texto.startsWith("Renovaste"), texto)
        assertEquals(r.salario, j.salario)
        assertEquals(c.gestor.ano + 1 + r.anos, j.contratoAte)
        assertNull(e.renovacao)
        // Com muitos anos de contrato não há proposta.
        e.janelaOfertas = ""
        j.contratoAte = c.gestor.ano + 5
        c.jogadorCarreira.aoMercado()
        assertNull(e.renovacao)
    }

    @Test
    fun `premios individuais de cada liga e bola de ouro`() {
        val c = jogador("MC", "POR_10", 6)
        c.simularEpoca()
        val resumo = c.gestor.historico.last()
        // Uma entrada por liga, com todos os prémios.
        assertEquals(resumo.ligas.keys, resumo.premios.keys)
        val ouro = assertNotNull(resumo.bolaDeOuro)
        assertEquals(3, resumo.podio.size)
        assertEquals(ouro.jogadorId, resumo.podio.first().jogadorId)
        for ((ligaId, p) in resumo.premios) {
            val melhor = assertNotNull(p.melhorJogador, "$ligaId sem melhor jogador")
            val marcador = assertNotNull(p.melhorMarcador, "$ligaId sem melhor marcador")
            assertNotNull(p.melhorJovem, "$ligaId sem melhor jovem")
            assertTrue(p.melhorJovem!!.idade <= Premios.IDADE_JOVEM)
            // O melhor marcador é o que tem mais golos na liga (e bate com o resumo da liga).
            val liga = resumo.ligas.getValue(ligaId)
            assertEquals(liga.golosMarcador, marcador.golos)
            val maisGolos = c.bd.equipasDaLiga(ligaId).flatMap { it.plantel }.maxOf { it.epoca.golos }
            assertEquals(maisGolos, marcador.golos)
            // O melhor jogador tem a melhor nota entre quem jogou o suficiente.
            val total = c.gestor.estado.getValue(ligaId).totalJornadas
            val melhorNota = c.bd.equipasDaLiga(ligaId).flatMap { it.plantel }.filter { it.epoca.jogos >= maxOf(3, total / 2) }.maxOf { it.epoca.notaMedia() }
            assertEquals(melhorNota, melhor.nota, 1e-9)
            // Onze da época: 11 jogadores diferentes, um guarda-redes.
            assertEquals(11, p.onze.size, "$ligaId: onze")
            assertEquals(11, p.onze.map { it.jogadorId }.toSet().size)
            assertEquals(1, p.onze.count { it.posicao == "GR" })
        }
        // A mensagem dos prémios e a notícia da Bola de Ouro.
        assertTrue(c.mensagens.any { it.titulo.startsWith("Prémios individuais") })
        assertTrue(c.manchetes.any { it.titulo.contains("Bola de Ouro") })
        // Aparece no histórico gravado.
        val d = Gravador.deTexto(Gravador.paraTexto(c), Gravador.baseDadosParaTexto(c))
        val lido = d.gestor.historico.last()
        assertEquals(ouro.nome, lido.bolaDeOuro?.nome)
        assertEquals(resumo.premios.keys, lido.premios.keys)
        assertEquals(resumo.premios.getValue("POR1").onze.map { it.nome }, lido.premios.getValue("POR1").onze.map { it.nome })
    }

    @Test
    fun `o jogador do utilizador ganha premios e fica registado`() {
        val c = jogador("PL", "POR_10", 6)
        estrela(c)
        c.simularEpoca()
        val j = c.jogador!!
        val e = c.jogadorCarreira.estado
        val total = c.estadoLiga.totalJornadas
        // Números de campeão: nota altíssima e muitos golos (a época já foi fechada: refaz-se o fecho com estes números).
        j.epoca.jogos = total
        j.epoca.somaNotas = 9.5 * total
        j.epoca.golos = 60
        j.epoca.assistencias = 20
        val antigo = c.gestor.historico.last()
        val novos = Premios.calcular(c.bd, c.gestor.estado, antigo.ligas, antigo.europa)
        val liga = c.liga.id
        assertEquals(j.id, novos.ligas.getValue(liga).melhorJogador?.jogadorId)
        assertEquals(j.id, novos.ligas.getValue(liga).melhorMarcador?.jogadorId)
        assertEquals(j.id, novos.bolaDeOuro?.jogadorId, "devia ganhar a Bola de Ouro")
        assertTrue(novos.ligas.getValue(liga).onze.any { it.jogadorId == j.id })
        assertTrue(novos.ligas.getValue(liga).onze.first { it.jogadorId == j.id }.posicao == "PL")
        c.gestor.historico[c.gestor.historico.lastIndex] =
            ResumoEpoca(antigo.ano, antigo.ligas, antigo.tacas, antigo.supertacas, antigo.europa, novos.ligas, novos.bolaDeOuro, novos.podio)
        e.epocaFechada = 0
        e.temporadas.clear()
        e.premios.clear()
        c.geradorNoticias.marcas.remove("premios/${antigo.ano}")
        c.jogadorCarreira.fecharEpoca()
        c.jogadorCarreira.anunciarPremios(c.gestor.historico.last())
        c.geradorNoticias.publicar()
        val titulos = e.premios.map { it.titulo }
        assertTrue("Bola de Ouro" in titulos, "$titulos")
        assertTrue(titulos.any { it.startsWith("Melhor jogador") } && titulos.any { it.startsWith("Melhor marcador") })
        assertTrue(titulos.any { it.startsWith("Onze da época") })
        assertEquals(1, e.temporadas.size)
        assertTrue(e.temporadas.first().jogos >= total)
        assertTrue(c.mensagens.any { it.titulo.startsWith("Prémios individuais") && it.texto.contains(j.nome) })
        assertTrue(c.manchetes.any { it.titulo == "Rui Craque conquista a Bola de Ouro!" && it.doClube })
        assertTrue(c.mensagens.any { it.titulo.startsWith("Balanço da época") })
        // Não se conta duas vezes.
        c.jogadorCarreira.fecharEpoca()
        assertEquals(1, e.temporadas.size)
    }

    @Test
    fun `convocatoria para a selecao de um pais da base de dados e de outro`() {
        for (nacao in listOf("ING", "NED")) {
            val c = jogador("MC", "POR_10", 12, nacao)
            estrela(c)
            val j = c.jogador!!
            repeat(14) { c.avancar() }
            val s = assertNotNull(c.gestor.selecoes)
            assertTrue(s.internacionalizacoes(j.id) > 0, "$nacao: sem jogos pela seleção")
            assertTrue(c.mensagens.any { it.titulo == "Convocatória" && it.texto.contains("Foste convocado") }, "$nacao: sem mensagem de convocatória")
            assertTrue(c.mensagens.any { it.titulo == "A tua seleção" }, "$nacao: sem resumo dos jogos pela seleção")
        }
        // Um jogador fraco não é convocado.
        val fraco = jogador("MC", "POR_10", 12, "ING")
        repeat(14) { fraco.avancar() }
        assertEquals(0, fraco.gestor.selecoes!!.internacionalizacoes(fraco.jogador!!.id))
    }

    @Test
    fun `fim de carreira passa a treinador com propostas`() {
        // Reforma por idade: aos 37 anos (38 no verão) a reforma é certa.
        val c = jogador("MC", "POR_10", 3)
        estrela(c, 70)
        val j = c.jogador!!
        j.idade = 37
        c.simularEpoca()
        val clubeFinal = c.info.equipaId
        c.novaEpoca()
        assertEquals(ModoCarreira.TREINADOR, c.info.modo)
        assertNull(c.info.jogadorId)
        assertNull(c.jogador)
        assertTrue(c.jogadorCarreira.estado.reformado)
        assertEquals(clubeFinal, c.info.equipaId)
        val m = c.mensagens.first { it.titulo == "Fim da carreira de jogador" }
        assertTrue(m.texto.contains("pendurou as botas") && m.texto.contains("jogos") && m.texto.contains("treinador"), m.texto)
        assertTrue(c.info.propostas.isNotEmpty(), "devia haver propostas de outros clubes")
        // Aceitar uma proposta muda o clube do treinador; o jogo continua.
        c.aceitarProposta(c.info.propostas.first())
        assertNotNull(c.avancar())
        // Reforma antecipada por nível muito baixo.
        val d = jogador("MC", "POR_10", 4)
        val k = d.jogador!!
        k.idade = 34
        for (a in Jogador.ATRIBUTOS) k.atributos[a] = 30
        d.simularEpoca()
        d.novaEpoca()
        assertEquals(ModoCarreira.TREINADOR, d.info.modo)
        assertTrue(d.mensagens.any { it.titulo == "Fim da carreira de jogador" })
    }

    @Test
    fun `carreira de jogador grava e carrega todo o estado novo`() {
        val c = jogador("PL", "POR_10", 6)
        estrela(c, 85)
        val j = c.jogador!!
        val e = c.jogadorCarreira.estado
        c.jogadorCarreira.definirFoco(FocoTreino.ATAQUE)
        e.pedido = PedidoTreinador(TipoPedido.GOLOS, 4.0, 8, "Marca 4 golos.")
        repeat(8) { c.avancar() }
        if (e.ofertas.isEmpty()) c.jogadorCarreira.gerarOfertas()
        c.simularEpoca()
        e.renovacao = pt.futebolcarreira.nucleo.carreira.OfertaRenovacao(120_000, 3)
        e.ofertas.clear()
        e.ofertas += pt.futebolcarreira.nucleo.carreira.OfertaJogador(99, "POR_03", "transferencia", 200_000, 4, 5_000_000, EstatutoNoClube.TITULAR)
        e.pedidoSaida = "transferencia"
        val texto = Gravador.paraTexto(c)
        val d = Gravador.deTexto(texto, Gravador.baseDadosParaTexto(c))
        val f = d.jogadorCarreira.estado
        assertEquals(FocoTreino.ATAQUE, f.foco)
        assertEquals(e.estatuto, f.estatuto)
        assertEquals(e.minutosCarreira, f.minutosCarreira)
        assertEquals(e.temporadas.size, f.temporadas.size)
        assertEquals(e.temporadas.map { "${it.epoca} ${it.clubeId} ${it.jogos} ${it.golos} ${it.minutos}" }, f.temporadas.map { "${it.epoca} ${it.clubeId} ${it.jogos} ${it.golos} ${it.minutos}" })
        assertEquals(e.trofeus.map { it.titulo }, f.trofeus.map { it.titulo })
        assertEquals(e.premios.map { it.titulo }, f.premios.map { it.titulo })
        assertEquals(e.ofertas.map { "${it.id} ${it.clubeId} ${it.salario} ${it.anos} ${it.preco} ${it.estatuto}" }, f.ofertas.map { "${it.id} ${it.clubeId} ${it.salario} ${it.anos} ${it.preco} ${it.estatuto}" })
        assertEquals(e.renovacao?.salario, f.renovacao?.salario)
        assertEquals("transferencia", f.pedidoSaida)
        assertEquals(e.janelaOfertas, f.janelaOfertas)
        assertEquals(e.ultimasNotas, f.ultimasNotas)
        assertEquals(e.pedidosCumpridos + e.pedidosFalhados, f.pedidosCumpridos + f.pedidosFalhados)
        assertEquals(e.atributosVistos, f.atributosVistos)
        assertEquals(j.nacionalidade, d.jogador!!.nacionalidade)
        assertEquals(c.gestor.historico.last().bolaDeOuro?.nome, d.gestor.historico.last().bolaDeOuro?.nome)
        assertEquals(c.gestor.historico.last().premios.size, d.gestor.historico.last().premios.size)
        // O foco individual volta a valer ao carregar.
        assertEquals(FocoTreino.ATAQUE, d.gestor.focosIndividuais[d.jogador!!.id])
        // E o jogo continua na época seguinte.
        d.novaEpoca()
        assertNotNull(d.avancar())
    }

    @Test
    fun `gravacao antiga de jogador sem estado novo carrega e continua`() {
        val c = jogador("MC", "POR_10", 6)
        estrela(c, 80)
        repeat(10) { c.avancar() }
        val estado = Json.parseToJsonElement(Gravador.paraTexto(c)).jsonObject
        val info = estado.getValue("info").jsonObject
        assertTrue("estadoJogador" in info)
        val infoAntigo = JsonObject(info.filterKeys { it != "estadoJogador" })
        val antigo = JsonObject(estado.filterKeys { it != "info" } + ("info" to infoAntigo)).toString()
        val d = Gravador.deTexto(antigo, Gravador.baseDadosParaTexto(c))
        assertEquals(ModoCarreira.JOGADOR, d.info.modo)
        assertTrue(d.jogadorCarreira.estado.temporadas.isEmpty() && d.jogadorCarreira.estado.minutosEpoca == 0)
        repeat(8) { assertNotNull(d.avancar()) }
        assertTrue(d.jogadorCarreira.estado.minutosEpoca > 0)
        d.simularEpoca()
        // Também carrega um histórico de antes dos prémios.
        val fim = Json.parseToJsonElement(Gravador.paraTexto(d)).jsonObject
        val historico = JsonArray(fim.getValue("historico").jsonArray.map { h ->
            JsonObject(h.jsonObject.filterKeys { it != "premios" && it != "bolaDeOuro" && it != "podio" })
        })
        val semPremios = JsonObject(fim.filterKeys { it != "historico" } + ("historico" to historico)).toString()
        val e = Gravador.deTexto(semPremios, Gravador.baseDadosParaTexto(d))
        assertTrue(e.gestor.historico.isNotEmpty() && e.gestor.historico.all { it.premios.isEmpty() && it.bolaDeOuro == null })
        e.novaEpoca()
        e.simularEpoca()
        assertTrue(e.gestor.historico.last().premios.isNotEmpty())
    }

    @Test
    fun `uma carreira de treinador nao tem nada de jogador`() {
        val c = Carreira.nova(carregar(), "t", "Mister", ModoCarreira.TREINADOR, "POR_02", semente = 2)
        assertTrue(!c.jogadorCarreira.ativo)
        c.simularEpoca()
        if (c.info.despedido) {
            c.aceitarProposta(c.info.propostas.first())
            c.simularEpoca()
        }
        assertTrue(c.gestor.epocaTerminada())
        assertTrue(c.jogadorCarreira.estado.temporadas.isEmpty())
        // Mas os prémios individuais da época aparecem na mesma.
        assertTrue(c.mensagens.any { it.titulo.startsWith("Prémios individuais") })
        assertNotNull(c.gestor.historico.last().bolaDeOuro)
    }
}

/** Pequeno atalho para os testes (a lista de nacionalidades é do núcleo). */
private object CarreiraJogadorAjuda {
    fun nacionalidades(bd: BaseDados) = pt.futebolcarreira.nucleo.carreira.CarreiraJogador.nacionalidadesPossiveis(bd)
}
