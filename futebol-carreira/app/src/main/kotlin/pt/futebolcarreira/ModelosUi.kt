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
enum class Ecra { MENU, NOVA_CARREIRA, CARREGAR, CARREIRA }

/** Emblema simples de um clube: sigla e duas cores (em formato ARGB). */
class EmblemaUi(val sigla: String, val fundo: Long, val letras: Long)

// ---------------------------------------------------------- nova carreira

class ClubeUi(val id: String, val nome: String, val cidade: String, val emblema: EmblemaUi, val estrelas: Int, val geral: Int)

class LigaOpcaoUi(val id: String, val nome: String, val clubes: List<ClubeUi>)

class PaisUi(val id: String, val nome: String, val ligas: List<LigaOpcaoUi>)

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

class EpocaUi(val epoca: String, val campeao: String, val pontos: Int, val sobem: String, val descem: String, val marcador: String)

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
)

class ProximoJogoUi(
    val jornada: Int, val totalJornadas: Int, val data: String, val emCasa: Boolean,
    val adversario: String, val emblemaAdversario: EmblemaUi, val detalhes: String,
)

class MensagemUi(val data: String, val titulo: String, val texto: String)

class MeuJogadorUi(val nome: String, val linha1: String, val linha2: String)

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
class DirecaoUi(val objetivo: String, val direcao: Int, val adeptos: Int)

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
class CarreiraUi(
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
)
