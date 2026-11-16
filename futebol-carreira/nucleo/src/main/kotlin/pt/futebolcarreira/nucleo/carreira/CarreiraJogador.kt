package pt.futebolcarreira.nucleo.carreira

import kotlinx.serialization.Serializable
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.FocoTreino
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.simulacao.CalendarioDatas
import pt.futebolcarreira.nucleo.simulacao.FichaJogador
import pt.futebolcarreira.nucleo.simulacao.JogoJogado
import pt.futebolcarreira.nucleo.simulacao.Premiado
import pt.futebolcarreira.nucleo.simulacao.ResumoEpoca
import pt.futebolcarreira.nucleo.simulacao.TipoEuropa
import java.time.LocalDate

// ---------------------------------------------------------------------------------------------
// Fase 9: a carreira do jogador. Tudo o que só existe na via de jogador está aqui (estado gravado,
// estatuto no clube, pedidos do treinador, propostas, renovações, prémios e fim de carreira).
// ---------------------------------------------------------------------------------------------

/** O lugar do jogador no plantel, conforme a posição dele em relação aos colegas da mesma posição. */
enum class EstatutoNoClube(val nome: String) {
    INDISCUTIVEL("Titular indiscutível"),
    TITULAR("Titular"),
    ROTACAO("Rotação"),
    SUPLENTE("Suplente"),
    POUCO_UTILIZADO("Pouco utilizado"),
}

/** O que o treinador pode pedir ao jogador. */
enum class TipoPedido { GOLOS, ASSISTENCIAS, BALIZA_INVIOLADA, NOTA_MEDIA, FISICO }

/**
 * Um pedido do treinador: uma meta para os próximos jogos (ou até uma data, no físico).
 * "valor" é o que já foi conseguido (golos, assistências, jogos sem sofrer, soma das notas).
 */
@Serializable
class PedidoTreinador(
    val tipo: TipoPedido,
    val meta: Double,
    /** Número de jogos do clube em que a meta conta (0 no pedido de físico, que tem prazo). */
    val jogos: Int,
    val texto: String,
    var jogosFeitos: Int = 0,
    var valor: Double = 0.0,
    /** Jogos em que o jogador participou (para a nota média). */
    var aparicoes: Int = 0,
    val fisicoInicial: Int = 0,
    /** Só no físico: dia limite (aaaa-mm-dd). */
    val prazo: String = "",
)

/** Uma proposta de outro clube: transferência (paga-se ao clube atual) ou empréstimo. */
@Serializable
class OfertaJogador(
    val id: Int,
    val clubeId: String,
    /** "transferencia" ou "emprestimo". */
    val tipo: String,
    val salario: Int,
    /** Épocas de contrato (1 no empréstimo). */
    val anos: Int,
    val preco: Long,
    /** O lugar que o jogador teria no plantel do clube novo. */
    val estatuto: EstatutoNoClube,
) {
    val emprestimo get() = tipo == "emprestimo"
}

/** Proposta do clube atual para renovar o contrato. */
@Serializable
class OfertaRenovacao(val salario: Int, val anos: Int)

/** O que o jogador fez numa época (num clube). */
@Serializable
class TemporadaJogador(
    val ano: Int,
    val epoca: String,
    val clubeId: String,
    val clube: String,
    val jogos: Int,
    val golos: Int,
    val assistencias: Int,
    val nota: Double,
    val minutos: Int,
    val geral: Int,
    val idade: Int,
)

/** Um prémio ou um troféu conquistado numa época. */
@Serializable
class Conquista(val ano: Int, val epoca: String, val titulo: String, val detalhe: String = "")

/** Tudo o que se grava sobre a carreira do jogador (vai dentro de [InfoCarreira]; é opcional, para as gravações antigas). */
@Serializable
class EstadoJogador(
    /** Atributos que o jogador treina mais (só dele). */
    var foco: FocoTreino = FocoTreino.EQUILIBRADO,
    var estatuto: EstatutoNoClube? = null,
    var minutosEpoca: Int = 0,
    var minutosCarreira: Int = 0,
    /** Já registado nesta época noutro clube (para a época se dividir quando se muda de clube a meio). */
    var parcialJogos: Int = 0,
    var parcialGolos: Int = 0,
    var parcialAssistencias: Int = 0,
    var parcialNotas: Double = 0.0,
    val temporadas: MutableList<TemporadaJogador> = mutableListOf(),
    val premios: MutableList<Conquista> = mutableListOf(),
    val trofeus: MutableList<Conquista> = mutableListOf(),
    var pedido: PedidoTreinador? = null,
    var pedidosCumpridos: Int = 0,
    var pedidosFalhados: Int = 0,
    /** Jogos do clube desde o último pedido do treinador / desde a última conversa para pedir minutos. */
    var jogosDesdePedido: Int = 0,
    var jogosDesdeConversa: Int = 99,
    /** Notas dos últimos jogos em que participou (as mais recentes no fim). */
    val ultimasNotas: MutableList<Double> = mutableListOf(),
    /** Pontos de ajuda na escolha do onze e durante quantos jogos (negativo = o treinador desconfia). */
    var ajuda: Double = 0.0,
    var ajudaJogos: Int = 0,
    val ofertas: MutableList<OfertaJogador> = mutableListOf(),
    var proximaOferta: Int = 1,
    /** Janela de mercado para a qual já se criaram propostas ("2026-verao"). */
    var janelaOfertas: String = "",
    var renovacao: OfertaRenovacao? = null,
    /** "", "transferencia" ou "emprestimo": o que o jogador pediu para a próxima janela. */
    var pedidoSaida: String = "",
    var reformado: Boolean = false,
    /** Atributos no último treino mensal (para dizer o que mudou). */
    val atributosVistos: MutableMap<String, Int> = mutableMapOf(),
    /** Época (ano) cujo fecho já foi tratado. */
    var epocaFechada: Int = 0,
)

/** Jogos, golos e assistências de uma carreira. */
class TotaisCarreira(val jogos: Int, val golos: Int, val assistencias: Int, val minutos: Int)

/**
 * A carreira do jogador, vista do [Carreira]: estatuto, minutos, pedidos do treinador, treino individual,
 * propostas e renovações, prémios e fim de carreira.
 */
class CarreiraJogador(private val c: Carreira) {
    private val bd get() = c.bd
    private val rng get() = c.gestor.aleatorio

    /** O estado gravado (criado quando falta, por exemplo numa gravação antiga). */
    val estado: EstadoJogador
        get() = c.info.estadoJogador ?: EstadoJogador().also { c.info.estadoJogador = it }

    /** true se é a via de jogador e o jogador ainda joga. */
    val ativo: Boolean get() = c.info.modo == ModoCarreira.JOGADOR && c.jogador != null

    // ------------------------------------------------------------------ estatuto

    /**
     * O lugar do jogador no plantel de um clube (o dele, por defeito): conta quantos colegas da mesma posição
     * são melhores do que ele e quantos lugares há nessa posição na formação.
     */
    fun calcularEstatuto(j: Jogador, equipa: Equipa = c.equipa): EstatutoNoClube {
        val colegas = equipa.plantel.filter { it.posicao == j.posicao && it.id != j.id }.map { it.geral() }.sortedDescending()
        val lugares = maxOf(1, equipa.formacao.lugares.count { it == j.posicao })
        val minha = j.geral()
        val acima = colegas.count { it > minha }
        if (acima < lugares) {
            val melhorSuplente = colegas.getOrNull(lugares - 1) ?: (minha - 10)
            return if (minha - melhorSuplente >= 5) EstatutoNoClube.INDISCUTIVEL else EstatutoNoClube.TITULAR
        }
        val maisFracoTitular = colegas.getOrNull(lugares - 1) ?: minha
        val falta = maisFracoTitular - minha
        return when {
            falta <= 2 -> EstatutoNoClube.ROTACAO
            falta <= 6 -> EstatutoNoClube.SUPLENTE
            else -> EstatutoNoClube.POUCO_UTILIZADO
        }
    }

    /** O estatuto atual (calcula-o se ainda não está guardado). */
    fun estatuto(): EstatutoNoClube {
        val j = c.jogador ?: return EstatutoNoClube.POUCO_UTILIZADO
        return estado.estatuto ?: calcularEstatuto(j).also { estado.estatuto = it }
    }

    /** Totais de carreira: épocas fechadas mais o que se fez nesta (ainda por fechar). */
    fun totais(): TotaisCarreira {
        val e = estado
        val j = c.jogador
        var jogos = e.temporadas.sumOf { it.jogos }
        var golos = e.temporadas.sumOf { it.golos }
        var assist = e.temporadas.sumOf { it.assistencias }
        if (j != null) {
            jogos += j.epoca.jogos + j.epocaTaca.jogos - e.parcialJogos
            golos += j.epoca.golos + j.epocaTaca.golos - e.parcialGolos
            assist += j.epoca.assistencias + j.epocaTaca.assistencias - e.parcialAssistencias
        }
        return TotaisCarreira(jogos, golos, assist, e.minutosCarreira)
    }

    /** Números da época em curso no clube atual: jogos, golos, assistências, nota média e minutos. */
    fun epocaAtual(): TemporadaJogador? {
        val j = c.jogador ?: return null
        val e = estado
        val jogos = j.epoca.jogos + j.epocaTaca.jogos - e.parcialJogos
        val notas = j.epoca.somaNotas + j.epocaTaca.somaNotas - e.parcialNotas
        return TemporadaJogador(
            c.gestor.ano, c.gestor.nomeEpoca(), c.equipa.id, c.equipa.nome, jogos,
            j.epoca.golos + j.epocaTaca.golos - e.parcialGolos, j.epoca.assistencias + j.epocaTaca.assistencias - e.parcialAssistencias,
            if (jogos == 0) 0.0 else notas / jogos, e.minutosEpoca, j.geral(), j.idade,
        )
    }

    // ------------------------------------------------------------------ antes e depois dos jogos

    /** Prepara o jogo seguinte: o foco de treino do jogador e a ajuda dada na escolha do onze. */
    fun prepararJogo() {
        val j = c.jogador ?: return
        if (c.info.modo != ModoCarreira.JOGADOR) return
        val e = estado
        c.gestor.focosIndividuais.remove(j.id)
        if (e.foco != FocoTreino.EQUILIBRADO) c.gestor.focosIndividuais[j.id] = e.foco
        c.equipa.ajudaNaEscolha.remove(j.id)
        if (e.ajudaJogos > 0 && e.ajuda != 0.0) c.equipa.ajudaNaEscolha[j.id] = e.ajuda
    }

    /** A ficha do jogador num jogo do clube e os minutos que jogou (ficha null = não jogou). */
    fun fichaEMinutos(jogo: JogoDoClube): Pair<FichaJogador?, Int> {
        val id = c.info.jogadorId ?: return null to 0
        val relatorio = jogo.resultado?.relatorio ?: return null to 0
        val lado = if (jogo.emCasa) relatorio.casa else relatorio.fora
        val ficha = lado.fichas.firstOrNull { it.jogador.id == id } ?: return null to 0
        val fim = if (ficha.saiu > 0) ficha.saiu else relatorio.duracao
        return ficha to (fim - ficha.entrou).coerceAtLeast(0)
    }

    /** Linhas sobre o jogador na mensagem do jogo: nota, golos e minutos (ou porque não jogou). */
    fun linhasDoJogo(jogo: JogoDoClube): List<String> {
        val id = c.info.jogadorId ?: return emptyList()
        val relatorio = jogo.resultado?.relatorio ?: return emptyList()
        val lado = if (jogo.emCasa) relatorio.casa else relatorio.fora
        val (ficha, minutos) = fichaEMinutos(jogo)
        if (ficha == null) {
            return listOf(if (lado.indisponiveis.any { it.id == id }) "Estavas indisponível para este jogo." else "Não saíste do banco.")
        }
        val como = if (ficha.titular) "como titular" else "entrando aos ${ficha.entrou}'"
        val extra = (if (ficha.golos > 0) " (${ficha.golos} golo(s))" else "") + (if (ficha.assistencias > 0) " (${ficha.assistencias} assistência(s))" else "")
        return listOf("A tua nota: ${"%.1f".format(ficha.nota)}$extra. Jogaste $minutos minutos $como.")
    }

    /** Depois de um jogo do clube (de qualquer competição): minutos, pedido do treinador, estatuto e treino. */
    fun aposJogo(jogo: JogoDoClube) {
        val j = c.jogador ?: return
        if (c.info.modo != ModoCarreira.JOGADOR || jogo.resultado == null) return
        val e = estado
        val (ficha, minutos) = fichaEMinutos(jogo)
        if (ficha != null) {
            e.minutosEpoca += minutos
            e.minutosCarreira += minutos
            e.ultimasNotas += ficha.nota
            while (e.ultimasNotas.size > 8) e.ultimasNotas.removeAt(0)
        }
        e.jogosDesdePedido++
        e.jogosDesdeConversa++
        if (e.ajudaJogos > 0) {
            e.ajudaJogos--
            if (e.ajudaJogos == 0) e.ajuda = 0.0
        }
        atualizarPedido(jogo, ficha, minutos, j)
        if (e.pedido == null) oferecerPedido(jogo, j)
        verificarEstatuto(j)
    }

    /** Se o estatuto mudou (por exemplo depois de um treino ou de uma lesão de um colega), avisa. */
    private fun verificarEstatuto(j: Jogador) {
        val e = estado
        val novo = calcularEstatuto(j)
        val antigo = e.estatuto
        e.estatuto = novo
        if (antigo != null && antigo != novo) {
            val melhorou = novo.ordinal < antigo.ordinal
            c.mensagem(
                "Estatuto no clube: ${novo.nome}",
                "Passaste de «${antigo.nome}» a «${novo.nome}» no ${c.equipa.nome}." + if (melhorou) " O treinador conta cada vez mais contigo." else " Tens de trabalhar para voltares a ganhar o lugar.",
            )
        }
    }

    /** Depois de cada avanço: diz o que mudou nos atributos (o treino é mensal). */
    fun aposTreino() {
        val j = c.jogador ?: return
        if (c.info.modo != ModoCarreira.JOGADOR) return
        val e = estado
        val agora = j.atributos.toMap()
        if (e.atributosVistos.isNotEmpty() && agora != e.atributosVistos) {
            val mudancas = Jogador.ATRIBUTOS.mapNotNull { a ->
                val antes = e.atributosVistos[a] ?: return@mapNotNull null
                val depois = agora[a] ?: return@mapNotNull null
                if (antes == depois) null else "${NOMES_ATRIBUTOS.getValue(a)} ${if (depois > antes) "+" else ""}${depois - antes}"
            }
            if (mudancas.isNotEmpty()) {
                val foco = if (e.foco == FocoTreino.EQUILIBRADO) "" else " Foco de treino: ${e.foco.nome.lowercase()}."
                c.mensagem("Treino: a tua evolução", mudancas.joinToString() + ". Geral agora: ${j.geral()}.$foco")
            }
        }
        e.atributosVistos.clear()
        e.atributosVistos += agora
    }

    // ------------------------------------------------------------------ pedidos do treinador

    private fun oferecerPedido(jogo: JogoDoClube, j: Jogador) {
        val e = estado
        val liga = c.estadoLiga
        if (e.jogosDesdePedido < 6 || !jogo.eLiga || liga.jornada < 4 || liga.jornada > liga.totalJornadas - 9) return
        if (rng.nextDouble() >= 0.2) return
        val pedido = novoPedido(j)
        e.pedido = pedido
        c.mensagem(
            "Pedido do treinador",
            "${pedido.texto} Se cumprires, ${c.equipa.nome} reconhece o teu esforço (mais confiança e moral); se falhares, o treinador desconfia de ti durante uns jogos.",
        )
    }

    /** Cria um pedido que faça sentido para a posição e para o momento do jogador. */
    fun novoPedido(j: Jogador): PedidoTreinador {
        val tipos = mutableListOf<TipoPedido>()
        if (j.posicao in listOf("PL", "ED", "EE", "MOC")) tipos += TipoPedido.GOLOS
        if (j.posicao in listOf("MC", "MOC", "ED", "EE", "DD", "DE")) tipos += TipoPedido.ASSISTENCIAS
        if (j.posicao in listOf("GR", "DC", "DD", "DE", "MDC")) tipos += TipoPedido.BALIZA_INVIOLADA
        tipos += TipoPedido.NOTA_MEDIA
        if (j.idade <= 24 && j.geral() < j.potencial && j.atributo("fisico") < 90) tipos += TipoPedido.FISICO
        return when (tipos.random(rng)) {
            TipoPedido.GOLOS -> {
                val meta = if (j.posicao == "PL") 5 else 3
                PedidoTreinador(TipoPedido.GOLOS, meta.toDouble(), 8, "Marca $meta golos nos próximos 8 jogos do clube.")
            }
            TipoPedido.ASSISTENCIAS -> {
                val meta = if (j.posicao in listOf("DD", "DE")) 2 else 3
                PedidoTreinador(TipoPedido.ASSISTENCIAS, meta.toDouble(), 8, "Faz $meta assistências nos próximos 8 jogos do clube.")
            }
            TipoPedido.BALIZA_INVIOLADA -> PedidoTreinador(TipoPedido.BALIZA_INVIOLADA, 3.0, 8, "Joga em 3 jogos em que a equipa não sofra golos, nos próximos 8 jogos do clube.")
            TipoPedido.NOTA_MEDIA -> PedidoTreinador(TipoPedido.NOTA_MEDIA, 7.0, 6, "Mantém uma nota média acima de 7.0 nos próximos 6 jogos do clube (tens de jogar em pelo menos 3).")
            TipoPedido.FISICO -> {
                val meta = if (j.atributo("fisico") >= 80) 1 else 2
                val prazo = c.gestor.data.plusDays(120)
                PedidoTreinador(
                    TipoPedido.FISICO, meta.toDouble(), 0, "Melhora o físico em $meta ${if (meta == 1) "ponto" else "pontos"} até ${CalendarioDatas.textoCurto(prazo)}. Treina o físico para o conseguires.",
                    fisicoInicial = j.atributo("fisico"), prazo = prazo.toString(),
                )
            }
        }
    }

    /** Uma linha curta sobre como vai o pedido ("2 de 5 golos · faltam 5 jogos"). */
    fun progressoDoPedido(p: PedidoTreinador): String {
        val j = c.jogador
        val faltam = (p.jogos - p.jogosFeitos).coerceAtLeast(0)
        return when (p.tipo) {
            TipoPedido.GOLOS -> "${p.valor.toInt()} de ${p.meta.toInt()} golos · faltam $faltam jogos"
            TipoPedido.ASSISTENCIAS -> "${p.valor.toInt()} de ${p.meta.toInt()} assistências · faltam $faltam jogos"
            TipoPedido.BALIZA_INVIOLADA -> "${p.valor.toInt()} de ${p.meta.toInt()} jogos sem sofrer golos · faltam $faltam jogos"
            TipoPedido.NOTA_MEDIA -> (if (p.aparicoes == 0) "ainda não jogaste" else "nota média %.1f em ${p.aparicoes} jogos".format(p.valor / p.aparicoes)) + " · faltam $faltam jogos"
            TipoPedido.FISICO -> "físico ${j?.atributo("fisico") ?: 0} (partiste de ${p.fisicoInicial}) · até ${CalendarioDatas.textoCurto(LocalDate.parse(p.prazo))}"
        }
    }

    private fun atualizarPedido(jogo: JogoDoClube, ficha: FichaJogador?, minutos: Int, j: Jogador) {
        val p = estado.pedido ?: return
        p.jogosFeitos++
        when (p.tipo) {
            TipoPedido.GOLOS -> p.valor += ficha?.golos ?: 0
            TipoPedido.ASSISTENCIAS -> p.valor += ficha?.assistencias ?: 0
            TipoPedido.BALIZA_INVIOLADA -> if (ficha != null && minutos >= 45 && jogo.golosSofridos == 0) p.valor += 1.0
            TipoPedido.NOTA_MEDIA -> if (ficha != null) {
                p.valor += ficha.nota
                p.aparicoes++
            }
            TipoPedido.FISICO -> p.valor = (j.atributo("fisico") - p.fisicoInicial).toDouble()
        }
        val resolvido: Boolean? = when (p.tipo) {
            TipoPedido.GOLOS, TipoPedido.ASSISTENCIAS, TipoPedido.BALIZA_INVIOLADA ->
                if (p.valor >= p.meta) true else if (p.jogosFeitos >= p.jogos) false else null
            TipoPedido.NOTA_MEDIA ->
                if (p.jogosFeitos < p.jogos) null else p.aparicoes >= 3 && p.valor / p.aparicoes > p.meta
            TipoPedido.FISICO ->
                if (p.valor >= p.meta) true else if (c.gestor.data > LocalDate.parse(p.prazo)) false else null
        }
        if (resolvido != null) resolverPedido(resolvido)
    }

    private fun resolverPedido(cumprido: Boolean) {
        val e = estado
        val p = e.pedido ?: return
        val j = c.jogador ?: return
        e.pedido = null
        e.jogosDesdePedido = 0
        if (cumprido) {
            e.pedidosCumpridos++
            j.moral = (j.moral + 10).coerceAtMost(100)
            e.ajuda = 3.0
            e.ajudaJogos = 8
            c.mensagem("Pedido cumprido", "${p.texto} Missão cumprida! O treinador ficou satisfeito: moral em alta e mais confiança nos próximos jogos.")
        } else {
            e.pedidosFalhados++
            j.moral = (j.moral - 6).coerceAtLeast(0)
            e.ajuda = -2.5
            e.ajudaJogos = 4
            val motivo = if (p.tipo == TipoPedido.NOTA_MEDIA && p.aparicoes < 3) " Jogaste pouco para o pedido contar." else ""
            c.mensagem("Pedido falhado", "${p.texto} Não conseguiste.$motivo O treinador está desiludido e vai pensar duas vezes antes de contar contigo.")
        }
        prepararJogo()
    }

    /**
     * O jogador pede ao treinador mais minutos. É aceite se a forma ou as notas justificarem (ou se está perto
     * de ganhar o lugar); o treinador dá-lhe então uma ajuda na escolha do onze durante uns jogos.
     */
    fun pedirMinutos(): String {
        val j = c.jogador ?: return "Já não és jogador."
        val e = estado
        val estatuto = calcularEstatuto(j)
        if (estatuto == EstatutoNoClube.INDISCUTIVEL || estatuto == EstatutoNoClube.TITULAR) return "Já és titular: o treinador conta contigo."
        if (e.jogosDesdeConversa < 6) return "Falaste com o treinador há pouco tempo. Espera mais ${6 - e.jogosDesdeConversa} jogos."
        e.jogosDesdeConversa = 0
        val recentes = e.ultimasNotas.takeLast(5)
        val notaRecente = if (recentes.size >= 2) recentes.average() else 0.0
        val colegas = c.equipa.plantel.filter { it.posicao == j.posicao && it.id != j.id }.map { it.geral() }.sortedDescending()
        val lugares = maxOf(1, c.equipa.formacao.lugares.count { it == j.posicao })
        val falta = (colegas.getOrNull(lugares - 1) ?: j.geral()) - j.geral()
        val razoes = mutableListOf<String>()
        if (notaRecente >= 6.9) razoes += "as tuas notas recentes (%.1f)".format(notaRecente)
        if (j.forma >= 62) razoes += "a tua boa forma"
        if (falta <= 3) razoes += "estares muito perto do titular"
        return if (razoes.isNotEmpty()) {
            e.ajuda = maxOf(e.ajuda, 3.5)
            e.ajudaJogos = 6
            prepararJogo()
            val texto = "O treinador aceitou: por causa de ${razoes.joinToString(" e ")}, vais ter mais oportunidades nos próximos 6 jogos."
            c.mensagem("Conversa com o treinador", texto)
            texto
        } else {
            j.moral = (j.moral - 3).coerceAtLeast(0)
            val texto = "O treinador diz que ainda não mostraste o suficiente: precisas de melhores notas, de melhor forma ou de ficar mais perto do titular."
            c.mensagem("Conversa com o treinador", texto)
            texto
        }
    }

    /** Muda o foco de treino do jogador (conta a partir do próximo treino). */
    fun definirFoco(foco: FocoTreino) {
        estado.foco = foco
        prepararJogo()
    }

    // ------------------------------------------------------------------ propostas e renovações

    /** Chamado todos os dias de jogo e ao começar a época: com o mercado aberto, aparecem propostas (uma vez por janela). */
    fun aoMercado() {
        if (!ativo) return
        val e = estado
        if (!c.mercado.aberto) {
            if (e.ofertas.isNotEmpty() || e.renovacao != null) {
                e.ofertas.clear()
                e.renovacao = null
                c.mensagem("Mercado fechado", "As propostas que ficaram por responder caducaram.")
            }
            return
        }
        val data = c.gestor.data
        val chave = "${data.year}-${if (data.monthValue in 6..8) "verao" else "inverno"}"
        if (e.janelaOfertas == chave) return
        e.janelaOfertas = chave
        gerarOfertas()
        gerarRenovacao()
    }

    /** Cria as propostas desta janela: poucos clubes, conforme o nível, o estatuto e a reputação dos clubes. */
    fun gerarOfertas() {
        val j = c.jogador ?: return
        val e = estado
        e.ofertas.clear()
        if (j.emprestadoPor.isNotEmpty()) return
        val meu = c.equipa
        val estatutoAtual = estatuto()
        val pediu = e.pedidoSaida
        val valor = j.geral() + (if (j.epoca.jogos >= 5) (j.epoca.notaMedia() - 6.5) * 2.0 else 0.0) +
            (if (j.idade <= 22) (j.potencial - j.geral()) * 0.25 else 0.0) +
            when (estatutoAtual) { EstatutoNoClube.INDISCUTIVEL -> 2.0; EstatutoNoClube.TITULAR -> 1.0; EstatutoNoClube.ROTACAO -> 0.0; EstatutoNoClube.SUPLENTE -> -1.0; EstatutoNoClube.POUCO_UTILIZADO -> -2.0 }
        val salarioBase = maxOf(j.salario, j.salarioJusto())
        val candidatos = bd.equipas.values.filter { it.id != meu.id && it.plantel.size < MAX_PLANTEL }.mapNotNull { clube ->
            val diferenca = valor - clube.forca().geral
            if (diferenca < -6.0 || diferenca > 14.0) return@mapNotNull null
            val probabilidade = (0.12 + 0.012 * (diferenca + 6.0)).coerceAtMost(0.4) * (if (pediu.isNotEmpty()) 1.8 else 1.0)
            if (rng.nextDouble() >= probabilidade) return@mapNotNull null
            clube
        }.shuffled(rng)
        val maximo = 3 + (if (pediu.isNotEmpty()) 2 else 0) + (if (estatutoAtual >= EstatutoNoClube.SUPLENTE) 1 else 0)
        for (clube in candidatos.take(maximo)) {
            val emprestimo = pediu == "emprestimo" || (pediu.isEmpty() && j.idade <= 21 && estatutoAtual >= EstatutoNoClube.SUPLENTE && clube.reputacao <= meu.reputacao && rng.nextDouble() < 0.3)
            if (pediu == "transferencia" && emprestimo) continue
            val preco = if (emprestimo) 0L else (j.valorMercado() * (0.9 + rng.nextDouble() * 0.4)).toLong() / 10_000 * 10_000
            if (preco > clube.orcamento) continue
            val fator = (1.0 + 0.03 * (clube.reputacao - meu.reputacao) + rng.nextDouble() * 0.15).coerceIn(0.9, 1.8)
            val salario = if (emprestimo) j.salario else ((salarioBase * fator).toInt() / 1000 * 1000).coerceAtLeast(12_000)
            val anos = if (emprestimo) 1 else if (j.idade <= 28) rng.nextInt(3, 6) else rng.nextInt(2, 4)
            e.ofertas += OfertaJogador(e.proximaOferta++, clube.id, if (emprestimo) "emprestimo" else "transferencia", salario, anos, preco, calcularEstatuto(j, clube))
        }
        val tinhaPedido = pediu.isNotEmpty()
        e.pedidoSaida = ""
        if (e.ofertas.isNotEmpty()) {
            c.mensagem(
                "Propostas de outros clubes",
                "${e.ofertas.size} ${if (e.ofertas.size == 1) "clube quer" else "clubes querem"} contratar-te: " +
                    e.ofertas.joinToString { bd.equipas.getValue(it.clubeId).nome } + ". Vê em Carreira > Propostas.",
            )
        } else if (tinhaPedido) {
            c.mensagem("Sem propostas", "Ninguém apresentou uma proposta que valha a pena nesta janela. Continua a trabalhar.")
        }
    }

    /** O clube atual propõe renovar quando o contrato está a acabar (faltam duas épocas ou menos). */
    private fun gerarRenovacao() {
        val j = c.jogador ?: return
        val e = estado
        e.renovacao = null
        if (j.emprestadoPor.isNotEmpty() || j.contratoAte - c.gestor.ano > 2) return
        val bonus = when (estatuto()) { EstatutoNoClube.INDISCUTIVEL -> 1.25; EstatutoNoClube.TITULAR -> 1.15; EstatutoNoClube.ROTACAO -> 1.08; else -> 1.0 }
        val salario = (maxOf(j.salario * 1.1, j.salarioJusto() * bonus) / 1000).toInt() * 1000
        val anos = if (j.idade >= 32) rng.nextInt(1, 3) else rng.nextInt(3, 5)
        e.renovacao = OfertaRenovacao(salario, anos)
        c.mensagem(
            "Renovação de contrato",
            "O ${c.equipa.nome} quer renovar o teu contrato (acaba em ${j.contratoAte}): $anos épocas por ${Mercado.euros(salario.toLong())} por ano. Vê em Carreira > Propostas.",
        )
    }

    /** Aceita a renovação: o contrato passa a acabar mais tarde e o salário sobe. */
    fun aceitarRenovacao(): String {
        val j = c.jogador ?: return "Já não és jogador."
        val e = estado
        val r = e.renovacao ?: return "Não há nenhuma proposta de renovação."
        j.salario = r.salario
        j.contratoAte = c.gestor.ano + 1 + r.anos
        e.renovacao = null
        val texto = "Renovaste pelo ${c.equipa.nome} até ${j.contratoAte}, com ${Mercado.euros(r.salario.toLong())} por ano."
        c.mensagem("Contrato renovado", texto)
        c.baseDadosPorGravar = true
        return texto
    }

    fun recusarRenovacao(): String {
        estado.renovacao = null
        return "Recusaste a renovação. O contrato continua como estava."
    }

    /** O jogador pede para sair ("transferencia" ou "emprestimo"): se o mercado estiver aberto, as propostas aparecem logo. */
    fun pedirSaida(tipo: String): String {
        val j = c.jogador ?: return "Já não és jogador."
        val e = estado
        if (j.emprestadoPor.isNotEmpty()) return "Estás emprestado: só podes mudar quando voltares ao clube de origem."
        e.pedidoSaida = tipo
        j.moral = (j.moral - 3).coerceAtLeast(0)
        val oQue = if (tipo == "emprestimo") "um empréstimo" else "uma transferência"
        return if (c.mercado.aberto) {
            gerarOfertas()
            if (e.ofertas.isEmpty()) "Pediste $oQue, mas ninguém apresentou uma proposta que valha a pena nesta janela." else "Pediste $oQue e já há propostas. Vê em Carreira > Propostas."
        } else {
            "Pediste $oQue. O mercado está fechado: as propostas aparecem na próxima janela (julho, agosto ou janeiro)."
        }
    }

    /** Aceita uma proposta: o jogador muda de clube (ou é emprestado) e assina o contrato novo. */
    fun aceitarOferta(id: Int): String {
        val j = c.jogador ?: return "Já não és jogador."
        val e = estado
        val o = e.ofertas.firstOrNull { it.id == id } ?: return "Essa proposta já não existe."
        val destino = bd.equipas[o.clubeId] ?: return "Esse clube já não existe."
        if (!c.mercado.aberto) return "O mercado fechou: a proposta caducou."
        if (destino.plantel.size >= MAX_PLANTEL) return "${destino.nome} já não tem lugar no plantel."
        if (o.preco > destino.orcamento) return "${destino.nome} já não tem dinheiro para pagar a transferência."
        val origem = c.equipa
        registarEpocaParcial(j)
        origem.ajudaNaEscolha.remove(j.id)
        c.mercado.transferirJogador(j, destino, o.preco, o.salario, o.anos, o.emprestimo)
        c.info.equipaId = destino.id
        c.gestor.clubeDoUtilizador = destino.id
        e.ofertas.clear()
        e.renovacao = null
        e.pedido = null
        e.pedidoSaida = ""
        e.ajuda = 0.0
        e.ajudaJogos = 0
        e.jogosDesdePedido = 0
        e.estatuto = calcularEstatuto(j, destino)
        c.definirObjetivo()
        c.academia.garantirJovens()
        origem.invalidarForca()
        destino.invalidarForca()
        prepararJogo()
        val texto = if (o.emprestimo) "Foste emprestado ao ${destino.nome} até ao fim da época. Lugar previsto: ${e.estatuto!!.nome.lowercase()}."
        else "Assinaste pelo ${destino.nome} (${Mercado.euros(o.preco)}), contrato de ${o.anos} épocas com ${Mercado.euros(o.salario.toLong())} por ano. Lugar previsto: ${e.estatuto!!.nome.lowercase()}."
        c.mensagem("Novo clube: ${destino.nome}", texto)
        c.geradorNoticias.adicionar(
            if (o.emprestimo) 52 else 64, "transferencia",
            if (o.emprestimo) "${j.nome} emprestado ao ${destino.nome}" else "${j.nome} deixa o ${origem.nome} e assina pelo ${destino.nome}",
            texto, true,
        )
        return texto
    }

    fun recusarOferta(id: Int): String {
        val e = estado
        val o = e.ofertas.firstOrNull { it.id == id } ?: return "Essa proposta já não existe."
        e.ofertas.remove(o)
        return "Recusaste a proposta do ${bd.equipas[o.clubeId]?.nome ?: o.clubeId}."
    }

    // ------------------------------------------------------------------ épocas

    /** Guarda o que o jogador fez na época até agora (no clube atual) e recomeça a contar (muda de clube a meio da época). */
    private fun registarEpocaParcial(j: Jogador) {
        val e = estado
        val t = epocaAtual() ?: return
        if (t.jogos > 0 || e.minutosEpoca > 0) e.temporadas += t
        e.parcialJogos = j.epoca.jogos + j.epocaTaca.jogos
        e.parcialGolos = j.epoca.golos + j.epocaTaca.golos
        e.parcialAssistencias = j.epoca.assistencias + j.epocaTaca.assistencias
        e.parcialNotas = j.epoca.somaNotas + j.epocaTaca.somaNotas
        e.minutosEpoca = 0
    }

    /**
     * No fim da época (depois de fechada pelo gestor): regista a época, os troféus e os prémios do jogador
     * e faz o balanço. Só se trata uma vez por época.
     */
    fun fecharEpoca() {
        val j = c.jogador ?: return
        if (c.info.modo != ModoCarreira.JOGADOR) return
        val e = estado
        val resumo = c.gestor.historico.lastOrNull() ?: return
        if (e.epocaFechada == resumo.ano) return
        e.epocaFechada = resumo.ano
        val nomeEpoca = c.gestor.nomeEpoca(resumo.ano)
        val t = epocaAtual()
        if (t != null && (t.jogos > 0 || e.minutosEpoca > 0)) e.temporadas += t
        // Troféus do clube (só conta quem jogou alguma vez nesta época).
        val jogou = t != null && t.jogos > 0
        val clube = c.equipa
        if (jogou) {
            val liga = c.liga
            val ligaResumo = resumo.ligas[liga.id]
            if (ligaResumo != null && ligaResumo.campeao == clube.id && liga.nivel == 1) e.trofeus += Conquista(resumo.ano, nomeEpoca, "Campeão: ${liga.nome}", clube.nome)
            for ((id, r) in resumo.tacas + resumo.supertacas) {
                if (r.vencedor == clube.id) e.trofeus += Conquista(resumo.ano, nomeEpoca, "Vencedor: ${bd.competicoes[id]?.nome ?: id}", clube.nome)
            }
            for ((id, r) in resumo.europa) {
                if (r.vencedor == clube.id) e.trofeus += Conquista(resumo.ano, nomeEpoca, "Vencedor: ${TipoEuropa.values().firstOrNull { it.id == id }?.nome ?: id}", clube.nome)
            }
        }
        // Prémios individuais.
        val premiosLiga = resumo.premios[c.liga.id]
        if (premiosLiga != null) {
            if (premiosLiga.melhorJogador?.jogadorId == j.id) e.premios += Conquista(resumo.ano, nomeEpoca, "Melhor jogador: ${c.liga.nome}")
            if (premiosLiga.melhorMarcador?.jogadorId == j.id) e.premios += Conquista(resumo.ano, nomeEpoca, "Melhor marcador: ${c.liga.nome}", "${premiosLiga.melhorMarcador?.golos ?: 0} golos")
            if (premiosLiga.melhorJovem?.jogadorId == j.id) e.premios += Conquista(resumo.ano, nomeEpoca, "Melhor jovem: ${c.liga.nome}")
            if (premiosLiga.onze.any { it.jogadorId == j.id }) e.premios += Conquista(resumo.ano, nomeEpoca, "Onze da época: ${c.liga.nome}")
        }
        val podio = resumo.podio.indexOfFirst { it.jogadorId == j.id }
        if (podio == 0) e.premios += Conquista(resumo.ano, nomeEpoca, "Bola de Ouro")
        else if (podio > 0) e.premios += Conquista(resumo.ano, nomeEpoca, "${podio + 1}.º lugar na Bola de Ouro")
        // O pedido do treinador que ficou a meio caduca.
        if (e.pedido != null) {
            e.pedido = null
            e.jogosDesdePedido = 0
        }
        // Balanço da época.
        if (t != null) {
            val estatuto = estatuto()
            c.mensagem(
                "Balanço da época ${t.epoca}",
                "${t.jogos} jogos, ${t.golos} golos e ${t.assistencias} assistências" + (if (t.jogos > 0) ", nota média %.1f".format(t.nota) else "") +
                    ", ${t.minutos} minutos no ${t.clube}. Estatuto: ${estatuto.nome.lowercase()}. Geral ${t.geral}, potencial ${j.potencial}.",
            )
        }
        e.parcialJogos = 0
        e.parcialGolos = 0
        e.parcialAssistencias = 0
        e.parcialNotas = 0.0
        e.minutosEpoca = 0
    }

    /** Antes de a época nova começar: pode ser a hora de pendurar as botas (nível muito baixo em fim de carreira). */
    fun antesDaNovaEpoca() {
        val j = c.jogador ?: return
        if (c.info.modo != ModoCarreira.JOGADOR) return
        val fraco = (j.idade >= 31 && j.geral() < 50) || (j.idade >= 34 && j.geral() < 60)
        if (fraco) bd.removerJogador(j)
    }

    /** Depois de a época nova ter começado: o clube do jogador pode ter mudado (fim de empréstimo) ou ele ficou sem clube. */
    fun depoisDaNovaEpoca() {
        val j = c.jogador ?: return
        if (c.info.modo != ModoCarreira.JOGADOR) return
        val e = estado
        val antes = c.equipa
        if (j.equipa.isEmpty()) {
            // Ficou sem contrato: o último clube volta a assiná-lo.
            bd.mudarDeEquipa(j, antes.id)
            j.contratoAte = c.gestor.ano + 2
            j.salario = maxOf(j.salario, j.salarioJusto())
            c.mensagem("Contrato", "Estavas sem contrato e o ${antes.nome} voltou a assinar contigo até ${j.contratoAte}.")
        } else if (j.equipa != c.info.equipaId) {
            c.info.equipaId = j.equipa
            c.gestor.clubeDoUtilizador = j.equipa
            c.definirObjetivo()
            c.academia.garantirJovens()
            antes.ajudaNaEscolha.remove(j.id)
            c.mensagem("Fim do empréstimo", "Voltaste ao ${c.equipa.nome}, o teu clube de origem.")
        }
        e.estatuto = calcularEstatuto(j)
        e.ofertas.clear()
        e.renovacao = null
        e.janelaOfertas = ""
        e.pedido = null
        e.jogosDesdePedido = 0
        e.atributosVistos.clear()
        e.atributosVistos += j.atributos
        prepararJogo()
        aoMercado()
    }

    /**
     * O jogador deixou de existir (reformou-se): resumo da carreira e convite para treinador.
     * O clube onde terminou continua a ser o do utilizador; aparecem também propostas de outros clubes.
     */
    fun reformou() {
        val e = estado
        e.reformado = true
        val ultima = e.temporadas.lastOrNull()
        val epocas = e.temporadas.map { it.ano }.distinct().size
        val jogos = e.temporadas.sumOf { it.jogos }
        val golos = e.temporadas.sumOf { it.golos }
        val assist = e.temporadas.sumOf { it.assistencias }
        val internacional = c.gestor.selecoes?.let { s -> c.info.jogadorId?.let { s.internacionalizacoes(it) to s.golosPelaSelecao(it) } } ?: (0 to 0)
        val partes = mutableListOf<String>()
        partes += "${c.info.nome} pendurou as botas" + (ultima?.let { " aos ${it.idade + 1} anos" } ?: "") + ", depois de $epocas ${if (epocas == 1) "época" else "épocas"} de carreira."
        partes += "Números: $jogos jogos, $golos golos e $assist assistências."
        if (internacional.first > 0) partes += "Pela seleção: ${internacional.first} jogos e ${internacional.second} golos."
        if (e.trofeus.isNotEmpty()) partes += "Troféus: ${e.trofeus.groupingBy { it.titulo }.eachCount().entries.joinToString { (t, n) -> if (n > 1) "$n× $t" else t }}."
        if (e.premios.isNotEmpty()) partes += "Prémios: ${e.premios.groupingBy { it.titulo }.eachCount().entries.joinToString { (t, n) -> if (n > 1) "$n× $t" else t }}."
        partes += "O ${c.equipa.nome} convidou-te para treinador. Podes ficar ou ouvir outros clubes."
        c.mensagem("Fim da carreira de jogador", partes.joinToString(" "))
        c.geradorNoticias.adicionar(72, "reforma", "${c.info.nome} despede-se do futebol", partes.take(3).joinToString(" "), true)
        // Passa a treinador: fica no último clube, com propostas de outros.
        val reputacao = c.equipa.reputacao
        val interessados = bd.equipas.values.filter { it.id != c.info.equipaId && it.reputacao in (reputacao - 6)..(reputacao + 10) }
            .shuffled(rng).take(2)
        c.info.propostas.clear()
        c.info.propostas += interessados.map { it.id }
    }

    // ------------------------------------------------------------------ seleção

    /** Mensagem sobre os jogos em que o jogador atuou ao serviço da seleção. */
    fun avisosDaSelecao(jogos: List<JogoJogado>): List<String> {
        val id = c.info.jogadorId ?: return emptyList()
        val s = c.gestor.selecoes ?: return emptyList()
        val linhas = mutableListOf<String>()
        for (jj in jogos) {
            val fichas = jj.relatorio.casa.fichas + jj.relatorio.fora.fichas
            val f = fichas.firstOrNull { it.jogador.id == id } ?: continue
            val relatorio = jj.relatorio
            val fim = if (f.saiu > 0) f.saiu else relatorio.duracao
            val g = jj.jogo
            linhas += "Pela seleção (${s.nome(g.casa)} ${g.golosCasa}-${g.golosFora} ${s.nome(g.fora)}): ${(fim - f.entrou).coerceAtLeast(0)} minutos, nota ${"%.1f".format(f.nota)}" +
                (if (f.golos > 0) ", ${f.golos} golo(s)" else "") + "."
        }
        return linhas
    }

    // ------------------------------------------------------------------ prémios (todas as vias)

    /** Mensagem e notícias dos prémios individuais da época que acabou (também na via de treinador). Uma vez por época. */
    fun anunciarPremios(resumo: ResumoEpoca) {
        val chave = "premios/${resumo.ano}"
        if (chave in c.geradorNoticias.marcas) return
        c.geradorNoticias.marcas += chave
        val nomeEpoca = c.gestor.nomeEpoca(resumo.ano)
        val minhaLiga = resumo.premios[c.liga.id]
        val linha = { r: Premiado -> "${r.nome} (${r.clube})" }
        val partes = mutableListOf<String>()
        minhaLiga?.melhorJogador?.let { partes += "Melhor jogador da ${c.liga.nome}: ${linha(it)}, nota %.2f.".format(it.nota) }
        minhaLiga?.melhorMarcador?.let { partes += "Melhor marcador: ${linha(it)}, ${it.golos} golos." }
        minhaLiga?.melhorJovem?.let { partes += "Melhor jovem: ${linha(it)}." }
        resumo.bolaDeOuro?.let { partes += "Bola de Ouro: ${linha(it)}." }
        if (partes.isNotEmpty()) c.mensagem("Prémios individuais $nomeEpoca", partes.joinToString(" "))
        val id = c.info.jogadorId
        resumo.bolaDeOuro?.let { b ->
            val meu = b.jogadorId == id
            c.geradorNoticias.adicionar(
                if (meu) 95 else 74, "premio", if (meu) "${b.nome} conquista a Bola de Ouro!" else "Bola de Ouro $nomeEpoca: ${b.nome}",
                "${b.nome} (${b.clube}) foi o melhor jogador da época: ${b.jogos} jogos, ${b.golos} golos, ${b.assistencias} assistências.", meu,
            )
        }
        if (id != null && minhaLiga != null) {
            if (minhaLiga.melhorJogador?.jogadorId == id) c.geradorNoticias.adicionar(88, "premio", "${c.info.nome} é o melhor jogador da ${c.liga.nome}", "Eleito melhor jogador da época $nomeEpoca.", true)
            if (minhaLiga.melhorMarcador?.jogadorId == id) c.geradorNoticias.adicionar(86, "premio", "${c.info.nome} é o melhor marcador da ${c.liga.nome}", "Terminou a época $nomeEpoca com ${minhaLiga.melhorMarcador?.golos ?: 0} golos.", true)
            if (minhaLiga.melhorJovem?.jogadorId == id) c.geradorNoticias.adicionar(80, "premio", "${c.info.nome} é o melhor jovem da ${c.liga.nome}", "Eleito melhor jovem da época $nomeEpoca.", true)
            if (minhaLiga.onze.any { it.jogadorId == id }) c.geradorNoticias.adicionar(78, "premio", "${c.info.nome} no onze da época", "Faz parte da equipa da época da ${c.liga.nome} ($nomeEpoca).", true)
        }
    }

    companion object {
        val NOMES_ATRIBUTOS = mapOf(
            "ritmo" to "Ritmo", "remate" to "Remate", "passe" to "Passe", "drible" to "Drible",
            "defesa" to "Defesa", "fisico" to "Físico", "guarda_redes" to "Guarda-redes",
        )

        /** Nacionalidades à escolha na criação do jogador: as dos seis países da base de dados e as seleções maiores. */
        fun nacionalidadesPossiveis(bd: pt.futebolcarreira.nucleo.dados.BaseDados): List<Pair<String, String>> {
            val catalogo = bd.catalogoSelecoes()?.nacoes.orEmpty()
            val daBase = bd.paises.values.map { it.id to it.nome }
            val outras = catalogo.filter { it.id !in bd.paises }.sortedByDescending { it.reputacao }.take(14).map { it.id to it.nome }
            return daBase + outras
        }
    }
}
