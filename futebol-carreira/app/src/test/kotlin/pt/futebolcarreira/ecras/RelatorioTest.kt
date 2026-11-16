package pt.futebolcarreira.ecras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

class RelatorioTest {
    private val dados = DadosRelatorio("0.12", "Marca Modelo", "Android 14 (API 34)", "Clube: FC Teste\nData de jogo: 12/08/2027", "O botão não responde")
    private val agora = LocalDateTime.of(2026, 11, 16, 9, 5, 7)

    @Test
    fun textoTemTodasAsSecoes() {
        val t = Relatorio.montarTexto(dados, agora)
        assertTrue(t.contains("16/11/2026 09:05:07"))
        assertTrue(t.contains("Versão da app: 0.12"))
        assertTrue(t.contains("Marca Modelo"))
        assertTrue(t.contains("Clube: FC Teste"))
        assertTrue(t.contains("O botão não responde"))
    }

    @Test
    fun notaVaziaFicaIndicada() {
        assertTrue(Relatorio.montarTexto(dados.copy(nota = "  "), agora).contains("(sem nota)"))
    }

    @Test
    fun gravaFicheiroEApagaOsMaisAntigos() {
        val pasta = kotlin.io.path.createTempDirectory("relatorios").toFile()
        try {
            repeat(23) { i -> Relatorio.gravar(pasta, dados, null, agora.plusSeconds(i.toLong())) }
            val ficheiros = File(pasta, Relatorio.PASTA).listFiles()!!.map { it.name }
            assertEquals(20, ficheiros.size)
            assertFalse(ficheiros.contains("relatorio-20261116-090507.txt"))
            assertTrue(ficheiros.contains("relatorio-20261116-090529.txt"))
        } finally {
            pasta.deleteRecursively()
        }
    }
}
