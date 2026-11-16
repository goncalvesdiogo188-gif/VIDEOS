package pt.futebolcarreira.aovivo

import org.junit.Assert.assertEquals
import org.junit.Test

class MedidorDesempenhoTest {
    private val MS = 1_000_000L

    @Test
    fun sessentaImagensPorSegundoConstantes() {
        val m = MedidorDesempenho()
        var t = 5_000L
        val passo = 1_000_000_000L / 60
        repeat(130) { m.imagem(t); t += passo }
        assertEquals(60f, m.imagensPorSegundo, 0.5f)
        assertEquals(16.67f, m.mediaMs, 0.1f)
        assertEquals(16.67f, m.piorMs, 0.1f)
    }

    @Test
    fun piorTempoFicaRegistado() {
        val m = MedidorDesempenho()
        var t = 1L
        m.imagem(t)
        repeat(20) { t += 10 * MS; m.imagem(t) }
        t += 100 * MS; m.imagem(t)          // uma imagem lenta (janela de 1 s ainda não fechou)
        repeat(80) { t += 10 * MS; m.imagem(t) }
        assertEquals(100f, m.piorMs, 0.01f)
    }

    @Test
    fun semJanelaCompletaNaoPublicaNada() {
        val m = MedidorDesempenho()
        m.imagem(1L); m.imagem(1L + 10 * MS); m.imagem(1L + 20 * MS)
        assertEquals(0f, m.imagensPorSegundo, 0f)
    }

    @Test
    fun reiniciarLimpaOsNumeros() {
        val m = MedidorDesempenho()
        var t = 1L
        repeat(150) { m.imagem(t); t += 10 * MS }
        m.reiniciar()
        assertEquals(0f, m.imagensPorSegundo, 0f)
        assertEquals(0f, m.piorMs, 0f)
    }

    @Test
    fun textoTemOsQuatroNumeros() {
        assertEquals("60 im/s · média 16.7 ms · pior 33.0 ms · 120 MB", MedidorDesempenho.formatar(60f, 16.7f, 33f, 120))
    }
}
