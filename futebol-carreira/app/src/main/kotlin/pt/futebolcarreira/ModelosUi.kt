package pt.futebolcarreira

import pt.futebolcarreira.nucleo.carreira.Instalacao
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.modelos.FocoTreino
import pt.futebolcarreira.nucleo.modelos.Intensidade
import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.Mentalidade
import pt.futebolcarreira.nucleo.simulacao.TipoEvento

// O que os ecrãs mostram: cópias simples dos dados do jogo, seguras para desenhar.
// (Os ecrãs nunca mexem diretamente no núcleo; pedem tudo ao JogoViewModel.)

/** Ecrã principal que está aberto. */
enum class Ecra { MENU, NOVA_CARREIRA, CARREGAR, CARREIRA, EDITOR }

/** Emblema simples de um clube: sigla e duas cores (em formato ARGB). */
class EmblemaUi(val sigla: String, val fundo: Long, val letras: Long)

// ---------------------------------------------------------- nova carreira

class ClubeUi(val id: String, val nome: String, val cidade: String, val emblema: EmblemaUi, val estrelas: Int, val geral: Int)

class LigaOpcaoUi(val id: String, val nome: String, val clubes: List<ClubeUi>)

class PaisUi(val id: String, val nome: String, val ligas: List<LigaOpcaoUi>)

/** Uma nacionalidade à escolha na criação do jogador. */
class NacaoUi(val id: String, val nome: String)

// ------------------------------------------------------------------- liga

enum class Zona { NORMAL, SUBIDA, DESCIDA }

class OpcaoLiga(val id: String, val texto: String)

class LinhaUi(
    val posicao: Int, val equipaId: String, val nome: String,
    val j: Int, val v: Int, val e: Int, val d: Int, val gm: Int, val gs: Int, val pts: Int,
    val zona: Zona,
    /** true na linha do clube do utilizador. */
    val meu: Boolean = false,
)

class ResultadoUi(val indice: Int, val casa: String, val golosCasa: Int, val golosFora: Int, val fora: String, val temRelato: Boolean, val meu: Boolean)

class EpocaUi(
    val epoca: String, val campeao: String, val pontos: Int, val sobem: String, val descem: String, val marcador: String,
    /** Prémios individuais da liga ("" = época de antes dos prémios) e a Bola de Ouro (entre todas as ligas). */
    val melhorJogador: String = "", val melhorJovem: String = "", val bolaDeOuro: String = "",
)

/** Linha de uma lista de melhores jogadores (marcadores, assistências, notas). */
class LinhaJogadorUi(val nome: String, val clube: String, val jogos: Int, val valor: String)

class LigaUi(
    val ligas: List<OpcaoLiga> = emptyList(),
    val ligaId: String = "",
    val tabela: List<LinhaUi> = emptyList(),
    /** Jornadas já jogadas e a que está a ser mostrada nos resultados. */
    val jornada: Int = 0,
    val jornadaVista: Int = 0,
    val dataJornada: String = "",
    val resultados: List<ResultadoUi> = emptyList(),
    val marcadores: List<LinhaJogadorUi> = emptyList(),
    val assistencias: List<LinhaJogadorUi> = emptyList(),
    val notas: List<LinhaJogadorUi> = emptyList(),
    val historico: List<EpocaUi> = emptyList(),
    val titulos: List<Pair<String, Int>> = emptyList(),
)

// ---------------------------------------------------------------- plantel

class JogadorUi(
    val id: Int,
    val posicao: String, val nome: String, val idade: Int, val geral: Int,
    /** Exato ("78") ou estimado pelos olheiros ("72–80"). */
    val potencial: String, val valor: String,
    val jogos: Int, val golos: Int, val assistencias: Int, val nota: String,
    /** "" se está apto; senão o motivo (lesão ou castigo). */
    val estado: String,
    /** true se é o jogador do utilizador (via de jogador). */
    val meu: Boolean = false,
    /** Forma e moral por palavras (só no clube do utilizador; "" nos outros). */
    val forma: String = "",
)

/** Um lugar do onze inicial e quem lá joga. */
class LugarUi(val indice: Int, val lugar: String, val nome: String, val posicao: String, val nota: Int)

/** Um jogador que pode ser escolhido para um lugar do onze. "aviso" explica se já é titular ou se não pode jogar. */
class CandidatoUi(val jogadorId: Int, val posicao: String, val nome: String, val nota: Int, val aviso: String, val apto: Boolean)

/** Janela para escolher quem joga num lugar. */
class EscolhaUi(val indice: Int, val lugar: String, val candidatos: List<CandidatoUi>)

class PlantelUi(
    val equipaId: String, val titulo: String, val detalhes: String, val emblema: EmblemaUi, val jogadores: List<JogadorUi>,
    /** null = automática. */
    val formacao: Formacao?, val formacaoEmUso: String, val mentalidade: Mentalidade?,
    /** true se o utilizador pode mudar a tática (é o treinador deste clube). */
    val podeMudarTatica: Boolean,
    /** O onze para o próximo jogo, os suplentes, e se o onze foi escolhido à mão. */
    val onze: List<LugarUi>,
    val suplentes: String,
    val onzeManual: Boolean,
)

// ------------------------------------------------------------------- jogo

/** Um acontecimento no relato. "tipo" null = frase do narrador (início, intervalo, fim). */
class EventoUi(val minuto: Int, val minutoTexto: String, val texto: String, val tipo: TipoEvento?, val casa: Boolean, val golosCasa: Int, val golosFora: Int)

class FichaUi(
    val lugar: String, val nome: String, val nota: String, val titular: Boolean,
    val golos: Int, val assistencias: Int, val amarelos: Int, val vermelho: Boolean, val lesionado: Boolean,
    val entrou: Int, val saiu: Int,
)

/** Um jogo aberto para ver o relato, as estatísticas e as notas. */
class JogoUi(
    val casa: String, val fora: String, val emblemaCasa: EmblemaUi, val emblemaFora: EmblemaUi,
    val golosCasa: Int, val golosFora: Int, val duracao: Int,
    val taticaCasa: String, val taticaFora: String,
    val eventos: List<EventoUi>,
    /** nome da estatística, valor da casa, valor de fora. */
    val estatisticas: List<Triple<String, String, String>>,
    val fichasCasa: List<FichaUi>, val fichasFora: List<FichaUi>,
    val melhorEmCampo: String,
    /** true = abrir já a passar o jogo minuto a minuto. */
    val emDireto: Boolean,
)

// --------------------------------------------------------------- carreira

/** Um jogo do clube no calendário. "resultado" vazio = ainda não se jogou. */
class JogoCalendarioUi(
    val jornada: Int, val data: String, val adversario: String, val emblema: EmblemaUi, val emCasa: Boolean,
    val resultado: String,
    /** "V", "E", "D" ou "" (por jogar). */
    val desfecho: String,
    val temRelato: Boolean,
    /** Competição ("" = a liga do clube) e ronda ("Quartos de final"); nos jogos fora da liga, "chave" abre o relato. */
    val competicao: String = "",
    val ronda: String = "",
    val chave: String = "",
    /** Campo neutro: não é nem "em casa" nem "fora". */
    val neutro: Boolean = false,
)

class ProximoJogoUi(
    val jornada: Int, val totalJornadas: Int, val data: String, val emCasa: Boolean,
    val adversario: String, val emblemaAdversario: EmblemaUi, val detalhes: String,
    /** Competição ("" = a liga do clube) e ronda ("Quartos de final", "Jornada 4"...). */
    val competicao: String = "",
    val ronda: String = "",
    /** Campo neutro: não se diz "em casa" nem "fora". */
    val neutro: Boolean = false,
    /** Nome do derby ("Derby de Lisboa"); "" se não é um derby. */
    val derby: String = "",
)

class MensagemUi(val data: String, val titulo: String, val texto: String)

class MeuJogadorUi(val nome: String, val linha1: String, val linha2: String, val aviso: String = "")

// --------------------------------------------------- carreira do jogador

class PedidoTreinadorUi(val texto: String, val progresso: String)

class OfertaUi(
    val id: Int, val clube: String, val emblema: EmblemaUi, val detalhes: String, val condicoes: String,
    val estatuto: String, val emprestimo: Boolean,
)

class TemporadaUi(val epoca: String, val clube: String, val jogos: Int, val golos: Int, val assistencias: Int, val nota: String, val minutos: Int)

class ConquistaUi(val epoca: String, val titulo: String, val detalhe: String)

/** O separador «Carreira» da via de jogador: perfil, percurso, propostas e prémios. */
class JogadorCarreiraUi(
    val nome: String, val linha1: String, val clube: String, val emblema: EmblemaUi,
    val geral: Int, val potencial: Int,
    val formaMoral: String,
    /** Nome do estatuto e o seu nível (0 = indiscutível ... 4 = pouco utilizado). */
    val estatuto: String, val estatutoNivel: Int,
    val atributos: List<Pair<String, Int>>,
    /** título, valor (contrato, salário, valor de mercado...). */
    val contrato: List<Pair<String, String>>,
    val estaEpoca: String, val minutosEpoca: Int, val totais: String,
    val selecao: String,
    val foco: FocoTreino,
    val pedido: PedidoTreinadorUi?,
    val renovacao: String?,
    val ofertas: List<OfertaUi>,
    val mercadoAberto: Boolean, val emprestado: Boolean,
    /** "" ou o que ficou pedido para a próxima janela ("transferência", "empréstimo"). */
    val pedidoSaida: String,
    val temporadas: List<TemporadaUi>,
    val trofeus: List<ConquistaUi>,
    val premios: List<ConquistaUi>,
    /** Prémios da liga na última época fechada: título, vencedor. */
    val premiosDaLiga: List<Pair<String, String>>,
    val tituloPremiosDaLiga: String,
    /** Bola de Ouro de cada época (da mais recente para a mais antiga): época, vencedor. */
    val bolaDeOuro: List<Pair<String, String>>,
)

// ---------------------------------------------------- mercado e finanças

/** O que se pode fazer com um jogador no mercado. */
enum class AcaoMercado { PAGAR_PEDIDO, OFERECER_90, OFERECER_80, PEDIR_EMPRESTIMO, CONTRATAR_LIVRE, VENDER, EMPRESTAR, PROMOVER, DISPENSAR }

class BotaoMercadoUi(val acao: AcaoMercado, val texto: String)

/** Ficha completa de um jogador: atributos, contrato e o que se pode fazer com ele. */
class FichaJogadorUi(
    val id: Int, val nome: String, val linha1: String, val clube: String, val emblema: EmblemaUi?,
    val geral: Int, val potencial: String,
    /** nome do atributo, valor (1 a 99). */
    val atributos: List<Pair<String, Int>>,
    /** título, valor (valor de mercado, salário, contrato...). */
    val detalhes: List<Pair<String, String>>,
    val botoes: List<BotaoMercadoUi>,
    /** Explica por que não há botões (mercado fechado, etc.); "" se não for preciso. */
    val nota: String,
    /** Evolução do geral: (idade, geral) no fim de cada época, mais o valor atual. Vazio ou só um ponto = ainda sem histórico. */
    val evolucao: List<Pair<Int, Int>> = emptyList(),
)

enum class FiltroMercado(val nome: String) { TODOS("Todos"), POSSO_PAGAR("Posso pagar"), LIVRES("Sem clube") }

class LinhaMercadoUi(val id: Int, val posicao: String, val nome: String, val clube: String, val idade: Int, val geral: Int, val preco: String)

class MercadoUi(
    val aberto: Boolean, val caixa: String, val posicao: String?, val filtro: FiltroMercado,
    val jogadores: List<LinhaMercadoUi>,
)

class FinancasUi(
    val caixa: String, val caixaNegativa: Boolean, val folhaSalarial: String,
    /** descrição, valor por extenso. */
    val receitas: List<Pair<String, String>>, val despesas: List<Pair<String, String>>,
    val saldo: String,
    val estadio: String, val obras: String, val podeAmpliar: Boolean,
)

/** Objetivo da direção e confiança no treinador (0 a 100). */
class DirecaoUi(
    val objetivo: String, val direcao: Int, val adeptos: Int,
    /** Ambiente à volta do clube: nome ("Tenso"), nível (0 = eufórico ... 4 = hostil) e uma frase. */
    val ambiente: String = "Normal", val ambienteNivel: Int = 2, val ambienteTexto: String = "",
)

/** Uma notícia (manchete e texto); "doClube" = diz respeito ao clube do utilizador. */
class NoticiaUi(val data: String, val titulo: String, val texto: String, val doClube: Boolean)

/** Um clube que quer contratar o treinador. */
class PropostaUi(val equipaId: String, val nome: String, val emblema: EmblemaUi, val detalhes: String)

// ------------------------------------------------- treino, academia, olheiros

class InstalacaoUi(val instalacao: Instalacao, val nome: String, val efeito: String, val nivel: Int, val botao: String, val podeMelhorar: Boolean)

class JovemUi(val id: Int, val posicao: String, val nome: String, val idade: Int, val geral: Int, val potencial: Int)

class OpcaoPaisUi(val id: String, val nome: String, val custo: String)

/** Treino, instalações, academia e olheiros do clube do utilizador (só na via de treinador). */
class GestaoClubeUi(
    val foco: FocoTreino, val intensidade: Intensidade,
    val instalacoes: List<InstalacaoUi>,
    val academia: List<JovemUi>,
    val paises: List<OpcaoPaisUi>,
    /** "" se o olheiro está livre; senão onde está e quando volta. */
    val olheiroEmViagem: String,
    val relatorio: List<LinhaMercadoUi>,
    val caixa: String,
)

/** Tudo o que os ecrãs da carreira mostram. */
data class CarreiraUi(
    val clube: String,
    val emblema: EmblemaUi,
    val epoca: String,
    val data: String,
    val mercadoAberto: Boolean,
    val modo: ModoCarreira,
    val utilizador: String,
    val nomeLiga: String,
    val posicao: Int,
    val pontos: Int,
    val proximo: ProximoJogoUi?,
    val ultimo: JogoCalendarioUi?,
    /** A época acabou: mostra-se o resumo e o botão para começar a seguinte. */
    val terminada: Boolean,
    val resumoFim: String,
    val proximaEpoca: String,
    /** As linhas da tabela à volta do clube. */
    val miniTabela: List<LinhaUi>,
    val mensagens: List<MensagemUi>,
    val meuJogador: MeuJogadorUi?,
    /** Só na via de treinador. */
    val direcao: DirecaoUi?,
    val propostas: List<PropostaUi>,
    val despedido: Boolean,
    val mercado: MercadoUi,
    val financas: FinancasUi,
    val calendario: List<JogoCalendarioUi>,
    val plantel: PlantelUi,
    val liga: LigaUi,
    val gestao: GestaoClubeUi?,
    /** As notícias, da mais recente para a mais antiga. */
    val noticias: List<NoticiaUi> = emptyList(),
    /** Só na via de jogador (Fase 9). */
    val jogadorCarreira: JogadorCarreiraUi? = null,
)

// ------------------------------------------- fase 7: taças, Europa, seleções

enum class TipoCompeticao { LIGA, TACA, SUPERTACA, EUROPA, SELECOES }

/** Uma entrada do seletor de competições. "pais" é o país ou a bandeira em texto ("PT", "UEFA"). */
class CompeticaoOpcaoUi(val id: String, val nome: String, val tipo: TipoCompeticao, val pais: String = "")

/**
 * Uma perna de uma eliminatória. Os golos estão sempre na ordem da eliminatória
 * (equipa A = a primeira da lista, equipa B = a segunda), mesmo na segunda mão.
 * "chave" é uma chave opaca para abrir o relato ("" = sem relato).
 */
class PernaUi(val golosA: Int, val golosB: Int, val data: String = "", val chave: String = "")

/** Uma eliminatória entre duas equipas (uma ou duas mãos). */
class EliminatoriaParUi(
    val casa: String, val fora: String, val emblemaCasa: EmblemaUi, val emblemaFora: EmblemaUi,
    /** Pernas já jogadas (vazio = por jogar). */
    val pernas: List<PernaUi>,
    /** Número de mãos da eliminatória (1 ou 2), para mostrar a data das que faltam. */
    val maos: Int = 1,
    /** Data do próximo jogo, se ainda faltar jogar. */
    val dataProxima: String = "",
    /** Agregado das duas mãos (só em eliminatórias a duas mãos já com resultado). */
    val agregadoA: Int? = null, val agregadoB: Int? = null,
    /** "a.p." — decidida no prolongamento. */
    val prolongamento: Boolean = false,
    /** "g.p." — decidida nos penáltis: golos de A e de B (null = sem penáltis). */
    val penaltisA: Int? = null, val penaltisB: Int? = null,
    /** 0 = por decidir, 1 = passa a equipa A, 2 = passa a equipa B. */
    val vencedor: Int = 0,
    /** true se envolve o clube do utilizador. */
    val meu: Boolean = false,
)

class RondaUi(val nome: String, val pares: List<EliminatoriaParUi>)

/** Quadro de eliminatórias de uma competição. "rondaAtual" = índice da ronda a mostrar à partida. */
class EliminatoriasUi(val rondas: List<RondaUi> = emptyList(), val rondaAtual: Int = 0)

/** Um jogo de uma fase de grupos/liga. Sem golos = por jogar. */
class JogoFaseUi(
    val casa: String, val fora: String, val golosCasa: Int? = null, val golosFora: Int? = null,
    val data: String = "", val chave: String = "", val meu: Boolean = false,
)

class JornadaFaseUi(val nome: String, val data: String, val jogos: List<JogoFaseUi>)

/** Fase de liga europeia: tabela única e jogos por jornada. Zonas: até "diretos" passam já; até "playoff" vão ao play-off. */
class FaseLigaUi(
    val tabela: List<LinhaUi> = emptyList(),
    val jornadas: List<JornadaFaseUi> = emptyList(),
    val jornadaAtual: Int = 0,
    val diretos: Int = 8, val playoff: Int = 24,
)

/** Um grupo (seleções): tabela e jogos. As linhas com zona SUBIDA passam à fase seguinte. */
class GrupoUi(val nome: String, val tabela: List<LinhaUi>, val jornadas: List<JornadaFaseUi> = emptyList())

class VencedorUi(val epoca: String, val competicao: String, val vencedor: String, val finalista: String = "")

/** Tudo o que o separador «Liga» mostra para as competições que não são ligas. */
class CompeticaoUi(
    val eliminatorias: EliminatoriasUi? = null,
    val faseLiga: FaseLigaUi? = null,
    val vencedores: List<VencedorUi> = emptyList(),
)

class TorneioUi(
    val id: String, val nome: String,
    /** "Em curso", "Datas: 5 a 28 de junho de 2028", "Terminado"... */
    val estado: String,
    val grupos: List<GrupoUi> = emptyList(),
    val eliminatorias: EliminatoriasUi? = null,
)

/** Linha do ranking de seleções (tipo FIFA). "meu" = a seleção do utilizador. */
class LinhaRankingUi(val posicao: Int, val selecao: String, val pais: String, val pontos: String, val variacao: Int = 0, val meu: Boolean = false)

class ConvocadoUi(
    val posicao: String, val nome: String, val clube: String, val geral: Int,
    /** true se é o jogador do utilizador ou joga no clube do utilizador. */
    val meu: Boolean = false,
    /** Seleção (usado na lista dos meus convocados). */
    val selecao: String = "",
)

class ConvocatoriaUi(val selecaoId: String, val selecao: String, val pais: String, val selecionador: String, val convocados: List<ConvocadoUi>)

class SelecoesUi(
    val torneios: List<TorneioUi> = emptyList(),
    /** Próximos torneios: nome e datas. */
    val proximos: List<Pair<String, String>> = emptyList(),
    val ranking: List<LinhaRankingUi> = emptyList(),
    val convocatorias: List<ConvocatoriaUi> = emptyList(),
    val meusConvocados: List<ConvocadoUi> = emptyList(),
    val vencedores: List<VencedorUi> = emptyList(),
) {
    val vazio get() = torneios.isEmpty() && ranking.isEmpty() && convocatorias.isEmpty() && vencedores.isEmpty() && proximos.isEmpty()
}

/** Estado novo da fase 7: o seletor, a competição escolhida e as seleções. Tudo vazio = o jogo fica como antes. */
class Fase7Ui(
    /** Taças, supertaças e competições europeias (as ligas continuam em LigaUi.ligas). */
    val competicoes: List<CompeticaoOpcaoUi> = emptyList(),
    /** "" = está escolhida uma liga (LigaUi); senão o id de uma entrada de "competicoes". */
    val competicaoId: String = "",
    val competicao: CompeticaoUi = CompeticaoUi(),
    val selecoes: SelecoesUi = SelecoesUi(),
)
