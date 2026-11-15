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
import pt.futebolcarreira.nucleo.carreira.JogoDoClube
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.carreira.ResumoGravacao
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Formacao
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

    /** Plantel de outro clube aberto numa janela (null = nenhum). */
    var plantel by mutableStateOf<PlantelUi?>(null)
        private set

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
        ui = novo
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
    private fun naCarreira(acao: (Carreira) -> JogoUi?) {
        val c = carreira ?: return
        jornadaEscolhida = null
        val ligaVista = ligaId
        executar({
            val jogoParaAbrir = acao(c)
            armazem.gravar(c)
            criarUi(c, ligaVista) to jogoParaAbrir
        }) { (novo, jogoParaAbrir) ->
            porGravar = false
            ui = novo
            if (jogoParaAbrir != null) jogo = jogoParaAbrir
        }
    }

    /** Volta a criar a cópia dos dados para os ecrãs (depois de uma mudança rápida, sem simulação). */
    private fun atualizar() {
        val c = carreira ?: return
        ui = criarUi(c, ligaId)
    }

    fun escolherLiga(id: String) {
        if (ocupado) return
        ligaId = id
        jornadaEscolhida = null
        atualizar()
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
        c.jogosDoClube().firstOrNull { it.jornada == jornada }?.resultado?.let { abrirJogo(c, it) }
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
        val calendario = c.jogosDoClube().map { jogoCalendario(it) }
        val proximo = c.proximoJogo()
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
                ProximoJogoUi(
                    it.jornada, c.estadoLiga.totalJornadas, CalendarioDatas.texto(it.data), it.emCasa, adversario.nome, emblema(adversario),
                    // Antes da primeira jornada ainda não há classificação a sério.
                    (if (c.estadoLiga.jornada > 0) "$lugar.º lugar · " else "") + "Geral ${adversario.forca().geral.toInt()} · ${adversario.formacao.nome}",
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
            calendario = calendario,
            plantel = criarPlantel(c, equipa),
            liga = criarLiga(c, ligaVista),
        )
    }

    private fun jogoCalendario(j: JogoDoClube) = JogoCalendarioUi(
        j.jornada, CalendarioDatas.textoCurto(j.data), j.adversario.nome, emblema(j.adversario), j.emCasa,
        resultado = if (j.resultado == null) "" else "${j.golosMarcados}-${j.golosSofridos}",
        desfecho = when {
            j.resultado == null -> ""
            j.golosMarcados > j.golosSofridos -> "V"
            j.golosMarcados < j.golosSofridos -> "D"
            else -> "E"
        },
        temRelato = j.resultado?.relatorio != null,
    )

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
                        it.posicao, it.nome, it.idade, it.geral(), it.potencial, formatarEuros(it.valorMercado()),
                        it.epoca.jogos, it.epoca.golos, it.epoca.assistencias,
                        if (it.epoca.jogos == 0) "-" else "%.1f".format(it.epoca.notaMedia()),
                        estado(it), it.id == c.info.jogadorId,
                    )
                },
            formacao = equipa.formacao.takeIf { !equipa.formacaoAutomatica },
            formacaoEmUso = equipa.formacao.nome,
            mentalidade = equipa.mentalidade,
            podeMudarTatica = equipa.id == c.info.equipaId && c.info.modo == ModoCarreira.TREINADOR,
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
        if (valor >= 1_000_000) "%.1f M€".format(valor / 1_000_000.0) else "${valor / 1000} mil €"

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
