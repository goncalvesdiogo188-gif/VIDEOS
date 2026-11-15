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
