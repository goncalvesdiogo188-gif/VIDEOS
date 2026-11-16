package pt.futebolcarreira.nucleo.carreira

import kotlinx.serialization.Serializable
import pt.futebolcarreira.nucleo.modelos.Jogador
import java.time.LocalDate
import kotlin.random.Random

// Fase 13 (F1 a F4): emoção fora de campo. Aqui ficam os dados (tudo opcional, para as gravações antigas)
// e o coordenador [Emocao], que a Carreira chama em três sítios. A lógica de cada parte está em
// Imprensa.kt (F1), Conversas.kt (F2), Balneario.kt (F3) e Momentos.kt (F4).

/** O que uma resposta muda. Todos os valores são pequenos (cerca de -8 a +9). */
@Serializable
data class Efeitos(
    /** Moral de todo o plantel. */
    val moral: Int = 0,
    val adeptos: Int = 0,
    val direcao: Int = 0,
    /** Moral do jogador visado (o primeiro, se a decisão for sobre um) e do segundo (conflitos). */
    val alvo: Int = 0,
    val alvo2: Int = 0,
) {
    fun inverso() = Efeitos(-moral, -adeptos, -direcao, -alvo, -alvo2)
}

/**
 * Uma resposta possível. "risco" é a probabilidade (em %) de correr mal: nesse caso valem "efeitosFalha"
 * (por omissão, o contrário dos efeitos). "promessaJogos" > 0 cria a promessa de dar minutos ao jogador visado.
 */
@Serializable
class OpcaoDecisao(
    val rotulo: String,
    val dica: String = "",
    val resposta: String = "",
    val efeitos: Efeitos = Efeitos(),
    val risco: Int = 0,
    val respostaFalha: String = "",
    val efeitosFalha: Efeitos? = null,
    val manchete: String = "",
    val manchetesFalha: String = "",
    val promessaJogos: Int = 0,
)

/**
 * Uma decisão à espera do treinador. "tipo": "imprensa", "palestra", "conversa", "conflito", "protesto" ou "homenagem".
 * Se não se responder até "validaAte" (o dia do próximo jogo), aplica-se a opção "omissao" (-1 = nada acontece).
 */
@Serializable
class Decisao(
    val id: Int,
    val tipo: String,
    val titulo: String,
    val texto: String,
    val opcoes: List<OpcaoDecisao>,
    val data: String,
    val validaAte: String,
    val jogoChave: String = "",
    val jogadorId: Int? = null,
    val jogador2Id: Int? = null,
    val omissao: Int = -1,
)

/** Uma promessa feita a um jogador: tantos jogos nos próximos jogos do clube. */
@Serializable
class Promessa(val jogadorId: Int, val jogosAlvo: Int, var jogosRestantes: Int, var feitos: Int = 0, val criada: String = "")

/** Um momento da carreira (prémio, homenagem, nova lenda...). */
@Serializable
class Momento(val data: String, val tipo: String, val titulo: String, val texto: String)

/** Uma época do clube na sala de história. */
@Serializable
class EpocaClube(
    val ano: Int, val nome: String, val liga: String, val posicao: Int, val pontos: Int,
    val titulos: List<String> = emptyList(), val marcador: String = "", val golosMarcador: Int = 0,
)

/** O que um jogador fez ao serviço do clube, somado de todas as épocas já fechadas. */
@Serializable
class FichaHistorica(
    val id: Int, var nome: String, var posicao: String,
    var jogos: Int = 0, var golos: Int = 0, var assistencias: Int = 0, var epocas: Int = 0, var ultimoAno: Int = 0,
    var lenda: Boolean = false, var despedido: Boolean = false,
)

/** Um recorde do clube: "valor" serve para comparar; "texto" é o que se mostra. */
@Serializable
class Recorde(val titulo: String, var valor: Int, var texto: String, var epoca: String)

/** A sala de história de um clube. */
@Serializable
class HistoriaClube(
    val epocas: MutableList<EpocaClube> = mutableListOf(),
    val jogadores: MutableMap<Int, FichaHistorica> = mutableMapOf(),
    val recordes: MutableMap<String, Recorde> = mutableMapOf(),
    val momentos: MutableList<Momento> = mutableListOf(),
)

/** Tudo o que a Fase 13 grava (vai no ficheiro da carreira; as gravações antigas não o têm e começa vazio). */
@Serializable
class EstadoEmocao(
    var clubeId: String = "",
    var proximoId: Int = 1,
    val decisoes: MutableList<Decisao> = mutableListOf(),
    var capitaoId: Int? = null,
    /** true se o treinador escolheu o capitão (senão escolhe-se sozinho o mais influente). */
    var capitaoEscolhido: Boolean = false,
    val lideres: MutableList<Int> = mutableListOf(),
    val promessas: MutableList<Promessa> = mutableListOf(),
    /** Cânticos e reações das bancadas, o mais recente primeiro. */
    val bancadas: MutableList<String> = mutableListOf(),
    /** O que se decidiu e o que aconteceu no balneário, o mais recente primeiro. */
    val registo: MutableList<String> = mutableListOf(),
    /** Chaves de coisas já feitas ("imp/liga/2026-11-20"), para não repetir. */
    val feitos: MutableList<String> = mutableListOf(),
    /** Última vez que aconteceu cada tipo de coisa (tipo -> data), para espaçar. */
    val ultima: MutableMap<String, String> = mutableMapOf(),
    /** Última conversa com cada jogador (id -> data). */
    val conversas: MutableMap<Int, String> = mutableMapOf(),
    /** A história de cada clube que o utilizador já treinou (id do clube -> história). */
    val historias: MutableMap<String, HistoriaClube> = mutableMapOf(),
    var epocaFechada: Int = 0,
)

/**
 * Coordena a Fase 13. A carreira chama [atualizar] no fim de cada avanço e [novaEpoca] no início de cada época;
 * as decisões respondem-se com [responder] e o capitão muda com [nomearCapitao].
 * Não usa o gerador de números aleatórios da carreira (usa um próprio, ligado ao dia): ligar a Fase 13 não muda os resultados.
 */
class Emocao(internal val c: Carreira) {
    var estado = EstadoEmocao(clubeId = c.info.equipaId)

    internal val g get() = c.gestor
    internal val bd get() = c.bd
    internal val treinador get() = c.info.modo == ModoCarreira.TREINADOR

    /** A sala de história do clube atual. */
    val historia: HistoriaClube get() = estado.historias.getOrPut(c.info.equipaId) { HistoriaClube() }

    val pendentes: List<Decisao> get() = estado.decisoes.toList()

    /** Se o treinador mudou de clube, esquece o que era do clube antigo (a história de cada clube guarda-se à parte). */
    fun sincronizar() {
        if (estado.clubeId == c.info.equipaId) return
        estado = EstadoEmocao(clubeId = c.info.equipaId, proximoId = estado.proximoId, historias = estado.historias)
    }

    /** Um gerador de números aleatórios ligado ao dia e à chave: igual sempre que se repete a mesma situação. */
    internal fun rng(chave: String): Random = Random(java.util.Objects.hash(g.ano, g.data.toEpochDay(), chave, c.info.equipaId).toLong())

    internal fun jogador(id: Int?): Jogador? = id?.let { bd.jogadores[it] }

    internal fun plantel(): List<Jogador> = c.equipa.plantel.toList()

    internal fun moralMedia(): Double = plantel().map { it.moral }.average().takeIf { !it.isNaN() } ?: 50.0

    internal fun dataTexto(d: LocalDate = g.data) = pt.futebolcarreira.nucleo.simulacao.CalendarioDatas.textoCurto(d)

    internal fun registar(texto: String) {
        estado.registo.add(0, "${dataTexto()} · $texto")
        while (estado.registo.size > MAX_REGISTO) estado.registo.removeAt(estado.registo.lastIndex)
    }

    internal fun marcar(chave: String): Boolean {
        if (chave in estado.feitos) return false
        estado.feitos += chave
        while (estado.feitos.size > 80) estado.feitos.removeAt(0)
        return true
    }

    /** Passaram pelo menos "dias" dias desde a última vez que aconteceu "tipo"? */
    internal fun passou(tipo: String, dias: Int): Boolean {
        val ultima = estado.ultima[tipo]?.let { LocalDate.parse(it) } ?: return true
        return g.data.toEpochDay() - ultima.toEpochDay() >= dias
    }

    internal fun noticia(prioridade: Int, tipo: String, titulo: String, texto: String, imediato: Boolean = false) {
        c.geradorNoticias.adicionar(prioridade, tipo, titulo, texto, true)
        if (imediato) c.geradorNoticias.publicar()
    }

    internal fun mudarMoral(j: Jogador?, delta: Int) {
        if (j != null && delta != 0) j.moral = (j.moral + delta).coerceIn(0, 100)
    }

    internal fun mudarMoralEquipa(delta: Int) {
        if (delta != 0) for (j in plantel()) mudarMoral(j, delta)
    }

    internal fun mudarConfianca(adeptos: Int, direcao: Int) {
        c.info.confiancaAdeptos = (c.info.confiancaAdeptos + adeptos).coerceIn(0, 100)
        c.info.confiancaDirecao = (c.info.confiancaDirecao + direcao).coerceIn(0, 100)
    }

    /** O dia a partir do qual uma decisão nova deixa de valer: o dia do próximo jogo (ou daqui a uma semana). */
    internal fun validade(): String = (c.proximoJogo()?.data ?: g.data.plusDays(7)).toString()

    internal fun nova(
        tipo: String, titulo: String, texto: String, opcoes: List<OpcaoDecisao>, chave: String = "",
        jogadorId: Int? = null, jogador2Id: Int? = null, omissao: Int = -1,
    ): Decisao {
        val d = Decisao(estado.proximoId++, tipo, titulo, texto, opcoes, g.data.toString(), validade(), chave, jogadorId, jogador2Id, omissao)
        estado.decisoes += d
        estado.ultima[tipo] = g.data.toString()
        return d
    }

    // ------------------------------------------------ chamado pela carreira

    /**
     * Chamado no fim de cada avanço ("jogado" = o jogo do clube que acabou de se jogar, ou null).
     * Regista recordes, promessas e a reação das bancadas; fecha a época; e, na via de treinador,
     * cria as decisões para o próximo jogo (conferência de imprensa, palestra, conversas, conflitos, protestos).
     */
    fun atualizar(jogado: JogoDoClube?) {
        sincronizar()
        val fim = g.epocaTerminada()
        if (jogado != null) Momentos.registarJogo(this, jogado)
        if (fim) Momentos.fecharEpoca(this)
        if (!treinador || c.info.despedido) {
            estado.decisoes.clear()
            return
        }
        expirar()
        if (jogado != null) {
            Balneario.aposJogo(this, jogado)
            Imprensa.aposJogo(this, jogado)
            Conversas.aposJogo(this, jogado)
        }
        if (!fim) {
            c.proximoJogo()?.let { prox ->
                Imprensa.antesDoJogo(this, prox)
                Conversas.antesDoJogo(this, prox)
            }
        }
        Balneario.manter(this)
        while (estado.decisoes.size > MAX_PENDENTES) estado.decisoes.removeAt(0)
    }

    /** Início de uma época: o capitão e os líderes refazem-se e despedem-se as lendas que saíram. */
    fun novaEpoca() {
        sincronizar()
        estado.promessas.clear()
        estado.decisoes.clear()
        if (treinador) Balneario.manter(this)
        Momentos.novaEpoca(this)
    }

    // ------------------------------------------------ respostas

    /** Responde a uma decisão com a opção escolhida. Devolve o texto para mostrar (resposta e efeitos). */
    fun responder(id: Int, opcao: Int): String {
        val d = estado.decisoes.firstOrNull { it.id == id } ?: return "Esta decisão já não está disponível."
        val o = d.opcoes.getOrNull(opcao) ?: return ""
        estado.decisoes.remove(d)
        return aplicar(d, o)
    }

    private fun expirar() {
        val hoje = g.data
        for (d in estado.decisoes.filter { !LocalDate.parse(it.validaAte).isAfter(hoje) }) {
            estado.decisoes.remove(d)
            val o = d.opcoes.getOrNull(d.omissao)
            if (o == null) {
                registar("Sem resposta: ${d.titulo}.")
            } else {
                aplicar(d, o, aTempo = false)
            }
        }
    }

    private fun aplicar(d: Decisao, o: OpcaoDecisao, aTempo: Boolean = true): String {
        val falhou = o.risco > 0 && rng("${d.id}/${d.jogoChave}").nextInt(100) < o.risco
        val ef = if (falhou) (o.efeitosFalha ?: o.efeitos.inverso()) else o.efeitos
        mudarMoralEquipa(ef.moral)
        mudarConfianca(ef.adeptos, ef.direcao)
        mudarMoral(jogador(d.jogadorId), ef.alvo)
        mudarMoral(jogador(d.jogador2Id), ef.alvo2)
        val alvo = jogador(d.jogadorId)
        if (o.promessaJogos > 0 && !falhou && alvo != null) {
            estado.promessas.removeAll { it.jogadorId == alvo.id }
            estado.promessas += Promessa(alvo.id, o.promessaJogos, PRAZO_PROMESSA, 0, g.data.toString())
        }
        val resposta = (if (falhou) o.respostaFalha.ifEmpty { o.resposta } else o.resposta)
        val manchete = if (falhou) o.manchetesFalha.ifEmpty { o.manchete } else o.manchete
        if (manchete.isNotEmpty()) noticia(58, d.tipo, manchete, resposta, imediato = true)
        if (d.tipo != "palestra") estado.ultima[d.tipo] = g.data.toString()
        val efeitos = resumoEfeitos(ef, alvo, jogador(d.jogador2Id))
        registar((if (aTempo) "" else "Sem resposta a tempo: ") + "${d.titulo}: «${o.rotulo.trim('«', '»', ' ')}». $efeitos")
        if (o.promessaJogos > 0 && !falhou && alvo != null) {
            return "$resposta\nPrometeste dar ${if (o.promessaJogos == 1) "1 jogo" else "${o.promessaJogos} jogos"} a ${alvo.nome} nos próximos $PRAZO_PROMESSA jogos.\n$efeitos"
        }
        return "$resposta\n$efeitos"
    }

    private fun resumoEfeitos(e: Efeitos, a: Jogador?, b: Jogador?): String {
        fun s(v: Int) = if (v > 0) "+$v" else "$v"
        val partes = listOfNotNull(
            e.moral.takeIf { it != 0 }?.let { "moral da equipa ${s(it)}" },
            e.adeptos.takeIf { it != 0 }?.let { "adeptos ${s(it)}" },
            e.direcao.takeIf { it != 0 }?.let { "direção ${s(it)}" },
            e.alvo.takeIf { it != 0 && a != null }?.let { "moral de ${a!!.nome} ${s(it)}" },
            e.alvo2.takeIf { it != 0 && b != null }?.let { "moral de ${b!!.nome} ${s(it)}" },
        )
        return if (partes.isEmpty()) "Sem efeitos visíveis." else "Efeitos: " + partes.joinToString(", ") + "."
    }

    /** O treinador nomeia o capitão (tem de ser do plantel). Devolve o texto para mostrar. */
    fun nomearCapitao(jogadorId: Int): String = Balneario.nomear(this, jogadorId)

    /**
     * Palestra ao intervalo (para o jogo ao vivo ou para quem a quiser usar): "golosMeus" e "golosDeles" são o resultado
     * ao intervalo. Mexe na moral dos jogadores aptos e devolve o texto. Efeito pequeno, no máximo cerca de 5 pontos.
     */
    fun palestraIntervalo(tom: TomPalestra, golosMeus: Int, golosDeles: Int): String = Conversas.intervalo(this, tom, golosMeus, golosDeles)

    companion object {
        const val MAX_PENDENTES = 4
        const val MAX_REGISTO = 24
        /** Número de jogos do clube para cumprir uma promessa de minutos. */
        const val PRAZO_PROMESSA = 5
    }
}
