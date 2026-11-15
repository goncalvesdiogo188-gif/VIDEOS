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
        if (gestor.epocaTerminada()) return null
        val alvo = proximoJogo()
        if (alvo == null) {
            gestor.simularAteAoFim()
        } else {
            val antes = gestor.data
            gestor.simularAte(alvo.data)
            avisarMercado(antes, gestor.data)
        }
        val jogado = alvo?.let { a -> jogosDoClube().first { it.jornada == a.jornada } }
        if (jogado != null) mensagemDoJogo(jogado)
        if (gestor.epocaTerminada()) mensagemFimDeEpoca()
        return jogado
    }

    /** Simula tudo o que falta desta época (modo "férias"). */
    fun simularEpoca() {
        while (!gestor.epocaTerminada()) avancar()
    }

    /** Começa a época seguinte (só depois de esta acabar). */
    fun novaEpoca() {
        if (!gestor.epocaTerminada()) return
        gestor.avancarParaEpocaSeguinte()
        baseDadosPorGravar = true
        // O jogador do utilizador pode ter chegado ao fim da carreira: passa a treinador do clube.
        if (info.modo == ModoCarreira.JOGADOR && jogador == null) {
            info.modo = ModoCarreira.TREINADOR
            info.jogadorId = null
            mensagem("Fim da carreira de jogador", "Penduraste as botas. O ${equipa.nome} convidou-te para treinador: começa uma nova vida no banco.")
        }
        mensagem(
            "Começa a época ${gestor.nomeEpoca()}",
            "O ${equipa.nome} joga na ${liga.nome}. O primeiro jogo é a ${proximoJogo()?.let { CalendarioDatas.textoCurto(it.data) } ?: "?"}.",
        )
    }

    private fun mensagem(titulo: String, texto: String) {
        mensagens.add(0, Mensagem(gestor.data.toString(), titulo, texto))
        while (mensagens.size > MAX_MENSAGENS) mensagens.removeAt(mensagens.lastIndex)
    }

    /** Avisa quando o mercado de transferências abre ou fecha entre dois dias. */
    private fun avisarMercado(antes: LocalDate, depois: LocalDate) {
        val abertoAntes = CalendarioDatas.mercadoAberto(antes)
        val abertoDepois = CalendarioDatas.mercadoAberto(depois)
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
        } + (if (jogo.emCasa) " em casa com o " else " fora com o ") + jogo.adversario.nome
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
        linhas += "O ${equipa.nome} está em ${posicao()}.º lugar com ${estadoLiga.tabela.linhas.getValue(info.equipaId).pts} pontos."
        mensagem(titulo, linhas.joinToString(" "))
    }

    private fun mensagemFimDeEpoca() {
        val resumo = gestor.historico.last().ligas[equipa.liga] ?: return
        val destino = when {
            info.equipaId == resumo.campeao && liga.nivel == 1 -> " O ${equipa.nome} é campeão!"
            info.equipaId in resumo.sobem -> " O ${equipa.nome} sobe de divisão!"
            info.equipaId in resumo.descem -> " O ${equipa.nome} desce de divisão."
            else -> ""
        }
        mensagem(
            "Fim da época ${gestor.nomeEpoca()}",
            "O ${equipa.nome} terminou a ${liga.nome} em ${posicao()}.º lugar.$destino " +
                "Vencedor da liga: ${bd.equipas[resumo.campeao]?.nome} (${resumo.pontos} pontos).",
        )
    }

    private fun jogos(n: Int) = if (n == 1) "1 jogo" else "$n jogos"

    companion object {
        const val MAX_MENSAGENS = 60

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
                carreira.mensagem("Bem-vindo ao ${equipa.nome}", "Assinaste o teu primeiro contrato. Trabalha para ganhar um lugar na equipa!")
            } else {
                carreira.mensagem("Bem-vindo ao ${equipa.nome}", "A direção apresentou-te como novo treinador. A época ${gestor.nomeEpoca()} está a começar.")
            }
            return carreira
        }
    }
}
