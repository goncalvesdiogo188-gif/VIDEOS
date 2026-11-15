package pt.futebolcarreira.nucleo.modelos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Um país da base de dados. */
@Serializable
class Pais(val id: String = "", val nome: String = "")

/** Uma competição. Na Fase 1 só existem ligas (todos contra todos). */
@Serializable
class Competicao(
    val id: String = "",
    val nome: String = "",
    val pais: String = "",
    /** 1 = primeira divisão, 2 = segunda... */
    val nivel: Int = 1,
    val tipo: String = "liga",
    /** 2 = casa e fora. */
    val voltas: Int = 2,
    @SerialName("pontos_vitoria") val pontosVitoria: Int = 3,
    @SerialName("pontos_empate") val pontosEmpate: Int = 1,
    /** Quantas equipas descem (e sobem da liga abaixo). */
    val descidas: Int = 0,
    /** Id da liga para onde descem ("" = ninguém desce). */
    @SerialName("desce_para") val descePara: String = "",
)

/** Listas de nomes próprios e apelidos de um país (para gerar jogadores). */
@Serializable
class ListaNomes(val nomes: List<String> = emptyList(), val apelidos: List<String> = emptyList())
