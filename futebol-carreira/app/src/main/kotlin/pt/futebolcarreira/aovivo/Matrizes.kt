package pt.futebolcarreira.aovivo

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Matrizes 4×4 em coluna-maior (mesma convenção do OpenGL e de `android.opengl.Matrix`),
 * sem alocar memória por chamada.
 *
 * `android.opengl.Matrix.rotateM` cria um `float[32]` temporário em cada chamada; o renderer
 * chama-o cerca de 110 vezes por imagem, o que gera lixo 60 vezes por segundo.
 *
 * Os arrays auxiliares são partilhados e não são seguros para várias threads. O renderer
 * corre sempre na thread GL, por isso isto é suficiente.
 */
object Mat4 {

    /** Matriz de rotação temporária. Preenchida em cada chamada. */
    private val r = FloatArray(16)

    /** Resultado temporário de `m * r`, copiado de volta para `m` no fim. */
    private val t = FloatArray(16)

    /**
     * Equivalente a `Matrix.rotateM(m, 0, graus, x, y, z)`: `m = m * R(graus, x, y, z)`.
     *
     * @param graus ângulo em graus.
     * @param x componente x do eixo.
     * @param y componente y do eixo.
     * @param z componente z do eixo. O eixo é normalizado se não tiver comprimento 1.
     */
    fun rodar(m: FloatArray, graus: Float, x: Float, y: Float, z: Float) {
        definirRotacao(graus, x, y, z)
        for (col in 0 until 4) {
            for (lin in 0 until 4) {
                var soma = 0f
                for (k in 0 until 4) {
                    soma += m[lin + 4 * k] * r[k + 4 * col]
                }
                t[lin + 4 * col] = soma
            }
        }
        System.arraycopy(t, 0, m, 0, 16)
    }

    /** Preenche `r` com a rotação, como `android.opengl.Matrix.setRotateM`. */
    private fun definirRotacao(graus: Float, x0: Float, y0: Float, z0: Float) {
        val a = Math.toRadians(graus.toDouble()).toFloat()
        val s = sin(a)
        val c = cos(a)

        var x = x0
        var y = y0
        var z = z0
        val len = sqrt(x * x + y * y + z * z)
        if (len != 1f) {
            val inv = 1f / len
            x *= inv
            y *= inv
            z *= inv
        }
        val nc = 1f - c

        r[0] = x * x * nc + c
        r[1] = y * x * nc + z * s
        r[2] = z * x * nc - y * s
        r[3] = 0f
        r[4] = x * y * nc - z * s
        r[5] = y * y * nc + c
        r[6] = z * y * nc + x * s
        r[7] = 0f
        r[8] = x * z * nc + y * s
        r[9] = y * z * nc - x * s
        r[10] = z * z * nc + c
        r[11] = 0f
        r[12] = 0f
        r[13] = 0f
        r[14] = 0f
        r[15] = 1f
    }
}
