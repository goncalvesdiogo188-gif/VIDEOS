package pt.futebolcarreira.aovivo

import pt.futebolcarreira.nucleo.aovivo.Comando
import pt.futebolcarreira.nucleo.aovivo.ComandoExtra
import pt.futebolcarreira.nucleo.aovivo.TipoPasse
import pt.futebolcarreira.nucleo.aovivo.TipoRemate
import kotlin.math.atan2
import kotlin.math.hypot

/** Conversão do joystick do ecrã para o movimento no campo. */
object Joystick {
    /** Abaixo deste valor (0..1) o joystick conta como parado. */
    const val ZONA_MORTA = 0.15f

    /** Resultado reutilizável de [paraCampo] (sem alocar um Pair a cada imagem). */
    class VetorCampo {
        var x = 0f
        var y = 0f
    }

    /**
     * "dx" e "dy" são o arrasto no ecrã, de -1 a 1 (dy positivo = para cima no ecrã).
     * "sentido" é o da câmara (+1: o ecrã mostra o +x à direita e o +y para cima; -1: tudo ao contrário).
     * Devolve (movX, movY) relativos ao campo; a zona morta é retirada e o resto volta a esticar-se até 1.
     */
    fun paraCampo(dx: Float, dy: Float, sentido: Int): Pair<Float, Float> {
        val v = VetorCampo()
        paraCampo(dx, dy, sentido, v)
        return v.x to v.y
    }

    /** Igual a [paraCampo], mas escreve o resultado em "saida" (para o motor não alocar nada por imagem). */
    fun paraCampo(dx: Float, dy: Float, sentido: Int, saida: VetorCampo) {
        val m = hypot(dx, dy)
        if (m < ZONA_MORTA) { saida.x = 0f; saida.y = 0f; return }
        val escala = ((m.coerceAtMost(1f) - ZONA_MORTA) / (1f - ZONA_MORTA)) / m
        val s = if (sentido < 0) -1f else 1f
        saida.x = dx * escala * s
        saida.y = dy * escala * s
    }
}

/** O comando básico e o que ele não diz (passe dirigido, remate colocado, finta, proteger). */
class ComandoCompleto(val comando: Comando, val extra: ComandoExtra)

/**
 * Estado dos controlos tácteis. Os botões e o joystick escrevem aqui (fio principal);
 * o motor lê com [consumir] a cada passo (fio do OpenGL), por isso tudo está protegido.
 */
class ControloTactil(private val relogioNs: () -> Long = System::nanoTime) {
    private val trinco = Any()
    private var jx = 0f
    private var jy = 0f
    private var correr = false
    private var passeDesde = -1L
    private var remateDesde = -1L
    private var passePendente = false
    private var rematePendente = false
    private var trocarPendente = false
    private var forcaPasse = 0.3f
    private var forcaRemate = 0.5f
    private var tipoPasseAtual = TipoPasse.NORMAL
    private var tipoPassePend = TipoPasse.NORMAL
    private var tipoRemateAtual = TipoRemate.NORMAL
    private var tipoRematePend = TipoRemate.NORMAL
    private var setaX = 0f
    private var setaY = 0f
    private var setaPendX = 0f
    private var setaPendY = 0f
    private var fintaAteNs = -1L
    private var fintaLado = 1
    private var proteger = false
    private val movimento = Joystick.VetorCampo()

    /** Posição do joystick no ecrã (-1..1, dy para cima). */
    fun joystick(dx: Float, dy: Float) = synchronized(trinco) { jx = dx; jy = dy }

    fun sprint(ativo: Boolean) = synchronized(trinco) { correr = ativo }

    fun passePremido() = premirPasse(TipoPasse.NORMAL)

    fun passeSolto() = soltarPasse()

    fun rematePremido() = premirRemate(TipoRemate.NORMAL)

    fun remateSolto() = soltarRemate()

    fun trocar() = synchronized(trinco) { trocarPendente = true }

    // --- botões avançados -------------------------------------------------

    /** Premir um botão com força (passe, profundidade, cruzamento, remates). Os outros ignoram-se aqui. */
    fun premir(b: BotaoId) {
        when (b) {
            BotaoId.PASSE -> premirPasse(TipoPasse.NORMAL)
            BotaoId.PROFUNDIDADE -> premirPasse(TipoPasse.PROFUNDIDADE)
            BotaoId.CRUZAMENTO -> premirPasse(TipoPasse.CRUZAMENTO)
            BotaoId.REMATE -> premirRemate(TipoRemate.NORMAL)
            BotaoId.COLOCADO -> premirRemate(TipoRemate.COLOCADO)
            BotaoId.POR_CIMA -> premirRemate(TipoRemate.POR_CIMA)
            BotaoId.SPRINT -> sprint(true)
            BotaoId.TROCAR -> trocar()
            BotaoId.FINTA -> finta()
            BotaoId.PROTEGER -> proteger(true)
        }
    }

    fun soltar(b: BotaoId) {
        when (b) {
            BotaoId.PASSE, BotaoId.PROFUNDIDADE, BotaoId.CRUZAMENTO -> soltarPasse()
            BotaoId.REMATE, BotaoId.COLOCADO, BotaoId.POR_CIMA -> soltarRemate()
            BotaoId.SPRINT -> sprint(false)
            BotaoId.PROTEGER -> proteger(false)
            else -> {}
        }
    }

    private fun premirPasse(t: TipoPasse) = synchronized(trinco) {
        if (passeDesde < 0) { passeDesde = relogioNs(); tipoPasseAtual = t; setaX = 0f; setaY = 0f }
    }

    private fun soltarPasse() = synchronized(trinco) {
        if (passeDesde < 0) return
        forcaPasse = forcaDoPasse(segundos(passeDesde))
        passeDesde = -1
        passePendente = true
        tipoPassePend = tipoPasseAtual
        setaPendX = setaX; setaPendY = setaY
        setaX = 0f; setaY = 0f
    }

    private fun premirRemate(t: TipoRemate) = synchronized(trinco) {
        if (remateDesde < 0) { remateDesde = relogioNs(); tipoRemateAtual = t }
    }

    private fun soltarRemate() = synchronized(trinco) {
        if (remateDesde < 0) return
        forcaRemate = forcaDoRemate(segundos(remateDesde))
        remateDesde = -1
        rematePendente = true
        tipoRematePend = tipoRemateAtual
    }

    /** A seta do passe dirigido: o arrasto do dedo (dx, dy em -1..1, dy para cima) enquanto o botão do passe está premido. */
    fun seta(dx: Float, dy: Float) = synchronized(trinco) { setaX = dx; setaY = dy }

    /** Intensidade (0..1) da seta atual, para o ecrã a desenhar. */
    fun setaAgora(): Pair<Float, Float> = synchronized(trinco) { setaX to setaY }

    fun finta() = synchronized(trinco) {
        fintaAteNs = relogioNs() + (DURACAO_FINTA * 1e9f).toLong()
        fintaLado = -fintaLado   // alterna o lado a cada finta
    }

    fun proteger(ativo: Boolean) = synchronized(trinco) { proteger = ativo }

    /** Força que o passe teria se fosse solto agora (0 se o botão não está premido): para a barra. */
    fun forcaPasseAgora(): Float = synchronized(trinco) { if (passeDesde < 0) 0f else forcaDoPasse(segundos(passeDesde)) }

    fun forcaRemateAgora(): Float = synchronized(trinco) { if (remateDesde < 0) 0f else forcaDoRemate(segundos(remateDesde)) }

    /** Larga tudo (por exemplo, quando o jogo é pausado). */
    fun limpar() = synchronized(trinco) {
        jx = 0f; jy = 0f; correr = false
        passeDesde = -1; remateDesde = -1
        passePendente = false; rematePendente = false; trocarPendente = false
        setaX = 0f; setaY = 0f; setaPendX = 0f; setaPendY = 0f
        fintaAteNs = -1; proteger = false
        tipoPassePend = TipoPasse.NORMAL; tipoRematePend = TipoRemate.NORMAL
    }

    /** O comando para o próximo passo do motor; os toques (passe, remate, trocar) só valem uma vez. */
    fun consumir(sentido: Int): Comando = consumirCompleto(sentido).comando

    /** Como [consumir], mas com o que o comando básico não diz. Os ângulos já vêm no referencial do campo. */
    fun consumirCompleto(sentido: Int): ComandoCompleto = synchronized(trinco) {
        Joystick.paraCampo(jx, jy, sentido, movimento)
        val c = Comando(
            movX = movimento.x, movY = movimento.y, passe = passePendente, remate = rematePendente,
            forca = if (rematePendente) forcaRemate else forcaPasse, sprint = correr, trocar = trocarPendente,
        )
        var tipoPasse = if (passePendente) tipoPassePend else TipoPasse.NORMAL
        var anguloPasse: Float? = null
        if (passePendente && hypot(setaPendX, setaPendY) >= LIMIAR_SETA) {
            val s = if (sentido < 0) -1f else 1f
            anguloPasse = atan2(setaPendY * s, setaPendX * s)
            if (tipoPasse == TipoPasse.NORMAL) tipoPasse = TipoPasse.DIRIGIDO
        }
        val e = ComandoExtra(
            tipoPasse = tipoPasse, anguloPasse = anguloPasse,
            tipoRemate = if (rematePendente) tipoRematePend else TipoRemate.NORMAL,
            finta = fintaAteNs > relogioNs(), fintaLado = fintaLado, proteger = proteger,
        )
        passePendente = false; rematePendente = false; trocarPendente = false
        tipoPassePend = TipoPasse.NORMAL; tipoRematePend = TipoRemate.NORMAL
        setaPendX = 0f; setaPendY = 0f
        ComandoCompleto(c, e)
    }

    private fun segundos(desde: Long) = (relogioNs() - desde) / 1e9f

    companion object {
        /** Abaixo disto o passe é curto. */
        const val TOQUE_CURTO = 0.25f
        const val TEMPO_FORCA_MAXIMA = 1.0f
        /** Arrasto mínimo (0..1 do raio) para a seta contar como direção. */
        const val LIMIAR_SETA = 0.35f
        const val DURACAO_FINTA = 0.35f

        fun forcaDoPasse(segundos: Float): Float =
            if (segundos < TOQUE_CURTO) 0.3f else (0.3f + 0.7f * ((segundos - TOQUE_CURTO) / (TEMPO_FORCA_MAXIMA - TOQUE_CURTO))).coerceIn(0.3f, 1f)

        fun forcaDoRemate(segundos: Float): Float = (segundos / 0.9f).coerceIn(0.15f, 1f)
    }
}
