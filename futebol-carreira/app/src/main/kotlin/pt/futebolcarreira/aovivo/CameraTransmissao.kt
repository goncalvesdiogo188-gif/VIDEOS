package pt.futebolcarreira.aovivo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Câmara de transmissão: vê o campo de lado, um pouco de cima, e segue a bola devagar.
 * Só matemática (sem OpenGL), para se poder testar.
 *
 * "sentido" = +1 vê o campo do lado de y negativo (o ecrã mostra o +x à direita);
 * -1 vê do lado oposto (o +x fica à esquerda). Serve para a equipa do utilizador atacar sempre para a direita.
 */
class CameraTransmissao(
    val recuo: Float = 34f,       // distância ao alvo, na direção do lado da câmara (m)
    val altura: Float = 22f,      // altura da câmara (m)
    val campoDeVisaoV: Float = 45f,   // graus, na vertical
    private val margemX: Float = 15f, // o alvo não passa daqui para a linha de fundo
) {
    var sentido = 1
        private set
    var alvoX = 0f
        private set
    var alvoY = 0f
        private set
    private var iniciada = false

    val olhoX get() = alvoX
    val olhoY get() = alvoY - sentido * recuo
    val olhoZ get() = altura

    /** Aproxima o alvo da bola. "dt" em segundos; a primeira chamada salta logo para a bola. */
    fun atualizar(bolaX: Float, bolaY: Float, dt: Float, novoSentido: Int = sentido) {
        sentido = if (novoSentido < 0) -1 else 1
        val limite = METADE_COMPRIMENTO - margemX
        val tx = bolaX.coerceIn(-limite, limite)
        val ty = bolaY * SEGUE_Y
        if (!iniciada) {
            alvoX = tx; alvoY = ty; iniciada = true
            return
        }
        val kx = 1f - exp(-3.5f * dt.coerceIn(0f, 0.25f))
        val ky = 1f - exp(-2.5f * dt.coerceIn(0f, 0.25f))
        alvoX += (tx - alvoX) * kx
        alvoY += (ty - alvoY) * ky
    }

    /** Projeta um ponto do campo no ecrã: (-1..1, -1..1) se estiver à vista; null se estiver atrás da câmara. */
    fun projetar(x: Float, y: Float, z: Float, proporcao: Float): Pair<Float, Float>? {
        // Vetores da câmara: frente (do olho para o alvo), direita e cima.
        var fx = alvoX - olhoX
        var fy = alvoY - olhoY
        var fz = -olhoZ
        val n = sqrt(fx * fx + fy * fy + fz * fz)
        fx /= n; fy /= n; fz /= n
        // direita = frente x (0,0,1)
        var rx = fy
        var ry = -fx
        val rn = sqrt(rx * rx + ry * ry)
        rx /= rn; ry /= rn
        // cima = direita x frente
        val ux = ry * fz
        val uy = -rx * fz
        val uz = rx * fy - ry * fx
        val dx = x - olhoX
        val dy = y - olhoY
        val dz = z - olhoZ
        val profundidade = dx * fx + dy * fy + dz * fz
        if (profundidade <= 0.1f) return null
        val tanV = tan((campoDeVisaoV * PI / 360.0)).toFloat()
        val px = (dx * rx + dy * ry) / (profundidade * tanV * proporcao)
        val py = (dx * ux + dy * uy + dz * uz) / (profundidade * tanV)
        return px to py
    }

    /** true se o ponto fica dentro do ecrã (com uma margem, 0..1). */
    fun naVista(x: Float, y: Float, z: Float, proporcao: Float, margem: Float = 0f): Boolean {
        val p = projetar(x, y, z, proporcao) ?: return false
        return abs(p.first) <= 1f - margem && abs(p.second) <= 1f - margem
    }

    companion object {
        const val METADE_COMPRIMENTO = 52.5f
        const val METADE_LARGURA = 34f
        /** Fração da posição lateral da bola que a câmara acompanha. */
        const val SEGUE_Y = 0.6f
    }
}
