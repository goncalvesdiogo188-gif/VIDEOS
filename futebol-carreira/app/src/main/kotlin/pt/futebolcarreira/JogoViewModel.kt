package pt.futebolcarreira

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Instalacao
import pt.futebolcarreira.nucleo.carreira.JogoDoClube
import pt.futebolcarreira.nucleo.carreira.Mercado
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.carreira.Resposta
import pt.futebolcarreira.nucleo.simulacao.Financas
import pt.futebolcarreira.nucleo.carreira.ResumoGravacao
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.FocoTreino
import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.Intensidade
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.modelos.Mentalidade
import pt.futebolcarreira.nucleo.simulacao.CalendarioDatas
import pt.futebolcarreira.nucleo.simulacao.FichaJogador
import pt.futebolcarreira.nucleo.simulacao.LadoRelatorio
import pt.futebolcarreira.nucleo.simulacao.Relato
import pt.futebolcarreira.nucleo.simulacao.Resultado
import pt.futebolcarreira.nucleo.simulacao.TipoEvento

/**
 * Liga os ecrãs ao núcleo do jogo. Tudo o que demora (carregar, simular, gravar)
 * corre em segundo plano para a aplicação não "congelar"; no fim, os ecrãs
 * recebem uma cópia nova dos dados para mostrar.
 */
class JogoViewModel(app: Application) : AndroidViewModel(app) {
    private val armazem = Armazem(app.filesDir)

    /** A carreira aberta (null = estamos nos menus). */
    private var carreira: Carreira? = null

    /** Liga à vista no separador «Liga» ("" = a do clube). */
    private var ligaId = ""

    /** Jornada escolhida nos resultados; null = a última jogada. */
    private var jornadaEscolhida: Int? = null

    /** Há alterações (por exemplo, de tática) que ainda não foram gravadas. */
    private var porGravar = false

    var ecra by mutableStateOf(Ecra.MENU)
        private set

    /** true enquanto se carrega, simula ou grava (os botões ficam desligados). */
    var ocupado by mutableStateOf(false)
        private set

    /** Carreiras gravadas (para o menu). */
    var gravacoes by mutableStateOf<List<ResumoGravacao>>(emptyList())
        private set

    /** Países, ligas e clubes para escolher numa carreira nova. */
    var paises by mutableStateOf<List<PaisUi>>(emptyList())
        private set

    /** O que os ecrãs da carreira mostram (null = nenhuma carreira aberta). */
    var ui by mutableStateOf<CarreiraUi?>(null)
        private set

    /** Taças, competições europeias e seleções (vazio até o núcleo as fornecer). */
    var fase7 by mutableStateOf(Fase7Ui())
        private set

    /** Competição escolhida no separador «Liga» ("" = uma liga). */
    private var competicaoId = ""

    /** Só para as capturas de ecrã: mostra dados de demonstração da fase 7. */
    private var demonstracao = false

    /** Plantel de outro clube aberto numa janela (null = nenhum). */
    var plantel by mutableStateOf<PlantelUi?>(null)
        private set

    /** Janela para escolher um titular (null = fechada). */
    var escolha by mutableStateOf<EscolhaUi?>(null)
        private set

    /** Ficha de jogador aberta (null = fechada). */
    var ficha by mutableStateOf<FichaJogadorUi?>(null)
        private set

    private var filtroPosicao: String? = null
    private var filtroMercado = FiltroMercado.TODOS

    /** Jogo aberto (null = nenhum). */
    var jogo by mutableStateOf<JogoUi?>(null)
        private set

    /** Mensagem a mostrar ao utilizador (erros, avisos); null = nenhuma. */
    var aviso by mutableStateOf<String?>(null)
        private set

    init {
        executar({ armazem.listar() }) { gravacoes = it }
    }

    fun fecharAviso() {
        aviso = null
    }

    // ------------------------------------------------------------------ menus

    fun abrirMenu() {
        if (!ocupado) ecra = Ecra.MENU
    }

    fun abrirCarregar() {
        if (!ocupado) ecra = Ecra.CARREGAR
    }

    /** Abre o ecrã de nova carreira (e carrega a lista de clubes, da primeira vez). */
    fun abrirNovaCarreira() {
        if (ocupado) return
        ecra = Ecra.NOVA_CARREIRA
        if (paises.isNotEmpty()) return
        executar({
            val bd = lerBaseDados()
            bd.paises.values.map { pais ->
                PaisUi(pais.id, pais.nome, bd.ligasDoPais(pais.id).map { liga ->
                    LigaOpcaoUi(liga.id, liga.nome, bd.equipasDaLiga(liga.id).sortedByDescending { it.reputacao }.map { e ->
                        ClubeUi(e.id, e.nome, e.cidade, emblema(e), ((e.reputacao - 28) / 12).coerceIn(1, 5), e.forca().geral.toInt())
                    })
                })
            }
        }) { paises = it }
    }

    /** Lê a base de dados que vem com a aplicação (pasta assets/dados). */
    private fun lerBaseDados(): BaseDados {
        val assets = getApplication<Application>().assets
        val bd = BaseDados()
        bd.carregar { caminho ->
            try {
                assets.open("dados/$caminho").bufferedReader().use { it.readText() }
            } catch (e: java.io.IOException) {
                null  // ficheiro não existe
            }
        }
        check(bd.equipas.isNotEmpty()) { "Base de dados vazia. ${bd.erros.take(3).joinToString(" ")}" }
        return bd
    }

    /** Cria uma carreira nova, grava-a e abre-a. */
    fun comecarCarreira(modo: ModoCarreira, nome: String, equipaId: String, posicao: String) = executar({
        val c = Carreira.nova(lerBaseDados(), "c${System.currentTimeMillis()}", nome, modo, equipaId, posicao)
        armazem.gravar(c)
        c to criarUi(c, ligaVista = "")
    }) { (c, novo) -> abrir(c, novo) }

    fun carregarCarreira(id: String) = executar({
        val c = armazem.carregar(id)
        c to criarUi(c, ligaVista = "")
    }) { (c, novo) -> abrir(c, novo) }

    /** Abre a carreira gravada mais recentemente. */
    fun continuar() {
        gravacoes.firstOrNull()?.let { carregarCarreira(it.id) }
    }

    fun apagarCarreira(id: String) = executar({
        armazem.apagar(id)
        armazem.listar()
    }) { gravacoes = it }

    private fun abrir(c: Carreira, novo: CarreiraUi) {
        carreira = c
        ligaId = ""
        jornadaEscolhida = null
        porGravar = false
        plantel = null
        jogo = null
        ficha = null
        filtroPosicao = null
        filtroMercado = FiltroMercado.TODOS
        ui = novo
        competicaoId = ""
        fase7 = criarFase7(c)
        ecra = Ecra.CARREIRA
    }

    /** Grava e volta ao menu principal. */
    fun sairParaMenu() = executar({
        carreira?.let { armazem.gravar(it) }
        armazem.listar()
    }) {
        gravacoes = it
        carreira = null
        ui = null
        plantel = null
        jogo = null
        ecra = Ecra.MENU
    }

    /** Chamado quando a aplicação vai para segundo plano: grava o que estiver por gravar. */
    fun aoSairDaApp() {
        val c = carreira ?: return
        if (ocupado || !porGravar) return
        try {
            armazem.gravar(c)
            porGravar = false
        } catch (e: Exception) {
            aviso = "Não foi possível gravar: ${e.message}"
        }
    }

    // --------------------------------------------------------------- carreira

    /** Joga a próxima jornada do clube e abre o jogo em direto. */
    fun avancar() = naCarreira { c ->
        c.avancar()?.resultado?.let { criarJogo(c, it, emDireto = true) }
    }

    fun novaEpoca() = naCarreira { c ->
        c.novaEpoca()
        null
    }

    /** Simula todos os jogos que faltam nesta época. */
    fun simularEpoca() = naCarreira { c ->
        c.simularEpoca()
        null
    }

    /** Corre uma ação na carreira em segundo plano, grava e atualiza os ecrãs. */
    private fun naCarreira(depois: () -> Unit = {}, acao: (Carreira) -> JogoUi?) {
        val c = carreira ?: return
        jornadaEscolhida = null
        executar({
            val jogoParaAbrir = acao(c)
            armazem.gravar(c)
            criarUi(c, ligaId) to jogoParaAbrir
        }) { (novo, jogoParaAbrir) ->
            porGravar = false
            ui = novo
            fase7 = criarFase7(c)
            if (jogoParaAbrir != null) jogo = jogoParaAbrir
            depois()
        }
    }

    /** Volta a criar a cópia dos dados para os ecrãs (depois de uma mudança rápida, sem simulação). */
    private fun atualizar() {
        val c = carreira ?: return
        ui = criarUi(c, ligaId)
        fase7 = criarFase7(c)
        if (demonstracao) ui = ui?.let { demoCarreira(it) }
    }

    fun escolherLiga(id: String) {
        if (ocupado) return
        ligaId = id
        competicaoId = ""
        jornadaEscolhida = null
        atualizar()
    }

    /** Escolhe uma taça, supertaça ou competição europeia no separador «Liga». */
    fun escolherCompeticao(id: String) {
        if (ocupado) return
        competicaoId = id
        atualizar()
    }

    /**
     * Abre o relato de um jogo identificado por uma chave dada por [LigacaoFase7]. Só os jogos do clube do utilizador
     * têm relato gravado; para os outros mostra-se o aviso habitual.
     */
    fun abrirJogoPorChave(chave: String) {
        val c = carreira ?: return
        if (ocupado || demonstracao) return
        val resultado = LigacaoFase7.resolverChave(c, chave)
        if (resultado == null) aviso = "O relato deste jogo não ficou gravado. Só se guardam os relatos dos jogos do teu clube." else abrirJogo(c, resultado)
    }

    /** Só para capturas de ecrã: enche a fase 7 com dados inventados. */
    internal fun mostrarDemonstracaoFase7() {
        demonstracao = true
        competicaoId = "taca-pt"
        atualizar()
    }

    /** Taças, supertaças e Europa vêm do núcleo ([LigacaoFase7]); as seleções ficam vazias até existirem. */
    private fun criarFase7(c: Carreira): Fase7Ui {
        if (demonstracao) return demoFase7(competicaoId)
        return LigacaoFase7.criar(c, competicaoId, ::emblema)
    }

    /** Anda para a jornada anterior (-1) ou seguinte (+1) nos resultados. */
    fun mudarJornada(passo: Int) {
        if (ocupado) return
        val liga = ui?.liga ?: return
        if (liga.jornada == 0) return
        jornadaEscolhida = (liga.jornadaVista + passo).coerceIn(1, liga.jornada).takeIf { it < liga.jornada }
        atualizar()
    }

    /** Muda a formação do clube do utilizador (null = o adjunto decide). Conta a partir do jogo seguinte. */
    fun mudarFormacao(formacao: Formacao?) {
        val c = carreira ?: return
        if (ocupado || c.info.modo != ModoCarreira.TREINADOR) return
        c.equipa.formacaoAutomatica = formacao == null
        if (formacao == null) c.equipa.escolherFormacao() else c.equipa.formacao = formacao
        porGravar = true
        atualizar()
    }

    /** Muda a mentalidade do clube do utilizador (null = decide-se jogo a jogo). */
    fun mudarMentalidade(mentalidade: Mentalidade?) {
        val c = carreira ?: return
        if (ocupado || c.info.modo != ModoCarreira.TREINADOR) return
        c.equipa.mentalidade = mentalidade
        porGravar = true
        atualizar()
    }

    /** Abre a lista de jogadores que podem ocupar um lugar do onze. */
    fun abrirEscolha(indice: Int) {
        val c = carreira ?: return
        if (ocupado || c.info.modo != ModoCarreira.TREINADOR) return
        val equipa = c.equipa
        val onze = equipa.onzeParaJogo()
        val lugar = onze.getOrNull(indice)?.lugar ?: return
        escolha = EscolhaUi(indice, lugar, equipa.plantel
            .sortedByDescending { Equipa.notaNoLugar(it, lugar) }
            .map { j ->
                val titular = onze.firstOrNull { it.jogador === j }
                CandidatoUi(
                    j.id, j.posicao, j.nome, Equipa.notaNoLugar(j, lugar),
                    aviso = estado(j).ifEmpty { if (titular != null) "No onze (${titular.lugar})" else "" },
                    apto = j.disponivel,
                )
            })
    }

    fun fecharEscolha() {
        escolha = null
    }

    /** Põe o jogador escolhido no lugar que estava aberto. */
    fun escolherTitular(jogadorId: Int) {
        val c = carreira ?: return
        val aberta = escolha ?: return
        escolha = null
        val jogador = c.bd.jogadores[jogadorId]?.takeIf { it.disponivel && it.equipa == c.info.equipaId } ?: return
        c.equipa.definirTitular(aberta.indice, jogador)
        porGravar = true
        atualizar()
    }

    /** Volta a deixar o adjunto escolher o onze. */
    fun onzeAutomatico() {
        val c = carreira ?: return
        if (ocupado) return
        c.equipa.onzeManual = null
        porGravar = true
        atualizar()
    }

    /** Aceita a proposta de um clube (muda de clube). */
    fun aceitarProposta(equipaId: String) = naCarreira { c ->
        c.aceitarProposta(equipaId)
        ligaId = ""
        null
    }

    fun recusarPropostas() = naCarreira { c ->
        c.recusarPropostas()
        null
    }

    // ------------------------------------------------------------- mercado

    fun filtrarMercado(posicao: String?, filtro: FiltroMercado) {
        if (ocupado) return
        filtroPosicao = posicao
        filtroMercado = filtro
        atualizar()
    }

    /** Abre a ficha de um jogador (de qualquer clube). */
    fun abrirJogador(id: Int) {
        val c = carreira ?: return
        if (ocupado) return
        plantel = null
        c.bd.jogadores[id]?.let { ficha = criarFicha(c, it) }
    }

    fun fecharJogador() {
        ficha = null
    }

    /** Faz um negócio com o jogador da ficha aberta e mostra a resposta. */
    fun negociar(acao: AcaoMercado) {
        val c = carreira ?: return
        val id = ficha?.id ?: return
        val j = c.bd.jogadores[id] ?: return
        if (ocupado) return
        var resposta = Resposta.NAO_PERMITIDO
        // Quando o trabalho acabar, mostra a resposta e atualiza (ou fecha) a ficha.
        val depois = {
            aviso = resposta.frase
            ficha = if (resposta == Resposta.ACEITE && acao in listOf(AcaoMercado.VENDER, AcaoMercado.EMPRESTAR, AcaoMercado.DISPENSAR)) null
            else c.bd.jogadores[id]?.let { criarFicha(c, it) }
        }
        naCarreira(depois) {
            val m = c.mercado
            resposta = when (acao) {
                AcaoMercado.PAGAR_PEDIDO -> m.comprar(j, m.pedido(j))
                AcaoMercado.OFERECER_90 -> m.comprar(j, m.pedido(j) * 9 / 10)
                AcaoMercado.OFERECER_80 -> m.comprar(j, m.pedido(j) * 8 / 10)
                AcaoMercado.PEDIR_EMPRESTIMO -> m.pedirEmprestimo(j)
                AcaoMercado.CONTRATAR_LIVRE -> m.contratarLivre(j)
                AcaoMercado.VENDER -> m.vender(j)
                AcaoMercado.EMPRESTAR -> m.emprestar(j)
                AcaoMercado.PROMOVER -> c.academia.promover(j)
                AcaoMercado.DISPENSAR -> c.academia.dispensar(j)
            }
            null
        }
    }

    fun ampliarEstadio() {
        val c = carreira ?: return
        var resposta = Resposta.NAO_PERMITIDO
        naCarreira({ if (resposta != Resposta.ACEITE) aviso = resposta.frase }) {
            resposta = c.mercado.ampliarEstadio()
            null
        }
    }

    private fun criarFicha(c: Carreira, j: Jogador): FichaJogadorUi {
        val m = c.mercado
        val dono = c.bd.equipas[j.equipa]
        val meu = j.equipa == c.info.equipaId
        val treinador = c.info.modo == ModoCarreira.TREINADOR && !c.info.despedido
        val emprestado = j.emprestadoPor.isNotEmpty()
        val botoes = mutableListOf<BotaoMercadoUi>()
        var nota = ""
        when {
            !treinador -> nota = if (c.info.modo == ModoCarreira.JOGADOR) "Na via de jogador não tratas de transferências." else ""
            dono == null -> botoes += BotaoMercadoUi(AcaoMercado.CONTRATAR_LIVRE, "Contratar (sem custo de transferência)")
            j.academia && meu -> {
                nota = "Está na academia: treina, mas ainda não joga. Aos 20 anos sobe ao plantel (se houver lugar) ou sai."
                botoes += BotaoMercadoUi(AcaoMercado.PROMOVER, "Subir ao plantel principal")
                botoes += BotaoMercadoUi(AcaoMercado.DISPENSAR, "Dispensar da academia")
            }
            j.academia -> nota = "Joga na academia de ${dono.nome}."
            emprestado -> nota = "Está emprestado por ${c.bd.equipas[j.emprestadoPor]?.nome} até ao fim da época."
            !m.aberto -> nota = "O mercado está fechado. Abre em julho, agosto e janeiro."
            meu -> {
                botoes += BotaoMercadoUi(AcaoMercado.VENDER, "Pôr à venda (cerca de ${Mercado.euros(j.valorMercado().toLong())})")
                botoes += BotaoMercadoUi(AcaoMercado.EMPRESTAR, "Emprestar até ao fim da época")
            }
            j.id in m.recusados -> nota = "O clube já recusou uma proposta tua e não negoceia mais nesta janela."
            else -> {
                val pedido = m.pedido(j)
                botoes += BotaoMercadoUi(AcaoMercado.PAGAR_PEDIDO, "Pagar o que pedem: ${Mercado.euros(pedido)}")
                botoes += BotaoMercadoUi(AcaoMercado.OFERECER_90, "Oferecer ${Mercado.euros(pedido * 9 / 10)} (podem recusar)")
                botoes += BotaoMercadoUi(AcaoMercado.OFERECER_80, "Oferecer ${Mercado.euros(pedido * 8 / 10)} (arriscado)")
                botoes += BotaoMercadoUi(AcaoMercado.PEDIR_EMPRESTIMO, "Pedir emprestado até ao fim da época")
            }
        }
        val e = j.epoca
        return FichaJogadorUi(
            j.id, j.nome,
            "${j.posicao} · ${j.idade} anos · pé ${j.pe} · ${c.bd.paises[j.nacionalidade]?.nome ?: j.nacionalidade}",
            dono?.nome ?: "Sem clube", dono?.let { emblema(it) },
            j.geral(), c.olheiros.potencial(j),
            listOf("Ritmo" to "ritmo", "Remate" to "remate", "Passe" to "passe", "Drible" to "drible", "Defesa" to "defesa", "Físico" to "fisico", "Guarda-redes" to "guarda_redes")
                .map { (nome, chave) -> nome to j.atributo(chave) },
            listOfNotNull(
                if (!c.olheiros.conhece(j)) "Potencial" to "Estimado. Manda um olheiro ao país dele para saberes ao certo." else null,
                if (meu && !j.academia) "Forma e moral" to formaMoral(j) else null,
                "Valor de mercado" to Mercado.euros(j.valorMercado().toLong()),
                "Cláusula de rescisão" to Mercado.euros(j.clausula()),
                "Salário por ano" to Mercado.euros(j.salario.toLong()),
                if (dono != null) "Contrato até" to "${j.contratoAte}" else null,
                "Esta época" to "${e.jogos} jogos, ${e.golos} golos, ${e.assistencias} assist." + (if (e.jogos > 0) ", nota %.1f".format(e.notaMedia()) else ""),
                estado(j).takeIf { it.isNotEmpty() }?.let { "Estado" to it },
            ),
            botoes, nota,
        )
    }

    private fun criarMercado(c: Carreira): MercadoUi {
        val caixa = c.equipa.orcamento
        val candidatos = c.bd.jogadores.values.asSequence()
            .filter { it.equipa != c.info.equipaId && it.emprestadoPor.isEmpty() }
            .filter { filtroPosicao == null || it.posicao == filtroPosicao }
            .filter {
                when (filtroMercado) {
                    FiltroMercado.TODOS -> true
                    FiltroMercado.LIVRES -> it.equipa.isEmpty()
                    FiltroMercado.POSSO_PAGAR -> it.equipa.isEmpty() || it.valorMercado() * 1.2 <= caixa
                }
            }
            .sortedByDescending { it.geral() }
            .take(60).toList()
        return MercadoUi(
            c.mercado.aberto, Mercado.euros(caixa), filtroPosicao, filtroMercado,
            candidatos.map { j ->
                val dono = c.bd.equipas[j.equipa]
                LinhaMercadoUi(j.id, j.posicao, j.nome, dono?.abreviatura ?: "livre", j.idade, j.geral(), if (dono == null) "livre" else Mercado.euros(c.mercado.pedido(j)))
            },
        )
    }

    private fun criarFinancas(c: Carreira): FinancasUi {
        val e = c.equipa
        val k = e.contas
        val m = c.mercado
        return FinancasUi(
            caixa = Mercado.euros(e.orcamento), caixaNegativa = e.orcamento < 0,
            folhaSalarial = Mercado.euros(e.folhaSalarial()),
            receitas = listOf("Bilheteira" to k.bilheteira, "Patrocínios e televisão" to k.patrocinios, "Prémios" to k.premios, "Vendas de jogadores" to k.vendas)
                .map { it.first to Mercado.euros(it.second) },
            despesas = listOf("Salários" to k.salarios, "Compras de jogadores" to k.compras, "Obras no estádio" to k.obras)
                .map { it.first to Mercado.euros(it.second) },
            saldo = Mercado.euros(k.receitas() - k.despesas()),
            estadio = "${e.estadio}: ${e.capacidade} lugares · cerca de ${Financas.assistencia(e)} espectadores e ${Mercado.euros(Financas.bilheteira(e))} por jogo",
            obras = "Aumentar ${m.lugaresNovos()} lugares por ${Mercado.euros(m.custoAmpliacao())}",
            podeAmpliar = c.info.modo == ModoCarreira.TREINADOR && !c.info.despedido && e.orcamento >= m.custoAmpliacao(),
        )
    }

    /** Abre a janela com o plantel de um clube qualquer. */
    fun abrirPlantel(equipaId: String) {
        val c = carreira ?: return
        if (ocupado) return
        c.bd.equipas[equipaId]?.let { plantel = criarPlantel(c, it) }
    }

    fun fecharPlantel() {
        plantel = null
    }

    /** Abre um jogo da jornada que está à vista no separador «Liga». */
    fun abrirJogo(indice: Int) {
        val c = carreira ?: return
        val liga = ui?.liga ?: return
        if (ocupado) return
        val resultado = c.gestor.estado[liga.ligaId]?.resultados?.getOrNull(liga.jornadaVista - 1)?.getOrNull(indice) ?: return
        abrirJogo(c, resultado)
    }

    /** Abre um jogo do clube do utilizador (do calendário ou do ecrã inicial). */
    fun abrirJogoDoClube(jornada: Int) {
        val c = carreira ?: return
        if (ocupado) return
        c.jogosDeLiga().firstOrNull { it.jornada == jornada }?.resultado?.let { abrirJogo(c, it) }
    }

    private fun abrirJogo(c: Carreira, resultado: Resultado) {
        val novo = criarJogo(c, resultado, emDireto = false)
        if (novo == null) aviso = "O relato deste jogo não ficou gravado. Só se guardam os relatos dos jogos do teu clube." else jogo = novo
    }

    fun fecharJogo() {
        jogo = null
    }

    // ------------------------------------------------- cópias para os ecrãs

    /** Corre "trabalho" em segundo plano e entrega o resultado a "depois" (já no ecrã). */
    private fun <T> executar(trabalho: () -> T, depois: (T) -> Unit) {
        if (ocupado) return
        ocupado = true
        viewModelScope.launch {
            try {
                depois(withContext(Dispatchers.Default) { trabalho() })
            } catch (e: Exception) {
                aviso = "Ocorreu um erro: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                ocupado = false
            }
        }
    }

    /** Copia da carreira tudo o que os ecrãs precisam de mostrar. */
    private fun criarUi(c: Carreira, ligaVista: String): CarreiraUi {
        val g = c.gestor
        val equipa = c.equipa
        val ordem = c.estadoLiga.tabela.ordenada()
        val posicao = c.posicao()
        val tabela = linhasTabela(c, equipa.liga)
        // O calendário traz todos os jogos do clube: liga, taças e competições europeias.
        val jogosDoClube = c.jogosDoClube()
        val calendario = jogosDoClube.map { jogoCalendario(it) }
        val proximo = jogosDoClube.firstOrNull { it.resultado == null }
        val terminada = g.epocaTerminada()

        // As cinco linhas da tabela à volta do clube.
        val primeira = (posicao - 3).coerceIn(0, maxOf(0, tabela.size - 5))
        val resumo = g.historico.lastOrNull()?.takeIf { terminada }?.ligas?.get(equipa.liga)
        val nome = { id: String -> c.bd.equipas[id]?.nome ?: id }

        return CarreiraUi(
            clube = equipa.nome,
            emblema = emblema(equipa),
            epoca = g.nomeEpoca(),
            data = CalendarioDatas.texto(g.data),
            mercadoAberto = CalendarioDatas.mercadoAberto(g.data),
            modo = c.info.modo,
            utilizador = c.info.nome,
            nomeLiga = c.liga.nome,
            posicao = posicao,
            pontos = ordem.getOrNull(posicao - 1)?.pts ?: 0,
            proximo = proximo?.let {
                val adversario = it.adversario
                val lugar = ordem.indexOfFirst { l -> l.id == adversario.id } + 1
                // Só na liga e com o adversário da mesma liga faz sentido dizer o lugar na tabela.
                val naLiga = it.eLiga && lugar > 0 && c.estadoLiga.jornada > 0
                ProximoJogoUi(
                    it.jornada, c.estadoLiga.totalJornadas, CalendarioDatas.texto(it.data), it.emCasa, adversario.nome, emblema(adversario),
                    // Antes da primeira jornada ainda não há classificação a sério.
                    (if (naLiga) "$lugar.º lugar · " else "") + "Geral ${adversario.forca().geral.toInt()} · ${adversario.formacao.nome}",
                    competicao = if (it.eLiga) "" else it.competicaoNome,
                    ronda = if (it.eLiga) "" else rondaTexto(it),
                    neutro = it.neutro,
                )
            },
            ultimo = calendario.lastOrNull { it.desfecho.isNotEmpty() },
            terminada = terminada,
            resumoFim = if (resumo == null) "" else listOfNotNull(
                "O teu clube terminou em $posicao.º lugar.",
                "Vencedor: ${nome(resumo.campeao)} (${resumo.pontos} pontos).",
                resumo.sobem.takeIf { it.isNotEmpty() }?.let { "Sobem: ${it.joinToString { id -> nome(id) }}." },
                resumo.descem.takeIf { it.isNotEmpty() }?.let { "Descem: ${it.joinToString { id -> nome(id) }}." },
                resumo.melhorMarcador.takeIf { it.isNotEmpty() }?.let { "Melhor marcador: $it (${resumo.clubeMarcador}), ${resumo.golosMarcador} golos." },
            ).joinToString("\n"),
            proximaEpoca = g.nomeEpoca(g.ano + 1),
            miniTabela = tabela.drop(primeira).take(5),
            mensagens = c.mensagens.map { MensagemUi(CalendarioDatas.textoCurto(java.time.LocalDate.parse(it.data)), it.titulo, it.texto) },
            meuJogador = c.jogador?.let { j ->
                val e = j.epoca
                MeuJogadorUi(
                    j.nome,
                    "${j.posicao} · ${j.idade} anos · Geral ${j.geral()} · Potencial ${j.potencial}",
                    "Esta época: ${e.jogos} jogos, ${e.golos} golos, ${e.assistencias} assistências" +
                        (if (e.jogos > 0) ", nota média %.1f".format(e.notaMedia()) else "") + estado(j).let { if (it.isEmpty()) "" else "\n$it" },
                )
            },
            direcao = if (c.info.modo == ModoCarreira.TREINADOR) DirecaoUi(c.info.objetivoTexto, c.info.confiancaDirecao, c.info.confiancaAdeptos) else null,
            propostas = c.info.propostas.mapNotNull { id ->
                c.bd.equipas[id]?.let { e ->
                    PropostaUi(id, e.nome, emblema(e), "${c.bd.competicoes[e.liga]?.nome ?: ""} · Reputação ${e.reputacao} · Geral ${e.forca().geral.toInt()}")
                }
            },
            despedido = c.info.despedido,
            mercado = criarMercado(c),
            financas = criarFinancas(c),
            calendario = calendario,
            plantel = criarPlantel(c, equipa),
            liga = criarLiga(c, ligaVista),
            gestao = criarGestao(c),
        )
    }

    /** "Oitavos de final · ida": a ronda e, nas eliminatórias a duas mãos, a mão. */
    private fun rondaTexto(j: JogoDoClube) = j.ronda + when (j.perna) { 1 -> " · ida"; 2 -> " · volta"; else -> "" }

    private fun jogoCalendario(j: JogoDoClube): JogoCalendarioUi {
        val r = j.resultado
        // Nos jogos decididos nos penáltis o resultado em campo é um empate: conta quem passou.
        val penaltis = r?.takeIf { it.penaltis }?.let { "${if (j.emCasa) it.penaltisCasa else it.penaltisFora}-${if (j.emCasa) it.penaltisFora else it.penaltisCasa} g.p." }
        return JogoCalendarioUi(
            j.jornada, CalendarioDatas.textoCurto(j.data), j.adversario.nome, emblema(j.adversario), j.emCasa,
            resultado = if (r == null) "" else "${j.golosMarcados}-${j.golosSofridos}" + (if (penaltis != null) " ($penaltis)" else ""),
            desfecho = when {
                r == null -> ""
                j.golosMarcados > j.golosSofridos -> "V"
                j.golosMarcados < j.golosSofridos -> "D"
                r.penaltis -> if (j.passou == true) "V" else "D"
                else -> "E"
            },
            temRelato = r?.relatorio != null,
            competicao = if (j.eLiga) "" else j.competicaoNome,
            ronda = if (j.eLiga) "" else rondaTexto(j),
            chave = if (j.eLiga || r == null) "" else LigacaoFase7.chave(j.competicaoId, j.data, r.casa, r.fora),
            neutro = j.neutro,
        )
    }

    /** Tabela de uma liga, com as zonas de subida e descida. */
    private fun linhasTabela(c: Carreira, liga: String): List<LinhaUi> {
        val competicao = c.bd.competicoes.getValue(liga)
        // Quantas sobem desta liga = quantas descem da liga de cima.
        val sobem = c.bd.competicoes.values.firstOrNull { it.descePara == liga }?.descidas ?: 0
        val ordem = c.gestor.estado.getValue(liga).tabela.ordenada()
        return ordem.mapIndexed { i, l ->
            val zona = when {
                (competicao.nivel == 1 && i == 0) || i < sobem -> Zona.SUBIDA
                competicao.descePara.isNotEmpty() && i >= ordem.size - competicao.descidas -> Zona.DESCIDA
                else -> Zona.NORMAL
            }
            LinhaUi(i + 1, l.id, c.bd.equipas[l.id]?.nome ?: l.id, l.j, l.v, l.e, l.d, l.gm, l.gs, l.pts, zona, l.id == c.info.equipaId)
        }
    }

    /** O separador «Liga»: tabela, resultados, melhores jogadores e histórico da liga escolhida. */
    private fun criarLiga(c: Carreira, ligaVista: String): LigaUi {
        val g = c.gestor
        val id = ligaVista.takeIf { it in g.estado } ?: c.equipa.liga
        ligaId = id
        val estado = g.estado.getValue(id)
        val nome = { equipa: String -> c.bd.equipas[equipa]?.nome ?: equipa }

        // Jornada à vista nos resultados (a última, se não houver outra escolhida).
        val vista = (jornadaEscolhida ?: estado.jornada).coerceIn(0, estado.jornada)
        val linhaJogador = { j: Jogador, valor: String -> LinhaJogadorUi(j.nome, c.bd.equipas[j.equipa]?.abreviatura ?: "", j.epoca.jogos, valor) }
        // Para a nota média só conta quem jogou pelo menos metade dos jogos.
        val minimoJogos = maxOf(1, estado.jornada / 2)
        val historico = g.historico.reversed().mapNotNull { resumo ->
            resumo.ligas[id]?.let {
                EpocaUi(
                    g.nomeEpoca(resumo.ano), nome(it.campeao), it.pontos,
                    it.sobem.joinToString { e -> nome(e) }, it.descem.joinToString { e -> nome(e) },
                    if (it.melhorMarcador.isEmpty()) "" else "${it.melhorMarcador} (${it.clubeMarcador}), ${it.golosMarcador} golos",
                )
            }
        }
        return LigaUi(
            ligas = c.bd.paises.values.flatMap { pais -> c.bd.ligasDoPais(pais.id).map { OpcaoLiga(it.id, "${pais.nome} – ${it.nome}") } },
            ligaId = id,
            tabela = linhasTabela(c, id),
            jornada = estado.jornada,
            jornadaVista = vista,
            dataJornada = estado.datas.getOrNull(vista - 1)?.let { CalendarioDatas.texto(it) } ?: "",
            resultados = estado.resultados.getOrNull(vista - 1).orEmpty().mapIndexed { i, r ->
                ResultadoUi(i, nome(r.casa), r.golosCasa, r.golosFora, nome(r.fora), r.relatorio != null, r.casa == c.info.equipaId || r.fora == c.info.equipaId)
            },
            marcadores = g.melhores(id, 10) { it.epoca.golos.toDouble() }.map { linhaJogador(it, "${it.epoca.golos}") },
            assistencias = g.melhores(id, 10) { it.epoca.assistencias.toDouble() }.map { linhaJogador(it, "${it.epoca.assistencias}") },
            notas = g.melhores(id, 10, minimoJogos) { it.epoca.notaMedia() }.map { linhaJogador(it, "%.2f".format(it.epoca.notaMedia())) },
            historico = historico,
            titulos = historico.groupingBy { it.campeao }.eachCount().toList().sortedByDescending { it.second },
        )
    }

    private fun criarPlantel(c: Carreira, equipa: Equipa): PlantelUi {
        val forca = equipa.forca()
        val onze = equipa.onzeParaJogo()
        return PlantelUi(
            equipaId = equipa.id,
            titulo = "${equipa.nome} (${equipa.abreviatura})",
            detalhes = "${equipa.estadio} · ${equipa.capacidade} lugares\n" +
                "Reputação ${equipa.reputacao} · Ataque ${forca.ataque.toInt()} · Defesa ${forca.defesa.toInt()} · Títulos ${equipa.titulos}",
            emblema = emblema(equipa),
            jogadores = equipa.plantel
                .sortedWith(compareBy<Jogador> { Jogador.POSICOES.indexOf(it.posicao) }.thenByDescending { it.geral() })
                .map {
                    JogadorUi(
                        it.id, it.posicao, it.nome, it.idade, it.geral(), c.olheiros.potencial(it), formatarEuros(it.valorMercado()),
                        it.epoca.jogos, it.epoca.golos, it.epoca.assistencias,
                        if (it.epoca.jogos == 0) "-" else "%.1f".format(it.epoca.notaMedia()),
                        estado(it), it.id == c.info.jogadorId,
                        if (equipa.id == c.info.equipaId) formaMoral(it) else "",
                    )
                },
            formacao = equipa.formacao.takeIf { !equipa.formacaoAutomatica },
            formacaoEmUso = equipa.formacao.nome,
            mentalidade = equipa.mentalidade,
            podeMudarTatica = equipa.id == c.info.equipaId && c.info.modo == ModoCarreira.TREINADOR,
            onze = onze.mapIndexed { i, l -> LugarUi(i, l.lugar, l.jogador?.nome ?: "(vazio)", l.jogador?.posicao ?: "", l.nota) },
            suplentes = equipa.suplentesParaJogo(onze).joinToString { it.nome },
            onzeManual = equipa.onzeManual != null,
        )
    }

    /** Forma e moral por palavras: "forma boa · moral normal". */
    private fun formaMoral(j: Jogador): String {
        fun palavra(v: Int) = when {
            v >= 72 -> "ótima"
            v >= 58 -> "boa"
            v >= 42 -> "normal"
            v >= 28 -> "fraca"
            else -> "má"
        }
        return "forma ${palavra(j.forma)} · moral ${palavra(j.moral)}"
    }

    // --------------------------------------------- treino, academia, olheiros

    fun mudarFoco(foco: FocoTreino) = mudarClube { it.equipa.focoTreino = foco }

    fun mudarIntensidade(intensidade: Intensidade) = mudarClube { it.equipa.intensidade = intensidade }

    private fun mudarClube(acao: (Carreira) -> Unit) {
        val c = carreira ?: return
        if (ocupado || c.info.modo != ModoCarreira.TREINADOR) return
        acao(c)
        porGravar = true
        atualizar()
    }

    fun melhorarInstalacao(i: Instalacao) {
        val c = carreira ?: return
        var resposta = Resposta.NAO_PERMITIDO
        naCarreira({ aviso = if (resposta == Resposta.ACEITE) "Obras feitas: ${i.nome.lowercase()} no nível ${c.academia.nivel(i)}." else resposta.frase }) {
            resposta = c.academia.melhorar(i)
            null
        }
    }

    /** Manda o olheiro a um país, à procura de jovens de uma posição ("" = todas). */
    fun enviarOlheiro(pais: String, posicao: String) {
        val c = carreira ?: return
        var resposta = Resposta.NAO_PERMITIDO
        naCarreira({ aviso = if (resposta == Resposta.ACEITE) "O olheiro partiu. Volta daqui a 4 semanas com um relatório." else resposta.frase }) {
            resposta = c.olheiros.enviar(pais, posicao)
            null
        }
    }

    private fun criarGestao(c: Carreira): GestaoClubeUi? {
        if (c.info.modo != ModoCarreira.TREINADOR) return null
        val e = c.equipa
        val ativo = !c.info.despedido
        val o = c.olheiros
        return GestaoClubeUi(
            e.focoTreino, e.intensidade,
            Instalacao.entries.map { i ->
                val custo = c.academia.custoMelhoria(i)
                InstalacaoUi(
                    i, i.nome, i.efeito, c.academia.nivel(i),
                    if (custo == null) "Nível máximo" else "Melhorar por ${Mercado.euros(custo)}",
                    ativo && custo != null && custo <= e.orcamento,
                )
            },
            e.academia.sortedByDescending { it.potencial }.map { JovemUi(it.id, it.posicao, it.nome, it.idade, it.geral(), it.potencial) },
            c.bd.paises.values.map { OpcaoPaisUi(it.id, it.nome, Mercado.euros(o.custo(it.id))) },
            o.estado.missao?.let { m ->
                "Em viagem: ${c.bd.paises[m.pais]?.nome ?: m.pais}" + (if (m.posicao.isNotEmpty()) " (${m.posicao})" else "") +
                    ". Volta a ${CalendarioDatas.textoCurto(java.time.LocalDate.parse(m.regressa))}."
            } ?: "",
            o.estado.relatorio.mapNotNull { c.bd.jogadores[it] }.map { j ->
                val dono = c.bd.equipas[j.equipa]
                LinhaMercadoUi(j.id, j.posicao, j.nome, dono?.abreviatura ?: "livre", j.idade, j.geral(), "pot. ${j.potencial}")
            },
            Mercado.euros(e.orcamento),
        )
    }

    /** "" se o jogador está apto; senão o motivo por que não pode jogar. */
    private fun estado(j: Jogador): String = when {
        j.lesao > 0 -> "Lesionado: falha ${jogos(j.lesao)}"
        j.suspensao > 0 -> "Castigado: falha ${jogos(j.suspensao)}"
        else -> ""
    }

    private fun jogos(n: Int) = if (n == 1) "1 jogo" else "$n jogos"

    /** Prepara um jogo para o ecrã de jogo. Devolve null se o relato não existir. */
    private fun criarJogo(c: Carreira, resultado: Resultado, emDireto: Boolean): JogoUi? {
        val r = resultado.relatorio ?: return null
        val equipaCasa = c.bd.equipas[r.casa.equipa] ?: return null
        val equipaFora = c.bd.equipas[r.fora.equipa] ?: return null
        val casa = equipaCasa.nome
        val fora = equipaFora.nome

        val eventos = mutableListOf(EventoUi(0, "", "Apita o árbitro: começa o jogo!", null, true, 0, 0))
        var golosCasa = 0
        var golosFora = 0
        var intervalo = false
        for (e in r.eventos) {
            if (!intervalo && e.minuto > 45) {
                intervalo = true
                eventos += EventoUi(45, "", "Intervalo: $casa $golosCasa - $golosFora $fora.", null, true, golosCasa, golosFora)
            }
            if (e.tipo == TipoEvento.GOLO) {
                golosCasa = e.golosCasa
                golosFora = e.golosFora
            }
            eventos += EventoUi(e.minuto, Relato.minuto(e.minuto), Relato.frase(e, if (e.casa) casa else fora), e.tipo, e.casa, golosCasa, golosFora)
        }
        if (!intervalo) eventos += EventoUi(45, "", "Intervalo: $casa $golosCasa - $golosFora $fora.", null, true, golosCasa, golosFora)
        eventos += EventoUi(r.duracao, "", "Fim do jogo: $casa ${r.golosCasa} - ${r.golosFora} $fora.", null, true, r.golosCasa, r.golosFora)

        val ec = r.casa.estatisticas
        val ef = r.fora.estatisticas
        return JogoUi(
            casa, fora, emblema(equipaCasa), emblema(equipaFora), r.golosCasa, r.golosFora, r.duracao,
            "${r.casa.formacao.nome} · ${r.casa.mentalidade.nome}", "${r.fora.formacao.nome} · ${r.fora.mentalidade.nome}",
            eventos,
            listOf(
                Triple("Posse de bola", "${ec.posse}%", "${ef.posse}%"),
                Triple("Remates", "${ec.remates}", "${ef.remates}"),
                Triple("Remates à baliza", "${ec.rematesBaliza}", "${ef.rematesBaliza}"),
                Triple("Cantos", "${ec.cantos}", "${ef.cantos}"),
                Triple("Faltas", "${ec.faltas}", "${ef.faltas}"),
                Triple("Cartões amarelos", "${ec.amarelos}", "${ef.amarelos}"),
                Triple("Cartões vermelhos", "${ec.vermelhos}", "${ef.vermelhos}"),
            ),
            fichas(r.casa), fichas(r.fora),
            r.melhorEmCampo?.let { "${it.jogador.nome} (%.1f)".format(it.nota) } ?: "",
            emDireto,
        )
    }

    private fun fichas(lado: LadoRelatorio): List<FichaUi> = lado.fichas
        .sortedWith(compareByDescending<FichaJogador> { it.titular }.thenBy { it.entrou })
        .map {
            FichaUi(
                it.lugar, it.jogador.nome, "%.1f".format(it.nota), it.titular,
                it.golos, it.assistencias, it.amarelos, it.vermelho, it.lesao > 0, it.entrou, it.saiu,
            )
        }

    private fun formatarEuros(valor: Int): String =
        if (valor >= 1_000_000) "%.1f M€".format(valor / 1_000_000.0) else "${valor / 1000} mil €"  // igual a Mercado.euros

    /** Emblema de um clube a partir das cores da base de dados ("#rrggbb"). */
    private fun emblema(e: Equipa): EmblemaUi {
        val fundo = cor(e.cores.getOrNull(0)) ?: 0xFF2E7D32
        var letras = cor(e.cores.getOrNull(1)) ?: 0xFFFFFFFF
        // Se as duas cores forem parecidas, as letras ficam a preto ou a branco para se lerem.
        if (kotlin.math.abs(brilho(fundo) - brilho(letras)) < 90) letras = if (brilho(fundo) > 140) 0xFF000000 else 0xFFFFFFFF
        return EmblemaUi(e.abreviatura, fundo, letras)
    }

    private fun cor(texto: String?): Long? =
        texto?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it }

    private fun brilho(cor: Long): Int =
        ((((cor shr 16) and 0xFF) * 299 + ((cor shr 8) and 0xFF) * 587 + (cor and 0xFF) * 114) / 1000).toInt()
}
