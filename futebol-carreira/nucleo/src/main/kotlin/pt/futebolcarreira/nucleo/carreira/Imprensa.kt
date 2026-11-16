package pt.futebolcarreira.nucleo.carreira

/**
 * F1: conferências de imprensa. Antes dos jogos importantes (derbies, finais, eliminatórias, choques no topo)
 * e depois de resultados fortes (goleadas, derrotas pesadas, séries), os jornalistas fazem uma pergunta e há três
 * respostas: calma, ousada (arrisca-se) e defensiva. Mexem na moral, nos adeptos e na direção e geram manchetes.
 */
object Imprensa {
    private const val INTERVALO_DIAS = 4

    /** Porque é que o jogo é importante ("derby", "final", "europa", "taca", "topo"), ou null se não é. */
    fun importancia(c: Carreira, jogo: JogoDoClube): String? = when {
        jogo.eFinal -> "final"
        jogo.derby != null && jogo.intensidadeDerby >= 2 -> "derby"
        jogo.tipo == "europa" && jogo.confronto != null -> "europa"
        jogo.tipo != "liga" && jogo.tipo != "europa" && jogo.confronto != null && jogo.indiceRonda >= 2 -> "taca"
        jogo.eLiga && c.estadoLiga.jornada >= 8 && c.posicao() <= 3 &&
            c.estadoLiga.tabela.ordenada().indexOfFirst { it.id == jogo.adversario.id } in 0..2 -> "topo"
        else -> null
    }

    internal fun antesDoJogo(e: Emocao, jogo: JogoDoClube) {
        val c = e.c
        val chave = "imp/${jogo.competicaoId}/${jogo.data}"
        if (e.estado.decisoes.any { it.tipo == "imprensa" } || !e.passou("imprensa", INTERVALO_DIAS)) return
        val motivo = importancia(c, jogo) ?: return
        if (!e.marcar(chave)) return
        val adv = jogo.adversario.nome
        val eu = c.equipa.nome
        val nome = c.info.nome
        val (titulo, texto, opcoes) = when (motivo) {
            "derby" -> Triple(
                "Conferência de imprensa: ${jogo.derby}",
                "Véspera do ${jogo.derby}, contra o $adv. Os jornalistas querem saber como vês o jogo.",
                listOf(
                    OpcaoDecisao(
                        "«É mais um jogo de três pontos. Respeito o adversário, mas confio nos meus jogadores.»", "Calma: pouco risco.",
                        "A tua serenidade agrada às bancadas e ao balneário.", Efeitos(moral = 1, adeptos = 1, direcao = 1),
                        manchete = "«Mais um jogo de três pontos»: $nome mantém a calma antes do ${jogo.derby}",
                    ),
                    OpcaoDecisao(
                        "«Vamos ganhar. Ponto final.»", "Ousada: os adeptos adoram, mas aumenta a pressão.",
                        "Os adeptos vibram com a tua confiança.", Efeitos(moral = 2, adeptos = 4),
                        risco = 35, respostaFalha = "A frase foi lida como arrogância e a pressão sobre o plantel aumentou.",
                        efeitosFalha = Efeitos(moral = -3, adeptos = -3, direcao = -1),
                        manchete = "«Vamos ganhar»: $nome promete vitória no ${jogo.derby}",
                        manchetesFalha = "Arrogância? $nome promete vitória e abre a porta à pressão",
                    ),
                    OpcaoDecisao(
                        "«Não gosto de falar de favoritos. Cada jogo é um jogo.»", "Defensiva: sem polémica.",
                        "Resposta segura, sem polémica.", Efeitos(adeptos = -1, direcao = 1),
                    ),
                ),
            )
            "final" -> Triple(
                "Conferência de imprensa: a final",
                "A final da ${jogo.competicaoNome} é contra o $adv. A imprensa só fala disto.",
                listOf(
                    OpcaoDecisao(
                        "«Uma final joga-se, não se fala. Estamos preparados.»", "Calma.",
                        "Passas a imagem de uma equipa focada.", Efeitos(moral = 2, adeptos = 1, direcao = 1),
                        manchete = "$eu «preparado» para a final, garante $nome",
                    ),
                    OpcaoDecisao(
                        "«Vamos levantar o troféu, custe o que custar.»", "Ousada: sobe a expectativa.",
                        "A promessa incendeia os adeptos.", Efeitos(moral = 3, adeptos = 5, direcao = 1),
                        risco = 40, respostaFalha = "A promessa pesa nos ombros dos jogadores.", efeitosFalha = Efeitos(moral = -4, adeptos = -4, direcao = -2),
                        manchete = "$nome promete o troféu: «Vamos levantá-lo»",
                        manchetesFalha = "Pressão máxima: $nome promete um troféu que ainda não ganhou",
                    ),
                    OpcaoDecisao(
                        "«O $adv é favorito. Nós só temos de ser competitivos.»", "Defensiva: tira pressão ao grupo.",
                        "Tiras pressão ao plantel, mas os adeptos esperavam mais ambição.", Efeitos(moral = 2, adeptos = -2),
                    ),
                ),
            )
            "europa" -> Triple(
                "Conferência de imprensa: noite europeia",
                "Há jogo da ${jogo.competicaoNome} contra o $adv. Perguntam-te se o $eu está à altura.",
                listOf(
                    OpcaoDecisao(
                        "«Respeitamos a Europa, mas jogamos em casa com a nossa identidade.»", "Calma.",
                        "Boa resposta: humildade com ambição.", Efeitos(moral = 1, adeptos = 2, direcao = 1),
                    ),
                    OpcaoDecisao(
                        "«Somos candidatos a ir longe nesta competição.»", "Ousada.",
                        "A ambição europeia entusiasma o clube.", Efeitos(moral = 2, adeptos = 3, direcao = 1),
                        risco = 35, respostaFalha = "Ficaste sem margem para falhar e a imprensa não perdoa.", efeitosFalha = Efeitos(moral = -2, adeptos = -3, direcao = -2),
                        manchete = "$nome aponta alto na ${jogo.competicaoNome}: «Somos candidatos»",
                        manchetesFalha = "Candidatos? $nome fala alto antes do duelo com o $adv",
                    ),
                    OpcaoDecisao(
                        "«O objetivo é passar de ronda. O resto logo se vê.»", "Defensiva.",
                        "Uma resposta prudente.", Efeitos(adeptos = -1, direcao = 1),
                    ),
                ),
            )
            "taca" -> Triple(
                "Conferência de imprensa: a taça",
                "Chegou a hora decisiva na ${jogo.competicaoNome}: a ronda dos ${jogo.ronda.lowercase()} é contra o $adv.",
                listOf(
                    OpcaoDecisao(
                        "«A taça é um objetivo, e vamos trabalhar para isso.»", "Calma.",
                        "Passas confiança sem exagerar.", Efeitos(moral = 1, adeptos = 2),
                    ),
                    OpcaoDecisao(
                        "«Quero esta taça para o ${eu}.»", "Ousada.",
                        "O público gosta da tua ambição.", Efeitos(moral = 2, adeptos = 4),
                        risco = 30, respostaFalha = "Não caiu bem entre os mais cautelosos e a direção pediu moderação.", efeitosFalha = Efeitos(moral = -2, adeptos = -2, direcao = -2),
                        manchete = "$nome quer a ${jogo.competicaoNome}: «Esta taça é nossa»",
                    ),
                    OpcaoDecisao(
                        "«O campeonato é a prioridade. A taça é um bónus.»", "Defensiva: tira pressão, mas desagrada.",
                        "A direção aprova, os adeptos nem tanto.", Efeitos(adeptos = -2, direcao = 2),
                    ),
                ),
            )
            else -> Triple(
                "Conferência de imprensa: choque no topo",
                "O $adv, outro candidato, visita-te (ou recebe-te) na luta pelo título. Querem saber se é a decisão do campeonato.",
                listOf(
                    OpcaoDecisao(
                        "«Ainda falta muito. É um jogo importante, mas não decide nada.»", "Calma.",
                        "A calma dá segurança ao balneário.", Efeitos(moral = 1, direcao = 1, adeptos = 1),
                    ),
                    OpcaoDecisao(
                        "«É o jogo do campeonato e vamos ganhá-lo.»", "Ousada.",
                        "O estádio vai esgotar.", Efeitos(moral = 2, adeptos = 4),
                        risco = 35, respostaFalha = "Disseste demais: o $adv agradece o combustível.", efeitosFalha = Efeitos(moral = -2, adeptos = -3, direcao = -1),
                        manchete = "$nome: «É o jogo do campeonato»",
                        manchetesFalha = "$nome «aquece» o duelo com o $adv e provoca reações",
                    ),
                    OpcaoDecisao(
                        "«Não vou dizer nada sobre o adversário.»", "Defensiva.",
                        "Uma resposta seca, que ninguém comenta.", Efeitos(adeptos = -1, direcao = 1),
                    ),
                ),
            )
        }
        e.nova("imprensa", titulo, texto, opcoes, chave, omissao = 2)
    }

    internal fun aposJogo(e: Emocao, jogo: JogoDoClube) {
        val c = e.c
        if (e.estado.decisoes.any { it.tipo == "imprensa" } || !e.passou("imprensa", INTERVALO_DIAS)) return
        val saldo = jogo.golosMarcados - jogo.golosSofridos
        val recentes = c.jogosDoClube().filter { it.resultado != null }.takeLast(6)
        val vitoriasSeguidas = recentes.reversed().takeWhile { it.golosMarcados > it.golosSofridos }.size
        val semVencer = recentes.reversed().takeWhile { it.golosMarcados <= it.golosSofridos }.size
        val chave = "imp/pos/${jogo.competicaoId}/${jogo.data}"
        val adv = jogo.adversario.nome
        val eu = c.equipa.nome
        val nome = c.info.nome
        val marcador = "${jogo.golosMarcados}-${jogo.golosSofridos}"
        val tipo = when {
            saldo >= 3 -> "goleada"
            saldo <= -3 -> "pesada"
            jogo.passou == false && !jogo.eFinal -> "eliminado"
            vitoriasSeguidas >= 4 && saldo > 0 -> "serie"
            semVencer >= 4 -> "crise"
            saldo > 0 && jogo.derby != null -> "derby"
            else -> return
        }
        if (!e.marcar(chave)) return
        val (titulo, texto, opcoes) = when (tipo) {
            "goleada" -> Triple(
                "Conferência de imprensa: goleada",
                "O $eu venceu o $adv por $marcador. Os jornalistas pedem-te a leitura do jogo.",
                listOf(
                    OpcaoDecisao(
                        "«Boa exibição, mas o trabalho continua.»", "Calma.", "Manténs os pés assentes na terra.",
                        Efeitos(moral = 1, adeptos = 1, direcao = 1),
                    ),
                    OpcaoDecisao(
                        "«Esta equipa tem tudo para ser campeã.»", "Ousada.", "O entusiasmo contagia as bancadas.",
                        Efeitos(moral = 2, adeptos = 4, direcao = 0), risco = 30,
                        respostaFalha = "Os adversários guardaram a frase para a usar contra ti.", efeitosFalha = Efeitos(moral = -1, adeptos = -2, direcao = -1),
                        manchete = "$nome após a goleada: «Esta equipa pode ser campeã»",
                    ),
                    OpcaoDecisao(
                        "«Foi só um jogo. Vamos esquecer e pensar no próximo.»", "Defensiva.", "A direção gosta de ver o foco no próximo desafio.",
                        Efeitos(adeptos = -1, direcao = 1),
                    ),
                ),
            )
            "pesada" -> Triple(
                "Conferência de imprensa: derrota pesada",
                "O $eu perdeu por $marcador com o $adv. A sala está cheia e as perguntas são duras.",
                listOf(
                    OpcaoDecisao(
                        "«A responsabilidade é minha. Vamos trabalhar para dar a volta.»", "Calma: assumes a culpa.",
                        "Protege o plantel e os adeptos respeitam a frontalidade.", Efeitos(moral = 3, adeptos = 1, direcao = -1),
                        manchete = "$nome assume culpas após a derrota: «A responsabilidade é minha»",
                    ),
                    OpcaoDecisao(
                        "«Alguns jogadores não estiveram à altura do clube.»", "Ousada: pressiona o grupo.",
                        "Os adeptos concordam, mas o balneário ficou ferido.", Efeitos(moral = -3, adeptos = 2, direcao = 1),
                        risco = 30, respostaFalha = "O balneário não perdoou a crítica pública.", efeitosFalha = Efeitos(moral = -6, adeptos = 0, direcao = -2),
                        manchete = "$nome critica jogadores: «Não estiveram à altura do clube»",
                        manchetesFalha = "Balneário em alvoroço: $nome critica publicamente os jogadores",
                    ),
                    OpcaoDecisao(
                        "«A arbitragem condicionou o jogo.»", "Defensiva: desvia as atenções.",
                        "Os adeptos gostam da defesa do clube, a direção nem tanto.", Efeitos(moral = 1, adeptos = 2, direcao = -3),
                        manchete = "$nome aponta o dedo à arbitragem após o $marcador",
                    ),
                ),
            )
            "eliminado" -> Triple(
                "Conferência de imprensa: eliminação",
                "O $eu ficou fora da ${jogo.competicaoNome}, frente ao $adv. Perguntam-te se foi um fracasso.",
                listOf(
                    OpcaoDecisao(
                        "«Dói, mas temos de olhar para o campeonato.»", "Calma.", "Viras a página sem dramas.", Efeitos(moral = 1, adeptos = -1, direcao = 0),
                    ),
                    OpcaoDecisao(
                        "«Foi um fracasso, e a culpa é só minha.»", "Ousada: choque de honestidade.",
                        "A frontalidade foi apreciada.", Efeitos(moral = 2, adeptos = 3, direcao = -2), risco = 30,
                        respostaFalha = "A palavra «fracasso» foi manchete e a direção não gostou.", efeitosFalha = Efeitos(moral = -1, adeptos = -1, direcao = -4),
                        manchete = "$nome fala em fracasso após a eliminação",
                    ),
                    OpcaoDecisao(
                        "«Sorte e azar fazem parte do futebol.»", "Defensiva.", "Uma resposta mole, mas sem consequências.", Efeitos(adeptos = -2),
                    ),
                ),
            )
            "serie" -> Triple(
                "Conferência de imprensa: boa fase",
                "São $vitoriasSeguidas vitórias seguidas do $eu. Perguntam-te se já é caso para falar de título.",
                listOf(
                    OpcaoDecisao(
                        "«Estamos num bom momento, mas é preciso humildade.»", "Calma.", "Humildade que o balneário aprecia.",
                        Efeitos(moral = 2, adeptos = 1, direcao = 1),
                    ),
                    OpcaoDecisao(
                        "«Somos os melhores. Quem quiser que nos apanhe.»", "Ousada.", "O estádio acorda para o sonho.",
                        Efeitos(moral = 2, adeptos = 4), risco = 35,
                        respostaFalha = "A provocação deu moral aos rivais e criou pressão em casa.", efeitosFalha = Efeitos(moral = -3, adeptos = -2, direcao = -1),
                        manchete = "$nome lança o desafio: «Quem quiser que nos apanhe»",
                        manchetesFalha = "$nome «provoca» os rivais: a série pode pesar",
                    ),
                    OpcaoDecisao(
                        "«Só penso no próximo jogo.»", "Defensiva.", "A frase de sempre.", Efeitos(adeptos = -1, direcao = 1),
                    ),
                ),
            )
            "crise" -> Triple(
                "Conferência de imprensa: má fase",
                "O $eu leva $semVencer jogos sem vencer. Os assobios já se ouvem e perguntam-te se estás em risco.",
                listOf(
                    OpcaoDecisao(
                        "«Estou a trabalhar com o grupo e as vitórias vão voltar.»", "Calma.", "Falas com serenidade e o grupo agradece.",
                        Efeitos(moral = 2, adeptos = 0, direcao = 0),
                    ),
                    OpcaoDecisao(
                        "«Peço o apoio dos adeptos: precisamos deles mais do que nunca.»", "Ousada: apelas ao coração.",
                        "O apelo toca nas bancadas.", Efeitos(moral = 1, adeptos = 4), risco = 35,
                        respostaFalha = "Soou a desculpa e os adeptos reagiram mal.", efeitosFalha = Efeitos(moral = -1, adeptos = -3, direcao = -1),
                        manchete = "$nome pede apoio aos adeptos: «Precisamos deles mais do que nunca»",
                    ),
                    OpcaoDecisao(
                        "«Não comento a minha situação.»", "Defensiva.", "A direção acha prudente, os adeptos não.", Efeitos(adeptos = -2, direcao = 1),
                    ),
                ),
            )
            else -> Triple(
                "Conferência de imprensa: vitória no derby",
                "O $eu venceu o ${jogo.derby} por $marcador. Os jornalistas querem uma frase para os rivais.",
                listOf(
                    OpcaoDecisao(
                        "«Respeito o rival. Hoje foi o nosso dia.»", "Calma.", "Elegância que se nota.", Efeitos(moral = 1, adeptos = 1, direcao = 1),
                    ),
                    OpcaoDecisao(
                        "«A cidade é nossa.»", "Ousada.", "As bancadas vão abaixo.", Efeitos(moral = 2, adeptos = 5), risco = 30,
                        respostaFalha = "O rival respondeu em tom duro e a polémica não pára.", efeitosFalha = Efeitos(moral = 0, adeptos = -2, direcao = -2),
                        manchete = "«A cidade é nossa»: $nome incendeia o ${jogo.derby}",
                        manchetesFalha = "Polémica depois do ${jogo.derby}: as palavras de $nome incomodam",
                    ),
                    OpcaoDecisao(
                        "«Já estou a pensar no próximo jogo.»", "Defensiva.", "Nada a assinalar.", Efeitos(adeptos = -1, direcao = 1),
                    ),
                ),
            )
        }
        e.nova("imprensa", titulo, texto, opcoes, chave, omissao = 2)
    }
}
