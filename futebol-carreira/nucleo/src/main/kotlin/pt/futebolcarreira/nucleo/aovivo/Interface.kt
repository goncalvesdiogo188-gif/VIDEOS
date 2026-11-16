package pt.futebolcarreira.nucleo.aovivo

/** O que o utilizador faz com os controlos tácteis, em cada passo. */
data class Comando(
    val movX: Float = 0f, val movY: Float = 0f,   // joystick, -1..1 (relativo ao campo, não ao ecrã)
    val passe: Boolean = false,      // toque curto: passe para o colega na direção; mantido = passe longo (forca 0..1)
    val remate: Boolean = false,
    val forca: Float = 0.5f,         // 0..1, tempo que o botão esteve premido
    val sprint: Boolean = false,
    val trocar: Boolean = false,     // a defender: trocar para o jogador mais perto da bola
)

enum class Animacao { PARADO, CORRER, SPRINT, PASSAR, REMATAR, CARRINHO, CAIDO, DEFESA_GR, FESTEJAR }

data class JogadorEmCampo(
    val id: Int, val casa: Boolean, val numero: Int, val posicao: String, val nome: String,
    val x: Float, val y: Float, val direcao: Float,   // direção em radianos (0 = +x)
    val animacao: Animacao, val controlado: Boolean,   // controlado = o jogador que o utilizador comanda
)

data class Bola(val x: Float, val y: Float, val z: Float)

enum class FaseJogo { PONTAPE_SAIDA, A_JOGAR, LANCAMENTO, CANTO, PONTAPE_BALIZA, LIVRE, PENALTI, GOLO, INTERVALO, FIM }

/** Fotografia do jogo para desenhar; nova a cada passo. */
data class EstadoPartida(
    val minuto: Int, val segundosNoMinuto: Float,
    val golosCasa: Int, val golosFora: Int,
    val fase: FaseJogo, val segundaParte: Boolean,
    val jogadores: List<JogadorEmCampo>, val bola: Bola,
    val coresCasa: Pair<Long, Long>, val coresFora: Pair<Long, Long>,   // ARGB camisola, calções
    val siglaCasa: String, val siglaFora: String,
    val mensagem: String?,   // ex. "GOLO! Gyökeres 23'" para mostrar uns segundos
)

/** Um jogo jogado ao vivo. A implementação real está em 11A; 11B pode ter uma falsa para testar. */
interface PartidaAoVivo {
    val estado: EstadoPartida
    /** Avança o jogo "dt" segundos de tempo real (o tempo de jogo corre mais depressa: 90 minutos em ~ duracaoReal). */
    fun passo(dt: Float, comando: Comando)
    val terminou: Boolean
}
