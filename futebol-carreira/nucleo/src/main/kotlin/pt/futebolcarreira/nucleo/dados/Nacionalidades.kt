package pt.futebolcarreira.nucleo.dados

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Nacionalidades dos jogadores: um só código por nação e o nome de cada país em português
 * (ficheiro "nacionalidades.json": código -> nome).
 */
class Nacionalidades(private val nomes: Map<String, String> = emptyMap()) {
    /** Nome do país (o próprio código se não se conhecer). */
    fun nome(codigo: String): String = nomes[normalizar(codigo)] ?: nomes[codigo] ?: codigo

    fun conhece(codigo: String): Boolean = codigo in nomes

    companion object {
        /** Códigos alternativos que aparecem nos dados -> o código usado pelas seleções. */
        val ALIAS: Map<String, String> = mapOf(
            "CZE" to "RCH", "SCO" to "ESC", "ECU" to "EQU", "CMR" to "CAM", "GHA" to "GAN", "CAV" to "CPV",
            "COD" to "RDC", "GER" to "ALE", "ENG" to "ING", "MAL" to "MLI", "SWE" to "SUE", "BUR" to "BFA",
        )

        /** O código único de uma nação (aplica a tabela de alternativos). */
        fun normalizar(codigo: String): String = ALIAS[codigo] ?: codigo

        fun deTexto(texto: String?): Nacionalidades {
            if (texto == null) return Nacionalidades()
            return try {
                Nacionalidades(BaseDados.JSON.decodeFromString(MapSerializer(String.serializer(), String.serializer()), texto))
            } catch (e: Exception) {
                Nacionalidades()
            }
        }
    }
}
