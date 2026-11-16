package pt.futebolcarreira.nucleo.carreira

import kotlinx.serialization.Serializable
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.Competicao
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.simulacao.CalendarioDatas
import pt.futebolcarreira.nucleo.simulacao.Confronto
import pt.futebolcarreira.nucleo.simulacao.EstadoEliminatoria
import pt.futebolcarreira.nucleo.simulacao.EstadoLiga
import pt.futebolcarreira.nucleo.simulacao.GestorEpocas
import pt.futebolcarreira.nucleo.simulacao.FasesEuropa
import pt.futebolcarreira.nucleo.simulacao.JogoEuropa
import pt.futebolcarreira.nucleo.simulacao.Resultado
import pt.futebolcarreira.nucleo.simulacao.TipoEuropa
import pt.futebolcarreira.nucleo.simulacao.TipoEvento
import java.time.LocalDate

/** As duas vias da carreira. */
enum class ModoCarreira(val nome: String) { TREINADOR("Treinador"), JOGADOR("Jogador") }

/** Quem é o utilizador nesta carreira. */
@Serializable
class InfoCarreira(
    /** Identificador da carreira (também é o nome do ficheiro de gravação). */
    val id: String,
    /** Nome do treinador ou do jogador. */
    val nome: String,
    var modo: ModoCarreira,
    var equipaId: String,
    /** Só na via de jogador: o id do jogador do utilizador. */
    var jogadorId: Int? = null,
    /** Objetivo da direção para esta época: o lugar mínimo na tabela e a frase que o descreve. */
    var objetivoPosicao: Int = 0,
    var objetivoTexto: String = "",
    /** Confiança da direção e dos adeptos no treinador (0 a 100). */
    var confiancaDirecao: Int = 60,
    var confiancaAdeptos: Int = 60,
    /** true = o treinador foi despedido e tem de aceitar uma proposta para continuar. */
    var despedido: Boolean = false,
    /** Clubes (ids) que querem contratar o treinador. */
    val propostas: MutableList<String> = mutableListOf(),
    /** Só na via de jogador (Fase 9): estatuto, pedidos do treinador, propostas, prémios... Gravações antigas não o têm. */
    var estadoJogador: EstadoJogador? = null,
)

/** Uma mensagem na caixa de entrada (resultados, lesões, avisos...). */
@Serializable
class Mensagem(val data: String, val titulo: String, val texto: String)

/**
 * Um jogo do clube do utilizador no calendário (de qualquer competição). "resultado" é null se ainda não se jogou.
 *
 * Nas ligas, "jornada" é o número da jornada e "indice" a posição do jogo na jornada.
 * Nas taças, "jornada" é o número da ronda (1 = primeira) e "indice" a posição do confronto na ronda;
 * "perna" distingue as mãos (0 = jogo único, 1 = ida, 2 = volta). Num mesmo dia o clube só tem um jogo,
 * por isso (competicaoId, data) identifica o jogo.
 */
class JogoDoClube(
    val jornada: Int,
    val indice: Int,
    val data: LocalDate,
    val adversario: Equipa,
    val emCasa: Boolean,
    val resultado: Resultado?,
    val competicaoId: String = "",
    val competicaoNome: String = "",
    /** "liga", "taca", "supertaca" ou "europa". */
    val tipo: String = "liga",
    /** Nome da ronda ("Oitavos de final"...); vazio nas ligas. */
    val ronda: String = "",
    /** Campo neutro: "emCasa" só diz qual dos lados está nomeado primeiro no resultado. */
    val neutro: Boolean = false,
    val perna: Int = 0,
    /** O confronto a que pertence (só nas taças): agregado, prolongamento, penáltis, quem passou. */
    val confronto: Confronto? = null,
    /** É a última ronda da competição (a final). */
    val eFinal: Boolean = false,
    /** Índice da ronda (0 = primeira), só nas taças. */
    val indiceRonda: Int = 0,
    /** Id do clube a que o jogo diz respeito. */
    val clubeId: String = "",
    /** Nome do derby (rivalidade) entre os dois clubes, ou null se não é um derby. */
    val derby: String? = null,
    /** Intensidade do derby (1 a 3); 0 = jogo normal. */
    val intensidadeDerby: Int = 0,
) {
    /** Só nas taças: true se o clube passou, false se foi eliminado, null se ainda não está decidido. */
    val passou: Boolean? get() = confronto?.vencedor?.let { it == clubeId }
    val eLiga get() = tipo == "liga"

    /** Golos do clube do utilizador e do adversário (só depois de jogado). */
    val golosMarcados get() = resultado?.let { if (emCasa) it.golosCasa else it.golosFora } ?: 0
    val golosSofridos get() = resultado?.let { if (emCasa) it.golosFora else it.golosCasa } ?: 0
}

/**
 * Uma carreira: o mundo do jogo (base de dados + épocas) visto do lado do utilizador,
 * que é treinador de um clube ou jogador de um clube.
 */
class Carreira(
    val bd: BaseDados, val gestor: GestorEpocas, val info: InfoCarreira,
    estadoOlheiros: EstadoOlheiros = EstadoOlheiros(),
) {
    /** Caixa de entrada: a mensagem mais recente fica em primeiro lugar. */
    val mensagens = mutableListOf<Mensagem>()

    /** As notícias (manchetes) do mundo do jogo, a mais recente em primeiro lugar (no máximo [GeradorNoticias.MAX_NOTICIAS]). */
    val manchetes = mutableListOf<Noticia>()

    /** Cria as manchetes a partir do que acontece nos jogos. */
    val geradorNoticias = GeradorNoticias(this)

    /**
     * true quando a base de dados (equipas, jogadores) mudou desde a última gravação.
     * Acontece ao criar a carreira e no início de cada época.
     */
    var baseDadosPorGravar = true

    /** Transferências, empréstimos e obras. */
    val mercado = Mercado(this)

    /** Jovens da academia e instalações do clube. */
    val academia = Academia(this)

    /** Viagens e relatórios do olheiro. */
    val olheiros = Olheiros(this, estadoOlheiros)

    /** A carreira do jogador (só tem efeito na via de jogador). */
    val jogadorCarreira = CarreiraJogador(this)

    init {
        gestor.clubeDoUtilizador = info.equipaId
        jogadorCarreira.prepararJogo()
    }

    /** O clube do utilizador. */
    val equipa: Equipa get() = bd.equipas.getValue(info.equipaId)

    /** A liga onde o clube joga esta época. */
    val liga: Competicao get() = bd.competicoes.getValue(equipa.liga)

    val estadoLiga: EstadoLiga get() = gestor.estado.getValue(equipa.liga)

    /** O jogador do utilizador (só na via de jogador). */
    val jogador: Jogador? get() = info.jogadorId?.let { bd.jogadores[it] }

    /** Lugar do clube na tabela (1 = primeiro). */
    fun posicao(): Int = estadoLiga.tabela.ordenada().indexOfFirst { it.id == info.equipaId } + 1

    /** A taça nacional do país do clube nesta época (null em gravações antigas, até à época seguinte). */
    val taca: EstadoEliminatoria? get() = gestor.tacaDoPais(liga.pais)

    /** A supertaça do país do clube nesta época. */
    val supertaca: EstadoEliminatoria? get() = gestor.tacaDoPais(liga.pais, supertaca = true)

    /**
     * Todos os jogos do clube nesta época, em todas as competições, por ordem de data.
     * Das taças só entram os confrontos já sorteados (o adversário seguinte só se conhece depois do sorteio).
     */
    fun jogosDoClube(): List<JogoDoClube> = (jogosDeLiga() + jogosDeTacas() + jogosEuropeus()).sortedBy { it.data }

    /**
     * Os jogos do clube na Liga dos Campeões ou na Liga Europa (vazio se não está apurado ou em gravações antigas).
     * Nas eliminatórias cria-se um [Confronto] com os jogos já feitos, para o agregado, o prolongamento,
     * os penáltis e "passou" funcionarem como nas taças.
     */
    fun jogosEuropeus(): List<JogoDoClube> {
        val europa = gestor.europa ?: return emptyList()
        val id = info.equipaId
        return TipoEuropa.values().flatMap { tipo ->
            val estado = europa.estado(tipo)
            if (id !in estado.clubes) return@flatMap emptyList()
            val liga = estado.jogosLiga.filter { it.casa == id || it.fora == id }.map { j ->
                jogoEuropeu(tipo, j, j.numero, "${FasesEuropa.LIGA} · ${j.numero}.ª jornada", 0, null, false, 0)
            }
            val fases = estado.eliminatorias.map { it.fase }.distinct()
            val eliminatorias = estado.eliminatorias.filter { it.a == id || it.b == id }.flatMap { t ->
                val primeiro = t.jogos.first()
                val c = Confronto(primeiro.casa, if (primeiro.casa == t.a) t.b else t.a, t.jogos.map { it.dia }, primeiro.neutro)
                for (j in t.jogos.filter { it.jogado }) c.jogos += resultadoEuropeu(j)
                if (t.terminada) c.vencedor = t.vencedor
                val indiceFase = FASES_ELIMINATORIAS.indexOf(t.fase)
                t.jogos.mapIndexed { i, j ->
                    jogoEuropeu(tipo, j, fases.indexOf(t.fase) + 1, t.fase, if (t.jogos.size > 1) i + 1 else 0, c, t.fase == FasesEuropa.FINAL, indiceFase)
                }
            }
            liga + eliminatorias
        }
    }

    private fun resultadoEuropeu(j: JogoEuropa) =
        Resultado(j.casa, j.fora, j.golosCasa, j.golosFora, j.relatorio, j.prolongamento, maxOf(0, j.penaltisCasa), maxOf(0, j.penaltisFora))

    private fun jogoEuropeu(tipo: TipoEuropa, j: JogoEuropa, jornada: Int, ronda: String, perna: Int, c: Confronto?, eFinal: Boolean, indiceRonda: Int): JogoDoClube {
        val emCasa = j.casa == info.equipaId
        val riv = bd.rivalidades().entre(j.casa, j.fora)
        return JogoDoClube(
            jornada, 0, j.dia, bd.equipas.getValue(if (emCasa) j.fora else j.casa), emCasa,
            if (j.jogado) resultadoEuropeu(j) else null,
            tipo.id, tipo.nome, "europa", ronda, j.neutro, perna, c, eFinal, indiceRonda, info.equipaId,
            derby = riv?.nome, intensidadeDerby = riv?.intensidade ?: 0,
        )
    }

    /** Só os jogos da liga. */
    fun jogosDeLiga(): List<JogoDoClube> {
        val e = estadoLiga
        return e.calendario.mapIndexedNotNull { jornada, jogos ->
            val indice = jogos.indexOfFirst { it.casa == info.equipaId || it.fora == info.equipaId }
            if (indice < 0) return@mapIndexedNotNull null
            val jogo = jogos[indice]
            val emCasa = jogo.casa == info.equipaId
            val riv = bd.rivalidades().entre(jogo.casa, jogo.fora)
            JogoDoClube(
                jornada + 1, indice, e.datas[jornada],
                bd.equipas.getValue(if (emCasa) jogo.fora else jogo.casa), emCasa,
                e.resultados.getOrNull(jornada)?.getOrNull(indice),
                competicaoId = liga.id, competicaoNome = liga.nome, clubeId = info.equipaId,
                derby = riv?.nome, intensidadeDerby = riv?.intensidade ?: 0,
            )
        }
    }

    /** Os jogos do clube nas taças e supertaças (só dos confrontos já sorteados). */
    fun jogosDeTacas(): List<JogoDoClube> = gestor.eliminatorias.values.flatMap { t ->
        val comp = bd.competicoes[t.id] ?: return@flatMap emptyList()
        t.rondas.flatMapIndexed { r, ronda ->
            val indice = ronda.confrontos.indexOfFirst { it.envolve(info.equipaId) }
            if (indice < 0) return@flatMapIndexed emptyList()
            val c = ronda.confrontos[indice]
            val riv = bd.rivalidades().entre(c.a, c.b)
            c.datas.mapIndexed { i, data ->
                JogoDoClube(
                    r + 1, indice, data, bd.equipas.getValue(c.adversarioDe(info.equipaId)), c.casaDoJogo(i) == info.equipaId,
                    c.jogos.getOrNull(i), comp.id, comp.nome, comp.tipo, ronda.nome, c.neutro,
                    if (c.idaEVolta) i + 1 else 0, c, r == t.defs.size - 1, r, info.equipaId,
                    derby = riv?.nome, intensidadeDerby = riv?.intensidade ?: 0,
                )
            }
        }
    }

    /** O próximo jogo do clube (null se já não há mais nesta época). */
    fun proximoJogo(): JogoDoClube? = jogosDoClube().firstOrNull { it.resultado == null }

    /** O último jogo que o clube jogou (null se ainda não jogou). */
    fun ultimoJogo(): JogoDoClube? = jogosDoClube().lastOrNull { it.resultado != null }

    /**
     * Avança no tempo até ao próximo jogo do clube e joga-o (as outras equipas também jogam).
     * Se o clube já não tem jogos, avança até ao fim da época.
     * Devolve o jogo do clube, ou null se não houve nenhum.
     */
    fun avancar(): JogoDoClube? {
        if (gestor.epocaTerminada() || info.despedido) return null
        jogadorCarreira.prepararJogo()
        var alvo = proximoJogo()
        val antes = gestor.data
        var negocios = emptyList<String>()
        gestor.fatorAmbiente = ambiente(alvo).fator
        if (alvo == null) {
            gestor.simularAteAoFim()
        } else {
            // Enquanto o mercado está aberto, os outros clubes também compram e vendem.
            negocios = mercado.negociosDosOutros(6)
            if (negocios.isNotEmpty()) mensagem("Mercado: negócios dos outros clubes", negocios.take(4).joinToString(" "))
            // Dia a dia: um sorteio de taça feito pelo caminho pode trazer um jogo do clube antes do que se esperava.
            while (true) {
                val proxima = gestor.proximaData() ?: break
                if (proxima > alvo!!.data) break
                gestor.simularAte(proxima)
                if (proxima == alvo.data) break
                alvo = proximoJogo() ?: break
            }
            avisarMercado(antes, gestor.data)
            // Caixa negativa preocupa a direção.
            if (equipa.orcamento < 0 && eTreinador) info.confiancaDirecao = (info.confiancaDirecao - 1).coerceAtLeast(0)
        }
        // O treino do mês mexe nos atributos: a base de dados tem de voltar a ser gravada.
        if (antes.monthValue != gestor.data.monthValue) baseDadosPorGravar = true
        if (gestor.noticias.isNotEmpty()) {
            if (eTreinador) mensagem("Treino", gestor.noticias.joinToString(" "))
            gestor.noticias.clear()
        }
        olheiros.verificar()
        jogadorCarreira.aoMercado()
        avisarSelecoes()
        val jogado = alvo?.let { a -> jogosDoClube().firstOrNull { it.competicaoId == a.competicaoId && it.data == a.data } }
        if (jogado != null) {
            if (jogado.eLiga) {
                mensagemDoJogo(jogado)
                avaliarJogo(jogado)
            } else if (jogado.tipo == "europa" && jogado.confronto == null) {
                mensagemDaFaseDeLiga(jogado)
                avaliarJogoDeTaca(jogado)
            } else {
                mensagemDaTaca(jogado)
                avaliarJogoDeTaca(jogado)
            }
            jogadorCarreira.aposJogo(jogado)
        }
        jogadorCarreira.aposTreino()
        // Notícias do que se jogou (no clube e no resto do mundo).
        val jogosN = gestor.jogosParaNoticias.toList()
        val confrontosN = gestor.confrontosParaNoticias.toList()
        gestor.jogosParaNoticias.clear()
        gestor.confrontosParaNoticias.clear()
        geradorNoticias.gerar(jogosN, confrontosN, negocios)
        if (gestor.epocaTerminada()) {
            mensagemFimDeEpoca()
            avaliarEpoca()
            jogadorCarreira.fecharEpoca()
            gestor.historico.lastOrNull()?.let { jogadorCarreira.anunciarPremios(it) }
        }
        geradorNoticias.publicar()
        return jogado
    }

    /** Simula tudo o que falta desta época (modo "férias"). */
    fun simularEpoca() {
        while (!gestor.epocaTerminada() && !info.despedido) avancar()
    }

    /** Começa a época seguinte (só depois de esta acabar). */
    fun novaEpoca() {
        if (!gestor.epocaTerminada() || info.despedido) return
        gestor.clubeDoUtilizador = info.equipaId
        jogadorCarreira.antesDaNovaEpoca()
        gestor.avancarParaEpocaSeguinte()
        mercado.recusados.clear()
        gestor.noticias.clear()
        gestor.jogosParaNoticias.clear()
        gestor.confrontosParaNoticias.clear()
        geradorNoticias.novaEpoca()
        // O torneio de verão (Final Four, Euro ou Mundial) jogou-se ao mudar de época: resultados e convocatórias.
        avisarSelecoes()
        geradorNoticias.publicar()
        if (gestor.avisos.isNotEmpty()) mensagem("Plantel: o que mudou no verão", gestor.avisos.joinToString(" "))
        val jovens = academia.novaGeracao()
        if (eTreinador) mensagem("Academia: nova geração", "Entraram na academia: " + jovens.joinToString { "${it.nome} (${it.posicao}, ${it.idade} anos)" } + ".")
        // Propostas que ficaram por aceitar perdem-se; a confiança volta a aproximar-se do normal.
        info.propostas.clear()
        info.confiancaDirecao = (info.confiancaDirecao + 60) / 2
        info.confiancaAdeptos = (info.confiancaAdeptos + 60) / 2
        definirObjetivo()
        baseDadosPorGravar = true
        if (info.modo == ModoCarreira.JOGADOR && jogador != null) jogadorCarreira.depoisDaNovaEpoca()
        // O jogador do utilizador pode ter chegado ao fim da carreira: passa a treinador (do clube onde acabou, ou de outro).
        if (info.modo == ModoCarreira.JOGADOR && jogador == null) {
            jogadorCarreira.reformou()
            info.modo = ModoCarreira.TREINADOR
            info.jogadorId = null
        }
        mensagem(
            "Começa a época ${gestor.nomeEpoca()}",
            "O teu clube joga nesta competição: ${liga.nome}. O primeiro jogo é a ${proximoJogo()?.let { CalendarioDatas.textoCurto(it.data) } ?: "?"}.",
        )
    }

    // ------------------------------------------------ direção e adeptos

    private val eTreinador get() = info.modo == ModoCarreira.TREINADOR

    /** A direção define o objetivo conforme a reputação do clube em relação aos outros da liga. */
    fun definirObjetivo() {
        val equipas = bd.equipasDaLiga(equipa.liga).sortedByDescending { it.reputacao }
        val n = equipas.size
        val lugar = equipas.indexOfFirst { it.id == info.equipaId } + 1
        // Quantas equipas sobem desta liga (0 se for a primeira divisão).
        val sobem = bd.competicoes.values.firstOrNull { it.descePara == liga.id }?.descidas ?: 0
        val (posicao, texto) = when {
            sobem > 0 && lugar <= sobem + 1 -> sobem to "Subir de divisão"
            sobem > 0 && lugar <= 6 -> 6 to "Ficar nos seis primeiros"
            sobem == 0 && lugar <= 2 -> 1 to "Ser campeão"
            sobem == 0 && lugar <= 5 -> 4 to "Ficar nos quatro primeiros"
            lugar <= n / 2 -> n / 2 to "Ficar na primeira metade da tabela"
            liga.descidas > 0 -> n - liga.descidas to "Evitar a descida"
            else -> n - 3 to "Fugir aos últimos lugares"
        }
        info.objetivoPosicao = posicao
        info.objetivoTexto = texto
    }

    /**
     * O ambiente à volta do clube neste momento: confiança dos adeptos e da direção, forma nos últimos 5 jogos
     * e, se o próximo jogo for um derby, a tensão que isso traz.
     */
    fun ambiente(proximo: JogoDoClube? = proximoJogo()): Ambiente {
        val recentes = jogosDoClube().filter { it.resultado != null }.takeLast(5)
        val pontos = recentes.map { if (it.golosMarcados > it.golosSofridos) 3 else if (it.golosMarcados == it.golosSofridos) 1 else 0 }.sum()
        return Ambiente.calcular(info.confiancaAdeptos, info.confiancaDirecao, pontos, recentes.size, proximo?.intensidadeDerby ?: 0)
    }

    /** Num derby os efeitos na confiança contam 1,5 a 2 vezes mais (conforme a intensidade). */
    private fun escalarDerby(valor: Int, intensidade: Int): Int =
        if (intensidade <= 0 || valor == 0) valor else (if (valor < 0) -1 else 1) * Math.round(Math.abs(valor) * (1.25 + 0.25 * intensidade)).toInt()

    /** Depois de cada jogo, a direção e os adeptos ficam mais (ou menos) contentes com o treinador. */
    internal fun avaliarJogo(jogo: JogoDoClube) {
        if (!eTreinador) return
        // Diferença de qualidade: positiva se o adversário é mais forte.
        val diferenca = jogo.adversario.forca().geral - equipa.forca().geral
        val saldo = jogo.golosMarcados - jogo.golosSofridos
        var direcao = when {
            saldo > 0 -> if (diferenca >= 3) 4 else 3
            saldo == 0 -> if (diferenca >= 4) 1 else -1
            else -> if (diferenca >= 6) -2 else if (diferenca <= -3) -4 else -3
        }
        var adeptos = when {
            saldo > 0 -> 4
            saldo == 0 -> -1
            else -> if (saldo <= -3) -7 else -5
        }
        direcao = escalarDerby(direcao, jogo.intensidadeDerby)
        adeptos = escalarDerby(adeptos, jogo.intensidadeDerby)
        // Com a época já lançada, conta também a distância ao objetivo.
        if (estadoLiga.jornada >= 6) {
            val atraso = posicao() - info.objetivoPosicao
            direcao += when {
                atraso <= 0 -> 1
                atraso >= 8 -> -2
                atraso >= 4 -> -1
                else -> 0
            }
        }
        info.confiancaDirecao = (info.confiancaDirecao + direcao).coerceIn(0, 100)
        info.confiancaAdeptos = (info.confiancaAdeptos + adeptos).coerceIn(0, 100)
        if (info.confiancaDirecao < LIMITE_DESPEDIMENTO && estadoLiga.jornada >= 8 && !gestor.epocaTerminada()) despedir()
    }

    /** No fim da época faz-se o balanço: objetivo cumprido ou não, propostas de outros clubes ou despedimento. */
    private fun avaliarEpoca() {
        if (!eTreinador) return
        val atraso = posicao() - info.objetivoPosicao
        val (direcao, adeptos, frase) = when {
            atraso <= 0 -> Triple(15, 10, "Objetivo cumprido (${info.objetivoTexto.lowercase()}). A direção está satisfeita com o teu trabalho.")
            atraso <= 2 -> Triple(-5, -5, "O objetivo (${info.objetivoTexto.lowercase()}) ficou por pouco. A direção esperava mais.")
            else -> Triple(-18, -12, "O objetivo (${info.objetivoTexto.lowercase()}) ficou longe. A direção está descontente.")
        }
        info.confiancaDirecao = (info.confiancaDirecao + direcao).coerceIn(0, 100)
        info.confiancaAdeptos = (info.confiancaAdeptos + adeptos).coerceIn(0, 100)
        mensagem("Balanço da direção", frase)
        if (info.confiancaDirecao < LIMITE_FIM_DE_EPOCA) {
            despedir()
        } else if (info.confiancaDirecao >= 72) {
            // Bom trabalho chama a atenção de clubes maiores.
            val reputacao = equipa.reputacao
            val interessados = bd.equipas.values
                .filter { it.id != info.equipaId && it.reputacao in (reputacao + 2)..(reputacao + 12) }
                .shuffled(gestor.aleatorio).take(2)
            if (interessados.isNotEmpty()) {
                info.propostas += interessados.map { it.id }
                mensagem("Propostas de outros clubes", "O teu trabalho não passou despercebido: ${interessados.joinToString(" e ") { it.nome }} querem contratar-te. Podes aceitar ou ficar.")
            }
        }
    }

    /** A direção despede o treinador; aparecem propostas de clubes mais modestos. */
    private fun despedir() {
        info.despedido = true
        val reputacao = equipa.reputacao
        val interessados = bd.equipas.values
            .filter { it.id != info.equipaId && it.reputacao in (reputacao - 14)..(reputacao - 3) }
            .shuffled(gestor.aleatorio).take(3)
            .ifEmpty { bd.equipas.values.filter { it.id != info.equipaId }.sortedBy { it.reputacao }.take(3) }
        info.propostas.clear()
        info.propostas += interessados.map { it.id }
        mensagem("Foste despedido", "A direção perdeu a confiança em ti e rescindiu o contrato. Tens propostas de: ${interessados.joinToString { it.nome }}.")
    }

    /** O treinador aceita uma proposta e muda de clube. */
    fun aceitarProposta(equipaId: String) {
        if (equipaId !in info.propostas) return
        // O clube antigo volta a ser gerido pelo computador.
        equipa.onzeManual = null
        equipa.formacaoAutomatica = true
        equipa.mentalidade = null
        equipa.escolherFormacao()
        info.equipaId = equipaId
        gestor.clubeDoUtilizador = equipaId
        info.despedido = false
        info.propostas.clear()
        info.confiancaDirecao = 60
        info.confiancaAdeptos = 60
        definirObjetivo()
        academia.garantirJovens()
        // O olheiro do clube novo começa do zero.
        olheiros.estado.missao = null
        olheiros.estado.relatorio = emptyList()
        mensagem("Novo clube: ${equipa.nome}", "Assinaste contrato. Objetivo da direção: ${info.objetivoTexto.lowercase()}.")
    }

    /** O treinador recusa as propostas (só se não estiver despedido). */
    fun recusarPropostas() {
        if (!info.despedido) info.propostas.clear()
    }

    internal fun mensagem(titulo: String, texto: String, data: LocalDate = gestor.data) {
        mensagens.add(0, Mensagem(data.toString(), titulo, texto))
        while (mensagens.size > MAX_MENSAGENS) mensagens.removeAt(mensagens.lastIndex)
    }

    // ------------------------------------------------ seleções nacionais

    /**
     * Avisos das seleções desde a última vez: convocatórias do clube (ou do jogador), resultados das seleções que
     * interessam (a do país do clube e a do jogador), o vencedor das competições que acabaram e as lesões
     * ao serviço da seleção. Poucas mensagens: uma de cada tipo por chamada.
     */
    private fun avisarSelecoes() {
        val convocatorias = gestor.convocatoriasPendentes.toList()
        val jogos = gestor.jogosSelecoesPendentes.toList()
        gestor.convocatoriasPendentes.clear()
        gestor.jogosSelecoesPendentes.clear()
        val s = gestor.selecoes ?: return
        for (c in convocatorias) avisarConvocatoria(s, c)
        if (jogos.isEmpty()) return
        val data = jogos.maxOf { it.jogo.dataLocal() }
        if (!eTreinador) {
            val meus = jogadorCarreira.avisosDaSelecao(jogos)
            if (meus.isNotEmpty()) mensagem("A tua seleção", meus.joinToString(" "), data)
        }
        val interesse = setOfNotNull(equipa.pais, jogador?.nacionalidade)
        val linhas = jogos.filter { it.jogo.casa in interesse || it.jogo.fora in interesse }.map { j ->
            val g = j.jogo
            val pen = if (g.penaltisCasa >= 0) " (${g.penaltisCasa}-${g.penaltisFora} nos penáltis)" else if (g.prolongamento) " (após prolongamento)" else ""
            "${s.nome(g.casa)} ${g.golosCasa}-${g.golosFora} ${s.nome(g.fora)}$pen"
        }
        if (linhas.isNotEmpty()) {
            val corpo = linhas.take(6).joinToString(". ") + (if (linhas.size > 6) ". E mais ${linhas.size - 6} jogos." else ".")
            mensagem("Seleções: resultados", corpo, data)
            val manchete = listOf("Dia de seleções: ${linhas.first()}", "Seleção em ação: ${linhas.first()}", "Paragem para as seleções: ${linhas.first()}").random(gestor.aleatorio)
            geradorNoticias.adicionar(50, "selecao", manchete, corpo, false, data)
        }
        // Vencedores: a final acabou de se jogar.
        for (j in jogos.filter { it.fase == "Final" }) {
            val comp = s.competicao(j.competicaoId)?.takeIf { it.terminada } ?: continue
            val v = comp.vencedor ?: continue
            val texto = "${s.nome(v)} venceu a final (${s.nome(j.jogo.casa)} ${j.jogo.golosCasa}-${j.jogo.golosFora} ${s.nome(j.jogo.fora)})."
            mensagem("Seleções: ${comp.nome}", texto, j.jogo.dataLocal())
            val titulo = listOf("${s.nome(v)} conquista a ${comp.nome}", "${comp.nome}: ${s.nome(v)} é campeã", "${s.nome(v)} no topo: vence a ${comp.nome}").random(gestor.aleatorio)
            geradorNoticias.adicionar(if (v == equipa.pais) 80 else 62, "selecao", titulo, texto, false, j.jogo.dataLocal())
        }
        // Lesões dos nossos ao serviço da seleção.
        val lesoes = LinkedHashMap<Int, String>()
        for (j in jogos) {
            for (f in j.relatorio.casa.fichas + j.relatorio.fora.fichas) {
                if (f.lesao <= 0 || f.jogador.equipa != info.equipaId) continue
                if (!eTreinador && f.jogador.id != info.jogadorId) continue
                lesoes[f.jogador.id] = "${f.jogador.nome} (${f.lesao} ${if (f.lesao == 1) "jornada" else "jornadas"})"
            }
        }
        if (lesoes.isNotEmpty()) {
            mensagem(
                "Lesão ao serviço da seleção",
                (if (eTreinador) "Lesionados nos jogos das seleções: " else "Lesionaste-te nos jogos da seleção: ") + lesoes.values.take(5).joinToString() + ".",
                data,
            )
        }
    }

    private fun avisarConvocatoria(s: pt.futebolcarreira.nucleo.simulacao.EstadoSelecoes, c: pt.futebolcarreira.nucleo.simulacao.ConvocatoriaNova) {
        val meus = c.convocados.flatMap { (nacao, ids) -> ids.mapNotNull { id -> bd.jogadores[id]?.takeIf { it.equipa == info.equipaId }?.let { it to nacao } } }
        if (meus.isEmpty()) return
        if (!eTreinador) {
            val nacao = meus.firstOrNull { it.first.id == info.jogadorId }?.second ?: return
            mensagem("Convocatória", "Foste convocado para a seleção de ${s.nome(nacao)}!", c.data)
            geradorNoticias.adicionar(46, "selecao", "Convocado! ${jogador?.nome ?: info.nome} chamado à seleção", "O jogador do ${equipa.nome} foi chamado à seleção de ${s.nome(nacao)}.", true, c.data)
            return
        }
        val lista = meus.take(8).joinToString { (j, n) -> "${j.nome} (${s.nome(n)})" } + if (meus.size > 8) " e mais ${meus.size - 8}" else ""
        mensagem("Convocatórias", "Jogadores do teu clube chamados às seleções: $lista.", c.data)
        val titulo = listOf("${equipa.nome} cede ${meus.size} à seleção", "Convocatórias: ${meus.size} jogadores do ${equipa.nome} chamados", "Seleções chamam ${meus.size} do ${equipa.nome}").random(gestor.aleatorio)
        geradorNoticias.adicionar(44, "selecao", titulo, "Chamados: $lista.", true, c.data)
    }

    /** Avisa quando o mercado de transferências abre ou fecha entre dois dias. */
    private fun avisarMercado(antes: LocalDate, depois: LocalDate) {
        val abertoAntes = CalendarioDatas.mercadoAberto(antes)
        val abertoDepois = CalendarioDatas.mercadoAberto(depois)
        if (abertoAntes != abertoDepois) mercado.recusados.clear()
        if (abertoAntes && !abertoDepois) mensagem("Mercado fechado", "O mercado de transferências fechou.")
        if (!abertoAntes && abertoDepois) mensagem("Mercado aberto", "O mercado de transferências abriu e fica aberto até ao fim do mês.")
    }

    /** Golos, lesões e castigos do jogo do clube (e a nota do jogador do utilizador). */
    private fun linhasDoRelatorio(jogo: JogoDoClube): MutableList<String> {
        val linhas = mutableListOf<String>()
        jogo.derby?.let { linhas += "$it: jogo de grande intensidade." }
        val relatorio = jogo.resultado?.relatorio
        if (relatorio != null) {
            val lado = if (jogo.emCasa) relatorio.casa else relatorio.fora
            val golos = relatorio.eventos.filter { it.tipo == TipoEvento.GOLO && it.casa == jogo.emCasa }
            if (golos.isNotEmpty()) linhas += "Golos: " + golos.joinToString { "${it.jogador.nome} ${it.minuto}'" } + "."
            for (ficha in lado.fichas) {
                val j = ficha.jogador
                when {
                    ficha.lesao > 0 -> linhas += "${j.nome} lesionou-se e falha ${jogos(ficha.lesao)}."
                    ficha.vermelho -> linhas += "${j.nome} foi expulso e falha ${jogos(j.suspensao)}."
                    ficha.amarelos > 0 && j.suspensao > 0 -> linhas += "${j.nome} chegou aos ${j.epoca.amarelos} amarelos e falha ${jogos(j.suspensao)}."
                }
            }
            if (info.jogadorId != null) linhas += jogadorCarreira.linhasDoJogo(jogo)
        }
        return linhas
    }

    /** Resumo do jogo do clube: resultado, marcadores, lesões e castigos. */
    private fun mensagemDoJogo(jogo: JogoDoClube) {
        val marcador = "${jogo.golosMarcados}-${jogo.golosSofridos}"
        val titulo = when {
            jogo.golosMarcados > jogo.golosSofridos -> "Vitória por $marcador"
            jogo.golosMarcados < jogo.golosSofridos -> "Derrota por $marcador"
            else -> "Empate a ${jogo.golosMarcados}"
        } + (if (jogo.emCasa) " em casa contra " else " fora contra ") + jogo.adversario.nome
        val linhas = linhasDoRelatorio(jogo)
        linhas += "O teu clube está em ${posicao()}.º lugar com ${estadoLiga.tabela.linhas.getValue(info.equipaId).pts} pontos."
        mensagem(titulo, linhas.joinToString(" "))
    }

    /** Resumo de um jogo de taça ou supertaça: resultado, prolongamento e penáltis, e o que isso quer dizer para o clube. */
    private fun mensagemDaTaca(jogo: JogoDoClube) {
        val resultado = jogo.resultado ?: return
        val c = jogo.confronto
        val marcador = "${jogo.golosMarcados}-${jogo.golosSofridos}"
        val extra = (if (resultado.prolongamento) " a.p." else "") +
            (if (resultado.penaltis) " (g.p. ${if (jogo.emCasa) resultado.penaltisCasa else resultado.penaltisFora}-${if (jogo.emCasa) resultado.penaltisFora else resultado.penaltisCasa})" else "")
        val resumo = when {
            jogo.golosMarcados > jogo.golosSofridos -> "Vitória por $marcador"
            jogo.golosMarcados < jogo.golosSofridos -> "Derrota por $marcador"
            else -> "Empate a ${jogo.golosMarcados}"
        } + extra
        val onde = when {
            jogo.neutro -> " em campo neutro contra "
            jogo.emCasa -> " em casa contra "
            else -> " fora contra "
        }
        val titulo = "${jogo.competicaoNome} (${jogo.ronda}): $resumo$onde${jogo.adversario.nome}"
        val linhas = linhasDoRelatorio(jogo)
        val nome = jogo.competicaoNome
        val seguinte = if (jogo.tipo == "europa") FASES_ELIMINATORIAS.getOrNull(jogo.indiceRonda + 1)
        else gestor.eliminatorias[jogo.competicaoId]?.defs?.getOrNull(jogo.indiceRonda + 1)?.nome
        when {
            c == null -> Unit
            jogo.passou == true && jogo.eFinal -> linhas += "O teu clube vence a $nome!"
            jogo.passou == true -> linhas += "O teu clube passa à próxima eliminatória" + (seguinte?.let { " ($it)" } ?: "") + "."
            jogo.passou == false -> linhas += if (jogo.eFinal) "O teu clube perde a final e fica a um passo da glória." else "O teu clube está eliminado da competição."
            else -> linhas += "Primeira mão: $marcador. A segunda mão é a ${CalendarioDatas.textoCurto(c.datas.last())}."
        }
        if (c != null && c.idaEVolta && jogo.perna == 2) linhas += "Resultado agregado: ${c.golos(info.equipaId)}-${c.golos(jogo.adversario.id)}."
        mensagem(titulo, linhas.joinToString(" "))
    }

    /** Jogo da fase de liga europeia: resultado, lugar na tabela e, no último jogo, o destino do clube. */
    private fun mensagemDaFaseDeLiga(jogo: JogoDoClube) {
        val tipo = TipoEuropa.values().first { it.id == jogo.competicaoId }
        val estado = gestor.europa?.estado(tipo) ?: return
        val marcador = "${jogo.golosMarcados}-${jogo.golosSofridos}"
        val titulo = "${jogo.competicaoNome} (${jogo.ronda}): " + when {
            jogo.golosMarcados > jogo.golosSofridos -> "Vitória por $marcador"
            jogo.golosMarcados < jogo.golosSofridos -> "Derrota por $marcador"
            else -> "Empate a ${jogo.golosMarcados}"
        } + (if (jogo.emCasa) " em casa contra " else " fora contra ") + jogo.adversario.nome
        val linhas = linhasDoRelatorio(jogo)
        val tabela = estado.tabelaLiga()
        val lugar = tabela.indexOfFirst { it.id == info.equipaId } + 1
        linhas += "O teu clube está em $lugar.º lugar na fase de liga com ${tabela.first { it.id == info.equipaId }.pts} pontos."
        // Quando a fase de liga acaba (todos os jogos feitos), diz-se o destino.
        if (estado.jogosLiga.all { it.jogado }) {
            linhas += when {
                lugar <= 8 -> "Fase de liga terminada: apurado diretamente para os oitavos de final!"
                else -> "Fase de liga terminada: apurado para o play-off (${if (lugar <= 16) "cabeça de série" else "sem cabeça de série"})."
            }
        }
        mensagem(titulo, linhas.joinToString(" "))
    }

    /** Taças: vitórias contam um pouco, sair cedo contra um adversário bem mais fraco custa um pouco. */
    private fun avaliarJogoDeTaca(jogo: JogoDoClube) {
        if (!eTreinador) return
        if (jogo.tipo == "europa" && jogo.confronto == null) {
            // Fase de liga europeia: pequenos ajustes por resultado.
            val (d, a) = when {
                jogo.golosMarcados > jogo.golosSofridos -> 1 to 2
                jogo.golosMarcados < jogo.golosSofridos -> 0 to -1
                else -> 0 to 0
            }
            info.confiancaDirecao = (info.confiancaDirecao + d).coerceIn(0, 100)
            info.confiancaAdeptos = (info.confiancaAdeptos + a).coerceIn(0, 100)
            return
        }
        val passou = jogo.passou ?: return
        val diferenca = jogo.adversario.forca().geral - equipa.forca().geral
        val (direcao, adeptos) = when {
            jogo.tipo == "supertaca" -> if (passou) 2 to 3 else 0 to -1
            jogo.tipo == "europa" -> if (passou && jogo.eFinal) 6 to 8 else if (passou) 2 to 3 else if (jogo.eFinal) 1 to 0 else 0 to -1
            passou && jogo.eFinal -> 5 to 7
            passou -> 1 to 2
            diferenca <= -6 && !jogo.eFinal -> -3 to -4
            else -> 0 to -1
        }
        info.confiancaDirecao = (info.confiancaDirecao + escalarDerby(direcao, jogo.intensidadeDerby)).coerceIn(0, 100)
        info.confiancaAdeptos = (info.confiancaAdeptos + escalarDerby(adeptos, jogo.intensidadeDerby)).coerceIn(0, 100)
    }

    private fun mensagemFimDeEpoca() {
        val epoca = gestor.historico.last()
        val resumo = epoca.ligas[equipa.liga] ?: return
        val destino = when {
            info.equipaId == resumo.campeao && liga.nivel == 1 -> " O teu clube é campeão!"
            info.equipaId in resumo.sobem -> " O teu clube sobe de divisão!"
            info.equipaId in resumo.descem -> " O teu clube desce de divisão."
            else -> ""
        }
        // Taças do país: quem ganhou e como correu ao clube.
        val tacas = StringBuilder()
        for ((id, t) in epoca.tacas + epoca.supertacas) {
            val comp = bd.competicoes[id] ?: continue
            if (comp.pais != liga.pais) continue
            val vencedor = bd.equipas[t.vencedor]?.nome ?: continue
            tacas.append(if (t.vencedor == info.equipaId) " O teu clube venceu a ${comp.nome}!" else " ${comp.nome}: $vencedor.")
        }
        for ((id, t) in epoca.europa) {
            val tipo = TipoEuropa.values().firstOrNull { it.id == id } ?: continue
            val vencedor = bd.equipas[t.vencedor]?.nome ?: continue
            tacas.append(if (t.vencedor == info.equipaId) " O teu clube venceu a ${tipo.nome}!" else " ${tipo.nome}: $vencedor.")
        }
        val chaveTitulo = "titulo/${gestor.ano}/${liga.id}"
        if (liga.nivel == 1 && chaveTitulo !in geradorNoticias.marcas) {
            geradorNoticias.marcas += chaveTitulo
            val campeao = bd.equipas[resumo.campeao]?.nome ?: resumo.campeao
            geradorNoticias.adicionar(70, "titulo", "$campeao é campeão da ${liga.nome}", "Com ${resumo.pontos} pontos, $campeao termina a época no primeiro lugar.", resumo.campeao == info.equipaId)
        }
        mensagem(
            "Fim da época ${gestor.nomeEpoca()}",
            "${equipa.nome}: ${posicao()}.º lugar (${liga.nome}).$destino " +
                "Vencedor: ${bd.equipas[resumo.campeao]?.nome} (${resumo.pontos} pontos).$tacas",
        )
    }

    private fun jogos(n: Int) = if (n == 1) "1 jogo" else "$n jogos"

    companion object {
        const val MAX_MENSAGENS = 60
        /** As fases de eliminatórias europeias, por ordem. */
        val FASES_ELIMINATORIAS = listOf(FasesEuropa.PLAYOFF, FasesEuropa.OITAVOS, FasesEuropa.QUARTOS, FasesEuropa.MEIAS, FasesEuropa.FINAL)
        /** Abaixo desta confiança, a direção despede a meio da época / no fim da época. */
        const val LIMITE_DESPEDIMENTO = 10
        const val LIMITE_FIM_DE_EPOCA = 25

        /**
         * Começa uma carreira nova num clube.
         * Na via de jogador cria-se um jovem de 17 anos com o nome do utilizador, na posição escolhida.
         */
        fun nova(
            bd: BaseDados, id: String, nome: String, modo: ModoCarreira, equipaId: String,
            posicao: String = "PL", semente: Long? = null,
            nacionalidade: String = "", pe: String = "",
        ): Carreira {
            val gestor = GestorEpocas(bd, semente)
            val info = InfoCarreira(id, nome.trim().ifEmpty { "Sem Nome" }, modo, equipaId)
            val carreira = Carreira(bd, gestor, info)
            val equipa = carreira.equipa
            carreira.definirObjetivo()
            carreira.academia.garantirJovens()
            if (modo == ModoCarreira.JOGADOR) {
                // Um jovem promissor: ainda abaixo dos titulares, mas com muito para crescer.
                val nivel = equipa.melhorOnze().map { it.nota }.average() - 8.0
                val jovem = gestor.gerador.criar(equipa.pais, posicao, nivel, 17)
                jovem.nome = info.nome
                jovem.nacionalidade = nacionalidade.ifEmpty { equipa.pais }
                if (pe in listOf("direito", "esquerdo", "ambos")) jovem.pe = pe
                jovem.potencial = (jovem.geral() + 22).coerceIn(86, 95)
                jovem.equipa = equipa.id
                jovem.contratoAte = gestor.ano + 3
                jovem.salario = jovem.salarioJusto()
                bd.adicionarJogador(jovem)
                info.jogadorId = jovem.id
                val estado = carreira.jogadorCarreira.estado
                estado.estatuto = carreira.jogadorCarreira.calcularEstatuto(jovem, equipa)
                estado.atributosVistos += jovem.atributos
                carreira.mensagem(
                    "Bem-vindo: ${equipa.nome}",
                    "Assinaste o teu primeiro contrato (até ${jovem.contratoAte}). Estatuto no plantel: ${estado.estatuto!!.nome.lowercase()}. Trabalha para ganhar um lugar na equipa!",
                )
            } else {
                carreira.mensagem("Bem-vindo: ${equipa.nome}", "A direção apresentou-te como novo treinador. Objetivo para ${gestor.nomeEpoca()}: ${info.objetivoTexto.lowercase()}.")
            }
            return carreira
        }
    }
}
