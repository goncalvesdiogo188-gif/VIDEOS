package pt.futebolcarreira

import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.simulacao.CalendarioDatas
import pt.futebolcarreira.nucleo.simulacao.Confronto
import pt.futebolcarreira.nucleo.simulacao.Eliminatoria
import pt.futebolcarreira.nucleo.simulacao.EstadoEliminatoria
import pt.futebolcarreira.nucleo.simulacao.CalendarioSelecoes
import pt.futebolcarreira.nucleo.simulacao.CompeticaoSelecoes
import pt.futebolcarreira.nucleo.simulacao.EstadoSelecoes
import pt.futebolcarreira.nucleo.simulacao.FaseSelecoes
import pt.futebolcarreira.nucleo.simulacao.JogoEuropa
import pt.futebolcarreira.nucleo.simulacao.JogoSelecao
import pt.futebolcarreira.nucleo.simulacao.TipoCompeticaoSelecoes
import pt.futebolcarreira.nucleo.simulacao.ResumoTaca
import pt.futebolcarreira.nucleo.simulacao.Resultado
import pt.futebolcarreira.nucleo.simulacao.TipoEuropa

/**
 * Ligação da fase 7 ao núcleo: converte as taças, supertaças e competições europeias (e os seus vencedores)
 * nos modelos dos ecrãs, e as seleções nacionais ([selecoes]; sem catálogo de seleções ficam vazias e o ecrã mostra um aviso).
 *
 * Chave de um jogo ("chave" nos modelos): "competição|data|casa|fora" (ids e data ISO). Só os jogos do clube
 * do utilizador têm relato gravado; [resolverChave] encontra-o, e para os outros devolve null.
 */
internal object LigacaoFase7 {
    fun chave(competicao: String, data: java.time.LocalDate, casa: String, fora: String) = "$competicao|$data|$casa|$fora"

    /** O resultado (com relato, se existir) do jogo do clube do utilizador a que a chave se refere; null se não for dele. */
    fun resolverChave(c: Carreira, chave: String): Resultado? {
        val p = chave.split("|")
        if (p.size != 4) return null
        val jogo = c.jogosDoClube().firstOrNull { it.competicaoId == p[0] && it.data.toString() == p[1] } ?: return null
        val r = jogo.resultado ?: return null
        return if (r.casa == p[2] && r.fora == p[3]) r else null
    }

    fun criar(c: Carreira, competicaoId: String, emblema: (Equipa) -> EmblemaUi): Fase7Ui {
        val bd = c.bd
        val g = c.gestor
        val opcoes = mutableListOf<CompeticaoOpcaoUi>()
        for (pais in bd.paises.values) {
            for (comp in bd.tacasDoPais(pais.id)) {
                if (comp.id !in g.eliminatorias) continue
                opcoes += CompeticaoOpcaoUi(comp.id, comp.nome, if (comp.eSupertaca) TipoCompeticao.SUPERTACA else TipoCompeticao.TACA, pais.nome)
            }
        }
        if (g.europa != null) for (tipo in TipoEuropa.values()) opcoes += CompeticaoOpcaoUi(tipo.id, tipo.nome, TipoCompeticao.EUROPA, "UEFA")
        val escolhida = opcoes.firstOrNull { it.id == competicaoId }
        val competicao = when {
            escolhida == null -> CompeticaoUi()
            escolhida.tipo == TipoCompeticao.EUROPA -> europa(c, TipoEuropa.values().first { it.id == escolhida.id }, emblema)
            else -> taca(c, g.eliminatorias.getValue(escolhida.id), emblema)
        }
        return Fase7Ui(opcoes, escolhida?.id ?: "", competicao, selecoes(c))
    }

    private fun texto(d: java.time.LocalDate) = CalendarioDatas.textoCurto(d)

    // ------------------------------------------------------------------------ seleções

    private val CORES_SELECOES = listOf(
        0xFFB71C1CL to 0xFFFFFFFFL, 0xFF1B5E20L to 0xFFFFFFFFL, 0xFF0D47A1L to 0xFFFFD600L, 0xFFF9A825L to 0xFF212121L,
        0xFF4A148CL to 0xFFFFFFFFL, 0xFF006064L to 0xFFFFFFFFL, 0xFFE65100L to 0xFFFFFFFFL, 0xFF263238L to 0xFFFFFFFFL,
    )

    private fun emblemaSelecao(s: EstadoSelecoes, id: String): EmblemaUi {
        val (fundo, letras) = CORES_SELECOES[Math.floorMod(id.hashCode(), CORES_SELECOES.size)]
        return EmblemaUi(s.nacoes[id]?.abreviatura ?: id, fundo, letras)
    }

    /** As seleções do núcleo nos modelos do ecrã «Seleções» (vazio se o jogo não tem seleções). */
    fun selecoes(c: Carreira): SelecoesUi {
        val s = c.gestor.selecoes ?: return SelecoesUi()
        val interesse = setOfNotNull(c.equipa.pais, c.jogador?.nacionalidade)
        val torneios = s.competicoes.sortedBy { it.terminada }.map { torneio(c, s, it, interesse) }
        val ranking = s.ranking().map { LinhaRankingUi(it.posicao, it.nome, it.nome, "${it.pontos}", 0, it.nacao in interesse) }
        val vencedores = s.vencedores().reversed().map {
            VencedorUi(it.ano.toString(), it.tipo.nome, s.nome(it.vencedor), it.finalista.takeIf { f -> f.isNotEmpty() }?.let(s::nome) ?: "")
        }
        return SelecoesUi(torneios, proximosTorneios(s), ranking, convocatorias(c, s, interesse), meusConvocados(c, s), vencedores)
    }

    /** Euro e Mundial que ainda não começaram (a competição só existe depois de acabar a qualificação). */
    private fun proximosTorneios(s: EstadoSelecoes): List<Pair<String, String>> =
        (s.epoca + 1..s.epoca + 6).mapNotNull { ano ->
            val tipo = CalendarioSelecoes.torneioDoAno(ano) ?: return@mapNotNull null
            if (s.competicao("${tipo.name}-$ano") != null) return@mapNotNull null
            val inicio = CalendarioSelecoes.inicioTorneio(ano - 1)
            "${tipo.nome} $ano" to "${texto(inicio)} a ${texto(inicio.plusDays(17))} $ano"
        }.take(2)

    private fun torneio(c: Carreira, s: EstadoSelecoes, comp: CompeticaoSelecoes, interesse: Set<String>): TorneioUi {
        val fases = comp.fases
        val faseGrupos = fases.firstOrNull { !it.eliminatoria }
        val grupos = faseGrupos?.let { f -> f.grupos.map { g -> grupo(s, comp, f, g, interesse) } }.orEmpty()
        val rondas = fases.filter { it.eliminatoria }.map { f -> RondaUi(f.nome, f.jogos.map { parSelecoes(s, it, interesse) }) }
        val estado = when {
            comp.terminada -> "Terminado" + (comp.vencedor?.let { " · Campeão: ${s.nome(it)}" } ?: "")
            else -> {
                val proximo = fases.lastOrNull()?.jogos?.filter { !it.jogado }?.minOfOrNull { it.dataLocal() }
                "Em curso · ${fases.last().nome}" + (proximo?.let { " · próximo jogo: ${texto(it)}" } ?: "")
            }
        }
        return TorneioUi(comp.id, comp.nome, estado, grupos, if (rondas.isEmpty()) null else EliminatoriasUi(rondas, rondas.lastIndex))
    }

    private fun grupo(s: EstadoSelecoes, comp: CompeticaoSelecoes, fase: FaseSelecoes, g: pt.futebolcarreira.nucleo.simulacao.GrupoSelecoes, interesse: Set<String>): GrupoUi {
        val passam = when (comp.tipo) {
            TipoCompeticaoSelecoes.LIGA_NACOES, TipoCompeticaoSelecoes.QUALIFICACAO_MUNDIAL -> 1
            else -> 2
        }
        val tabela = s.tabela(fase, g).mapIndexed { i, l ->
            LinhaUi(i + 1, l.nacao, s.nome(l.nacao), l.jogos, l.vitorias, l.empates, l.derrotas, l.golosMarcados, l.golosSofridos, l.pontos, if (i < passam) Zona.SUBIDA else Zona.NORMAL, l.nacao in interesse)
        }
        val jornadas = fase.jogos.filter { it.grupo == g.nome }.groupBy { it.ronda }.toSortedMap().map { (n, jogos) ->
            JornadaFaseUi(
                "Jornada $n", jogos.minOf { it.dataLocal() }.let(::texto),
                jogos.sortedBy { it.data }.map { j ->
                    JogoFaseUi(s.nome(j.casa), s.nome(j.fora), if (j.jogado) j.golosCasa else null, if (j.jogado) j.golosFora else null, texto(j.dataLocal()), "", j.casa in interesse || j.fora in interesse)
                },
            )
        }
        return GrupoUi(g.nome, tabela, jornadas)
    }

    private fun parSelecoes(s: EstadoSelecoes, j: JogoSelecao, interesse: Set<String>): EliminatoriaParUi {
        val pernas = if (j.jogado) listOf(PernaUi(j.golosCasa, j.golosFora, texto(j.dataLocal()), "")) else emptyList()
        val penaltis = j.penaltisCasa >= 0
        return EliminatoriaParUi(
            s.nome(j.casa), s.nome(j.fora), emblemaSelecao(s, j.casa), emblemaSelecao(s, j.fora), pernas, 1,
            dataProxima = if (j.jogado) "" else texto(j.dataLocal()),
            prolongamento = j.jogado && j.prolongamento,
            penaltisA = if (penaltis) j.penaltisCasa else null, penaltisB = if (penaltis) j.penaltisFora else null,
            vencedor = when (j.vencedor()) { null -> 0; j.casa -> 1; else -> 2 },
            meu = j.casa in interesse || j.fora in interesse,
        )
    }

    private val ORDEM_POSICOES = EstadoSelecoes.QUOTAS.keys.toList()

    /**
     * Convocatórias: as da janela em curso; as seleções sem convocatória feita (fora de janela) mostram a que
     * fariam agora. Primeiro as do utilizador, depois as das bases de dados e as fictícias mais bem classificadas.
     */
    private fun convocatorias(c: Carreira, s: EstadoSelecoes, interesse: Set<String>): List<ConvocatoriaUi> {
        val janela = s.convocadosDaJanela()
        val ranking = s.ranking().map { it.nacao }
        val nacoes = (interesse.toList() + s.nacoes.values.filter { it.baseDados }.map { it.id } + ranking.filter { s.nacoes[it]?.baseDados == false }.take(10))
            .distinct().filter { it in s.nacoes }
        return nacoes.map { id ->
            val nacao = s.nacoes.getValue(id)
            val jogadores = if (nacao.baseDados) {
                janela[id]?.mapNotNull { c.bd.jogadores[it] } ?: s.calcularConvocatoria(id)
            } else s.esquadraoFicticio(id)
            val convocados = jogadores.sortedWith(compareBy({ ORDEM_POSICOES.indexOf(it.posicao) }, { -it.geral() })).map { j ->
                val clube = c.bd.equipas[j.equipa]?.nome ?: "Clube estrangeiro"
                ConvocadoUi(j.posicao, j.nome, clube, j.geral().toInt(), j.equipa == c.info.equipaId && (c.info.jogadorId == null || c.info.jogadorId == j.id), nacao.nome)
            }
            ConvocatoriaUi(id, nacao.nome, nacao.nome, "", convocados)
        }
    }

    /** Os jogadores do clube (ou o jogador do utilizador) convocados para a janela em curso. */
    private fun meusConvocados(c: Carreira, s: EstadoSelecoes): List<ConvocadoUi> =
        s.convocadosDaJanela().flatMap { (nacao, ids) ->
            ids.mapNotNull { c.bd.jogadores[it] }.filter { it.equipa == c.info.equipaId && (c.info.jogadorId == null || c.info.jogadorId == it.id) }
                .map { ConvocadoUi(it.posicao, it.nome, c.equipa.nome, it.geral().toInt(), true, s.nome(nacao)) }
        }

    private fun vencedores(c: Carreira, escolher: (pt.futebolcarreira.nucleo.simulacao.ResumoEpoca) -> ResumoTaca?): List<VencedorUi> {
        val nome = { id: String -> c.bd.equipas[id]?.nome ?: id }
        return c.gestor.historico.reversed().mapNotNull { r ->
            escolher(r)?.let { VencedorUi(c.gestor.nomeEpoca(r.ano), "", nome(it.vencedor), it.finalista.takeIf { f -> f.isNotEmpty() }?.let(nome) ?: "") }
        }
    }

    // ------------------------------------------------------------- taças e supertaças

    private fun taca(c: Carreira, t: EstadoEliminatoria, emblema: (Equipa) -> EmblemaUi): CompeticaoUi {
        val rondas = t.rondas.map { r -> RondaUi(r.nome, r.confrontos.map { parDeConfronto(c, t.id, it, emblema) }) }
        return CompeticaoUi(
            eliminatorias = EliminatoriasUi(rondas, (rondas.size - 1).coerceAtLeast(0)),
            vencedores = vencedores(c) { it.tacas[t.id] ?: it.supertacas[t.id] },
        )
    }

    private fun parDeConfronto(c: Carreira, competicao: String, f: Confronto, emblema: (Equipa) -> EmblemaUi): EliminatoriaParUi {
        val a = c.bd.equipas.getValue(f.a)
        val b = c.bd.equipas.getValue(f.b)
        val pernas = f.jogos.mapIndexed { i, r ->
            // Os golos ficam sempre na ordem da eliminatória (A, B), seja quem for a jogar em casa.
            val ordemNormal = r.casa == f.a
            PernaUi(if (ordemNormal) r.golosCasa else r.golosFora, if (ordemNormal) r.golosFora else r.golosCasa, texto(f.datas[i]), chave(competicao, f.datas[i], r.casa, r.fora))
        }
        val pen = f.resultadoPenaltis
        return EliminatoriaParUi(
            a.nome, b.nome, emblema(a), emblema(b), pernas, f.datas.size,
            dataProxima = f.proximaData?.let(::texto) ?: "",
            agregadoA = if (f.idaEVolta && f.jogos.isNotEmpty()) f.golos(f.a) else null,
            agregadoB = if (f.idaEVolta && f.jogos.isNotEmpty()) f.golos(f.b) else null,
            prolongamento = f.prolongamento,
            penaltisA = pen?.first, penaltisB = pen?.second,
            vencedor = when (f.vencedor) { null -> 0; f.a -> 1; else -> 2 },
            meu = f.envolve(c.info.equipaId),
        )
    }

    // ------------------------------------------------------------------------ Europa

    private fun europa(c: Carreira, tipo: TipoEuropa, emblema: (Equipa) -> EmblemaUi): CompeticaoUi {
        val e = c.gestor.europa!!.estado(tipo)
        val nome = { id: String -> c.bd.equipas[id]?.nome ?: id }
        val euroMeu = c.info.equipaId
        val tabela = e.tabelaLiga().mapIndexed { i, l -> LinhaUi(i + 1, l.id, nome(l.id), l.j, l.v, l.e, l.d, l.gm, l.gs, l.pts, Zona.NORMAL, l.id == euroMeu) }
        val jornadas = (1..6).map { n ->
            val jogos = e.jogosLiga.filter { it.numero == n }
            JornadaFaseUi(
                "Jornada $n", jogos.firstOrNull()?.dia?.let(::texto) ?: "",
                jogos.map { j ->
                    JogoFaseUi(
                        nome(j.casa), nome(j.fora), if (j.jogado) j.golosCasa else null, if (j.jogado) j.golosFora else null,
                        texto(j.dia), if (j.jogado) chave(tipo.id, j.dia, j.casa, j.fora) else "", j.casa == euroMeu || j.fora == euroMeu,
                    )
                },
            )
        }
        val jogadas = jornadas.indices.count { i -> e.jogosLiga.filter { it.numero == i + 1 }.all { it.jogado } }
        val fases = e.eliminatorias.map { it.fase }.distinct()
        val rondas = fases.map { fase -> RondaUi(fase, e.eliminatorias.filter { it.fase == fase }.map { parEuropeu(c, tipo, it, emblema) }) }
        return CompeticaoUi(
            eliminatorias = EliminatoriasUi(rondas, (rondas.size - 1).coerceAtLeast(0)),
            faseLiga = FaseLigaUi(tabela, jornadas, (jogadas - 1).coerceIn(0, jornadas.size - 1), 8, 24),
            vencedores = vencedores(c) { it.europa[tipo.id] },
        )
    }

    private fun parEuropeu(c: Carreira, tipo: TipoEuropa, t: Eliminatoria, emblema: (Equipa) -> EmblemaUi): EliminatoriaParUi {
        val a = c.bd.equipas.getValue(t.a)
        val b = c.bd.equipas.getValue(t.b)
        val jogadas = t.jogos.filter { it.jogado }
        val pernas = jogadas.map { j: JogoEuropa ->
            val ordemNormal = j.casa == t.a
            PernaUi(if (ordemNormal) j.golosCasa else j.golosFora, if (ordemNormal) j.golosFora else j.golosCasa, texto(j.dia), chave(tipo.id, j.dia, j.casa, j.fora))
        }
        val ultimo = t.jogos.last()
        val aPenaltis = t.terminada && ultimo.foiAPenaltis
        val primeiraEmCasaDeA = ultimo.casa == t.a
        return EliminatoriaParUi(
            a.nome, b.nome, emblema(a), emblema(b), pernas, t.jogos.size,
            dataProxima = t.jogos.firstOrNull { !it.jogado }?.dia?.let(::texto) ?: "",
            agregadoA = if (t.jogos.size > 1 && jogadas.isNotEmpty()) t.golos(t.a) else null,
            agregadoB = if (t.jogos.size > 1 && jogadas.isNotEmpty()) t.golos(t.b) else null,
            prolongamento = t.terminada && ultimo.prolongamento,
            penaltisA = if (aPenaltis) (if (primeiraEmCasaDeA) ultimo.penaltisCasa else ultimo.penaltisFora) else null,
            penaltisB = if (aPenaltis) (if (primeiraEmCasaDeA) ultimo.penaltisFora else ultimo.penaltisCasa) else null,
            vencedor = when (t.vencedor) { "" -> 0; t.a -> 1; else -> 2 },
            meu = t.a == c.info.equipaId || t.b == c.info.equipaId,
        )
    }
}
