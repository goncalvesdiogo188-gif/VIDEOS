package pt.futebolcarreira.nucleo.carreira

/**
 * O ambiente à volta do clube: o que os adeptos e a direção sentem, a forma recente e a tensão dos derbies.
 * Mexe um pouco na assistência dos jogos em casa (fator) e aparece no Início.
 */
enum class Ambiente(val nome: String, val fator: Double, val descricao: String) {
    EUFORICO("Eufórico", 1.06, "As bancadas estão em êxtase."),
    BOM("Bom", 1.03, "Há confiança no balneário e nas bancadas."),
    NORMAL("Normal", 1.0, "Sem grandes sobressaltos."),
    TENSO("Tenso", 0.96, "Os adeptos já assobiam e a direção olha de lado."),
    HOSTIL("Hostil", 0.90, "O estádio está contra a equipa.");

    companion object {
        /**
         * "adeptos" e "direcao" são as confianças (0 a 100); "pontosRecentes" os pontos nos últimos jogos
         * (de "jogosRecentes", no máximo 5); "derby" a intensidade do próximo jogo (0 a 3), que acentua o que já se sente.
         */
        fun calcular(adeptos: Int, direcao: Int, pontosRecentes: Int, jogosRecentes: Int, derby: Int = 0): Ambiente {
            val forma = if (jogosRecentes > 0) pontosRecentes * 100.0 / (3 * jogosRecentes) else 50.0
            var pontuacao = 0.5 * adeptos + 0.2 * direcao + 0.3 * forma
            pontuacao = 50 + (pontuacao - 50) * (1.0 + 0.15 * derby)
            return when {
                pontuacao >= 80 -> EUFORICO
                pontuacao >= 62 -> BOM
                pontuacao >= 42 -> NORMAL
                pontuacao >= 28 -> TENSO
                else -> HOSTIL
            }
        }
    }
}
