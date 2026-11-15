package pt.futebolcarreira.nucleo.simulacao

import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.modelos.Mentalidade

/** Tipos de acontecimentos de um jogo. */
enum class TipoEvento { GOLO, DEFESA, REMATE_FORA, AMARELO, SEGUNDO_AMARELO, VERMELHO, LESAO, SUBSTITUICAO }

/**
 * Um acontecimento do jogo.
 * "outro" é quem assistiu (num golo) ou quem entrou (numa substituição).
 * "golosCasa"/"golosFora" é o marcador logo a seguir ao acontecimento.
 */
class Evento(
    val minuto: Int,
    val tipo: TipoEvento,
    val casa: Boolean,
    val jogador: Jogador,
    val outro: Jogador? = null,
    val penalti: Boolean = false,
    val golosCasa: Int = 0,
    val golosFora: Int = 0,
)

/** O que um jogador fez num jogo. */
class FichaJogador(val jogador: Jogador, val lugar: String, val titular: Boolean, val entrou: Int) {
    /** Minuto em que saiu (substituído, lesionado ou expulso); 0 = ficou até ao fim. */
    var saiu = 0
    var golos = 0
    var assistencias = 0
    var amarelos = 0
    var vermelho = false
    /** Vermelho direto (castigo maior do que por dois amarelos). */
    var vermelhoDireto = false
    /** Jornadas de paragem por lesão sofrida neste jogo (0 = não se lesionou). */
    var lesao = 0
    var defesas = 0
    var golosSofridos = 0
    /** Nota de 1 a 10. */
    var nota = 6.0
}

/** Números de uma equipa num jogo. */
class EstatisticasEquipa {
    var posse = 50
    var remates = 0
    var rematesBaliza = 0
    var cantos = 0
    var faltas = 0
    var amarelos = 0
    var vermelhos = 0
}

/** Tudo o que se passou de um dos lados. */
class LadoRelatorio(
    val equipa: String,
    val formacao: Formacao,
    val mentalidade: Mentalidade,
    val fichas: List<FichaJogador>,
    val estatisticas: EstatisticasEquipa,
    /** Jogadores que não puderam ser convocados (lesionados ou castigados). */
    val indisponiveis: List<Jogador>,
)

/** Relatório completo de um jogo. */
class RelatorioJogo(
    val casa: LadoRelatorio,
    val fora: LadoRelatorio,
    val golosCasa: Int,
    val golosFora: Int,
    /** Último minuto jogado (90 + descontos). */
    val duracao: Int,
    val eventos: List<Evento>,
) {
    /** O jogador com melhor nota. */
    val melhorEmCampo: FichaJogador? get() = (casa.fichas + fora.fichas).maxByOrNull { it.nota }
}
