package pt.futebolcarreira.aovivo

import org.junit.Assert.assertEquals
import org.junit.Test

/** A versão sem alocação de Joystick.paraCampo tem de dar exatamente o mesmo resultado. */
class JoystickSemAlocacaoTest {
    @Test
    fun caminhoSemAlocacaoDaOMesmoResultadoQueParaCampo() {
        val saida = Joystick.VetorCampo()
        val entradas = listOf(0f to 0f, 0.1f to 0.05f, 0.2f to 0f, 1f to 0f, 3f to 4f, -0.5f to 0.7f, 0f to -1f)
        for ((dx, dy) in entradas) for (sentido in listOf(1, -1)) {
            Joystick.paraCampo(dx, dy, sentido, saida)
            val esperado = Joystick.paraCampo(dx, dy, sentido)
            assertEquals(esperado.first, saida.x, 0f)
            assertEquals(esperado.second, saida.y, 0f)
        }
    }
}
