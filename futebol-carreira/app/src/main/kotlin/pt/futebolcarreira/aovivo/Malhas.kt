package pt.futebolcarreira.aovivo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Uma malha: triângulos com posição e normal (6 números por vértice), a partir da posição "inicio" do buffer comum. */
class Malha(val inicio: Int, val vertices: Int)

/** Formas básicas feitas à mão (sem OpenGL, para se poder testar). */
object Malhas {
    const val FLOATS_POR_VERTICE = 6

    class Conjunto(val dados: FloatArray, val caixa: Malha, val esfera: Malha, val disco: Malha, val anel: Malha, val anelGrosso: Malha)

    fun criar(): Conjunto {
        val v = ArrayList<Float>(4000)
        fun fechar(inicioFloats: Int) = Malha(inicioFloats / FLOATS_POR_VERTICE, (v.size - inicioFloats) / FLOATS_POR_VERTICE)
        fun vert(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
            v += x; v += y; v += z; v += nx; v += ny; v += nz
        }

        // Caixa de lado 1 centrada na origem.
        var i0 = v.size
        val faces = arrayOf(
            floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, -1f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f),
        )
        for (n in faces) {
            // Dois vetores no plano da face.
            val u = if (n[0] != 0f) floatArrayOf(0f, 1f, 0f) else if (n[1] != 0f) floatArrayOf(0f, 0f, 1f) else floatArrayOf(1f, 0f, 0f)
            val w = floatArrayOf(n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0])
            val canto = { a: Float, b: Float -> floatArrayOf(0.5f * n[0] + 0.5f * a * u[0] + 0.5f * b * w[0], 0.5f * n[1] + 0.5f * a * u[1] + 0.5f * b * w[1], 0.5f * n[2] + 0.5f * a * u[2] + 0.5f * b * w[2]) }
            val c = arrayOf(canto(-1f, -1f), canto(1f, -1f), canto(1f, 1f), canto(-1f, 1f))
            for (k in intArrayOf(0, 1, 2, 0, 2, 3)) vert(c[k][0], c[k][1], c[k][2], n[0], n[1], n[2])
        }
        val caixa = fechar(i0)

        // Esfera de raio 1.
        i0 = v.size
        val lat = 8
        val lon = 12
        fun p(a: Int, b: Int, k: Int): FloatArray {
            val fi = PI * a / lat - PI / 2
            val th = 2 * PI * b / lon
            return floatArrayOf((cos(fi) * cos(th)).toFloat(), (cos(fi) * sin(th)).toFloat(), sin(fi).toFloat())
        }
        for (a in 0 until lat) for (b in 0 until lon) {
            val p1 = p(a, b, 0); val p2 = p(a + 1, b, 0); val p3 = p(a + 1, b + 1, 0); val p4 = p(a, b + 1, 0)
            for (q in arrayOf(p1, p2, p3, p1, p3, p4)) vert(q[0], q[1], q[2], q[0], q[1], q[2])
        }
        val esfera = fechar(i0)

        // Disco de raio 1 no plano xy.
        i0 = v.size
        val seg = 24
        for (s in 0 until seg) {
            val a1 = 2 * PI * s / seg
            val a2 = 2 * PI * (s + 1) / seg
            vert(0f, 0f, 0f, 0f, 0f, 1f)
            vert(cos(a1).toFloat(), sin(a1).toFloat(), 0f, 0f, 0f, 1f)
            vert(cos(a2).toFloat(), sin(a2).toFloat(), 0f, 0f, 0f, 1f)
        }
        val disco = fechar(i0)

        fun anel(interior: Float, segmentos: Int): Malha {
            val inicio = v.size
            for (s in 0 until segmentos) {
                val a1 = 2 * PI * s / segmentos
                val a2 = 2 * PI * (s + 1) / segmentos
                val c1 = cos(a1).toFloat(); val s1 = sin(a1).toFloat(); val c2 = cos(a2).toFloat(); val s2 = sin(a2).toFloat()
                vert(c1, s1, 0f, 0f, 0f, 1f); vert(c2, s2, 0f, 0f, 0f, 1f); vert(c2 * interior, s2 * interior, 0f, 0f, 0f, 1f)
                vert(c1, s1, 0f, 0f, 0f, 1f); vert(c2 * interior, s2 * interior, 0f, 0f, 0f, 1f); vert(c1 * interior, s1 * interior, 0f, 0f, 0f, 1f)
            }
            return fechar(inicio)
        }
        val fino = anel(0.992f, 64)
        val grosso = anel(0.78f, 32)
        return Conjunto(v.toFloatArray(), caixa, esfera, disco, fino, grosso)
    }
}
