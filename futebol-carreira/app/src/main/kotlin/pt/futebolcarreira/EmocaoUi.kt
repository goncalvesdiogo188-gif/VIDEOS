package pt.futebolcarreira

import pt.futebolcarreira.nucleo.carreira.Balneario
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.LinhaHistoria
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.carreira.Momentos
import pt.futebolcarreira.nucleo.simulacao.CalendarioDatas
import java.time.LocalDate

// Fase 13: o que os ecrãs da emoção fora de campo (imprensa, balneário, história) mostram.

class OpcaoDecisaoUi(val rotulo: String, val dica: String)

/** Uma decisão à espera de resposta. "etiqueta" é a palavra curta do cartão («Imprensa», «Palestra»...). */
class DecisaoUi(val id: Int, val tipo: String, val etiqueta: String, val titulo: String, val texto: String, val opcoes: List<OpcaoDecisaoUi>)

class LiderUi(val id: Int, val nome: String, val posicao: String, val moral: Int, val capitao: Boolean, val influencia: Int)

class BalnearioUi(
    val humor: String,
    val moralMedia: Int,
    val capitao: String,
    val lideres: List<LiderUi>,
    val promessas: List<String>,
    val bancadas: List<String>,
    val registo: List<String>,
)

class LinhaHistoriaUi(val nome: String, val detalhe: String, val valor: String, val lenda: Boolean = false)

class HistoriaUi(
    val clube: String = "",
    val trofeus: List<Pair<String, Int>> = emptyList(),
    val epocas: List<String> = emptyList(),
    val marcadores: List<LinhaHistoriaUi> = emptyList(),
    val lendas: List<LinhaHistoriaUi> = emptyList(),
    val recordes: List<Pair<String, String>> = emptyList(),
    val momentos: List<Pair<String, String>> = emptyList(),
)

class EmocaoUi(
    val decisoes: List<DecisaoUi> = emptyList(),
    /** Só na via de treinador. */
    val balneario: BalnearioUi? = null,
    val historia: HistoriaUi = HistoriaUi(),
)

private fun etiqueta(tipo: String) = when (tipo) {
    "imprensa" -> "Imprensa"
    "palestra" -> "Palestra"
    "conversa" -> "Conversa"
    "conflito" -> "Balneário"
    "protesto" -> "Adeptos"
    "homenagem" -> "Homenagem"
    else -> "Decisão"
}

private fun linha(l: LinhaHistoria, comGolos: Boolean) = LinhaHistoriaUi(
    l.nome,
    "${l.posicao} · ${l.jogos} jogos · ${l.epocas} ${if (l.epocas == 1) "época" else "épocas"}" + if (l.noClube) "" else " · já saiu",
    if (comGolos) "${l.golos} golos" else "${l.jogos} jogos",
    l.lenda,
)

internal fun criarEmocaoUi(c: Carreira): EmocaoUi {
    val e = c.emocao
    e.sincronizar()
    val treinador = c.info.modo == ModoCarreira.TREINADOR
    val h = e.historia
    val decisoes = if (treinador && !c.info.despedido) e.pendentes.map { d ->
        DecisaoUi(d.id, d.tipo, etiqueta(d.tipo), d.titulo, d.texto, d.opcoes.map { OpcaoDecisaoUi(it.rotulo, it.dica) })
    } else emptyList()
    val balneario = if (!treinador) null else {
        val capitao = e.estado.capitaoId
        BalnearioUi(
            Balneario.humor(e), e.plantel().map { it.moral }.average().takeIf { !it.isNaN() }?.toInt() ?: 50,
            e.jogador(capitao)?.nome ?: "—",
            Balneario.candidatos(e, 6).map { LiderUi(it.id, it.nome, it.posicao, it.moral, it.id == capitao, Balneario.influencia(e, it).toInt()) },
            e.estado.promessas.mapNotNull { p ->
                e.jogador(p.jogadorId)?.let { "${it.nome}: ${p.feitos} de ${p.jogosAlvo} jogos prometidos (faltam ${maxOf(0, p.jogosRestantes)} jogos para o prazo)" }
            },
            e.estado.bancadas.toList(),
            e.estado.registo.take(8),
        )
    }
    fun data(texto: String) = CalendarioDatas.textoCurto(LocalDate.parse(texto))
    val historia = HistoriaUi(
        c.equipa.nome,
        Momentos.trofeus(e),
        h.epocas.asReversed().map { ep ->
            "${ep.nome}: ${ep.posicao}.º lugar (${ep.pontos} pts)" + (if (ep.titulos.isNotEmpty()) " · " + ep.titulos.joinToString(" + ") else "") +
                (if (ep.marcador.isNotEmpty()) " · ${ep.marcador} (${ep.golosMarcador} golos)" else "")
        },
        Momentos.melhoresMarcadores(e).map { linha(it, true) },
        Momentos.lendas(e).map { linha(it, false) },
        h.recordes.values.map { it.titulo to "${it.texto} (${it.epoca})" },
        h.momentos.take(12).map { data(it.data) to "${it.titulo}. ${it.texto}" },
    )
    return EmocaoUi(decisoes, balneario, historia)
}
