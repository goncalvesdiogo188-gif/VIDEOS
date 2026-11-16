package pt.futebolcarreira.aovivo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EstadoPrototipoTest {
    @Test
    fun semJoystickNadaSeMexe() {
        val e = EstadoPrototipo()
        val x = e.jogadorX
        repeat(60) { e.passo(1f / 60f) }
        assertEquals(x, e.jogadorX, 0.0001f)
        assertEquals(0f, e.jogadorZ, 0.0001f)
    }

    @Test
    fun joystickParaAFrenteMoveOJogadorAOitoMetrosPorSegundo() {
        val e = EstadoPrototipo()
        val x = e.jogadorX
        e.definirJoystick(1f, 0f)
        repeat(60) { e.passo(1f / 60f) }
        assertEquals(x + 8f, e.jogadorX, 0.05f)
        // A bola vem atrás do jogador, mesmo à frente dele.
        assertTrue(e.bolaX > e.jogadorX)
        assertTrue(e.bolaX - e.jogadorX < 2f)
    }

    @Test
    fun jogadorNaoSaiDoCampo() {
        val e = EstadoPrototipo()
        e.definirJoystick(1f, 1f)
        repeat(60 * 60) { e.passo(1f / 60f) }
        assertEquals(EstadoPrototipo.METADE_COMPRIMENTO, e.jogadorX, 0.001f)
        assertEquals(EstadoPrototipo.METADE_LARGURA, e.jogadorZ, 0.001f)
    }

    @Test
    fun joystickForaDoIntervaloEFicaLimitado() {
        val e = EstadoPrototipo()
        val x = e.jogadorX
        e.definirJoystick(50f, 0f)
        e.passo(1f)
        assertEquals(x + 8f, e.jogadorX, 0.001f)
    }
}
