package pt.futebolcarreira.aovivo

import pt.futebolcarreira.nucleo.aovivo.Comando
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
    private val movimento = Joystick.VetorCampo()

    /** Posição do joystick no ecrã (-1..1, dy para cima). */
    fun joystick(dx: Float, dy: Float) = synchronized(trinco) { jx = dx; jy = dy }

    fun sprint(ativo: Boolean) = synchronized(trinco) { correr = ativo }

    fun passePremido() = synchronized(trinco) { if (passeDesde < 0) passeDesde = relogioNs() }

    fun passeSolto() = synchronized(trinco) {
        if (passeDesde < 0) return
        forcaPasse = forcaDoPasse(segundos(passeDesde))
        passeDesde = -1
        passePendente = true
    }

    fun rematePremido() = synchronized(trinco) { if (remateDesde < 0) remateDesde = relogioNs() }

    fun remateSolto() = synchronized(trinco) {
        if (remateDesde < 0) return
        forcaRemate = forcaDoRemate(segundos(remateDesde))
        remateDesde = -1
        rematePendente = true
    }

    fun trocar() = synchronized(trinco) { trocarPendente = true }

    /** Força que o passe teria se fosse solto agora (0 se o botão não está premido): para a barra. */
    fun forcaPasseAgora(): Float = synchronized(trinco) { if (passeDesde < 0) 0f else forcaDoPasse(segundos(passeDesde)) }

    fun forcaRemateAgora(): Float = synchronized(trinco) { if (remateDesde < 0) 0f else forcaDoRemate(segundos(remateDesde)) }

    /** Larga tudo (por exemplo, quando o jogo é pausado). */
    fun limpar() = synchronized(trinco) {
        jx = 0f; jy = 0f; correr = false
        passeDesde = -1; remateDesde = -1
        passePendente = false; rematePendente = false; trocarPendente = false
    }

    /** O comando para o próximo passo do motor; os toques (passe, remate, trocar) só valem uma vez. */
    fun consumir(sentido: Int): Comando = synchronized(trinco) {
        Joystick.paraCampo(jx, jy, sentido, movimento)
        val c = Comando(
            movX = movimento.x, movY = movimento.y, passe = passePendente, remate = rematePendente,
            forca = if (rematePendente) forcaRemate else forcaPasse, sprint = correr, trocar = trocarPendente,
        )
        passePendente = false; rematePendente = false; trocarPendente = false
        c
    }

    private fun segundos(desde: Long) = (relogioNs() - desde) / 1e9f

    companion object {
        /** Abaixo disto o passe é curto. */
        const val TOQUE_CURTO = 0.25f
        const val TEMPO_FORCA_MAXIMA = 1.0f

        fun forcaDoPasse(segundos: Float): Float =
            if (segundos < TOQUE_CURTO) 0.3f else (0.3f + 0.7f * ((segundos - TOQUE_CURTO) / (TEMPO_FORCA_MAXIMA - TOQUE_CURTO))).coerceIn(0.3f, 1f)

        fun forcaDoRemate(segundos: Float): Float = (segundos / 0.9f).coerceIn(0.15f, 1f)
    }
}
