package pt.futebolcarreira

import kotlin.random.Random

// Dados inventados da fase 7, só para ver os ecrãs (capturas de ecrã). Não são usados no jogo normal.

private val CORES = listOf(
    0xFFB71C1CL to 0xFFFFFFFFL, 0xFF1B5E20L to 0xFFFFFFFFL, 0xFF0D47A1L to 0xFFFFD600L, 0xFFF9A825L to 0xFF212121L,
    0xFF4A148CL to 0xFFFFFFFFL, 0xFF006064L to 0xFFFFFFFFL, 0xFFE65100L to 0xFFFFFFFFL, 0xFF263238L to 0xFFFFFFFFL,
)

private fun emb(nome: String): EmblemaUi {
    val (f, l) = CORES[Math.floorMod(nome.hashCode(), CORES.size)]
    return EmblemaUi(nome.filter { it.isLetter() }.take(3).uppercase(), f, l)
}

private val CLUBES_PT = listOf(
    "Benfica", "Porto", "Sporting", "Braga", "Vitória SC", "Famalicão", "Estoril", "Boavista", "Gil Vicente", "Arouca",
    "Moreirense", "Rio Ave", "Casa Pia", "Farense", "Nacional", "Santa Clara", "Marítimo", "Paços Ferreira", "Portimonense", "Chaves",
    "Tondela", "Vizela", "Académica", "Penafiel", "Leixões", "Feirense", "Mafra", "Varzim", "Oliveirense", "Trofense", "Belenenses", "Covilhã",
)

private val CLUBES_EU = listOf(
    "Real Madrid", "Barcelona", "Bayern", "Man. City", "Liverpool", "Inter", "Milan", "PSG", "Dortmund", "Arsenal", "Atlético", "Juventus",
    "Benfica", "Sporting", "Porto", "Leverkusen", "Ajax", "PSV", "Napoli", "Marselha", "Celtic", "Brugge", "Salzburgo", "Galatasaray",
)

private val SELECOES = listOf(
    "França", "Espanha", "Inglaterra", "Portugal", "Alemanha", "Itália", "Países Baixos", "Croácia", "Bélgica", "Dinamarca",
    "Suíça", "Áustria", "Polónia", "Sérvia", "Turquia", "Ucrânia", "Suécia", "Escócia", "Noruega", "Hungria", "Grécia", "Chéquia", "Roménia", "Eslovénia",
)

private fun pares(equipas: List<String>) = equipas.chunked(2).filter { it.size == 2 }

/**
 * Uma eliminatória decidida de forma determinística.
 * "forcar": "" = resultado normal, "ap" = decidida no prolongamento, "pen" = decidida nos penáltis.
 */
private fun par(
    a: String, b: String, rng: Random, duasMaos: Boolean, jogadas: Int = if (duasMaos) 2 else 1,
    meu: String = "", forcar: String = "",
): EliminatoriaParUi {
    val maos = if (duasMaos) 2 else 1
    val meuPar = a == meu || b == meu
    val pernas = ArrayList<PernaUi>()
    for (i in 0 until jogadas) pernas += PernaUi(rng.nextInt(0, 4), rng.nextInt(0, 3), "dd/mm", "k$a$b$i")
    if (jogadas < maos) {
        return EliminatoriaParUi(a, b, emb(a), emb(b), pernas, maos, dataProxima = "18 fev", meu = meuPar)
    }
    if (forcar.isNotEmpty()) {
        // Empata o conjunto (agregado ou jogo único).
        val k = rng.nextInt(0, 2)
        val ult = pernas.last()
        val antA = pernas.dropLast(1).sumOf { it.golosA }
        val antB = pernas.dropLast(1).sumOf { it.golosB }
        pernas[pernas.size - 1] = PernaUi(antB + k, antA + k, ult.data, ult.chave)
    }
    var ap = false
    var pen: Pair<Int, Int>? = null
    var ga = pernas.sumOf { it.golosA }
    var gb = pernas.sumOf { it.golosB }
    var venc = if (ga > gb) 1 else if (gb > ga) 2 else 0
    if (venc == 0) {
        if (forcar == "ap") {
            // Golo de A (ou de B) no prolongamento.
            val ult = pernas.last()
            pernas[pernas.size - 1] = PernaUi(ult.golosA, ult.golosB + 1, ult.data, ult.chave)
            gb += 1
            venc = 2
            ap = true
        } else {
            pen = if (rng.nextBoolean()) 4 to 3 else 3 to 5
            venc = if (pen.first > pen.second) 1 else 2
        }
    }
    return EliminatoriaParUi(
        a, b, emb(a), emb(b), pernas, maos,
        agregadoA = if (duasMaos) ga else null, agregadoB = if (duasMaos) gb else null,
        prolongamento = ap, penaltisA = pen?.first, penaltisB = pen?.second, vencedor = venc, meu = meuPar,
    )
}

private fun vencedorDe(p: EliminatoriaParUi) = if (p.vencedor == 2) p.fora else p.casa

private fun linhaTabela(id: String, jogos: List<JogoFaseUi>, meu: String): LinhaUi {
    var v = 0; var e = 0; var d = 0; var gm = 0; var gs = 0
    for (j in jogos) {
        val (m, s) = when (id) {
            j.casa -> j.golosCasa!! to j.golosFora!!
            j.fora -> j.golosFora!! to j.golosCasa!!
            else -> continue
        }
        gm += m; gs += s
        if (m > s) v++ else if (m == s) e++ else d++
    }
    return LinhaUi(0, id, id, v + e + d, v, e, d, gm, gs, 3 * v + e, Zona.NORMAL, id == meu)
}

private fun tabelaDe(equipas: List<String>, jogos: List<JogoFaseUi>, meu: String = "", passam: Int = 0): List<LinhaUi> =
    equipas.map { linhaTabela(it, jogos, meu) }
        .sortedWith(compareByDescending<LinhaUi> { it.pts }.thenByDescending { it.gm - it.gs }.thenByDescending { it.gm })
        .mapIndexed { i, l ->
            LinhaUi(i + 1, l.equipaId, l.nome, l.j, l.v, l.e, l.d, l.gm, l.gs, l.pts, if (i < passam) Zona.SUBIDA else Zona.NORMAL, l.meu)
        }

private fun jornadasDe(equipas: List<String>, rng: Random, quantas: Int, jogadas: Int, meu: String = ""): List<JornadaFaseUi> =
    (0 until quantas).map { r ->
        val ordem = equipas.shuffled(rng)
        JornadaFaseUi("Jornada ${r + 1}", "${10 + r * 14} ${if (r < 2) "set" else "out"}", pares(ordem).map { (a, b) ->
            if (r < jogadas) JogoFaseUi(a, b, rng.nextInt(0, 5), rng.nextInt(0, 4), chave = "j$r$a$b", meu = a == meu || b == meu)
            else JogoFaseUi(a, b, meu = a == meu || b == meu)
        })
    }

private val NOMES_CONVOCADOS = listOf(
    "GR" to "Diogo Rocha", "GR" to "Rui Vaz", "GR" to "Nuno Matos",
    "DD" to "Tiago Lima", "DC" to "Pedro Sá", "DC" to "Rúben Dias", "DC" to "Gonçalo Brito", "DE" to "André Cunha", "DE" to "Nélson Pinto", "DD" to "Hugo Faria",
    "MDC" to "João Palhinha", "MC" to "Bruno Teixeira", "MC" to "Vítor Nunes", "MC" to "Rafael Neves", "MO" to "Bernardo Costa",
    "ED" to "Rafael Leão", "EE" to "Francisco Lopes", "ED" to "Pedro Neto", "MO" to "Fábio Silva",
    "PL" to "Gonçalo Ramos", "PL" to "Diogo Jota", "PL" to "Paulo Alves", "PL" to "Ricardo Horta",
)

private fun convocatoria(id: String, nome: String, seletor: String, minha: Set<String>, rng: Random): ConvocatoriaUi {
    val clubes = CLUBES_EU + CLUBES_PT.take(6)
    return ConvocatoriaUi(id, nome, id, seletor, NOMES_CONVOCADOS.mapIndexed { i, (pos, n) ->
        val clube = clubes[(i * 5 + nome.length) % clubes.size]
        ConvocadoUi(pos, n, clube, 90 - i / 2 - rng.nextInt(0, 3), meu = n in minha)
    })
}

/** Torna a carreira de demonstração mais realista: próximo jogo da Taça e jogos da Taça no calendário. */
internal fun demoCarreira(ui: CarreiraUi): CarreiraUi {
    val p = ui.proximo ?: return ui
    return ui.copy(
        proximo = ProximoJogoUi(
            p.jornada, p.totalJornadas, p.data, p.emCasa, p.adversario, p.emblemaAdversario, p.detalhes,
            competicao = "Taça de Portugal", ronda = "Quartos de final",
        ),
        calendario = ui.calendario.mapIndexed { i, j ->
            if (i % 4 == 3) JogoCalendarioUi(
                j.jornada, j.data, j.adversario, j.emblema, j.emCasa, j.resultado, j.desfecho, j.temRelato,
                competicao = "Taça de Portugal", ronda = "Oitavos de final", chave = "taca$i",
            ) else j
        },
    )
}

internal fun demoFase7(competicaoId: String): Fase7Ui {
    val competicoes = listOf(
        CompeticaoOpcaoUi("taca-pt", "Taça de Portugal", TipoCompeticao.TACA, "PT"),
        CompeticaoOpcaoUi("supertaca-pt", "Supertaça Cândido de Oliveira", TipoCompeticao.SUPERTACA, "PT"),
        CompeticaoOpcaoUi("lc", "Liga dos Campeões", TipoCompeticao.EUROPA, "UEFA"),
        CompeticaoOpcaoUi("le", "Liga Europa", TipoCompeticao.EUROPA, "UEFA"),
    )
    val rng = Random(7)
    val minhaEquipa = "Vitória SC"
    val competicao = when (competicaoId) {
        "taca-pt" -> {
            val r1 = pares(CLUBES_PT).map { (a, b) -> par(a, b, rng, false, meu = minhaEquipa) }
            val r2 = pares(r1.map(::vencedorDe)).map { (a, b) -> par(a, b, rng, false, meu = minhaEquipa) }
            val g2 = r2.map(::vencedorDe)
            // Quartos: a duas mãos, com a.p. e g.p. combinados à mão.
            val quartos = listOf(
                par(g2[0], g2[1], rng, true, forcar = "ap"),
                par(g2[2], g2[3], rng, true, meu = g2[2], forcar = "pen"),
                par(g2[4], g2[5], rng, true),
                par(g2[6], g2[7], rng, true, jogadas = 1),
            )
            val meias = (0 until 2).map { i -> EliminatoriaParUi("Por apurar", "Por apurar", emb("A$i"), emb("B$i"), emptyList(), 2, dataProxima = "24 abr") }
            val final = EliminatoriaParUi("Por apurar", "Por apurar", emb("A"), emb("B"), emptyList(), 1, dataProxima = "31 mai")
            CompeticaoUi(
                eliminatorias = EliminatoriasUi(
                    listOf(
                        RondaUi("1.ª eliminatória", r1), RondaUi("Oitavos de final", r2), RondaUi("Quartos de final", quartos),
                        RondaUi("Meias-finais", meias), RondaUi("Final", listOf(final)),
                    ),
                    rondaAtual = 2,
                ),
                vencedores = listOf("2026/27" to "Porto", "2025/26" to "Benfica", "2024/25" to "Sporting").map { (e, v) ->
                    VencedorUi(e, "Taça de Portugal", v, "Braga")
                },
            )
        }
        "supertaca-pt" -> CompeticaoUi(
            eliminatorias = EliminatoriasUi(listOf(RondaUi("Final", listOf(par("Porto", "Benfica", rng, false, meu = "Benfica")))), 0),
            vencedores = listOf(VencedorUi("2026/27", "Supertaça", "Benfica", "Porto"), VencedorUi("2025/26", "Supertaça", "Sporting", "Benfica")),
        )
        "lc", "le" -> {
            val jornadas = jornadasDe(CLUBES_EU, rng, 6, 4, "Benfica")
            val tabela = tabelaDe(CLUBES_EU, jornadas.flatMap { it.jogos }.filter { it.golosCasa != null }, "Benfica")
            val topo = tabela.map { it.nome }
            val playoff = (0 until 8).map { i -> par(topo[8 + i], topo[23 - i], rng, true, meu = "Benfica") }
            val oitavos = (0 until 8).map { i ->
                par(topo[i], vencedorDe(playoff[7 - i]), rng, true, jogadas = if (i < 4) 1 else 0, meu = "Benfica")
            }
            val quartos = (0 until 4).map { EliminatoriaParUi("Por apurar", "Por apurar", emb("A$it"), emb("B$it"), emptyList(), 2, dataProxima = "8 abr") }
            CompeticaoUi(
                eliminatorias = EliminatoriasUi(
                    listOf(RondaUi("Play-off", playoff), RondaUi("Oitavos de final", oitavos), RondaUi("Quartos de final", quartos)), 1,
                ),
                faseLiga = FaseLigaUi(tabela, jornadas, jornadaAtual = 3),
                vencedores = listOf("2026/27" to "Real Madrid", "2025/26" to "Inter", "2024/25" to "PSG").map { (e, v) ->
                    VencedorUi(e, "Liga dos Campeões", v, "Man. City")
                },
            )
        }
        else -> CompeticaoUi()
    }
    return Fase7Ui(competicoes, competicaoId, competicao, demoSelecoes())
}

private fun demoSelecoes(): SelecoesUi {
    val rng = Random(11)
    val ligaNacoes = (0 until 4).map { g ->
        val eq = SELECOES.subList(g * 4, g * 4 + 4)
        val js = jornadasDe(eq, rng, 3, 2, "Portugal")
        GrupoUi("Grupo A${g + 1}", tabelaDe(eq, js.flatMap { it.jogos }.filter { it.golosCasa != null }, "Portugal", 2), js)
    }
    val euroEq = SELECOES.shuffled(Random(3))
    val euroGrupos = ('A'..'F').mapIndexed { g, letra ->
        val eq = euroEq.subList(g * 4, g * 4 + 4)
        val js = jornadasDe(eq, rng, 3, 3, "Portugal")
        GrupoUi("Grupo $letra", tabelaDe(eq, js.flatMap { it.jogos }, "Portugal", 2), js)
    }
    val q = euroGrupos.flatMap { it.tabela.take(2) }.map { it.nome } + euroGrupos.map { it.tabela[2].nome }.take(4)
    val oitavos = pares(q).map { (a, b) -> par(a, b, rng, false, meu = "Portugal") }
    val quartos = pares(oitavos.map(::vencedorDe)).map { (a, b) -> par(a, b, rng, false, meu = "Portugal", jogadas = 0) }
    val euro = EliminatoriasUi(listOf(RondaUi("Oitavos de final", oitavos), RondaUi("Quartos de final", quartos)), 0)
    val naF = EliminatoriasUi(
        listOf(
            RondaUi("Meias-finais", listOf(par("Portugal", "Espanha", rng, false, forcar = "pen", meu = "Portugal"), par("França", "Itália", rng, false))),
            RondaUi("Final", listOf(par("Espanha", "França", rng, false, jogadas = 0))),
        ), 0,
    )
    val pontos = listOf(1892, 1871, 1855, 1843, 1810, 1799, 1782, 1760, 1741, 1722, 1705, 1688, 1670, 1655, 1630, 1611, 1595, 1570, 1544, 1521)
    val rank = pontos.mapIndexed { i, p ->
        LinhaRankingUi(i + 1, SELECOES[i], SELECOES[i], "$p.${(i * 7) % 10}", (i % 5) - 2, meu = SELECOES[i] == "Portugal")
    }
    val conv = convocatoria("Portugal", "Portugal", "Rui Matos", setOf("Rafael Neves", "Hugo Faria"), rng)
    return SelecoesUi(
        torneios = listOf(
            TorneioUi("ln", "Liga das Nações 2026/27", "Em curso · jornada 2 de 6", ligaNacoes, naF),
            TorneioUi("euro", "Euro 2028", "Em curso · fase a eliminar", euroGrupos, euro),
        ),
        proximos = listOf("Mundial 2030" to "11 jun a 13 jul 2030", "Liga das Nações 2028/29" to "set 2028 a jun 2029"),
        ranking = rank,
        convocatorias = listOf(conv, convocatoria("Espanha", "Espanha", "Luis Torres", emptySet(), rng)),
        meusConvocados = conv.convocados.filter { it.meu }.map { ConvocadoUi(it.posicao, it.nome, it.clube, it.geral, true, "Portugal") },
        vencedores = listOf(
            VencedorUi("2028", "Euro", "França", "Portugal"), VencedorUi("2026", "Mundial", "Argentina", "França"),
            VencedorUi("2025", "Liga das Nações", "Portugal", "Espanha"), VencedorUi("2024", "Euro", "Espanha", "Inglaterra"),
        ),
    )
}
