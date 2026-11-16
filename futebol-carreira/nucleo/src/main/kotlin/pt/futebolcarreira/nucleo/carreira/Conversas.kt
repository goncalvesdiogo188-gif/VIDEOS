package pt.futebolcarreira.nucleo.carreira

import pt.futebolcarreira.nucleo.modelos.Jogador

/** O tom de uma palestra. */
enum class TomPalestra(val nome: String) { MOTIVAR("Motivar"), PRESSIONAR("Pressionar"), ACALMAR("Acalmar") }

/**
 * F2: palestras antes do jogo e ao intervalo (motivar, pressionar, acalmar) e conversas individuais
 * (jogador descontente, suplente, jovem). Os efeitos são pequenos e reais: mexem na moral, que conta em cada jogo.
 */
object Conversas {

    // ------------------------------------------------ palestra antes do jogo

    internal fun antesDoJogo(e: Emocao, jogo: JogoDoClube) {
        val c = e.c
        if (e.estado.decisoes.any { it.tipo == "palestra" }) return
        val importante = Imprensa.importancia(c, jogo) != null
        val moral = e.moralMedia()
        val ultimo = c.ultimoJogo()
        val perdeu = ultimo != null && ultimo.golosMarcados < ultimo.golosSofridos
        if (!importante && moral >= 47 && !perdeu) return
        if (!e.marcar("pal/${jogo.competicaoId}/${jogo.data}")) return
        val dif = c.equipa.forca().geral - jogo.adversario.forca().geral   // positivo = somos mais fortes
        val favorito = dif >= 2
        val frageis = moral < 47
        val adv = jogo.adversario.nome
        val motivar = OpcaoDecisao(
            "Motivar: «Acreditem em vocês. Somos mais fortes quando jogamos juntos.»",
            if (frageis || perdeu) "Boa ideia com o moral em baixo." else "Sempre ajuda.",
            "O grupo sai do balneário de cabeça levantada.",
            Efeitos(moral = if (frageis || perdeu) 4 else 3),
        )
        val pressionar = if (favorito && !frageis) OpcaoDecisao(
            "Pressionar: «Quero a vitória. Sem desculpas.»", "Boa ideia contra um adversário mais fraco.",
            "Os jogadores entram em campo com fome de ganhar.", Efeitos(moral = 4), risco = 15,
            respostaFalha = "Alguns jogadores sentiram o peso das tuas palavras.", efeitosFalha = Efeitos(moral = -3),
        ) else OpcaoDecisao(
            "Pressionar: «Quero a vitória. Sem desculpas.»",
            if (frageis) "Arriscado com o moral em baixo." else "Arriscado contra um adversário forte.",
            "Os jogadores entram em campo com fome de ganhar.", Efeitos(moral = 4), risco = 50,
            respostaFalha = "As palavras duras fizeram tremer um grupo já nervoso.", efeitosFalha = Efeitos(moral = -5),
        )
        val acalmar = OpcaoDecisao(
            "Acalmar: «Joguem à vontade. Façam o que sabem.»",
            if (!favorito || importante) "Boa ideia num jogo grande." else "Seguro, mas não entusiasma.",
            "O grupo respira fundo e entra tranquilo.", Efeitos(moral = if (!favorito || importante) 4 else 2),
        )
        val titulo = if (importante) "Palestra antes do jogo grande" else "Palestra antes do jogo"
        val texto = when {
            perdeu && frageis -> "A derrota e o moral em baixo pesam no balneário. O que dizes aos jogadores antes de defrontar o $adv?"
            importante -> "O jogo com o $adv é importante. O balneário espera as tuas palavras."
            else -> "O moral do plantel está em baixo (${moral.toInt()}). Uma palavra certa antes do jogo com o $adv pode ajudar."
        }
        e.nova("palestra", titulo, texto, listOf(motivar, pressionar, acalmar), "pal/${jogo.data}")
    }

    // ------------------------------------------------ palestra ao intervalo

    /** Aplica a palestra do intervalo aos jogadores aptos. Devolve o texto (com a variação da moral). */
    internal fun intervalo(e: Emocao, tom: TomPalestra, golosMeus: Int, golosDeles: Int): String {
        val saldo = golosMeus - golosDeles
        val moral = e.moralMedia()
        val sorte = e.rng("intervalo/$golosMeus-$golosDeles/${tom.name}").nextInt(100)
        val (delta, texto) = when (tom) {
            TomPalestra.MOTIVAR -> (if (saldo < 0) 3 else 2) to "Pedes mais confiança e mais intensidade. O grupo responde."
            TomPalestra.PRESSIONAR -> when {
                saldo < 0 && moral >= 50 && sorte >= 20 -> 4 to "Exiges uma reação. Os jogadores voltam ao relvado com raiva."
                saldo < 0 && sorte >= 50 -> 3 to "Exiges uma reação e o grupo ouve-te."
                saldo < 0 -> -3 to "Subiste o tom e o grupo, nervoso, ficou ainda mais tenso."
                else -> -2 to "Ganhar e ouvir pressão: os jogadores não perceberam o tom."
            }
            TomPalestra.ACALMAR -> when {
                saldo > 0 -> 3 to "Pedes calma para gerir a vantagem. O grupo concentra-se."
                saldo == 0 -> 2 to "Acalmas os ânimos e pedes paciência. O grupo sai mais tranquilo."
                else -> 1 to "Pedes calma, mas o resultado ainda pesa."
            }
        }
        for (j in e.plantel().filter { it.disponivel }) e.mudarMoral(j, delta)
        val s = if (delta > 0) "+$delta" else "$delta"
        e.registar("Palestra ao intervalo (${tom.nome.lowercase()}, $golosMeus-$golosDeles): moral da equipa $s.")
        return "$texto\nMoral da equipa $s."
    }

    // ------------------------------------------------ conversas individuais

    internal fun aposJogo(e: Emocao, jogo: JogoDoClube) {
        val c = e.c
        if (e.estado.decisoes.any { it.tipo == "conversa" } || !e.passou("conversa", 8)) return
        if (e.rng("conversa/${jogo.data}").nextInt(100) >= 45) return
        val jornada = c.estadoLiga.jornada
        if (jornada < 4) return
        val onze = c.equipa.melhorOnze().mapNotNull { it.jogador?.id }.toSet()
        val aptos = e.plantel().filter { e.estado.conversas[it.id]?.let { d -> e.g.data.toEpochDay() - java.time.LocalDate.parse(d).toEpochDay() < 30 } != true }
        val descontente = aptos.filter { it.moral <= 38 && it.epoca.jogos >= 2 }.minByOrNull { it.moral }
        val suplente = aptos.filter { it.id !in onze && it.epoca.jogos <= maxOf(1, jornada / 3) && it.moral <= 54 && it.idade >= 22 }
            .maxByOrNull { it.geral() }
        val jovem = aptos.filter { it.idade <= 21 && it.potencial - it.geral() >= 8 && it.id !in onze && it.moral <= 62 }.maxByOrNull { it.potencial }
        val (jogador, tipo) = when {
            descontente != null -> descontente to "descontente"
            suplente != null -> suplente to "suplente"
            jovem != null -> jovem to "jovem"
            else -> return
        }
        if (!e.marcar("conv/${jogador.id}/${jogo.data}")) return
        e.estado.conversas[jogador.id] = e.g.data.toString()
        val d = when (tipo) {
            "descontente" -> descontente(e, jogador)
            "suplente" -> suplente(e, jogador)
            else -> jovem(e, jogador)
        }
        e.nova("conversa", d.first, d.second, d.third, "conv/${jogador.id}", jogadorId = jogador.id)
    }

    private fun descontente(e: Emocao, j: Jogador): Triple<String, String, List<OpcaoDecisao>> = Triple(
        "Conversa com ${j.nome}",
        "${j.nome} (${j.posicao}, ${j.idade} anos) anda abatido: o moral está em ${j.moral}. Pediu para falar contigo.",
        listOf(
            OpcaoDecisao("Ouvir e apoiar: «Estou aqui para ajudar.»", "Seguro.", "${j.nome} sente-se ouvido e agradece.", Efeitos(alvo = 6)),
            OpcaoDecisao(
                "Exigir mais: «Preciso de ti ao teu melhor nível.»", "Arriscado: pode motivar ou desanimar.",
                "${j.nome} aceita o desafio e promete reagir.", Efeitos(alvo = 7), risco = 40,
                respostaFalha = "${j.nome} saiu zangado do gabinete.", efeitosFalha = Efeitos(alvo = -6),
            ),
            OpcaoDecisao("Dar-lhe um dia de folga.", "Tira pressão, mas o grupo repara.", "${j.nome} recupera as forças.", Efeitos(moral = -1, alvo = 5)),
        ),
    )

    private fun suplente(e: Emocao, j: Jogador): Triple<String, String, List<OpcaoDecisao>> = Triple(
        "${j.nome} quer jogar",
        "${j.nome} (${j.posicao}, ${j.idade} anos, geral ${j.geral()}) queixa-se de falta de minutos e pede uma resposta clara.",
        listOf(
            OpcaoDecisao(
                "Prometer minutos: «Vais jogar nos próximos jogos.»", "Ele fica feliz, mas tens de cumprir (2 jogos em 5).",
                "${j.nome} sorri pela primeira vez em semanas.", Efeitos(alvo = 9), promessaJogos = 2,
            ),
            OpcaoDecisao(
                "Pedir paciência: «Trabalha e a oportunidade vem.»", "Pode não convencer.",
                "${j.nome} aceita, mas sem grande entusiasmo.", Efeitos(alvo = 3), risco = 30,
                respostaFalha = "${j.nome} não gostou da resposta.", efeitosFalha = Efeitos(alvo = -5),
            ),
            OpcaoDecisao(
                "Ser franco: «Se queres sair, não te vou impedir.»", "Honesto, mas desmotiva.",
                "${j.nome} agradece a franqueza, mas fica a pensar no futuro.", Efeitos(alvo = -2, moral = 0),
            ),
        ),
    )

    private fun jovem(e: Emocao, j: Jogador): Triple<String, String, List<OpcaoDecisao>> = Triple(
        "O jovem ${j.nome}",
        "${j.nome} tem ${j.idade} anos e muito potencial (${j.potencial}). Pergunta quando lhe vais dar uma oportunidade.",
        listOf(
            OpcaoDecisao(
                "Elogiar e prometer uma estreia: «O teu momento vai chegar.»", "Motiva muito, mas é uma promessa (1 jogo em 5).",
                "${j.nome} sai do gabinete a sonhar com a estreia.", Efeitos(alvo = 8), promessaJogos = 1,
            ),
            OpcaoDecisao("Pedir paciência e trabalho.", "Seguro.", "${j.nome} compreende e promete trabalhar.", Efeitos(alvo = 3)),
            OpcaoDecisao(
                "Exigir humildade: «Ainda tens muito a aprender.»", "Pode endurecer o carácter... ou ferir.",
                "A lição foi dura, mas ${j.nome} percebeu.", Efeitos(alvo = 4), risco = 35,
                respostaFalha = "${j.nome} ficou de rastos com as palavras duras.", efeitosFalha = Efeitos(alvo = -6),
            ),
        ),
    )
}
