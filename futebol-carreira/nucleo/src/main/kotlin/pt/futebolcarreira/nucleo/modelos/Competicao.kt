package pt.futebolcarreira.nucleo.modelos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Um país da base de dados. */
@Serializable
class Pais(val id: String = "", val nome: String = "")

/**
 * Uma competição: liga (todos contra todos, tipo "liga"), taça nacional (eliminatórias, tipo "taca")
 * ou supertaça (um só jogo, tipo "supertaca").
 */
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
    /** Taças: true = cada eliminatória é a duas mãos (as nacionais são a jogo único). */
    @SerialName("ida_e_volta") val idaEVolta: Boolean = false,
    /** Taças: a final joga-se em campo neutro. */
    @SerialName("final_neutra") val finalNeutra: Boolean = true,
    /** Supertaça: a liga cujo campeão joga e a taça cujo vencedor joga. */
    @SerialName("liga_ref") val ligaRef: String = "",
    @SerialName("taca_ref") val tacaRef: String = "",
) {
    val eLiga get() = tipo == "liga"
    val eTaca get() = tipo == "taca"
    val eSupertaca get() = tipo == "supertaca"
}

/** Listas de nomes próprios e apelidos de um país (para gerar jogadores). */
@Serializable
class ListaNomes(val nomes: List<String> = emptyList(), val apelidos: List<String> = emptyList())
