package pt.futebolcarreira

import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Gravador
import pt.futebolcarreira.nucleo.carreira.ResumoGravacao
import java.io.File

/**
 * Guarda as carreiras em ficheiros, na memória privada da aplicação.
 * Cada carreira tem três ficheiros dentro da pasta "carreiras":
 *   <id>.resumo.json   informação curta para a lista do menu
 *   <id>.json          estado da época (escrito depois de cada jornada)
 *   <id>.bd.json       base de dados da carreira (escrito no início de cada época)
 */
class Armazem(pastaDaApp: File) {
    private val pasta = File(pastaDaApp, "carreiras")

    /** Carreiras gravadas, da mais recente para a mais antiga. */
    fun listar(): List<ResumoGravacao> =
        pasta.listFiles { f -> f.name.endsWith(".resumo.json") }.orEmpty()
            .mapNotNull { f -> try { Gravador.resumoDeTexto(f.readText()) } catch (e: java.io.IOException) { null } }
            .sortedByDescending { it.gravadaEm }

    fun gravar(carreira: Carreira) {
        pasta.mkdirs()
        val id = carreira.info.id
        if (carreira.baseDadosPorGravar || !File(pasta, "$id.bd.json").exists()) {
            escrever("$id.bd.json", Gravador.baseDadosParaTexto(carreira))
            carreira.baseDadosPorGravar = false
        }
        escrever("$id.json", Gravador.paraTexto(carreira))
        escrever("$id.resumo.json", Gravador.resumoParaTexto(Gravador.resumo(carreira)))
    }

    /** Lança uma exceção se os ficheiros não existirem ou estiverem estragados. */
    fun carregar(id: String): Carreira =
        Gravador.deTexto(File(pasta, "$id.json").readText(), File(pasta, "$id.bd.json").readText())

    fun apagar(id: String) {
        for (sufixo in listOf(".resumo.json", ".json", ".bd.json")) File(pasta, id + sufixo).delete()
    }

    /** Escreve primeiro num ficheiro temporário, para nunca deixar uma gravação a meio. */
    private fun escrever(nome: String, texto: String) {
        val temporario = File(pasta, "$nome.tmp")
        temporario.writeText(texto)
        val destino = File(pasta, nome)
        if (!temporario.renameTo(destino)) {
            destino.delete()
            temporario.renameTo(destino)
        }
    }
}
