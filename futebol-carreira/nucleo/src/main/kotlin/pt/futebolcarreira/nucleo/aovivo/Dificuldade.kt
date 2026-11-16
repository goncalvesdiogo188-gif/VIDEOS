package pt.futebolcarreira.nucleo.aovivo

/**
 * Dificuldade do jogo ao vivo. Mexe na inteligência da equipa que o utilizador NÃO comanda
 * (com "semUtilizador" as duas equipas usam a dificuldade escolhida; os colegas do utilizador jogam sempre como em [NORMAL]).
 * A ajuda de mira ([ajudaMira]) é para os controlos do utilizador (0 = nenhuma, 1 = máxima).
 */
enum class Dificuldade(
    val nome: String,
    /** Altura da linha defensiva, em metros a somar à linha normal (positivo = mais subida e mais fora de jogo). */
    val linhaDefensiva: Float,
    /** Distância (m) à bola dentro da qual um jogador adversário pressiona. */
    val raioPressao: Float,
    /** Segundos de pressão coletiva logo a seguir a perder a bola (0 = não há). */
    val contraPressao: Float,
    /** 0..1: quanto os defesas e médios seguem o adversário em vez de guardarem a zona. */
    val marcacao: Float,
    /** Multiplicador da velocidade dos jogadores da IA. */
    val velocidade: Float,
    /** Soma à probabilidade de desarme com sucesso. */
    val bonusDesarme: Float,
    /** Soma à probabilidade de defesa do guarda-redes. */
    val bonusGuardaRedes: Float,
    /** Multiplicador do erro nos passes da IA (menor = mais certeiro). */
    val erroPasse: Float,
    /** Multiplicador do erro nos remates da IA. */
    val erroRemate: Float,
    /** Multiplicador do tempo que a IA demora a decidir com a bola (menor = decide mais depressa). */
    val tempoDecisao: Float,
    /** Probabilidade de a IA respeitar o fora de jogo (nos passes e nas desmarcações). */
    val consciencia: Float,
    /** Probabilidade de a saída de bola do guarda-redes ser curta e apoiada. */
    val saidaCurta: Float,
    /** Probabilidade (por décimo de segundo) de o pressionante tentar um carrinho. */
    val carrinho: Float,
    /** Para o utilizador: quanto a mira dos passes e remates é ajudada (0..1). */
    val ajudaMira: Float,
    /** 0..1: quanto a equipa se fecha à volta da bola quando defende. */
    val compactacao: Float,
) {
    FACIL("Fácil", -4f, 12f, 0f, 0.30f, 0.95f, -0.06f, -0.08f, 1.4f, 1.3f, 1.4f, 0.55f, 0.30f, 0.02f, 0.6f, 0.0f),
    NORMAL("Normal", 0f, 20f, 0f, 0.45f, 1.00f, 0.00f, 0.00f, 1.0f, 1.0f, 1.0f, 0.85f, 0.55f, 0.04f, 0.3f, 0.10f),
    DIFICIL("Difícil", 5f, 24f, 2.5f, 0.65f, 1.03f, 0.05f, 0.05f, 0.8f, 0.9f, 0.85f, 0.95f, 0.70f, 0.05f, 0.1f, 0.20f),
    LENDARIO("Lendário", 9f, 28f, 4f, 0.80f, 1.06f, 0.10f, 0.10f, 0.6f, 0.8f, 0.7f, 1.00f, 0.85f, 0.06f, 0.0f, 0.30f);

    companion object {
        /** Para gravar/ler a escolha do utilizador por nome (aceita também o nome do enum); devolve [NORMAL] se não reconhecer. */
        fun deTexto(t: String?): Dificuldade = entries.firstOrNull { it.name.equals(t, true) || it.nome.equals(t, true) } ?: NORMAL
    }
}
