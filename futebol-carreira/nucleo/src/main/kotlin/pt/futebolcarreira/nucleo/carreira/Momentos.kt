package pt.futebolcarreira.nucleo.carreira

import pt.futebolcarreira.nucleo.simulacao.TipoEuropa

/** Uma linha da sala de história: um jogador com os números ao serviço do clube. */
class LinhaHistoria(val id: Int, val nome: String, val posicao: String, val jogos: Int, val golos: Int, val assistencias: Int, val epocas: Int, val lenda: Boolean, val noClube: Boolean)

/**
 * F4: momentos de carreira. Prémios (treinador do mês e do ano, jogador do ano do clube), homenagens, lendas e despedidas,
 * e a «sala de história» do clube: títulos, épocas, melhores marcadores, lendas e recordes.
 */
object Momentos {
    /** Um jogador torna-se lenda do clube com tantos jogos, golos ou épocas (com um mínimo de jogos). */
    const val LENDA_JOGOS = 120
    const val LENDA_GOLOS = 50
    const val LENDA_EPOCAS = 6

    internal fun momento(e: Emocao, tipo: String, titulo: String, texto: String, mensagem: Boolean = true) {
        val h = e.historia
        h.momentos.add(0, Momento(e.g.data.toString(), tipo, titulo, texto))
        while (h.momentos.size > 60) h.momentos.removeAt(h.momentos.lastIndex)
        if (mensagem) e.c.mensagem(titulo, texto)
    }

    // ------------------------------------------------ depois de cada jogo

    internal fun registarJogo(e: Emocao, jogo: JogoDoClube) {
        val c = e.c
        val h = e.historia
        val epoca = e.g.nomeEpoca()
        val saldo = jogo.golosMarcados - jogo.golosSofridos
        val feitos = c.jogosDoClube().filter { it.resultado != null }
        if (saldo > 0) {
            val valor = saldo * 100 + jogo.golosMarcados
            val r = h.recordes["vitoria"]
            val texto = "${jogo.golosMarcados}-${jogo.golosSofridos} ${if (jogo.emCasa) "em casa com o" else "fora, com o"} ${jogo.adversario.nome}"
            if (r == null) {
                h.recordes["vitoria"] = Recorde("Maior vitória", valor, texto, epoca)
            } else if (valor > r.valor) {
                r.valor = valor; r.texto = texto; r.epoca = epoca
                if (saldo >= 4) momento(e, "recorde", "Recorde: maior vitória do clube", "O ${c.equipa.nome} bate o recorde de maior vitória: $texto.")
            }
        }
        val seq = feitos.reversed().takeWhile { it.golosMarcados > it.golosSofridos }.size
        val rs = h.recordes["sequencia"]
        if (seq >= 3 && (rs == null || seq > rs.valor)) {
            h.recordes["sequencia"] = Recorde("Mais vitórias seguidas", seq, "$seq vitórias seguidas", epoca)
            if (rs != null && seq >= 5) momento(e, "recorde", "Recorde: $seq vitórias seguidas", "O ${c.equipa.nome} nunca tinha vencido tantos jogos seguidos.")
        }
        // Treinador do Mês: quatro vitórias nos últimos cinco jogos, uma vez por mês.
        if (e.treinador && seq >= 1) {
            val ultimos = feitos.takeLast(5)
            if (ultimos.size == 5 && ultimos.count { it.golosMarcados > it.golosSofridos } >= 4 &&
                e.marcar("mes/${e.g.data.year}-${e.g.data.monthValue}")
            ) {
                e.mudarConfianca(2, 2)
                e.mudarMoralEquipa(1)
                val texto = "${c.info.nome} foi eleito Treinador do Mês depois de 4 vitórias nos últimos 5 jogos do ${c.equipa.nome}."
                momento(e, "premio", "Treinador do Mês", texto)
                e.noticia(54, "premio", "${c.info.nome} é o Treinador do Mês", texto)
            }
        }
    }

    // ------------------------------------------------ fim da época

    internal fun fecharEpoca(e: Emocao) {
        val c = e.c
        val g = e.g
        if (e.estado.epocaFechada == g.ano) return
        e.estado.epocaFechada = g.ano
        val h = e.historia
        val clube = c.info.equipaId
        val resumo = g.historico.lastOrNull()
        val ligaResumo = resumo?.ligas?.get(c.equipa.liga)
        val titulos = mutableListOf<String>()
        if (ligaResumo?.campeao == clube && c.liga.nivel == 1) titulos += c.liga.nome
        if (resumo != null) {
            for ((id, t) in resumo.tacas + resumo.supertacas) {
                val comp = e.bd.competicoes[id] ?: continue
                if (t.vencedor == clube && comp.pais == c.liga.pais) titulos += comp.nome
            }
            for ((id, t) in resumo.europa) {
                if (t.vencedor == clube) titulos += TipoEuropa.entries.firstOrNull { it.id == id }?.nome ?: id
            }
        }
        val plantel = e.plantel()
        val marcador = plantel.maxByOrNull { it.epoca.golos + it.epocaTaca.golos }?.takeIf { it.epoca.golos + it.epocaTaca.golos > 0 }
        val pos = c.posicao()
        val pts = c.estadoLiga.tabela.linhas[clube]?.pts ?: 0
        val epoca = g.nomeEpoca()
        h.epocas.removeAll { it.ano == g.ano }
        h.epocas += EpocaClube(
            g.ano, epoca, c.liga.nome, pos, pts, titulos, marcador?.nome ?: "", marcador?.let { it.epoca.golos + it.epocaTaca.golos } ?: 0,
        )
        // Recordes da época.
        val rm = h.recordes["epoca"]
        if (rm == null || pts > rm.valor) h.recordes["epoca"] = Recorde("Melhor época (pontos na liga)", pts, "$pts pontos ($pos.º lugar, ${c.liga.nome})", epoca)
        if (marcador != null) {
            val golos = marcador.epoca.golos + marcador.epocaTaca.golos
            val rg = h.recordes["golos"]
            if (rg == null || golos > rg.valor) h.recordes["golos"] = Recorde("Mais golos numa época", golos, "${marcador.nome}, $golos golos", epoca)
        }
        // Os números de cada jogador somam-se à sua ficha no clube; quem passa o limiar é lenda.
        for (j in plantel) {
            val jogos = j.epoca.jogos + j.epocaTaca.jogos
            if (jogos <= 0) continue
            val f = h.jogadores.getOrPut(j.id) { FichaHistorica(j.id, j.nome, j.posicao) }
            if (f.ultimoAno == g.ano) continue
            f.ultimoAno = g.ano
            f.nome = j.nome
            f.posicao = j.posicao
            f.jogos += jogos
            f.golos += j.epoca.golos + j.epocaTaca.golos
            f.assistencias += j.epoca.assistencias + j.epocaTaca.assistencias
            f.epocas++
            if (!f.lenda && (f.jogos >= LENDA_JOGOS || f.golos >= LENDA_GOLOS || (f.epocas >= LENDA_EPOCAS && f.jogos >= 90))) {
                f.lenda = true
                momento(e, "lenda", "${f.nome} é já uma lenda do ${c.equipa.nome}", "Com ${f.jogos} jogos e ${f.golos} golos pelo clube, ${f.nome} entra na galeria das lendas.")
                e.mudarConfianca(1, 0)
            }
        }
        // Prémios do clube.
        val melhor = plantel.filter { it.epoca.jogos >= 12 }.maxByOrNull { it.epoca.notaMedia() + 0.02 * it.epoca.golos }
        if (melhor != null) {
            e.mudarMoral(melhor, 6)
            val iguais = marcador != null && marcador.id == melhor.id
            val texto = "${melhor.nome} (${melhor.posicao}) foi eleito Jogador do Ano do ${c.equipa.nome}: nota média %.1f em ${melhor.epoca.jogos} jogos".format(melhor.epoca.notaMedia()) +
                (if (iguais) " e também o melhor marcador, com ${melhor.epoca.golos} golos." else ".")
            momento(e, "premio", "Jogador do Ano do clube", texto)
        }
        if (marcador != null && marcador.id != melhor?.id) {
            momento(e, "premio", "Melhor marcador do clube", "${marcador.nome} foi o melhor marcador do ${c.equipa.nome}, com ${marcador.epoca.golos + marcador.epocaTaca.golos} golos.")
        }
        if (titulos.isNotEmpty()) {
            e.mudarMoralEquipa(4)
            if (e.treinador) e.mudarConfianca(6, 2)
            momento(e, "titulo", "Época de conquistas ($epoca)", "O ${c.equipa.nome} termina a época com ${titulos.joinToString(", ")}. A cidade está em festa.")
        }
        if (e.treinador && !c.info.despedido && (titulos.contains(c.liga.nome) || pos <= c.info.objetivoPosicao - 3)) {
            e.mudarConfianca(3, 4)
            val texto = "${c.info.nome} foi eleito Treinador do Ano depois de uma época de sonho ($pos.º lugar, ${c.liga.nome})."
            momento(e, "premio", "Treinador do Ano", texto)
            e.noticia(60, "premio", "${c.info.nome} é o Treinador do Ano", texto)
        }
    }

    // ------------------------------------------------ início da época: despedidas

    internal fun novaEpoca(e: Emocao) {
        val c = e.c
        val h = e.historia
        val clube = c.info.equipaId
        // Só uma despedida por época, para não encher o ecrã: as outras ficam para a época seguinte.
        val f = h.jogadores.values.firstOrNull {
            it.lenda && !it.despedido && e.bd.jogadores[it.id].let { p -> p == null || (p.equipa != clube && p.emprestadoPor != clube) }
        } ?: return
        f.despedido = true
        val p = e.bd.jogadores[f.id]
        val motivo = if (p == null) "terminou a carreira" else "deixou o clube" + (e.bd.equipas[p.equipa]?.let { " para jogar no ${it.nome}" } ?: "")
        val nome = f.nome
        val clubeNome = c.equipa.nome
        if (!e.treinador) {
            momento(e, "despedida", "Despedida de $nome", "$nome $motivo depois de ${f.jogos} jogos e ${f.golos} golos pelo $clubeNome. Os adeptos aplaudiram-no de pé.")
            return
        }
        e.nova(
            "homenagem", "Homenagem a $nome",
            "$nome (${f.posicao}), lenda do $clubeNome com ${f.golos} golos em ${f.jogos} jogos, $motivo. Os adeptos querem despedir-se.",
            listOf(
                OpcaoDecisao(
                    "Cerimónia no estádio, antes de um jogo.", "A melhor despedida.",
                    "O estádio inteiro de pé: uma noite de lágrimas e aplausos.", Efeitos(moral = 2, adeptos = 5),
                    manchete = "$clubeNome despede-se de $nome com uma homenagem emotiva",
                ),
                OpcaoDecisao(
                    "Mensagem de agradecimento nas redes do clube.", "Simples e sincera.",
                    "Milhares de adeptos partilham a mensagem.", Efeitos(moral = 1, adeptos = 2),
                ),
                OpcaoDecisao(
                    "Deixar passar.", "Os adeptos não vão gostar.",
                    "Passou em branco, e os adeptos reparam.", Efeitos(moral = -1, adeptos = -3, direcao = -1),
                    manchete = "Adeptos criticam falta de homenagem a $nome",
                ),
            ),
            "hom/${f.id}", omissao = 1,
        )
    }

    // ------------------------------------------------ sala de história

    /** Os melhores marcadores de sempre do clube (incluindo a época em curso). */
    fun melhoresMarcadores(e: Emocao, quantos: Int = 8): List<LinhaHistoria> = linhas(e).filter { it.golos > 0 }.sortedByDescending { it.golos }.take(quantos)

    /** As lendas do clube, por jogos. */
    fun lendas(e: Emocao): List<LinhaHistoria> = linhas(e).filter { it.lenda }.sortedByDescending { it.jogos }

    private fun linhas(e: Emocao): List<LinhaHistoria> {
        val h = e.historia
        val clube = e.c.info.equipaId
        val ano = e.g.ano
        val atuais = e.plantel().associateBy { it.id }
        val ids = (h.jogadores.keys + atuais.keys)
        return ids.mapNotNull { id ->
            val f = h.jogadores[id]
            val p = atuais[id]
            val extra = if (p != null && (f == null || f.ultimoAno != ano)) p else null
            val jogos = (f?.jogos ?: 0) + (extra?.let { it.epoca.jogos + it.epocaTaca.jogos } ?: 0)
            if (jogos <= 0) return@mapNotNull null
            LinhaHistoria(
                id, f?.nome ?: p!!.nome, f?.posicao ?: p!!.posicao, jogos,
                (f?.golos ?: 0) + (extra?.let { it.epoca.golos + it.epocaTaca.golos } ?: 0),
                (f?.assistencias ?: 0) + (extra?.let { it.epoca.assistencias + it.epocaTaca.assistencias } ?: 0),
                (f?.epocas ?: 0) + if (extra != null) 1 else 0,
                f?.lenda == true,
                e.bd.jogadores[id]?.let { it.equipa == clube || it.emprestadoPor == clube } == true,
            )
        }
    }

    /** Os troféus do clube: (nome, quantidade), só os que já ganhou. */
    fun trofeus(e: Emocao): List<Pair<String, Int>> {
        val q = e.c.equipa
        return listOf("Campeonatos" to q.titulos, "Taças" to q.tacas, "Supertaças" to q.supertacas, "Títulos europeus" to q.europeias).filter { it.second > 0 }
    }
}
