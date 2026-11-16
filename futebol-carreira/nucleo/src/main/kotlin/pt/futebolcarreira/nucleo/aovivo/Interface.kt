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
    // Fase 13 (E1/E3): campos novos, todos com valor por omissão (ver LEIAME.md)
    /** 0 = 1.ª parte, 1 = 2.ª parte, 2 = 1.ª parte do prolongamento, 3 = 2.ª parte do prolongamento. No intervalo é o período que acabou de acabar. */
    val periodo: Int = 0,
    /** Preenchido nas fases PONTAPE_SAIDA, PONTAPE_BALIZA, CANTO, LIVRE, LANCAMENTO e PENALTI (incluindo os penáltis de desempate). */
    val bolaParada: BolaParada? = null,
    /** Preenchido durante e depois da marcação de penáltis de desempate. */
    val desempate: EstadoDesempate? = null,
    /** true enquanto o árbitro deixa seguir a vantagem. */
    val vantagem: Boolean = false,
    val dificuldade: Dificuldade = Dificuldade.NORMAL,
    /** Quantos lances (ver [PartidaAoVivo.lances]) já houve; serve para saber se há novos. */
    val numeroDeLances: Int = 0,
)

/** Um jogo jogado ao vivo. A implementação real está em 11A; 11B pode ter uma falsa para testar. */
interface PartidaAoVivo {
    val estado: EstadoPartida

    /**
     * Como [estado], mas com as posições interpoladas entre o último passo e o seguinte: o movimento fica liso mesmo
     * quando o ritmo das imagens não bate certo com o passo da simulação. Serve só para desenhar.
     */
    fun estadoInterpolado(): EstadoPartida = estado
    /** true na segunda parte (e no prolongamento a seguir). Não cria objetos, ao contrário de [estado]. */
    val naSegundaParte: Boolean get() = estado.segundaParte
    /** A fase do jogo. Não cria objetos, ao contrário de [estado]. */
    val faseDoJogo: FaseJogo get() = estado.fase
    /** Avança o jogo "dt" segundos de tempo real (o tempo de jogo corre mais depressa: 90 minutos em ~ duracaoReal). */
    fun passo(dt: Float, comando: Comando)
    val terminou: Boolean

    // Fase 13 (E1/E3): todos com implementação por omissão, para as partidas falsas dos testes continuarem a servir.
    /** Todos os lances de regras até agora (fora de jogo, vantagem, faltas, lances parados, prolongamento, penáltis...), por ordem. */
    val lances: List<Lance> get() = emptyList()

    /**
     * Executa o lance parado de quem o utilizador comanda (livre, canto, lançamento, pontapé de baliza, penálti), com direção e força.
     * Devolve false se não há nenhum lance parado a cargo da equipa do utilizador (ou se ainda é cedo: 0,4 s depois de ficar pronto).
     */
    fun executarParada(comando: ComandoParada): Boolean = false

    /** Guarda-redes do utilizador num penálti do adversário: lado do mergulho (-1, 0 = fica ao centro, +1; o eixo é o y do campo). Vale para o próximo penálti. */
    fun escolherMergulho(lado: Int) {}
}
