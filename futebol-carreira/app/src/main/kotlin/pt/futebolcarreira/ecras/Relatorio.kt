package pt.futebolcarreira.ecras

import android.graphics.Bitmap
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** O que um relatório de problema leva: tudo texto simples, para se poder ler sem a aplicação. */
data class DadosRelatorio(
    val versaoApp: String,
    val telemovel: String,
    val android: String,
    /** Resumo do estado do jogo (clube, data de jogo, ecrã atual…). */
    val estado: String,
    val nota: String,
)

/** Ficheiros de um relatório gravado. */
data class RelatorioGravado(val texto: File, val imagem: File?)

/** Escreve os relatórios de problemas em «<pasta de ficheiros da app>/relatorios/». */
object Relatorio {
    const val PASTA = "relatorios"
    private const val MAXIMO_GUARDADOS = 20
    private val NOME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val LEGIVEL = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    fun montarTexto(dados: DadosRelatorio, agora: LocalDateTime): String = buildString {
        appendLine("RELATÓRIO DE PROBLEMA — Futebol Carreira")
        appendLine("Data e hora: ${agora.format(LEGIVEL)}")
        appendLine("Versão da app: ${dados.versaoApp}")
        appendLine("Telemóvel: ${dados.telemovel} (${dados.android})")
        appendLine()
        appendLine("ESTADO DO JOGO")
        appendLine(dados.estado.trim())
        appendLine()
        appendLine("NOTA")
        appendLine(dados.nota.trim().ifEmpty { "(sem nota)" })
    }

    /**
     * Grava o relatório (e a imagem, se houver) e apaga os mais antigos, para a pasta não crescer sem limite.
     * Lança IOException se não conseguir escrever.
     */
    fun gravar(pastaDaApp: File, dados: DadosRelatorio, imagem: Bitmap?, agora: LocalDateTime = LocalDateTime.now()): RelatorioGravado {
        val pasta = File(pastaDaApp, PASTA)
        pasta.mkdirs()
        val base = "relatorio-${agora.format(NOME)}"
        val texto = File(pasta, "$base.txt")
        texto.writeText(montarTexto(dados, agora))
        var png: File? = null
        if (imagem != null) {
            val f = File(pasta, "$base.png")
            try {
                f.outputStream().use { imagem.compress(Bitmap.CompressFormat.PNG, 90, it) }
                png = f
            } catch (e: java.io.IOException) {
                f.delete()
            }
        }
        limparAntigos(pasta)
        return RelatorioGravado(texto, png)
    }

    private fun limparAntigos(pasta: File) {
        val antigos = pasta.listFiles { f -> f.name.endsWith(".txt") }.orEmpty().sortedByDescending { it.name }.drop(MAXIMO_GUARDADOS)
        for (t in antigos) {
            t.delete()
            File(pasta, t.name.removeSuffix(".txt") + ".png").delete()
        }
    }
}
