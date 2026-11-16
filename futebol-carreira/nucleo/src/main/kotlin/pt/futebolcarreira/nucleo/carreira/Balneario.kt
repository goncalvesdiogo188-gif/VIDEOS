package pt.futebolcarreira.nucleo.carreira

import pt.futebolcarreira.nucleo.modelos.Jogador

/**
 * F3: o balneário e as bancadas. O capitão e os líderes (os mais influentes do plantel), conflitos entre
 * jogadores, promessas de minutos que se cumprem ou se quebram, e a voz dos adeptos: cânticos e protestos.
 */
object Balneario {
    private const val INTERVALO_CONFLITO = 18
    private const val INTERVALO_PROTESTO = 21

    /** Quão influente é o jogador no balneário: qualidade, idade, tempo de casa, moral e posição de comando. */
    fun influencia(e: Emocao, j: Jogador): Double {
        val casa = e.historia.jogadores[j.id]?.epocas ?: 0
        return j.geral() * 0.5 + (minOf(j.idade, 34) - 18) * 1.4 + minOf(casa, 8) * 1.2 + (j.moral - 50) * 0.15 +
            if (j.posicao in listOf("GR", "DC", "MDC")) 2.0 else 0.0
    }

    /** Os candidatos a capitão, do mais influente para o menos. */
    fun candidatos(e: Emocao, quantos: Int = 6): List<Jogador> = e.plantel().sortedByDescending { influencia(e, it) }.take(quantos)

    /** Palavra para o estado do balneário, conforme a moral média do plantel. */
    fun humor(e: Emocao): String {
        val m = e.moralMedia()
        return when {
            m >= 62 -> "Unido"
            m >= 52 -> "Bom ambiente"
            m >= 44 -> "Normal"
            m >= 36 -> "Tenso"
            else -> "Em conflito"
        }
    }

    /** Garante que há capitão e líderes válidos (muda sozinho se o capitão saiu ou, não sendo escolhido, se aparece alguém bem mais influente). */
    internal fun manter(e: Emocao) {
        val st = e.estado
        val plantel = e.plantel()
        if (plantel.isEmpty()) return
        val ids = plantel.map { it.id }.toSet()
        val ordem = plantel.sortedByDescending { influencia(e, it) }
        val melhor = ordem.first()
        val atual = plantel.firstOrNull { it.id == st.capitaoId }
        val anterior = st.capitaoId
        if (atual == null) {
            st.capitaoEscolhido = false
            st.capitaoId = melhor.id
        } else if (!st.capitaoEscolhido && influencia(e, melhor) - influencia(e, atual) > 6) {
            st.capitaoId = melhor.id
        }
        if (anterior != st.capitaoId && st.capitaoId != null) {
            e.jogador(st.capitaoId)?.let { novo -> e.registar("Novo capitão: ${novo.nome}.") }
        }
        st.lideres.clear()
        st.lideres += (listOfNotNull(st.capitaoId) + ordem.map { it.id }).distinct().filter { it in ids }.take(4)
    }

    /** O treinador nomeia o capitão: ele sobe a moral, o capitão anterior desilude-se um pouco. */
    internal fun nomear(e: Emocao, jogadorId: Int): String {
        val st = e.estado
        val j = e.plantel().firstOrNull { it.id == jogadorId } ?: return "Esse jogador já não está no plantel."
        if (st.capitaoId == jogadorId) return "${j.nome} já é o capitão."
        val antigo = e.jogador(st.capitaoId)
        st.capitaoId = j.id
        st.capitaoEscolhido = true
        e.mudarMoral(j, 5)
        if (antigo != null) e.mudarMoral(antigo, -3)
        manter(e)
        e.registar("Nomeaste ${j.nome} capitão.")
        return "${j.nome} é o novo capitão. A braçadeira deu-lhe moral (+5)" + (antigo?.let { "; ${it.nome} ficou um pouco desiludido (-3)." } ?: ".")
    }

    /** O jogador entrou em campo neste jogo (titular ou suplente utilizado)? null se não há relatório. */
    internal fun participou(jogo: JogoDoClube, jogadorId: Int): Boolean? {
        val r = jogo.resultado?.relatorio ?: return null
        val lado = if (jogo.emCasa) r.casa else r.fora
        return lado.fichas.any { it.jogador.id == jogadorId && (it.titular || it.entrou > 0) }
    }

    internal fun aposJogo(e: Emocao, jogo: JogoDoClube) {
        promessas(e, jogo)
        capitao(e, jogo)
        adeptos(e, jogo)
        conflito(e, jogo)
    }

    // ------------------------------------------------ promessas

    private fun promessas(e: Emocao, jogo: JogoDoClube) {
        val c = e.c
        for (p in e.estado.promessas.toList()) {
            val j = e.jogador(p.jogadorId)
            if (j == null || j.equipa != c.info.equipaId) {
                e.estado.promessas.remove(p)
                continue
            }
            val jogou = participou(jogo, p.jogadorId) ?: continue
            if (!j.disponivel && !jogou) continue   // lesionado ou castigado: o prazo não conta
            p.jogosRestantes--
            if (jogou) p.feitos++
            when {
                p.feitos >= p.jogosAlvo -> {
                    e.estado.promessas.remove(p)
                    e.mudarMoral(j, 5)
                    e.registar("Cumpriste a promessa a ${j.nome}: ele jogou e a confiança subiu (+5).")
                }
                p.jogosRestantes <= 0 -> {
                    e.estado.promessas.remove(p)
                    e.mudarMoral(j, -12)
                    e.mudarMoralEquipa(-1)
                    e.registar("Quebraste a promessa a ${j.nome}: ele sente-se enganado (-12) e o balneário reparou (-1).")
                    e.noticia(52, "balneario", "${j.nome} sente-se enganado por ${c.info.nome}", "O jogador do ${c.equipa.nome} diz que lhe prometeram minutos e não os teve.")
                }
            }
        }
    }

    // ------------------------------------------------ o capitão segura (ou não) o grupo

    private fun capitao(e: Emocao, jogo: JogoDoClube) {
        if (jogo.golosMarcados >= jogo.golosSofridos) return
        val cap = e.jogador(e.estado.capitaoId) ?: return
        when {
            cap.moral >= 55 -> {
                e.mudarMoralEquipa(1)
                e.registar("Depois da derrota, o capitão ${cap.nome} chamou o grupo à razão (moral da equipa +1).")
            }
            cap.moral <= 30 -> {
                e.mudarMoralEquipa(-1)
                e.registar("O capitão ${cap.nome} está em baixo e não conseguiu levantar o grupo (moral da equipa -1).")
            }
        }
    }

    // ------------------------------------------------ cânticos e protestos

    private fun adeptos(e: Emocao, jogo: JogoDoClube) {
        val c = e.c
        val saldo = jogo.golosMarcados - jogo.golosSofridos
        val clube = c.equipa.nome
        val r = e.rng("bancadas/${jogo.data}")
        val canto: String? = when {
            saldo >= 3 -> listOf("«Mais um, mais um, mais um!»", "«Olé, olé, olé, olé... o $clube vai vencer!»", "«Que festa, que festa, que festa!»")
            saldo > 0 && c.info.confiancaAdeptos >= 65 -> listOf("«Ó $clube, ó $clube, a ganhar é que a gente se entende!»", "«Somos do $clube, ninguém nos pára!»", "«Vitória, vitória, vitória!»")
            saldo < 0 && c.info.confiancaAdeptos >= 50 -> listOf("«Equipa, equipa, não desistas!»", "«O $clube nunca morre, o $clube nunca desiste!»")
            saldo < 0 -> listOf("«Queremos raça, queremos garra!»", "«Joguem à bola, joguem à bola!»", "«Assim não dá, assim não dá!»")
            else -> null
        }?.let { it[r.nextInt(it.size)] }
        if (canto != null && r.nextInt(100) < 70) {
            val resultado = if (saldo > 0) "Vitória" else "Derrota"
            e.estado.bancadas.add(0, "${e.dataTexto()} · $resultado (${jogo.golosMarcados}-${jogo.golosSofridos} ao ${jogo.adversario.nome}): $canto")
            while (e.estado.bancadas.size > 15) e.estado.bancadas.removeAt(e.estado.bancadas.lastIndex)
        }
        // Protesto: derrota com os adeptos já a perder a paciência.
        if (saldo < 0 && c.info.confiancaAdeptos <= 32 && e.passou("protesto", INTERVALO_PROTESTO) &&
            e.estado.decisoes.none { it.tipo == "protesto" } && e.marcar("protesto/${jogo.data}")
        ) {
            e.estado.bancadas.add(0, "${e.dataTexto()} · Protesto: faixas pedem mudanças e os adeptos esperam-te à saída do estádio.")
            e.noticia(56, "balneario", "Adeptos protestam à porta do estádio do $clube", "Depois da derrota com o ${jogo.adversario.nome}, centenas de adeptos pediram mudanças ao treinador e à direção.")
            e.nova(
                "protesto", "Protesto dos adeptos",
                "Depois do ${jogo.golosMarcados}-${jogo.golosSofridos} com o ${jogo.adversario.nome}, há adeptos à porta do estádio a exigir mudanças. A confiança nas bancadas está em ${c.info.confiancaAdeptos}%.",
                listOf(
                    OpcaoDecisao(
                        "Receber os representantes dos adeptos e ouvir as queixas.", "Dá a cara, mas a conversa pode azedar.",
                        "Os adeptos agradecem a coragem de os receberes.", Efeitos(moral = 1, adeptos = 5, direcao = -1), risco = 25,
                        respostaFalha = "A conversa azedou e as imagens correram as redes sociais.", efeitosFalha = Efeitos(moral = -1, adeptos = -2, direcao = -1),
                        manchete = "$clube: ${c.info.nome} recebe os adeptos depois do protesto",
                    ),
                    OpcaoDecisao(
                        "Escrever uma mensagem pública a pedir confiança.", "Mais seguro, mas menos convincente.",
                        "A mensagem acalma parte das bancadas.", Efeitos(adeptos = 3, direcao = 1), risco = 30,
                        respostaFalha = "A mensagem foi lida como uma desculpa.", efeitosFalha = Efeitos(adeptos = -3, direcao = 0),
                    ),
                    OpcaoDecisao(
                        "Ignorar o protesto e focar-se no trabalho.", "A direção olha de lado.",
                        "O protesto fica sem resposta.", Efeitos(moral = 1, adeptos = -4, direcao = -2),
                        manchete = "Protesto ignorado no $clube: adeptos prometem voltar",
                    ),
                ),
                "protesto/${jogo.data}", omissao = 2,
            )
        }
    }

    // ------------------------------------------------ conflitos

    private fun conflito(e: Emocao, jogo: JogoDoClube) {
        val c = e.c
        if (e.estado.decisoes.any { it.tipo == "conflito" } || !e.passou("conflito", INTERVALO_CONFLITO)) return
        val recentes = c.jogosDoClube().filter { it.resultado != null }.takeLast(5)
        val derrotas = recentes.count { it.golosMarcados < it.golosSofridos }
        val tenso = e.moralMedia() < 46 || derrotas >= 3
        val chance = if (tenso) 25 else 8
        val r = e.rng("conflito/${jogo.data}")
        if (r.nextInt(100) >= chance) return
        val plantel = e.plantel().filter { it.disponivel }
        val descontente = plantel.filter { it.moral <= 50 }.minByOrNull { it.moral } ?: return
        val outro = e.jogador(e.estado.capitaoId)?.takeIf { it.id != descontente.id && it.equipa == c.info.equipaId }
            ?: plantel.filter { it.id != descontente.id }.maxByOrNull { it.geral() } ?: return
        if (!e.marcar("conf/${descontente.id}/${outro.id}/${jogo.data}")) return
        val motivo = listOf(
            "depois de uma troca de palavras ríspidas no balneário", "por uma disputa de bola no treino que acabou em empurrões",
            "por causa de uma crítica feita à frente de todos", "por culpa de uma falta de empenho apontada no treino",
        )[r.nextInt(4)]
        val clube = c.equipa.nome
        e.nova(
            "conflito", "Conflito no balneário",
            "${descontente.nome} e ${outro.nome} desentenderam-se $motivo. O resto do plantel está a ver o que fazes.",
            listOf(
                OpcaoDecisao(
                    "Falar com os dois e fazer as pazes.", "Seguro.", "Os dois dão um aperto de mão e o assunto morre ali.",
                    Efeitos(alvo = 3, alvo2 = 3),
                ),
                OpcaoDecisao(
                    "Castigar ${descontente.nome}: regras são regras.", "Firme, mas pode correr mal.",
                    "O grupo percebe que há regras.", Efeitos(moral = 1, alvo = -5, alvo2 = 2), risco = 25,
                    respostaFalha = "O castigo foi visto como injusto e dividiu o balneário.", efeitosFalha = Efeitos(moral = -2, alvo = -8),
                ),
                OpcaoDecisao(
                    "Ignorar: eles que resolvam.", "Pode resolver-se sozinho... ou não.", "A poeira assentou sozinha.", Efeitos(),
                    risco = 55, respostaFalha = "A tensão escalou e o balneário rachou.", efeitosFalha = Efeitos(moral = -2, alvo = -5, alvo2 = -5),
                    manchetesFalha = "Tensão no balneário do $clube: ${descontente.nome} e ${outro.nome} desentendem-se",
                ),
            ),
            "conf/${jogo.data}", descontente.id, outro.id, omissao = 2,
        )
    }
}
