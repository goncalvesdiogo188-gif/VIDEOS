package pt.futebolcarreira.nucleo.carreira

import kotlinx.serialization.Serializable
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.Competicao
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.simulacao.CalendarioDatas
import pt.futebolcarreira.nucleo.simulacao.EstadoLiga
import pt.futebolcarreira.nucleo.simulacao.GestorEpocas
import pt.futebolcarreira.nucleo.simulacao.Resultado
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
)

/** Uma mensagem na caixa de entrada (resultados, lesões, avisos...). */
@Serializable
class Mensagem(val data: String, val titulo: String, val texto: String)

/** Um jogo do clube do utilizador no calendário. "resultado" é null se ainda não se jogou. */
class JogoDoClube(
    val jornada: Int,
    val indice: Int,
    val data: LocalDate,
    val adversario: Equipa,
    val emCasa: Boolean,
    val resultado: Resultado?,
) {
    /** Golos do clube do utilizador e do adversário (só depois de jogado). */
    val golosMarcados get() = resultado?.let { if (emCasa) it.golosCasa else it.golosFora } ?: 0
    val golosSofridos get() = resultado?.let { if (emCasa) it.golosFora else it.golosCasa } ?: 0
}

/**
 * Uma carreira: o mundo do jogo (base de dados + épocas) visto do lado do utilizador,
 * que é treinador de um clube ou jogador de um clube.
 */
class Carreira(val bd: BaseDados, val gestor: GestorEpocas, val info: InfoCarreira) {
    /** Caixa de entrada: a mensagem mais recente fica em primeiro lugar. */
    val mensagens = mutableListOf<Mensagem>()

    /**
     * true quando a base de dados (equipas, jogadores) mudou desde a última gravação.
     * Acontece ao criar a carreira e no início de cada época.
     */
    var baseDadosPorGravar = true

    /** Transferências, empréstimos e obras. */
    val mercado = Mercado(this)

    init {
        gestor.clubeDoUtilizador = info.equipaId
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

    /** Todos os jogos do clube nesta época, por ordem. */
    fun jogosDoClube(): List<JogoDoClube> {
        val e = estadoLiga
        return e.calendario.mapIndexedNotNull { jornada, jogos ->
            val indice = jogos.indexOfFirst { it.casa == info.equipaId || it.fora == info.equipaId }
            if (indice < 0) return@mapIndexedNotNull null
            val jogo = jogos[indice]
            val emCasa = jogo.casa == info.equipaId
            JogoDoClube(
                jornada + 1, indice, e.datas[jornada],
                bd.equipas.getValue(if (emCasa) jogo.fora else jogo.casa), emCasa,
                e.resultados.getOrNull(jornada)?.getOrNull(indice),
            )
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
        val alvo = proximoJogo()
        if (alvo == null) {
            gestor.simularAteAoFim()
        } else {
            val antes = gestor.data
            // Enquanto o mercado está aberto, os outros clubes também compram e vendem.
            val negocios = mercado.negociosDosOutros(6)
            if (negocios.isNotEmpty()) mensagem("Mercado: negócios dos outros clubes", negocios.take(4).joinToString(" "))
            gestor.simularAte(alvo.data)
            avisarMercado(antes, gestor.data)
            // Caixa negativa preocupa a direção.
            if (equipa.orcamento < 0 && eTreinador) info.confiancaDirecao = (info.confiancaDirecao - 1).coerceAtLeast(0)
        }
        val jogado = alvo?.let { a -> jogosDoClube().first { it.jornada == a.jornada } }
        if (jogado != null) {
            mensagemDoJogo(jogado)
            avaliarJogo(jogado)
        }
        if (gestor.epocaTerminada()) {
            mensagemFimDeEpoca()
            avaliarEpoca()
        }
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
        gestor.avancarParaEpocaSeguinte()
        mercado.recusados.clear()
        if (gestor.avisos.isNotEmpty()) mensagem("Contratos e empréstimos", gestor.avisos.joinToString(" "))
        // Propostas que ficaram por aceitar perdem-se; a confiança volta a aproximar-se do normal.
        info.propostas.clear()
        info.confiancaDirecao = (info.confiancaDirecao + 60) / 2
        info.confiancaAdeptos = (info.confiancaAdeptos + 60) / 2
        definirObjetivo()
        baseDadosPorGravar = true
        // O jogador do utilizador pode ter chegado ao fim da carreira: passa a treinador do clube.
        if (info.modo == ModoCarreira.JOGADOR && jogador == null) {
            info.modo = ModoCarreira.TREINADOR
            info.jogadorId = null
            mensagem("Fim da carreira de jogador", "Penduraste as botas. O clube convidou-te para treinador: começa uma nova vida no banco.")
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

    /** Depois de cada jogo, a direção e os adeptos ficam mais (ou menos) contentes com o treinador. */
    private fun avaliarJogo(jogo: JogoDoClube) {
        if (!eTreinador) return
        // Diferença de qualidade: positiva se o adversário é mais forte.
        val diferenca = jogo.adversario.forca().geral - equipa.forca().geral
        val saldo = jogo.golosMarcados - jogo.golosSofridos
        var direcao = when {
            saldo > 0 -> if (diferenca >= 3) 4 else 3
            saldo == 0 -> if (diferenca >= 4) 1 else -1
            else -> if (diferenca >= 6) -2 else if (diferenca <= -3) -4 else -3
        }
        val adeptos = when {
            saldo > 0 -> 4
            saldo == 0 -> -1
            else -> if (saldo <= -3) -7 else -5
        }
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
                .shuffled().take(2)
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
            .shuffled().take(3)
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
        mensagem("Novo clube: ${equipa.nome}", "Assinaste contrato. Objetivo da direção: ${info.objetivoTexto.lowercase()}.")
    }

    /** O treinador recusa as propostas (só se não estiver despedido). */
    fun recusarPropostas() {
        if (!info.despedido) info.propostas.clear()
    }

    internal fun mensagem(titulo: String, texto: String) {
        mensagens.add(0, Mensagem(gestor.data.toString(), titulo, texto))
        while (mensagens.size > MAX_MENSAGENS) mensagens.removeAt(mensagens.lastIndex)
    }

    /** Avisa quando o mercado de transferências abre ou fecha entre dois dias. */
    private fun avisarMercado(antes: LocalDate, depois: LocalDate) {
        val abertoAntes = CalendarioDatas.mercadoAberto(antes)
        val abertoDepois = CalendarioDatas.mercadoAberto(depois)
        if (abertoAntes != abertoDepois) mercado.recusados.clear()
        if (abertoAntes && !abertoDepois) mensagem("Mercado fechado", "O mercado de transferências fechou.")
        if (!abertoAntes && abertoDepois) mensagem("Mercado aberto", "O mercado de transferências abriu e fica aberto até ao fim do mês.")
    }

    /** Resumo do jogo do clube: resultado, marcadores, lesões e castigos. */
    private fun mensagemDoJogo(jogo: JogoDoClube) {
        val marcador = "${jogo.golosMarcados}-${jogo.golosSofridos}"
        val titulo = when {
            jogo.golosMarcados > jogo.golosSofridos -> "Vitória por $marcador"
            jogo.golosMarcados < jogo.golosSofridos -> "Derrota por $marcador"
            else -> "Empate a ${jogo.golosMarcados}"
        } + (if (jogo.emCasa) " em casa contra " else " fora contra ") + jogo.adversario.nome
        val linhas = mutableListOf<String>()
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
                if (j.id == info.jogadorId) linhas += "A tua nota: ${"%.1f".format(ficha.nota)}" + (if (ficha.golos > 0) " (${ficha.golos} golo(s))" else "") + "."
            }
            if (info.jogadorId != null && lado.fichas.none { it.jogador.id == info.jogadorId }) linhas += "Não saíste do banco."
        }
        linhas += "O teu clube está em ${posicao()}.º lugar com ${estadoLiga.tabela.linhas.getValue(info.equipaId).pts} pontos."
        mensagem(titulo, linhas.joinToString(" "))
    }

    private fun mensagemFimDeEpoca() {
        val resumo = gestor.historico.last().ligas[equipa.liga] ?: return
        val destino = when {
            info.equipaId == resumo.campeao && liga.nivel == 1 -> " O teu clube é campeão!"
            info.equipaId in resumo.sobem -> " O teu clube sobe de divisão!"
            info.equipaId in resumo.descem -> " O teu clube desce de divisão."
            else -> ""
        }
        mensagem(
            "Fim da época ${gestor.nomeEpoca()}",
            "${equipa.nome}: ${posicao()}.º lugar (${liga.nome}).$destino " +
                "Vencedor: ${bd.equipas[resumo.campeao]?.nome} (${resumo.pontos} pontos).",
        )
    }

    private fun jogos(n: Int) = if (n == 1) "1 jogo" else "$n jogos"

    companion object {
        const val MAX_MENSAGENS = 60
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
        ): Carreira {
            val gestor = GestorEpocas(bd, semente)
            val info = InfoCarreira(id, nome.trim().ifEmpty { "Sem Nome" }, modo, equipaId)
            val carreira = Carreira(bd, gestor, info)
            val equipa = carreira.equipa
            carreira.definirObjetivo()
            if (modo == ModoCarreira.JOGADOR) {
                // Um jovem promissor: ainda abaixo dos titulares, mas com muito para crescer.
                val nivel = equipa.melhorOnze().map { it.nota }.average() - 8.0
                val jovem = gestor.gerador.criar(equipa.pais, posicao, nivel, 17)
                jovem.nome = info.nome
                jovem.nacionalidade = equipa.pais
                jovem.potencial = (jovem.geral() + 22).coerceIn(86, 95)
                jovem.equipa = equipa.id
                bd.adicionarJogador(jovem)
                info.jogadorId = jovem.id
                carreira.mensagem("Bem-vindo: ${equipa.nome}", "Assinaste o teu primeiro contrato. Trabalha para ganhar um lugar na equipa!")
            } else {
                carreira.mensagem("Bem-vindo: ${equipa.nome}", "A direção apresentou-te como novo treinador. Objetivo para ${gestor.nomeEpoca()}: ${info.objetivoTexto.lowercase()}.")
            }
            return carreira
        }
    }
}
