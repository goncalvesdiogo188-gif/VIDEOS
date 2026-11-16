package pt.futebolcarreira.aovivo

import org.junit.Assert.assertEquals
import org.junit.Test

class MatrizesTest {

    private val eps = 1e-5f

    private fun identidade(): FloatArray = FloatArray(16).also {
        for (i in 0 until 4) it[i + 4 * i] = 1f
    }

    private fun afirmarIguais(esperado: FloatArray, obtido: FloatArray) {
        for (i in 0 until 16) {
            assertEquals("posição $i", esperado[i], obtido[i], eps)
        }
    }

    @Test
    fun `rodar 90 graus em z sobre a identidade`() {
        val m = identidade()
        Mat4.rodar(m, 90f, 0f, 0f, 1f)
        assertEquals(0f, m[0], eps)
        assertEquals(1f, m[1], eps)
        assertEquals(-1f, m[4], eps)
        assertEquals(0f, m[5], eps)
        assertEquals(1f, m[10], eps)
        assertEquals(1f, m[15], eps)
    }

    @Test
    fun `rodar 0 graus deixa a matriz inalterada`() {
        val m = FloatArray(16) { (it * 0.37f) - 2f }
        val original = m.copyOf()
        Mat4.rodar(m, 0f, 0f, 1f, 0f)
        afirmarIguais(original, m)
    }

    @Test
    fun `30 graus seguidos de 60 em y igualam 90 graus em y`() {
        val dois = identidade()
        Mat4.rodar(dois, 30f, 0f, 1f, 0f)
        Mat4.rodar(dois, 60f, 0f, 1f, 0f)

        val um = identidade()
        Mat4.rodar(um, 90f, 0f, 1f, 0f)

        afirmarIguais(um, dois)
    }

    @Test
    fun `eixo nao normalizado da o mesmo resultado`() {
        val normalizado = identidade()
        Mat4.rodar(normalizado, 45f, 0f, 0f, 1f)

        val nao = identidade()
        Mat4.rodar(nao, 45f, 0f, 0f, 2f)

        afirmarIguais(normalizado, nao)
    }

    @Test
    fun `rotacao preserva a translacao`() {
        val m = identidade()
        m[12] = 5f
        m[13] = 7f
        Mat4.rodar(m, 90f, 0f, 0f, 1f)
        assertEquals(5f, m[12], eps)
        assertEquals(7f, m[13], eps)
        assertEquals(0f, m[14], eps)
        assertEquals(1f, m[15], eps)
    }
}
