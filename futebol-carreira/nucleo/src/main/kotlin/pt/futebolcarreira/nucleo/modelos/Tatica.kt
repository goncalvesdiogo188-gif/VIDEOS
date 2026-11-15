package pt.futebolcarreira.nucleo.modelos

/**
 * Mentalidade da equipa: mexe na força de ataque e de defesa (em pontos de "geral").
 * Atacar mais dá mais golos marcados, mas também mais golos sofridos.
 */
enum class Mentalidade(val nome: String, val ataque: Double, val defesa: Double) {
    DEFENSIVA("Defensiva", -3.0, 2.5),
    EQUILIBRADA("Equilibrada", 0.0, 0.0),
    OFENSIVA("Ofensiva", 2.5, -3.0),
}

/**
 * Formação: os 11 lugares em campo e um pequeno bónus de ataque/defesa
 * (uma formação com mais avançados ataca melhor e defende pior).
 */
enum class Formacao(val nome: String, val lugares: List<String>, val ataque: Double, val defesa: Double) {
    F433("4-3-3", listOf("GR", "DD", "DC", "DC", "DE", "MDC", "MC", "MC", "ED", "EE", "PL"), 1.0, -1.0),
    F442("4-4-2", listOf("GR", "DD", "DC", "DC", "DE", "ED", "MC", "MC", "EE", "PL", "PL"), 0.5, -0.5),
    F4231("4-2-3-1", listOf("GR", "DD", "DC", "DC", "DE", "MDC", "MDC", "ED", "MOC", "EE", "PL"), 0.0, 0.0),
    F352("3-5-2", listOf("GR", "DC", "DC", "DC", "ED", "MDC", "MC", "MC", "EE", "PL", "PL"), 1.0, -1.0),
    F532("5-3-2", listOf("GR", "DD", "DC", "DC", "DC", "DE", "MDC", "MC", "MC", "PL", "PL"), -2.0, 2.0),
}

/** O que a equipa treina mais: os atributos do foco evoluem mais depressa e os outros mais devagar. */
enum class FocoTreino(val nome: String, val atributos: Set<String>) {
    EQUILIBRADO("Equilibrado", emptySet()),
    ATAQUE("Ataque", setOf("remate", "drible")),
    POSSE("Posse de bola", setOf("passe", "drible")),
    DEFESA("Defesa", setOf("defesa", "guarda_redes")),
    FISICO("Físico", setOf("ritmo", "fisico")),
}

/** Intensidade do treino: mais carga faz evoluir mais depressa, mas traz mais lesões. */
enum class Intensidade(val nome: String, val crescimento: Double, val riscoLesao: Double) {
    LEVE("Leve", 0.8, 0.0005),
    NORMAL("Normal", 1.0, 0.002),
    FORTE("Forte", 1.2, 0.006),
}
