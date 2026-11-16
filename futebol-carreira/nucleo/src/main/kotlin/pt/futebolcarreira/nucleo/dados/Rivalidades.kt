package pt.futebolcarreira.nucleo.dados

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Uma rivalidade entre dois clubes (um derby). "intensidade": 1 (rivalidade), 2 (grande derby) ou 3 (clássico). */
@Serializable
class Rivalidade(
    val a: String,
    val b: String,
    val nome: String,
    @SerialName("intensidade") val intensidade: Int = 1,
) {
    fun envolve(x: String, y: String) = (x == a && y == b) || (x == b && y == a)
}

/** Todas as rivalidades da base de dados (ficheiro "rivalidades.json"). Ficheiro em falta = sem derbies. */
class Rivalidades(val lista: List<Rivalidade> = emptyList()) {
    private val porPar: Map<Pair<String, String>, Rivalidade> =
        lista.filter { it.intensidade in 1..3 }.flatMap { listOf((it.a to it.b) to it, (it.b to it.a) to it) }.toMap()

    /** A rivalidade entre dois clubes, ou null. */
    fun entre(x: String, y: String): Rivalidade? = porPar[x to y]

    /** Intensidade do derby entre dois clubes (0 = não é derby). */
    fun intensidade(x: String, y: String): Int = entre(x, y)?.intensidade ?: 0

    /** Rivalidades de um clube. */
    fun doClube(id: String): List<Rivalidade> = lista.filter { it.a == id || it.b == id }

    companion object {
        /** Lê o ficheiro; texto em falta ou estragado dá uma lista vazia. */
        fun deTexto(texto: String?): Rivalidades {
            if (texto == null) return Rivalidades()
            return try {
                Rivalidades(BaseDados.JSON.decodeFromString(ListSerializer(Rivalidade.serializer()), texto))
            } catch (e: Exception) {
                Rivalidades()
            }
        }
    }
}
