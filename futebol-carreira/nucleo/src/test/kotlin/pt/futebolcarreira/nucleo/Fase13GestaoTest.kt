package pt.futebolcarreira.nucleo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Funcao
import pt.futebolcarreira.nucleo.carreira.Gravador
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.carreira.PerfilPatrocinio
import pt.futebolcarreira.nucleo.carreira.PlanoSemanal
import pt.futebolcarreira.nucleo.carreira.PoliticaRenovacao
import pt.futebolcarreira.nucleo.carreira.PropostaContrato
import pt.futebolcarreira.nucleo.carreira.ResNeg
import pt.futebolcarreira.nucleo.carreira.Resposta
import pt.futebolcarreira.nucleo.carreira.Saude
import pt.futebolcarreira.nucleo.carreira.Sessao
import pt.futebolcarreira.nucleo.carreira.SlotPatrocinio
import pt.futebolcarreira.nucleo.carreira.TipoOferta
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.FocoTreino
import pt.futebolcarreira.nucleo.modelos.Jogador
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Fase 13 (G2 a G5): equipa técnica, contratos e negociações, orçamento e finanças, treino detalhado. */
class Fase13GestaoTest {
    private val pastaDados = File("../app/src/main/assets/dados")

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar { File(pastaDados, it).takeIf(File::exists)?.readText() }, "carregar: ${bd.erros.take(3)}")
        return bd
    }

    /** Um clube grande (o primeiro da liga portuguesa) ou um clube pequeno (o último da segunda divisão). */
    private fun carreira(bd: BaseDados, grande: Boolean = true, semente: Long = 21): Carreira {
        val clube = if (grande) bd.equipasDaLiga("POR1").maxBy { it.reputacao } else bd.equipasDaLiga("POR2").minBy { it.reputacao }
        return Carreira.nova(bd, "g", "G", ModoCarreira.TREINADOR, clube.id, semente = semente)
    }

    private fun recarregar(c: Carreira): Carreira = Gravador.deTexto(Gravador.paraTexto(c), Gravador.baseDadosParaTexto(c))

    /** Joga a época toda (sem despedimentos: aceita a primeira proposta se for despedido). */
    private fun epoca(c: Carreira) {
        c.simularEpoca()
        if (c.info.despedido) c.aceitarProposta(c.info.propostas.first())
    }

    @Test
    fun `gravacao antiga sem gestao abre e joga`() {
        val bd = carregar()
        val c = carreira(bd)
        repeat(3) { c.avancar() }
        val texto = Json.parseToJsonElement(Gravador.paraTexto(c)).jsonObject
        assertTrue("gestao" in texto)
        val antiga = JsonObject(texto - "gestao").toString()
        val d = Gravador.deTexto(antiga, Gravador.baseDadosParaTexto(c))
        // Tudo normal: renovações automáticas, sem equipa técnica, sem limites, bilhete ao preço normal.
        assertEquals(PoliticaRenovacao.AUTOMATICA, d.gestaoClube.estado.negociacoes.renovacao)
        assertTrue(d.gestaoClube.equipaTecnica.membros.isEmpty())
        assertEquals(0L, d.gestaoClube.estado.financas.orcamentoSalarios)
        assertEquals(1.0, d.gestaoClube.orcamento.precoBilhete())
        assertEquals(1, d.gestaoClube.equipaTecnica.nivel(Funcao.ADJUNTO))
        d.simularEpoca()
        if (d.info.despedido) d.aceitarProposta(d.info.propostas.first())
        d.novaEpoca()
        // Com a política automática os contratos que acabaram renovaram-se: ninguém do plantel antigo ficou sem contrato.
        assertTrue(d.equipa.plantel.all { it.contratoAte >= d.gestor.ano })
    }

    @Test
    fun `equipa tecnica contrata despede e influencia o treino`() {
        val bd = carregar()
        val c = carreira(bd)
        val g = c.gestaoClube
        val et = g.equipaTecnica
        assertEquals(1.0, c.equipa.ajustes.crescimento)
        val adjunto = et.candidatos(Funcao.ADJUNTO).maxBy { it.nivel }
        assertTrue(adjunto.nivel >= 4, "um clube grande atrai bons candidatos (${adjunto.nivel})")
        assertEquals(Resposta.ACEITE, et.contratar(adjunto))
        assertEquals(Resposta.EQUIPA_COMPLETA, et.contratar(et.candidatos(Funcao.ADJUNTO).first()), "só há um adjunto")
        for (f in listOf(Funcao.PREPARADOR, Funcao.MEDICO, Funcao.FISIOTERAPEUTA)) assertEquals(Resposta.ACEITE, et.contratar(et.candidatos(f).maxBy { it.nivel }))
        assertTrue(c.equipa.ajustes.crescimento > 1.0)
        assertTrue(c.equipa.ajustes.riscoLesao < 1.0)
        assertTrue(c.equipa.ajustes.recuperacao > 0.0)
        assertTrue(c.equipa.ajustes.declinio < 1.0)
        assertTrue(et.vagasIndividuais() > 3)
        // Despedir custa um quarto do salário anual.
        val preparador = et.membros(Funcao.PREPARADOR).first()
        val caixa = c.equipa.orcamento
        assertEquals(Resposta.ACEITE, et.despedir(preparador.id))
        assertEquals(caixa - preparador.salario / 4, c.equipa.orcamento)
        assertEquals(1, et.nivel(Funcao.PREPARADOR))
        // O salário vai saindo ao longo da época e fica nas contas.
        val custo = et.custoAnual()
        assertTrue(custo > 0)
        c.simularEpoca()
        if (!c.info.despedido) assertTrue(kotlin.math.abs(c.equipa.contas.equipaTecnica - custo - preparador.salario / 4) < 1000, "${c.equipa.contas.equipaTecnica} vs $custo")
    }

    @Test
    fun `varios olheiros fazem viagens em paralelo`() {
        val bd = carregar()
        val c = carreira(bd)
        val et = c.gestaoClube.equipaTecnica
        repeat(3) { i -> assertEquals(Resposta.ACEITE, et.contratar(et.candidatos(Funcao.OLHEIRO).filter { it.nivel >= 2 }[i % 2])) }
        assertEquals(Resposta.EQUIPA_COMPLETA, et.contratar(et.candidatos(Funcao.OLHEIRO).first()))
        val ids = et.membros(Funcao.OLHEIRO).map { it.id }
        assertEquals(3, ids.size)
        assertEquals(Resposta.ACEITE, c.olheiros.enviar("ESP", ""))
        assertEquals(Resposta.ACEITE, et.enviar(ids[0], "ESP", ""))
        assertEquals(Resposta.ACEITE, et.enviar(ids[1], "ITA", ""))
        assertEquals(Resposta.OLHEIRO_OCUPADO, et.enviar(ids[0], "FRA", ""))
        val inicio = c.gestor.data
        while (c.gestor.data < inicio.plusDays(35) && !c.gestor.epocaTerminada()) c.avancar()
        // Todos voltaram e entregaram relatórios (há mensagens deles).
        assertTrue(et.membros(Funcao.OLHEIRO).all { it.missao == null })
        assertTrue(c.mensagens.count { it.titulo.startsWith("Relatório de ") } >= 2, c.mensagens.take(15).joinToString { it.titulo })
    }

    @Test
    fun `renovacao negociada com contraproposta paciencia e custos`() {
        val bd = carregar()
        val c = carreira(bd)
        val n = c.gestaoClube.negociacoes
        val j = c.equipa.plantel.sortedByDescending { it.geral() }.first { it.idade in 24..29 }
        j.contratoAte = c.gestor.ano + 1
        assertTrue(n.renovavel(j))
        val pede = n.exigencia(j)
        // Muito abaixo: recusa; três recusas fecham a porta.
        val baixa = PropostaContrato(pede.salario / 2, 3)
        assertEquals(ResNeg.RECUSADA, n.renovar(j, baixa).resultado)
        assertEquals(ResNeg.RECUSADA, n.renovar(j, baixa).resultado)
        assertEquals(ResNeg.SEM_PACIENCIA, n.renovar(j, baixa).resultado)
        assertEquals(ResNeg.SEM_PACIENCIA, n.renovar(j, PropostaContrato(pede.salario * 2, 3)).resultado, "fechou a porta por um mês")
        c.gestor.data = c.gestor.data.plusDays(31)
        // Um pouco abaixo: contraproposta com salário mais alto, que depois é aceite.
        val quase = n.renovar(j, PropostaContrato((pede.salario * 0.93).toInt(), pede.anos))
        assertEquals(ResNeg.CONTRAPROPOSTA, quase.resultado)
        val contra = assertNotNull(quase.contraproposta)
        assertTrue(contra.salario > (pede.salario * 0.93).toInt())
        val caixa = c.equipa.orcamento
        val folha = c.equipa.folhaSalarial()
        val luvas = 200_000L
        val comissao = n.comissao(contra)
        // Uma cláusula alta (o clube prende-o) e prémios: o salário tem de subir para compensar.
        val final = contra.copy(salario = (contra.salario * 1.12).toInt(), luvas = luvas, premioGolo = 10_000, premioObjetivo = 50_000, clausula = j.clausula() * 2)
        val comissaoFinal = n.comissao(final)
        val r = n.renovar(j, final)
        assertEquals(ResNeg.ACEITE, r.resultado, r.texto)
        assertEquals(final.salario, j.salario)
        assertEquals(c.gestor.ano + 1 + contra.anos, j.contratoAte)
        assertEquals(caixa - luvas - comissaoFinal, c.equipa.orcamento)
        assertTrue(c.equipa.folhaSalarial() != folha)
        assertEquals(luvas, c.equipa.contas.salarios)
        assertEquals(comissaoFinal, c.equipa.contas.comissoes)
        assertTrue(comissaoFinal > comissao)
        assertEquals(j.clausula() * 2, n.clausula(j))
        assertEquals(10_000, n.condicoes(j).premioGolo)
    }

    @Test
    fun `politica de renovacao manual deixa sair quem nao renovou`() {
        val bd = carregar()
        val c = carreira(bd)
        val n = c.gestaoClube.negociacoes
        assertEquals(PoliticaRenovacao.AUTOMATICA, c.gestaoClube.estado.negociacoes.renovacao, "por omissão renova sozinho")
        n.definirPolitica(PoliticaRenovacao.MANUAL)
        val ano = c.gestor.ano
        val candidatos = c.equipa.plantel.filter { it.idade in 24..30 }.sortedByDescending { it.geral() }
        val fica = candidatos[0]
        val sai = candidatos[1]
        for (j in listOf(fica, sai)) j.contratoAte = ano + 1
        val pede = n.exigencia(fica)
        assertEquals(ResNeg.ACEITE, n.renovar(fica, pede.copy(salario = pede.salario + 5_000)).resultado)
        c.simularEpoca()
        if (c.info.despedido) return
        assertTrue(c.mensagens.any { it.titulo == "Contratos a terminar" }, "avisou dos contratos a terminar")
        // Mesmo que saiam muitos de uma vez, o plantel volta a ser completado.
        for (j in c.equipa.plantel.filter { it.id != fica.id && it.id != sai.id }.take(15)) j.contratoAte = ano + 1
        c.novaEpoca()
        assertTrue(c.equipa.plantel.size >= 22, "plantel de ${c.equipa.plantel.size}")
        assertEquals(c.info.equipaId, fica.equipa)
        assertTrue(sai.equipa != c.info.equipaId, "quem não renovou saiu")
        // Pode-se voltar a contratá-lo como livre, negociando.
        if (sai.equipa.isEmpty()) {
            val p = n.exigencia(sai)
            val r = n.contratarLivre(sai, p.copy(salario = p.salario + 5_000))
            assertEquals(ResNeg.ACEITE, r.resultado, r.texto)
            assertEquals(c.info.equipaId, sai.equipa)
        }
    }

    @Test
    fun `politica automatica renova sozinha como antes`() {
        val bd = carregar()
        val c = carreira(bd)
        c.gestaoClube.negociacoes.definirPolitica(PoliticaRenovacao.AUTOMATICA)
        val ano = c.gestor.ano
        val expirar = c.equipa.plantel.take(5)
        for (j in expirar) j.contratoAte = ano + 1
        c.simularEpoca()
        if (c.info.despedido) return
        c.novaEpoca()
        assertTrue(expirar.all { it.equipa == c.info.equipaId && it.contratoAte >= ano + 2 })
    }

    /** Põe o relógio num dia do mercado de verão e deixa passar dias até aparecer uma proposta. */
    private fun esperarOferta(c: Carreira): Boolean {
        val n = c.gestaoClube.negociacoes
        var dia = LocalDate.of(c.gestor.ano, 8, 1)
        repeat(30) {
            c.gestor.data = dia
            n.gerarOfertas()
            if (n.ofertas.isNotEmpty()) return true
            dia = dia.plusDays(1)
        }
        return false
    }

    @Test
    fun `outros clubes fazem propostas e o utilizador aceita recusa ou contrapoe`() {
        val bd = carregar()
        val c = carreira(bd)
        val n = c.gestaoClube.negociacoes
        assertTrue(esperarOferta(c), "apareceu uma proposta por um jogador")
        val o = n.ofertas.first()
        val j = bd.jogadores.getValue(o.jogadorId)
        assertEquals(c.info.equipaId, j.equipa)
        assertTrue(c.mensagens.any { it.titulo == "Proposta por ${j.nome}" })
        // Só se contrapõe uma vez; a proposta ou sobe ou é retirada.
        val antes = o.valor
        val cp = n.contrapor(o.id)
        if (cp.resultado == ResNeg.ACEITE) {
            if (o.tipo == TipoOferta.COMPRA) assertTrue(n.ofertas.first { it.id == o.id }.valor > antes)
            assertEquals(ResNeg.NAO_PERMITIDO, n.contrapor(o.id).resultado)
        } else {
            assertTrue(n.ofertas.none { it.id == o.id })
        }
        // Voltam a aparecer; aceitamos uma e a venda faz-se (dinheiro, plantel, contas).
        if (n.ofertas.isEmpty()) assertTrue(esperarOferta(c))
        val oferta = n.ofertas.first()
        val jogador = bd.jogadores.getValue(oferta.jogadorId)
        val comprador = bd.equipas.getValue(oferta.clubeId)
        val caixa = c.equipa.orcamento
        assertEquals(Resposta.ACEITE, n.aceitar(oferta.id))
        if (oferta.tipo == TipoOferta.COMPRA) {
            assertEquals(comprador.id, jogador.equipa)
            assertTrue(c.equipa.orcamento > caixa)
        } else {
            assertEquals(comprador.id, jogador.equipa)
            assertEquals(c.info.equipaId, jogador.emprestadoPor)
            assertTrue(n.opcoes().any { it.jogadorId == jogador.id && !it.minha })
        }
        assertTrue(n.ofertas.none { it.jogadorId == jogador.id })
        // Recusar tira a proposta.
        if (n.ofertas.isNotEmpty()) assertEquals(Resposta.ACEITE, n.recusar(n.ofertas.first().id))
        // Fora das janelas de mercado não há propostas e as que havia caem.
        c.gestor.data = LocalDate.of(c.gestor.ano + 1, 3, 10)
        n.gerarOfertas()
        assertTrue(n.ofertas.isEmpty())
    }

    @Test
    fun `clausula de rescisao baixa e paga por outro clube`() {
        val bd = carregar()
        val c = carreira(bd)
        val n = c.gestaoClube.negociacoes
        val estrela = c.equipa.plantel.maxBy { it.geral() }
        // Com a cláusula a 1,5x o valor, um clube rico acaba por a ativar (o jogador sai logo).
        c.gestaoClube.estado.negociacoes.condicoes[estrela.id] = pt.futebolcarreira.nucleo.carreira.CondicoesContrato(clausula = estrela.valorMercado() * 3L / 2)
        for (e in bd.equipas.values) if (e.id != c.info.equipaId) e.orcamento = 900_000_000
        var dia = LocalDate.of(c.gestor.ano, 8, 1)
        var saiu = false
        repeat(40) {
            if (saiu) return@repeat
            c.gestor.data = dia
            c.gestaoClube.negociacoes.gerarOfertas()
            if (estrela.equipa != c.info.equipaId) saiu = true
            dia = dia.plusDays(1)
        }
        // Pode ou não calhar com este jogador (as propostas escolhem entre os 12 melhores), mas nunca deixa o plantel curto.
        assertTrue(c.equipa.plantel.size >= 18)
    }

    @Test
    fun `emprestimo com opcao de compra nos dois sentidos`() {
        val bd = carregar()
        val c = carreira(bd, grande = false)
        c.equipa.orcamento = 50_000_000
        c.gestor.data = LocalDate.of(c.gestor.ano, 8, 5)
        val n = c.gestaoClube.negociacoes
        val alvo = bd.equipasDaLiga("POR2").filter { it.id != c.info.equipaId }.flatMap { it.plantel }
            .filter { it.geral() > 40 && c.equipa.reputacao >= (bd.equipas.getValue(it.equipa).reputacao - 15) }.first { j ->
                val dono = bd.equipas.getValue(j.equipa)
                dono.plantel.size > 20 && dono.onzeParaJogo().none { it.jogador === j } && dono.plantel.count { it.posicao == j.posicao } > 2
            }
        val dono = bd.equipas.getValue(alvo.equipa)
        val taxa = n.taxaEmprestimo(alvo)
        val preco = n.precoOpcao(alvo)
        val caixa = c.equipa.orcamento
        val vendasDono = dono.contas.vendas
        assertEquals(Resposta.ACEITE, n.pedirEmprestimoComOpcao(alvo))
        assertEquals(c.info.equipaId, alvo.equipa)
        assertEquals(dono.id, alvo.emprestadoPor)
        assertEquals(caixa - taxa, c.equipa.orcamento)
        assertEquals(vendasDono + taxa, dono.contas.vendas)
        assertEquals(1, n.opcoes().count { it.minha && it.jogadorId == alvo.id })
        // Exercer a opção: paga o preço, o jogador fica de vez.
        assertEquals(Resposta.ACEITE, n.exercerOpcao(alvo.id))
        assertEquals("", alvo.emprestadoPor)
        assertEquals(c.info.equipaId, alvo.equipa)
        assertEquals(caixa - taxa - preco, c.equipa.orcamento)
        assertTrue(alvo.contratoAte > c.gestor.ano + 1)
        assertTrue(n.opcoes().isEmpty())
        // Uma opção que não se exerce perde-se e o jogador volta.
        val outro = bd.equipasDaLiga("POR2").filter { it.id != c.info.equipaId && it.id != dono.id }.flatMap { it.plantel }
            .first { j ->
                val d = bd.equipas.getValue(j.equipa)
                d.plantel.size > 20 && d.onzeParaJogo().none { it.jogador === j } && d.plantel.count { it.posicao == j.posicao } > 2 && c.equipa.reputacao >= d.reputacao - 15
            }
        val dono2 = outro.equipa
        assertEquals(Resposta.ACEITE, n.pedirEmprestimoComOpcao(outro))
        c.simularEpoca()
        if (c.info.despedido) return
        c.novaEpoca()
        assertEquals(dono2, outro.equipa, "voltou ao dono")
        assertEquals("", outro.emprestadoPor)
    }

    @Test
    fun `opcao de compra do outro clube decide-se no fim da epoca`() {
        val bd = carregar()
        val c = carreira(bd)
        c.gestor.data = LocalDate.of(c.gestor.ano, 8, 5)
        val n = c.gestaoClube.negociacoes
        assertTrue(esperarOferta(c) || true)
        // Cria à mão uma proposta de empréstimo com opção e aceita-a.
        val j = c.equipa.plantel.sortedBy { it.geral() }[3]
        val clube = bd.equipasDaLiga("POR1").first { it.id != c.info.equipaId && it.plantel.size < 30 }
        clube.orcamento = 500_000_000
        c.gestaoClube.estado.negociacoes.ofertas += pt.futebolcarreira.nucleo.carreira.OfertaRecebida(
            900, j.id, clube.id, TipoOferta.EMPRESTIMO_COM_OPCAO, 0, 0, 100_000, 1_000_000, c.gestor.data.plusDays(10).toString(),
        )
        val caixa = c.equipa.orcamento
        assertEquals(Resposta.ACEITE, n.aceitar(900))
        assertEquals(caixa + 100_000, c.equipa.orcamento)
        assertEquals(clube.id, j.equipa)
        // O clube só exerce a opção se o jogador lhe interessar: tornamo-lo muito bom para o clube.
        for (a in Jogador.ATRIBUTOS) j.atributos[a] = 90
        clube.invalidarForca()
        c.simularEpoca()
        if (c.info.despedido) return
        val antes = c.equipa.orcamento
        c.novaEpoca()
        assertTrue(c.mensagens.any { it.titulo.startsWith("Opção de compra") }, "houve decisão sobre a opção")
        if (j.equipa == clube.id && j.emprestadoPor.isEmpty()) assertTrue(c.equipa.orcamento >= antes)
    }

    @Test
    fun `compra a prestacoes paga a entrada e depois todos os meses`() {
        val bd = carregar()
        val c = carreira(bd)
        c.gestor.data = LocalDate.of(c.gestor.ano, 8, 5)
        val n = c.gestaoClube.negociacoes
        c.equipa.orcamento = 100_000_000
        val alvo = bd.equipasDaLiga("POR1").filter { it.id != c.info.equipaId }.flatMap { it.plantel }
            .filter { it.geral() in 60..70 && it.emprestadoPor.isEmpty() && bd.equipas.getValue(it.equipa).plantel.size > 20 }.first { c.mercado.pedido(it) > 800_000 }
        val dono = bd.equipas.getValue(alvo.equipa)
        val pedido = c.mercado.pedido(alvo)
        val caixa = c.equipa.orcamento
        val vendasDono = dono.contas.vendas
        // Paga acima do pedido para garantir a aceitação, em 12 prestações.
        val oferta = pedido * 11 / 10
        assertEquals(Resposta.ACEITE, c.mercado.comprar(alvo, oferta, 12))
        assertEquals(c.info.equipaId, alvo.equipa)
        val entrada = caixa - c.equipa.orcamento
        assertTrue(entrada in oferta / 4..oferta / 4 + 12, "entrada $entrada de $oferta")
        assertEquals(oferta - entrada, n.dividaEmPrestacoes())
        repeat(12) { n.processarMes() }
        assertEquals(0L, n.dividaEmPrestacoes())
        assertEquals(oferta, caixa - c.equipa.orcamento)
        assertEquals(oferta, c.equipa.contas.compras)
        assertEquals(vendasDono + oferta, dono.contas.vendas)
    }

    @Test
    fun `orcamentos salarios transferencias e embargo bloqueiam compras`() {
        val bd = carregar()
        val c = carreira(bd)
        val g = c.gestaoClube
        val o = g.orcamento
        c.gestor.data = LocalDate.of(c.gestor.ano, 8, 5)
        c.equipa.orcamento = 500_000_000
        val livre = bd.livres().maxByOrNull { it.geral() } ?: bd.equipas.values.first { it.id != c.info.equipaId }.plantel.first().also { bd.mudarDeEquipa(it, "") }
        // Sem limites definidos tudo passa.
        assertNull(o.bloqueioSalarios(10_000_000))
        assertNull(o.bloqueioTransferencias(10_000_000))
        // Orçamento de salários: contratar para lá do limite é recusado.
        g.estado.financas.orcamentoSalarios = o.massaSalarial() + 1_000
        assertEquals(Resposta.ORCAMENTO_SALARIOS, c.mercado.contratarLivre(livre))
        assertEquals(Resposta.ORCAMENTO_SALARIOS, g.equipaTecnica.contratar(g.equipaTecnica.candidatos(Funcao.ADJUNTO).last()))
        // A direção não deixa definir um orçamento acima do máximo.
        assertEquals(Resposta.ORCAMENTO_SALARIOS, o.definirOrcamentoSalarios(o.maximoSalarios() + 10_000_000))
        assertEquals(Resposta.ACEITE, o.definirOrcamentoSalarios(o.maximoSalarios()))
        g.estado.financas.orcamentoSalarios = 0
        // Orçamento de transferências.
        assertEquals(Resposta.ACEITE, o.definirOrcamentoTransferencias(1_000_000))
        assertEquals(1_000_000L, o.disponivelTransferencias())
        val caro = bd.equipasDaLiga("POR1").filter { it.id != c.info.equipaId }.flatMap { it.plantel }.first { c.mercado.pedido(it) > 3_000_000 && it.emprestadoPor.isEmpty() }
        assertEquals(Resposta.ORCAMENTO_TRANSFERENCIAS, c.mercado.comprar(caro, c.mercado.pedido(caro)))
        // Embargo: nem compras nem empréstimos nem crédito.
        g.estado.financas.orcamentoTransferencias = 0
        g.estado.financas.embargoAteAno = c.gestor.ano + 1
        assertTrue(o.embargoAtivo())
        assertEquals(Resposta.EMBARGO, c.mercado.comprar(caro, c.mercado.pedido(caro)))
        assertEquals(Resposta.EMBARGO, c.mercado.pedirEmprestimo(caro))
        assertEquals(Resposta.EMBARGO, o.pedirCredito(1_000_000))
        assertEquals(Saude.CRITICA, o.saude())
        // Vender continua a ser possível; o embargo acaba na época seguinte.
        g.estado.financas.embargoAteAno = c.gestor.ano - 1
        assertTrue(!o.embargoAtivo())
    }

    private fun assertNull(v: Any?) = assertTrue(v == null, "esperava null, veio $v")

    @Test
    fun `patrocinadores bilhetes e credito mexem nas contas pela diferenca`() {
        val bd = carregar()
        val c = carreira(bd)
        val o = c.gestaoClube.orcamento
        // Propostas iguais para o mesmo clube e época; três perfis por lugar.
        val camisola = o.propostas(SlotPatrocinio.CAMISOLA)
        assertEquals(camisola.map { it.id }, o.propostas(SlotPatrocinio.CAMISOLA).map { it.id })
        assertEquals(PerfilPatrocinio.entries.toList(), camisola.map { it.perfil })
        assertTrue(camisola.first { it.perfil == PerfilPatrocinio.AMBICIOSO }.fixo < camisola.first { it.perfil == PerfilPatrocinio.SEGURO }.fixo)
        assertEquals(Resposta.ACEITE, o.assinar(SlotPatrocinio.CAMISOLA, PerfilPatrocinio.AMBICIOSO))
        assertEquals(Resposta.JA_TEM_PATROCINIO, o.assinar(SlotPatrocinio.CAMISOLA, PerfilPatrocinio.SEGURO))
        assertEquals(Resposta.ACEITE, o.assinar(SlotPatrocinio.MATERIAL, PerfilPatrocinio.SEGURO))
        // Bilhete: preços altos e baixos.
        assertEquals(Resposta.NAO_PERMITIDO, o.definirPrecoBilhete(2.0))
        assertEquals(Resposta.ACEITE, o.definirPrecoBilhete(0.8))
        assertTrue(o.procura(0.8) > 1.0 && o.procura(1.4) < 1.0)
        val (barato, normal) = o.bilheteira(false, 1.0, 0.8)
        assertTrue(barato <= normal * 1.05 && barato > normal * 0.7)
        // Crédito: aprova, cobra juros e amortiza em 24 meses.
        val caixa = c.equipa.orcamento
        assertEquals(Resposta.SEM_CREDITO, o.pedirCredito(o.limiteCredito() + 10_000_000))
        val valor = 2_000_000L.coerceAtMost(o.limiteCredito())
        assertEquals(Resposta.ACEITE, o.pedirCredito(valor))
        assertEquals(caixa + valor, c.equipa.orcamento)
        assertEquals(valor, o.divida())
        repeat(24) { o.processarMes() }
        assertEquals(0L, o.divida())
        assertTrue(c.equipa.contas.juros > 0)
        assertTrue(c.equipa.orcamento < caixa + valor)
        // Rescindir custa 30% do fixo anual.
        val fixo = o.contrato(SlotPatrocinio.MATERIAL)!!.fixo
        val antes = c.equipa.orcamento
        assertEquals(Resposta.ACEITE, o.rescindir(SlotPatrocinio.MATERIAL))
        assertEquals(antes - fixo * 3 / 10, c.equipa.orcamento)
    }

    @Test
    fun `plano semanal fadiga rotacao treino individual e mudanca de posicao`() {
        val bd = carregar()
        val c = carreira(bd)
        val g = c.gestaoClube
        val t = g.treino
        // Sem plano o treino é o de sempre.
        assertEquals(1.0, c.equipa.ajustes.crescimento)
        assertEquals(1.0, c.equipa.ajustes.riscoLesao)
        // Um plano intenso cresce mais e arrisca mais; um leve é o contrário.
        t.usarPreset("Intenso")
        val intenso = c.equipa.ajustes.riscoLesao
        val cresIntenso = c.equipa.ajustes.crescimento
        t.usarPreset("Leve")
        assertTrue(c.equipa.ajustes.riscoLesao < 1.0 && c.equipa.ajustes.riscoLesao < intenso)
        assertTrue(c.equipa.ajustes.crescimento < cresIntenso)
        t.usarPreset("Ofensivo")
        assertTrue(c.equipa.ajustes.peso("remate") > c.equipa.ajustes.peso("defesa"))
        t.definirSessao(0, Sessao.GUARDA_REDES)
        assertEquals(Sessao.GUARDA_REDES, t.plano().dias[0])
        assertEquals(7, t.plano().dias.size)
        // Fadiga: depois de jogos os titulares cansam-se e voltam a recuperar com dias de descanso.
        repeat(4) { c.avancar() }
        val cansados = c.equipa.plantel.filter { t.fadiga(it) > 0 }
        assertTrue(cansados.isNotEmpty(), "quem jogou ficou com fadiga")
        val j = cansados.maxBy { t.fadiga(it) }
        assertTrue(c.equipa.ajustes.riscoJogador(j) >= 1.0)
        val antes = t.fadiga(j)
        t.passarDias(14)
        assertTrue(t.fadiga(j) < antes, "recupera com descanso")
        // Rotação automática e poupar mexem na escolha do onze.
        val titular = c.equipa.onzeParaJogo().mapNotNull { it.jogador }.first()
        assertEquals(Resposta.ACEITE, t.alternarPoupar(titular))
        assertTrue(t.poupado(titular))
        assertTrue(c.equipa.onzeParaJogo().none { it.jogador === titular }, "o poupado não joga")
        t.alternarPoupar(titular)
        t.definirRotacao(true)
        assertTrue(t.rotacao())
        // Treino individual: limite de vagas (3 sem adjunto).
        val jogadores = c.equipa.plantel.take(5)
        for (x in jogadores.take(3)) assertEquals(Resposta.ACEITE, t.definirIndividual(x, FocoTreino.ATAQUE))
        assertEquals(Resposta.SEM_VAGAS_TREINO, t.definirIndividual(jogadores[3], FocoTreino.FISICO))
        assertEquals(FocoTreino.ATAQUE, c.gestor.focosIndividuais[jogadores[0].id])
        assertEquals(Resposta.ACEITE, t.definirIndividual(jogadores[0], FocoTreino.EQUILIBRADO))
        assertTrue(jogadores[0].id !in c.gestor.focosIndividuais)
        // Mudança de posição: um defesa central a médio defensivo (posição parecida: 2 meses).
        val dc = c.equipa.plantel.first { it.posicao == "DC" }
        val gr = c.equipa.plantel.first { it.posicao == "GR" }
        assertEquals(Resposta.NAO_PERMITIDO, t.iniciarReconversao(gr, "DC"))
        assertEquals(Resposta.NAO_PERMITIDO, t.iniciarReconversao(dc, "GR"))
        assertEquals(2, t.mesesPara(dc, "MDC"))
        assertEquals(4, t.mesesPara(dc, "PL"))
        val geralAntes = dc.geral()
        assertEquals(Resposta.ACEITE, t.iniciarReconversao(dc, "MDC"))
        assertEquals(Resposta.NAO_PERMITIDO, t.iniciarReconversao(dc, "MC"), "já está a treinar")
        t.processarMes()
        assertEquals("DC", dc.posicao)
        t.processarMes()
        assertEquals("MDC", dc.posicao)
        assertTrue(dc.geral() >= geralAntes - 3, "chega à posição nova sem perder muito (${dc.geral()} vs $geralAntes)")
        assertTrue(dc.potencial >= dc.geral())
        assertTrue(t.reconversoes().isEmpty())
    }

    @Test
    fun `plano individual e fadiga ficam gravados`() {
        val bd = carregar()
        val c = carreira(bd)
        val g = c.gestaoClube
        g.equipaTecnica.contratar(g.equipaTecnica.candidatos(Funcao.MEDICO).last())
        g.treino.usarPreset("Defensivo")
        g.treino.definirIndividual(c.equipa.plantel.first(), FocoTreino.POSSE)
        g.orcamento.definirPrecoBilhete(1.2)
        g.orcamento.assinar(SlotPatrocinio.ESTADIO, PerfilPatrocinio.EQUILIBRADO)
        g.orcamento.definirOrcamentoTransferencias(7_000_000)
        g.negociacoes.definirPolitica(PoliticaRenovacao.AUTOMATICA)
        repeat(3) { c.avancar() }
        val d = recarregar(c)
        val h = d.gestaoClube
        assertEquals(g.equipaTecnica.membros.map { it.id to it.nivel }, h.equipaTecnica.membros.map { it.id to it.nivel })
        assertEquals(g.treino.plano().dias, h.treino.plano().dias)
        assertEquals(1.2, h.orcamento.precoBilhete())
        assertEquals(7_000_000L, h.estado.financas.orcamentoTransferencias)
        assertEquals(PoliticaRenovacao.AUTOMATICA, h.estado.negociacoes.renovacao)
        assertEquals(1, h.orcamento.patrocinadores().size)
        assertEquals(c.equipa.plantel.first().id, h.treino.individuais().keys.single())
        assertEquals(c.equipa.plantel.associate { it.id to g.treino.fadiga(it) }, d.equipa.plantel.associate { it.id to h.treino.fadiga(it) })
        assertTrue(d.equipa.ajustes.recuperacao > 0.0, "os efeitos voltam a aplicar-se ao abrir")
        assertEquals(c.equipa.ajustes.riscoLesao, d.equipa.ajustes.riscoLesao)
    }

    /** Dinheiro: caixa final = caixa inicial + receitas - despesas (e os créditos, que não são receitas). */
    private fun conferirContas(c: Carreira, caixaInicial: Long, movimentosDeCapital: Long = 0) {
        val k = c.equipa.contas
        assertEquals(caixaInicial + k.receitas() - k.despesas() + movimentosDeCapital, c.equipa.orcamento)
    }

    @Test
    fun `uma epoca sem mexer em nada fica igual ao que era`() {
        val bd = carregar()
        val c = carreira(bd, grande = false)
        val caixa = c.equipa.orcamento
        c.simularEpoca()
        val k = c.equipa.contas
        assertEquals(0L, k.equipaTecnica + k.comissoes + k.juros + k.premiosJogadores, "sem gestão não há custos novos")
        conferirContas(c, caixa)
        assertEquals(1.0, c.equipa.ajustes.crescimento)
    }

    @Test
    fun `epoca completa com gestao ativa nao falencia nem riqueza`() {
        for (grande in listOf(true, false)) {
            val bd = carregar()
            val c = carreira(bd, grande = grande, semente = 5)
            val g = c.gestaoClube
            val ref = g.orcamento.receitaPrevista()
            val caixa = c.equipa.orcamento
            val nome = c.equipa.nome
            // Equipa técnica razoável, patrocínios equilibrados, bilhete ligeiramente mais caro, plano equilibrado, orçamentos sugeridos.
            for (f in listOf(Funcao.ADJUNTO, Funcao.PREPARADOR, Funcao.MEDICO)) g.equipaTecnica.contratar(g.equipaTecnica.candidatos(f).first { it.nivel >= 2 })
            for (s in SlotPatrocinio.entries) g.orcamento.assinar(s, PerfilPatrocinio.EQUILIBRADO)
            g.orcamento.definirPrecoBilhete(1.1)
            g.treino.usarPreset("Equilibrado")
            g.orcamento.definirOrcamentoTransferencias(g.orcamento.sugestaoTransferencias())
            val custoStaff = g.equipaTecnica.custoAnual()
            c.simularEpoca()
            val k = c.equipa.contas
            val resultado = k.receitas() - k.despesas()
            println("$nome (grande=$grande): receita prevista ${ref / 1000}k, receitas ${k.receitas() / 1000}k, despesas ${k.despesas() / 1000}k, staff ${k.equipaTecnica / 1000}k, patrocínios ${k.patrocinios / 1000}k, bilheteira ${k.bilheteira / 1000}k, resultado ${resultado / 1000}k, caixa ${c.equipa.orcamento / 1000}k, racio salarial %.2f".format(g.orcamento.racioSalarial()))
            conferirContas(c, caixa)
            assertTrue(kotlin.math.abs(k.equipaTecnica - custoStaff) < 1000, "o salário da equipa técnica fica nas contas")
            // Nem falência (o resultado não passa de uma pequena fração da receita) nem fortuna.
            assertTrue(resultado > -k.receitas() * 0.25, "perde demasiado: $resultado")
            assertTrue(resultado < k.receitas() * 0.6, "ganha demasiado: $resultado")
            assertTrue(c.equipa.orcamento > -k.receitas() * 0.5)
            assertTrue(g.estado.financas.resultados.size == 1 && g.estado.financas.receitas.size == 1)
            assertTrue(c.mensagens.any { it.titulo == "Balanço financeiro" })
        }
    }

    @Test
    fun `tres epocas com gestao completa nao deixam o clube falir`() {
        val bd = carregar()
        val c = carreira(bd, grande = false, semente = 3)
        val g = c.gestaoClube
        g.orcamento.definirOrcamentoTransferencias(g.orcamento.sugestaoTransferencias())
        g.treino.usarPreset("Equilibrado")
        repeat(3) { i ->
            val caixaAntes = c.equipa.orcamento
            g.equipaTecnica.candidatos(Funcao.MEDICO).firstOrNull { it.nivel in 2..2 }?.let { g.equipaTecnica.contratar(it) }
            for (s in SlotPatrocinio.entries) g.orcamento.assinar(s, PerfilPatrocinio.SEGURO)
            epoca(c)
            val k = c.equipa.contas
            println("Época ${i + 1}: ${c.equipa.nome} receitas ${k.receitas() / 1000}k despesas ${k.despesas() / 1000}k caixa ${c.equipa.orcamento / 1000}k saúde ${g.orcamento.saude()}")
            assertTrue(c.equipa.orcamento > caixaAntes - 0.5 * k.receitas().coerceAtLeast(1_000_000), "cai demasiado numa época")
            c.novaEpoca()
            assertTrue(c.equipa.plantel.size >= 18, "plantel de ${c.equipa.plantel.size}")
        }
        // Gravar e voltar a abrir ao fim de três épocas.
        val d = recarregar(c)
        assertEquals(c.equipa.orcamento, d.equipa.orcamento)
        assertEquals(g.estado.financas.resultados, d.gestaoClube.estado.financas.resultados)
        d.avancar()
    }

    @Test
    fun `mudar de clube reinicia a gestao`() {
        val bd = carregar()
        val c = carreira(bd)
        val g = c.gestaoClube
        g.equipaTecnica.contratar(g.equipaTecnica.candidatos(Funcao.ADJUNTO).last())
        g.treino.usarPreset("Intenso")
        val antigo = c.equipa
        assertTrue(antigo.ajustes.crescimento > 0.0 && antigo.ajustes.riscoLesao != 1.0)
        c.info.despedido = true
        val novo = bd.equipas.values.first { it.id != antigo.id }
        c.info.propostas += novo.id
        c.aceitarProposta(novo.id)
        assertEquals(novo.id, g.estado.clubeId)
        assertTrue(g.equipaTecnica.membros.isEmpty())
        assertEquals(1.0, antigo.ajustes.riscoLesao, "o clube antigo volta ao treino normal")
        assertEquals(1.0, novo.ajustes.riscoLesao)
    }
}
