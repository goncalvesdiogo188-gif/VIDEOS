package pt.futebolcarreira.nucleo.simulacao

/**
 * Um jogo jogado, guardado à espera de a carreira o transformar em notícia.
 * "competicaoId" é o id da liga, da taça ou da competição europeia ("UCL", "UEL"); "derby" é a intensidade (0 = normal).
 */
class JogoNoticia(val competicaoId: String, val tipo: String, val resultado: Resultado, val derby: Int, val fase: String = "")

/** Uma eliminatória de taça que ficou decidida (para as notícias de surpresas e finais). */
class ConfrontoNoticia(val competicaoId: String, val tipo: String, val ronda: String, val eFinal: Boolean, val confronto: Confronto)
