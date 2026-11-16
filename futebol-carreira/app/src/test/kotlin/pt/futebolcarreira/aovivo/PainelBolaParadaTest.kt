package pt.futebolcarreira.aovivo

import kotlin.math.PI
import kotlin.math.abs
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import pt.futebolcarreira.nucleo.aovivo.BolaParada
import pt.futebolcarreira.nucleo.aovivo.TipoParada

class PainelBolaParadaTest {
    private fun parada(tipo: TipoParada, doUtilizador: Boolean, guardaRedes: Boolean = false) = BolaParada(
        tipo, true, 0f, 0f, 1, 9, doUtilizador, 8f, 20f, 0f, true, false, false, guardaRedes,
    )

    @Test
    fun anguloEmFrenteNoSentidoPositivoEIgual() {
        assertEquals(0.5f, anguloDoCampo(0.5f, 1), 1e-6f)
    }

    @Test
    fun anguloNoSentidoNegativoViraMeiaVolta() {
        assertTrue(abs(anguloDoCampo(0f, -1) - PI.toFloat()) < 1e-6f)
    }

    @Test
    fun ladoLongeInverteComOSentido() {
        assertEquals(1f, ladoDoCampo(1f, 1), 1e-6f)
        assertEquals(-1f, ladoDoCampo(1f, -1), 1e-6f)
        assertEquals(0f, ladoDoCampo(0f, -1), 1e-6f)
    }

    @Test
    fun painelSoApareceQuandoHaLanceDoUtilizador() {
        assertFalse(precisaPainel(null))
        assertTrue(precisaPainel(parada(TipoParada.CANTO, true)))
        assertFalse(precisaPainel(parada(TipoParada.CANTO, false)))
        assertFalse(precisaPainel(parada(TipoParada.PONTAPE_SAIDA, true)))
        assertTrue(precisaPainel(parada(TipoParada.PENALTI, false, guardaRedes = true)))
    }
}
