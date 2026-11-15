package pt.futebolcarreira.nucleo.simulacao

/** Transforma os acontecimentos de um jogo em frases de relato. */
object Relato {
    /** Minuto como aparece no relato: 37', 90+3'. */
    fun minuto(m: Int): String = if (m > 90) "90+${m - 90}'" else "$m'"

    /** Frase para um acontecimento. "equipa" é o nome da equipa a que o acontecimento diz respeito. */
    fun frase(e: Evento, equipa: String): String {
        val nome = e.jogador.nome
        // Escolhe uma variante da frase de forma fixa, para o mesmo jogo dar sempre o mesmo relato.
        val variante = Math.floorMod(e.jogador.id + e.minuto, 3)
        return when (e.tipo) {
            TipoEvento.GOLO -> {
                val assistencia = e.outro?.let { " Assistência de ${it.nome}." } ?: ""
                when {
                    e.penalti -> "GOLO! $nome ($equipa) não falha da marca de penálti."
                    variante == 0 -> "GOLO! $nome ($equipa) remata para o fundo da baliza.$assistencia"
                    variante == 1 -> "GOLO! $nome ($equipa) não perdoa na cara do guarda-redes.$assistencia"
                    else -> "GOLO! Grande finalização de $nome ($equipa).$assistencia"
                }
            }
            TipoEvento.DEFESA -> {
                val guardaRedes = e.outro?.nome ?: "o guarda-redes"
                if (variante == 0) "Remate de $nome ($equipa) e grande defesa de $guardaRedes."
                else "$nome ($equipa) tenta a sorte, mas $guardaRedes segura."
            }
            TipoEvento.REMATE_FORA ->
                if (variante == 0) "$nome ($equipa) remata ao lado." else "Remate de $nome ($equipa) por cima da baliza."
            TipoEvento.AMARELO -> "Cartão amarelo para $nome ($equipa)."
            TipoEvento.SEGUNDO_AMARELO -> "Segundo amarelo para $nome ($equipa), que é expulso!"
            TipoEvento.VERMELHO -> "Cartão vermelho direto para $nome ($equipa)!"
            TipoEvento.LESAO -> "$nome ($equipa) está lesionado e não pode continuar."
            TipoEvento.SUBSTITUICAO -> "Substituição ($equipa): sai $nome, entra ${e.outro?.nome ?: "?"}."
        }
    }
}
