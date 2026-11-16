package pt.futebolcarreira.aovivo

import java.util.Locale

/**
 * Mede o desempenho do desenho 3D: imagens por segundo, tempo médio e pior tempo de uma imagem.
 * O fio do OpenGL chama [imagem] uma vez por imagem (só aritmética, sem criar objetos); a cada
 * [janelaNs] (1 s por defeito) fecha uma janela de medição e publica os números, que o ecrã lê de outro fio.
 */
class MedidorDesempenho(private val janelaNs: Long = 1_000_000_000L) {
    @Volatile var imagensPorSegundo = 0f
        private set
    /** Tempo médio entre imagens, em milissegundos. */
    @Volatile var mediaMs = 0f
        private set
    /** Pior tempo entre imagens na última janela, em milissegundos. */
    @Volatile var piorMs = 0f
        private set

    private var ultimoNs = 0L
    private var inicioJanelaNs = 0L
    private var contagem = 0
    private var somaNs = 0L
    private var piorNs = 0L

    /** Regista que uma imagem foi desenhada no instante [agoraNs] (System.nanoTime). */
    fun imagem(agoraNs: Long) {
        if (ultimoNs == 0L) {
            ultimoNs = agoraNs
            inicioJanelaNs = agoraNs
            return
        }
        val dt = agoraNs - ultimoNs
        ultimoNs = agoraNs
        contagem++
        somaNs += dt
        if (dt > piorNs) piorNs = dt
        val decorrido = agoraNs - inicioJanelaNs
        if (decorrido >= janelaNs) {
            imagensPorSegundo = contagem * 1e9f / decorrido
            mediaMs = somaNs / contagem / 1e6f
            piorMs = piorNs / 1e6f
            inicioJanelaNs = agoraNs
            contagem = 0
            somaNs = 0L
            piorNs = 0L
        }
    }

    /** Recomeça do zero (por exemplo, depois de uma pausa em que não se desenhou nada). */
    fun reiniciar() {
        ultimoNs = 0L
        inicioJanelaNs = 0L
        contagem = 0
        somaNs = 0L
        piorNs = 0L
        imagensPorSegundo = 0f
        mediaMs = 0f
        piorMs = 0f
    }

    /** Texto curto para o canto do ecrã, com a memória usada pela aplicação em MB. */
    fun texto(memoriaMb: Long = memoriaUsadaMb()): String = formatar(imagensPorSegundo, mediaMs, piorMs, memoriaMb)

    companion object {
        /** Memória Java usada neste momento, em MB. */
        fun memoriaUsadaMb(): Long {
            val r = Runtime.getRuntime()
            return (r.totalMemory() - r.freeMemory()) / (1024 * 1024)
        }

        fun formatar(fps: Float, mediaMs: Float, piorMs: Float, memoriaMb: Long): String =
            String.format(Locale.US, "%.0f im/s · média %.1f ms · pior %.1f ms · %d MB", fps, mediaMs, piorMs, memoriaMb)
    }
}
