package pt.futebolcarreira.nucleo.simulacao

import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.dados.GeradorJogadores
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.modelos.ListaNomes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Datas dos jogos das seleções. Usam as mesmas paragens que CalendarioDatas.jornadas
 * (sábados de setembro, outubro, novembro e março) mas NUNCA jogam ao sábado:
 *   quinta (sábado - 2), domingo (sábado + 1) e quarta-feira (sábado + 4).
 * Se os outros módulos mudarem as paragens, é só mexer aqui.
 */
object CalendarioSelecoes {
    private val sabado = TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)

    /** Sábado sem liga de cada janela: 0 = setembro, 1 = outubro, 2 = novembro, 3 = março (do ano seguinte). */
    fun paragem(epoca: Int, janela: Int): LocalDate = when (janela) {
        0 -> LocalDate.of(epoca, 9, 3)
        1 -> LocalDate.of(epoca, 10, 8)
        2 -> LocalDate.of(epoca, 11, 12)
        else -> LocalDate.of(epoca + 1, 3, 23)
    }.with(sabado)

    fun quinta(epoca: Int, janela: Int): LocalDate = paragem(epoca, janela).minusDays(2)
    fun domingo(epoca: Int, janela: Int): LocalDate = paragem(epoca, janela).plusDays(1)
    fun quarta(epoca: Int, janela: Int): LocalDate = paragem(epoca, janela).plusDays(4)

    /** Meias-finais da Final Four (fim de maio) e final (3 dias depois). */
    fun quintaJunho(epoca: Int): LocalDate = LocalDate.of(epoca + 1, 5, 28).with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY))
    fun domingoJunho(epoca: Int): LocalDate = quintaJunho(epoca).plusDays(3)

    /** Primeiro dia do Euro / Mundial (entre 5 e 11 de junho); a final é 17 dias depois, sempre antes de 1 de julho. */
    fun inicioTorneio(epoca: Int): LocalDate = LocalDate.of(epoca + 1, 6, 5).with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY))

    /** Que torneio se joga no verão deste ano: Euro nos anos múltiplos de 4, Mundial 2 anos depois. */
    fun torneioDoAno(ano: Int): TipoCompeticaoSelecoes? = when (ano % 4) {
        0 -> TipoCompeticaoSelecoes.EURO
        2 -> TipoCompeticaoSelecoes.MUNDIAL
        else -> null
    }
}

/**
 * Estado de todas as seleções nacionais e das suas competições. Autónomo: só precisa da BaseDados
 * (para os jogadores reais) e do selecoes.json.
 *
 * Seleções "da base de dados" (as nacionalidades dos clubes: POR, ESP, ING, ITA, ALE, FRA) convocam 23 jogadores
 * reais antes de cada janela. As outras têm um plantel fictício guardado SÓ aqui (ids negativos): esses
 * jogadores nunca entram em BaseDados.jogadores nem ficam livres.
 *
 * Como ligar ao GestorEpocas / Carreira / Gravador (resumo):
 *  - Início da carreira: EstadoSelecoes.criar(bd, catalogo, anoDaEpoca) (catalogo = CatalogoSelecoes.carregar(ler)).
 *  - Cada dia de jogo: se proximaData() for <= hoje: jogarAte(hoje). Isto simula os jogos e devolve-os.
 *  - Mais ou menos 5 dias antes de proximaData(): convocarProximaJanela() devolve (nação -> ids) quando há
 *    uma janela nova; para cada id convocado, avisar o utilizador "X foi convocado para a seleção".
 *  - Em 1 de julho (nova época): passarParaEpoca(novaEpoca) (acaba o que faltar do verão e cria a época).
 *  - Gravar: snapshot() / paraJson(); carregar: reconstruir(bd, snapshot) / deJson(bd, texto).
 */
class EstadoSelecoes private constructor(val bd: BaseDados) {
    var epoca = 0
        private set
    private var semente = 1L
    private var jogosJogados = 0
    private var rng: Random = Random(1)
    private var proximoId = -1
    private var nomesExtra: Map<String, ListaNomes> = emptyMap()

    val nacoes = LinkedHashMap<String, Nacao>()
    private val esquadroes = HashMap<String, MutableList<Jogador>>()
    private val ratings = HashMap<String, Double>()
    val competicoes = mutableListOf<CompeticaoSelecoes>()
    val historico = mutableListOf<EntradaHistorico>()
    private val caps = HashMap<Int, Int>()
    private val golos = HashMap<Int, Int>()
    private val suspensoes = HashMap<Int, Int>()
    private val convocatorias = HashMap<String, List<Int>>()
    private var janelaAtual: String? = null
    private var ultimaData: String? = null

    // ---------------------------------------------------------------- consultas

    fun competicoesAtivas(): List<CompeticaoSelecoes> = competicoes.filter { !it.terminada }
    fun competicoesTerminadas(): List<CompeticaoSelecoes> = competicoes.filter { it.terminada }
    fun competicao(id: String): CompeticaoSelecoes? = competicoes.firstOrNull { it.id == id }

    /** Vencedores de todas as edições já acabadas (mais recentes no fim). */
    fun vencedores(): List<EntradaHistorico> = historico.toList()

    fun nome(nacao: String): String = nacoes[nacao]?.nome ?: nacao

    /** Ranking tipo FIFA (Elo simples), da melhor para a pior. */
    fun ranking(): List<LinhaRanking> =
        ratings.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .mapIndexed { i, e -> LinhaRanking(i + 1, e.key, nome(e.key), e.value.roundToInt()) }

    fun pontosRanking(nacao: String): Int = (ratings[nacao] ?: 0.0).roundToInt()

    /** Jogos de uma seleção (de todas as competições em memória), por data. */
    fun jogosDaNacao(nacao: String): List<JogoSelecao> =
        competicoes.flatMap { it.jogos }.filter { it.casa == nacao || it.fora == nacao }.sortedBy { it.data }

    /** Tabela de um grupo (ordenada: pontos, diferença de golos, golos marcados, ranking). */
    fun tabela(fase: FaseSelecoes, grupo: GrupoSelecoes): List<LinhaTabelaSelecao> {
        val linhas = grupo.nacoes.associateWith { LinhaTabelaSelecao(it) }
        for (j in fase.jogos) {
            if (!j.jogado || j.grupo != grupo.nome) continue
            val c = linhas[j.casa] ?: continue
            val f = linhas[j.fora] ?: continue
            c.jogos++; f.jogos++
            c.golosMarcados += j.golosCasa; c.golosSofridos += j.golosFora
            f.golosMarcados += j.golosFora; f.golosSofridos += j.golosCasa
            when {
                j.golosCasa > j.golosFora -> { c.vitorias++; f.derrotas++ }
                j.golosCasa < j.golosFora -> { f.vitorias++; c.derrotas++ }
                else -> { c.empates++; f.empates++ }
            }
        }
        return linhas.values.sortedWith(comparadorLinhas())
    }

    private fun comparadorLinhas(): Comparator<LinhaTabelaSelecao> =
        compareByDescending<LinhaTabelaSelecao> { it.pontos }.thenByDescending { it.diferenca }
            .thenByDescending { it.golosMarcados }.thenByDescending { ratings[it.nacao] ?: 0.0 }.thenBy { it.nacao }

    /** Internacionalizações (jogos pela seleção) de um jogador. */
    fun internacionalizacoes(idJogador: Int): Int = caps[idJogador] ?: 0

    /** Golos que um jogador marcou pela seleção. */
    fun golosPelaSelecao(idJogador: Int): Int = golos[idJogador] ?: 0

    /** Plantel de uma seleção fictícia (vazio nas seleções da base de dados). */
    fun esquadraoFicticio(nacao: String): List<Jogador> = esquadroes[nacao].orEmpty()

    // ---------------------------------------------------------------- convocatórias

    /** Jogadores da base de dados (nos clubes, fora das academias) com esta nacionalidade. */
    private fun jogadoresReais(nacao: String): List<Jogador> = bd.jogadores.values.filter { it.nacionalidade == nacao && !it.academia }

    /** Há jogadores que chegam para formar o núcleo da seleção (16 ou mais, com pelo menos 2 guarda-redes)? */
    private fun nucleoReal(reais: List<Jogador>) = reais.size >= MINIMO_REAIS && reais.count { it.posicao == "GR" } >= MINIMO_GR_REAIS

    /**
     * A seleção é feita sobretudo com jogadores reais da base de dados (nações dos clubes ou com jogadores que chegam
     * para formar o núcleo; o plantel fictício só completa o que faltar)?
     */
    fun usaJogadoresReais(nacao: String): Boolean {
        val n = nacoes[nacao] ?: return false
        return n.baseDados || nucleoReal(jogadoresReais(nacao))
    }

    /**
     * Escolhe 23 jogadores (3 GR + melhores por posição; lesionados só se faltar gente). Não altera o estado.
     * Nações com núcleo real: primeiro os reais de cada posição e o fictício completa o que falta. Nas outras
     * (poucos jogadores reais) vale o melhor de cada posição, reais ou fictícios.
     */
    fun calcularConvocatoria(nacao: String): List<Jogador> {
        val reais = jogadoresReais(nacao)
        val ficticios = esquadroes[nacao].orEmpty()
        val nucleo = nacoes[nacao]?.baseDados == true || nucleoReal(reais)
        fun nota(j: Jogador) = j.geral() + j.bonusForma() * 0.5 - (if (j.lesao > 0) 20 + minOf(j.lesao, 10) else 0)
        val porNota = compareByDescending<Jogador> { nota(it) }
        // Com núcleo real, os reais ganham sempre aos fictícios (bónus que os põe à frente).
        val prioridade = compareByDescending<Jogador> { (if (nucleo && it.id > 0) 1000.0 else 0.0) + nota(it) }
        val candidatos = reais + ficticios
        val escolhidos = LinkedHashSet<Jogador>()
        for ((pos, quantos) in QUOTAS) {
            escolhidos += candidatos.filter { it.posicao == pos }.sortedWith(prioridade).take(quantos)
        }
        if (escolhidos.size < TAMANHO_CONVOCATORIA) {
            escolhidos += candidatos.filter { it !in escolhidos }.sortedWith(prioridade.thenComparing(porNota)).take(TAMANHO_CONVOCATORIA - escolhidos.size)
        }
        return escolhidos.toList()
    }

    private fun resolver(nacao: String, ids: List<Int>): List<Jogador> {
        val ficticios = esquadroes[nacao].orEmpty().associateBy { it.id }
        return ids.mapNotNull { bd.jogadores[it] ?: ficticios[it] }
    }

    /** A convocatória em vigor de uma seleção (calcula-a se ainda não existir nesta janela). */
    fun convocados(nacao: String): List<Jogador> {
        nacoes[nacao] ?: return emptyList()
        val atual = convocatorias[nacao]?.let { resolver(nacao, it) }
        if (atual != null && atual.size >= MINIMO_CONVOCADOS) return atual
        // Sem jogadores reais nenhum (nem para entrar por mérito), é o plantel fictício, sem guardar nada.
        if (nacoes[nacao]?.baseDados != true && bd.jogadores.values.none { it.nacionalidade == nacao && !it.academia }) return esquadroes[nacao].orEmpty()
        val nova = calcularConvocatoria(nacao)
        convocatorias[nacao] = nova.map { it.id }
        return nova
    }

    /** A convocatória a mostrar: a da janela em curso, ou a que se faria agora (sem a guardar). */
    fun convocatoriaAtual(nacao: String): List<Jogador> {
        val guardada = convocatorias[nacao]?.let { resolver(nacao, it) }
        if (guardada != null && guardada.size >= MINIMO_CONVOCADOS) return guardada
        return calcularConvocatoria(nacao)
    }

    /** Convocatórias da janela em curso (seleções com jogadores reais; ids negativos são fictícios): nação -> ids. */
    fun convocadosDaJanela(): Map<String, List<Int>> = convocatorias.toMap()

    /** Em que seleção está convocado este jogador (na janela em curso)? null = não está. */
    fun nacaoDoConvocado(idJogador: Int): String? = convocatorias.entries.firstOrNull { idJogador in it.value }?.key

    fun foiConvocado(idJogador: Int): Boolean = nacaoDoConvocado(idJogador) != null

    /**
     * Prepara a janela internacional que vem a seguir, se ainda não foi preparada: convoca as seleções
     * da base de dados que jogam nela. Devolve nação -> ids dos convocados (vazio se não há janela nova).
     * É aqui que a carreira deve avisar "X foi convocado para a seleção".
     */
    fun convocarProximaJanela(): Map<String, List<Int>> {
        val proxima = proximaData() ?: return emptyMap()
        if (!janelaNova(proxima) || janelaAtual == proxima.toString()) return emptyMap()
        janelaAtual = proxima.toString()
        convocatorias.clear()
        val fim = proxima.plusDays(JANELA_DIAS.toLong())
        val jogam = competicoesAtivas().flatMap { it.jogos }
            .filter { !it.jogado && it.dataLocal() in proxima..fim }.flatMap { listOf(it.casa, it.fora) }.toSet()
        for (n in jogam) convocados(n)
        return convocatorias.toMap()
    }

    private fun janelaNova(data: LocalDate): Boolean {
        val ultima = ultimaData ?: return true
        return ChronoUnit.DAYS.between(LocalDate.parse(ultima), data) > JANELA_DIAS
    }

    // ---------------------------------------------------------------- jogar

    /** Data do próximo jogo de seleções por jogar (em qualquer competição ativa); null = nada marcado. */
    fun proximaData(): LocalDate? =
        competicoesAtivas().flatMap { it.fases.lastOrNull()?.jogos.orEmpty() }.filter { !it.jogado }.minOfOrNull { it.dataLocal() }

    /** Joga todos os jogos marcados até "limite" (inclusive), dia a dia. Devolve os jogos jogados. */
    fun jogarAte(limite: LocalDate): List<JogoJogado> {
        val feitos = mutableListOf<JogoJogado>()
        while (true) {
            val dia = proximaData() ?: break
            if (dia > limite) break
            feitos += jogarDia(dia)
        }
        return feitos
    }

    /** Joga já tudo o que falta do verão desta época (Final Four, Euro ou Mundial, até 30 de junho). */
    fun jogarTorneioDeVerao(): List<JogoJogado> = jogarAte(LocalDate.of(epoca + 1, 6, 30))

    private fun jogarDia(dia: LocalDate): List<JogoJogado> {
        if (janelaNova(dia) && janelaAtual != dia.toString()) {
            janelaAtual = dia.toString()
            convocatorias.clear()
        }
        val feitos = mutableListOf<JogoJogado>()
        for (c in competicoesAtivas()) {
            val fase = c.fases.last()
            for (jogo in fase.jogos.filter { !it.jogado && it.dataLocal() <= dia }) feitos += jogar(c, fase, jogo)
        }
        for (c in competicoesAtivas()) avancar(c)
        criarTorneios()
        ultimaData = dia.toString()
        return feitos
    }

    private class Guardado(val j: Jogador, val jogos: Int, val golos: Int, val assist: Int, val amarelos: Int, val vermelhos: Int, val notas: Double, val suspensao: Int)

    private fun jogar(c: CompeticaoSelecoes, fase: FaseSelecoes, jogo: JogoSelecao): JogoJogado {
        val casa = construirEquipa(jogo.casa)
        val fora = construirEquipa(jogo.fora)
        val jogadores = casa.plantel + fora.plantel
        // Guarda o que é do clube: estatísticas da época e castigos não passam para as seleções.
        val guardados = jogadores.map { Guardado(it, it.epoca.jogos, it.epoca.golos, it.epoca.assistencias, it.epoca.amarelos, it.epoca.vermelhos, it.epoca.somaNotas, it.suspensao) }
        for (j in jogadores) {
            j.epoca.limpar()
            j.suspensao = suspensoes[j.id] ?: 0
        }
        val relatorio = MotorJogo.simular(casa, fora, rng)
        MotorJogo.aplicar(relatorio)
        jogo.golosCasa = relatorio.golosCasa
        jogo.golosFora = relatorio.golosFora
        for (ficha in relatorio.casa.fichas + relatorio.fora.fichas) {
            caps[ficha.jogador.id] = (caps[ficha.jogador.id] ?: 0) + 1
            if (ficha.golos > 0) golos[ficha.jogador.id] = (golos[ficha.jogador.id] ?: 0) + ficha.golos
        }
        // Castigos das seleções ficam guardados à parte; as lesões ficam no jogador (passam para o clube).
        for (j in jogadores) if (j.suspensao > 0) suspensoes[j.id] = j.suspensao else suspensoes.remove(j.id)
        for (g in guardados) {
            val e = g.j.epoca
            e.jogos = g.jogos; e.golos = g.golos; e.assistencias = g.assist; e.amarelos = g.amarelos
            e.vermelhos = g.vermelhos; e.somaNotas = g.notas
            g.j.suspensao = g.suspensao
        }
        if (fase.eliminatoria && jogo.golosCasa == jogo.golosFora) decidirEmpate(jogo, casa, fora)
        jogo.jogado = true
        jogosJogados++
        atualizarRatings(jogo)
        return JogoJogado(c.id, c.nome, fase.nome, jogo, relatorio)
    }

    /** Prolongamento (30 minutos) e, se continuar empatado, penáltis. Tudo isolado aqui. */
    private fun decidirEmpate(jogo: JogoSelecao, casa: Equipa, fora: Equipa) {
        val fc = casa.forca()
        val ff = fora.forca()
        val base = 1.3 / 3.0
        val extraCasa = rng.poisson(base * exp((fc.ataque - ff.defesa) / SimuladorJogo.ESCALA), 3)
        val extraFora = rng.poisson(base * exp((ff.ataque - fc.defesa) / SimuladorJogo.ESCALA), 3)
        jogo.golosCasa += extraCasa
        jogo.golosFora += extraFora
        jogo.prolongamento = true
        repeat(extraCasa) { marcador(casa)?.let { golos[it.id] = (golos[it.id] ?: 0) + 1 } }
        repeat(extraFora) { marcador(fora)?.let { golos[it.id] = (golos[it.id] ?: 0) + 1 } }
        if (jogo.golosCasa != jogo.golosFora) return
        val vantagem = (fc.geral - ff.geral) * 0.003
        val pc = (0.75 + vantagem).coerceIn(0.6, 0.88)
        val pf = (0.75 - vantagem).coerceIn(0.6, 0.88)
        var a = 0
        var b = 0
        repeat(5) {
            if (rng.nextDouble() < pc) a++
            if (rng.nextDouble() < pf) b++
        }
        while (a == b) {
            if (rng.nextDouble() < pc) a++
            if (rng.nextDouble() < pf) b++
        }
        jogo.penaltisCasa = a
        jogo.penaltisFora = b
    }

    private fun marcador(equipa: Equipa): Jogador? {
        val peso = mapOf("PL" to 5.0, "MOC" to 3.0, "ED" to 3.0, "EE" to 3.0, "MC" to 1.5, "MDC" to 0.7, "DD" to 0.5, "DE" to 0.5, "DC" to 0.7)
        val aptos = equipa.plantel.filter { it.lesao == 0 && it.posicao != "GR" }
        if (aptos.isEmpty()) return null
        val pesos = aptos.map { (peso[it.posicao] ?: 0.5) * (it.geral() / 70.0).pow(3) }
        var sorteio = rng.nextDouble() * pesos.sum()
        for (i in aptos.indices) {
            sorteio -= pesos[i]
            if (sorteio <= 0) return aptos[i]
        }
        return aptos.last()
    }

    private fun construirEquipa(nacao: String): Equipa {
        val n = nacoes.getValue(nacao)
        val e = Equipa(id = "SEL_$nacao", nome = n.nome, abreviatura = n.abreviatura, pais = nacao, reputacao = n.reputacao)
        e.plantel += convocados(nacao)
        e.normalizar()
        e.escolherFormacao()
        e.invalidarForca()
        return e
    }

    private fun atualizarRatings(jogo: JogoSelecao) {
        val rc = ratings[jogo.casa] ?: return
        val rf = ratings[jogo.fora] ?: return
        val esperado = 1.0 / (1.0 + 10.0.pow((rf - rc) / 400.0))
        val real = when {
            jogo.golosCasa > jogo.golosFora -> 1.0
            jogo.golosCasa < jogo.golosFora -> 0.0
            else -> 0.5
        }
        val dif = abs(jogo.golosCasa - jogo.golosFora)
        val mult = when {
            dif <= 1 -> 1.0
            dif == 2 -> 1.5
            else -> (11 + dif) / 8.0
        }
        val troca = 30.0 * mult * (real - esperado)
        ratings[jogo.casa] = rc + troca
        ratings[jogo.fora] = rf - troca
    }

    // ---------------------------------------------------------------- épocas

    /**
     * Chamar a 1 de julho da nova época. Joga o que faltar do verão anterior, envelhece os plantéis
     * fictícios, limpa castigos e cria a Liga das Nações e (na época antes de um torneio) a qualificação.
     */
    fun passarParaEpoca(novaEpoca: Int) {
        while (epoca < novaEpoca) {
            jogarAte(LocalDate.of(epoca + 1, 6, 30))
            // Termina o que ainda estiver a meio (não deve acontecer, mas não fica nada pendurado).
            while (competicoesAtivas().isNotEmpty() && proximaData() != null) jogarAte(proximaData()!!)
            competicoes.removeAll { it.terminada && it.epoca < epoca }
            envelhecerEsquadroes()
            suspensoes.clear()
            convocatorias.clear()
            janelaAtual = null
            epoca++
            criarCompeticoesDaEpoca(epoca)
        }
    }

    private fun envelhecerEsquadroes() {
        for ((id, plantel) in esquadroes) {
            val nacao = nacoes.getValue(id)
            for (i in plantel.indices) {
                val j = plantel[i]
                j.idade++
                j.lesao = 0
                val delta = when {
                    j.idade <= 23 -> rng.nextInt(0, 3)
                    j.idade <= 28 -> if (rng.nextDouble() < 0.3) 1 else 0
                    j.idade >= 31 -> -rng.nextInt(0, 2)
                    else -> 0
                }
                if (delta > 0 && j.geral() < j.potencial) j.ajustarAtributos(delta)
                if (delta < 0) j.ajustarAtributos(delta)
                if (j.idade >= 36 || (j.idade >= 34 && rng.nextDouble() < 0.3)) plantel[i] = novoJogador(nacao, j.posicao, false)
            }
        }
    }

    // ---------------------------------------------------------------- criação das competições

    private fun criarCompeticoesDaEpoca(e: Int) {
        competicoes += criarLigaDasNacoes(e)
        val torneio = CalendarioSelecoes.torneioDoAno(e + 1)
        if (torneio != null) competicoes += criarQualificacao(e, torneio)
    }

    private fun porRanking(ids: Collection<String>): List<String> = ids.sortedWith(compareByDescending<String> { ratings[it] ?: 0.0 }.thenBy { it })

    /** Sorteio por potes: cada grupo recebe uma seleção de cada pote (os potes são fatias do ranking). */
    private fun sortear(ids: List<String>, grupos: Int): List<List<String>> {
        val resultado = List(grupos) { mutableListOf<String>() }
        for (pote in porRanking(ids).chunked(grupos)) {
            pote.shuffled(rng).forEachIndexed { i, n -> resultado[i] += n }
        }
        return resultado
    }

    private fun letra(i: Int) = ('A' + i).toString()

    /** Jornadas de todos-contra-todos (método do círculo) para um número par de equipas. */
    private fun jornadas(equipas: List<String>, duplo: Boolean): List<List<Pair<String, String>>> {
        val lista = equipas.toMutableList()
        val n = lista.size
        val primeira = (0 until n - 1).map { r ->
            val jogos = (0 until n / 2).map { i ->
                val a = lista[i]
                val b = lista[n - 1 - i]
                if ((i + r) % 2 == 0) a to b else b to a
            }
            lista.add(1, lista.removeAt(n - 1))
            jogos
        }
        return if (duplo) primeira + primeira.map { r -> r.map { it.second to it.first } } else primeira
    }

    private fun jogosDoGrupo(nome: String, equipas: List<String>, duplo: Boolean, neutro: Boolean, data: (Int) -> LocalDate): List<JogoSelecao> =
        jornadas(equipas, duplo).flatMapIndexed { r, jogos ->
            jogos.map { (a, b) ->
                val inverter = neutro && rng.nextBoolean()
                JogoSelecao(if (inverter) b else a, if (inverter) a else b, data(r).toString(), nome, r + 1)
            }
        }

    private fun jogoEliminatoria(a: String, b: String, data: LocalDate, neutro: Boolean = true, grupo: String = ""): JogoSelecao {
        val inverter = neutro && rng.nextBoolean()
        return JogoSelecao(if (inverter) b else a, if (inverter) a else b, data.toString(), grupo)
    }

    /**
     * Liga das Nações (todas as épocas, só europeias): as 32 seleções europeias ordenadas por ranking
     * formam a Liga A (16) e a Liga B (16), cada uma com 4 grupos de 4, casa e fora (6 jornadas: quinta e domingo
     * das janelas de setembro, outubro e novembro). Não há subidas nem descidas: cada edição volta a
     * distribuir pelo ranking. Os 4 vencedores dos grupos da Liga A jogam a Final Four em campo neutro
     * (meias-finais e final no fim de maio, antes do Euro/Mundial, por isso não há conflito de datas).
     */
    private fun criarLigaDasNacoes(e: Int): CompeticaoSelecoes {
        val c = CompeticaoSelecoes("LN-$e", TipoCompeticaoSelecoes.LIGA_NACOES, "Liga das Nações $e/${(e + 1).toString().takeLast(2)}", e + 1, e)
        val europeias = porRanking(nacoes.values.filter { it.europeia }.map { it.id })
        val grupos = mutableListOf<GrupoSelecoes>()
        val jogos = mutableListOf<JogoSelecao>()
        for ((k, liga) in europeias.chunked(16).withIndex()) {
            val n = liga.size / 4
            if (n == 0) continue
            sortear(liga.take(n * 4), n).forEachIndexed { i, equipas ->
                val nome = "Liga ${letra(k)} - Grupo ${i + 1}"
                grupos += GrupoSelecoes(nome, equipas)
                jogos += jogosDoGrupo(nome, equipas, true, false) { r ->
                    if (r % 2 == 0) CalendarioSelecoes.quinta(e, r / 2) else CalendarioSelecoes.domingo(e, r / 2)
                }
            }
        }
        c.fases += FaseSelecoes("Fase de grupos", grupos, jogos)
        return c
    }

    private data class Zona(val nome: String, val vagas: Int, val diretos: Int, val nacoes: List<String>)

    private fun zonasQualificacao(tipo: TipoCompeticaoSelecoes): List<Zona> {
        val europeias = nacoes.values.filter { it.europeia }.map { it.id }
        return if (tipo == TipoCompeticaoSelecoes.EURO) listOf(Zona("Europa", 24, 2, europeias))
        else listOf(
            Zona("Europa", 16, 1, europeias),
            Zona("Fora da Europa", 16, 2, nacoes.values.filter { !it.europeia }.map { it.id }),
        )
    }

    /**
     * Qualificação (época anterior ao torneio): grupos de 4 a uma volta, jogados às quartas-feiras das
     * janelas de setembro, outubro e novembro. Euro: 8 grupos europeus, 2 primeiros apurados (16) e os 16
     * restantes jogam um play-off a um jogo em março (8 vencedores) = 24. Mundial: Europa 8 grupos (só o
     * primeiro apurado, 2.ºs e 3.ºs jogam play-off: 16 vagas) e resto do mundo 6 grupos (2 primeiros + 4 vencedores do play-off
     * entre os 8 melhores restantes: 16 vagas) = 32.
     */
    private fun criarQualificacao(e: Int, torneio: TipoCompeticaoSelecoes): CompeticaoSelecoes {
        val ano = e + 1
        val tipo = if (torneio == TipoCompeticaoSelecoes.EURO) TipoCompeticaoSelecoes.QUALIFICACAO_EURO else TipoCompeticaoSelecoes.QUALIFICACAO_MUNDIAL
        val c = CompeticaoSelecoes("${tipo.name}-$ano", tipo, "${tipo.nome} $ano", ano, e)
        val grupos = mutableListOf<GrupoSelecoes>()
        val jogos = mutableListOf<JogoSelecao>()
        for (zona in zonasQualificacao(torneio)) {
            sortear(zona.nacoes, zona.nacoes.size / 4).forEachIndexed { i, equipas ->
                val nome = "${zona.nome} - Grupo ${letra(i)}"
                grupos += GrupoSelecoes(nome, equipas)
                jogos += jogosDoGrupo(nome, equipas, false, false) { r -> CalendarioSelecoes.quarta(e, r) }
            }
        }
        c.fases += FaseSelecoes("Fase de grupos", grupos, jogos)
        return c
    }

    /** Cria o Euro/Mundial assim que a qualificação respetiva acaba. */
    private fun criarTorneios() {
        for (q in competicoes.toList()) {
            if (!q.terminada || (q.tipo != TipoCompeticaoSelecoes.QUALIFICACAO_EURO && q.tipo != TipoCompeticaoSelecoes.QUALIFICACAO_MUNDIAL)) continue
            val tipo = if (q.tipo == TipoCompeticaoSelecoes.QUALIFICACAO_EURO) TipoCompeticaoSelecoes.EURO else TipoCompeticaoSelecoes.MUNDIAL
            if (competicoes.any { it.id == "${tipo.name}-${q.ano}" }) continue
            competicoes += criarTorneio(tipo, q.ano, q.epoca, q.qualificadas.toList())
        }
    }

    /** Torneio final: grupos de 4 a uma volta (quinta, domingo e quarta-feira) em campo neutro. */
    private fun criarTorneio(tipo: TipoCompeticaoSelecoes, ano: Int, e: Int, equipas: List<String>): CompeticaoSelecoes {
        val c = CompeticaoSelecoes("${tipo.name}-$ano", tipo, "${tipo.nome} $ano", ano, e)
        val inicio = CalendarioSelecoes.inicioTorneio(e)
        val grupos = mutableListOf<GrupoSelecoes>()
        val jogos = mutableListOf<JogoSelecao>()
        sortear(equipas, equipas.size / 4).forEachIndexed { i, g ->
            val nome = "Grupo ${letra(i)}"
            grupos += GrupoSelecoes(nome, g)
            jogos += jogosDoGrupo(nome, g, false, true) { r -> inicio.plusDays(3L * r) }
        }
        c.fases += FaseSelecoes("Fase de grupos", grupos, jogos)
        return c
    }

    // ---------------------------------------------------------------- avançar de fase

    private fun avancar(c: CompeticaoSelecoes) {
        if (c.terminada) return
        val fase = c.fases.last()
        if (!fase.completa) return
        when (c.tipo) {
            TipoCompeticaoSelecoes.LIGA_NACOES -> avancarLigaDasNacoes(c, fase)
            TipoCompeticaoSelecoes.QUALIFICACAO_EURO, TipoCompeticaoSelecoes.QUALIFICACAO_MUNDIAL -> avancarQualificacao(c, fase)
            TipoCompeticaoSelecoes.EURO, TipoCompeticaoSelecoes.MUNDIAL -> avancarTorneio(c, fase)
        }
    }

    private fun terminar(c: CompeticaoSelecoes, final: JogoSelecao) {
        val v = final.vencedor() ?: final.casa
        c.vencedor = v
        c.finalista = if (v == final.casa) final.fora else final.casa
        c.terminada = true
        historico += EntradaHistorico(c.ano, c.tipo, c.nome, v, c.finalista ?: "")
    }

    private fun avancarLigaDasNacoes(c: CompeticaoSelecoes, fase: FaseSelecoes) {
        when (c.fases.size) {
            1 -> {
                val v = fase.grupos.filter { it.nome.startsWith("Liga A") }.map { tabela(fase, it).first() }.sortedWith(comparadorLinhas())
                if (v.size < 4) {
                    // Sem Final Four possível: ganha o melhor vencedor de grupo.
                    val melhor = v.firstOrNull()?.nacao ?: fase.jogos.first().casa
                    c.vencedor = melhor; c.terminada = true
                    historico += EntradaHistorico(c.ano, c.tipo, c.nome, melhor)
                    return
                }
                val data = CalendarioSelecoes.quintaJunho(c.epoca)
                c.fases += FaseSelecoes("Meias-finais", jogos = mutableListOf(
                    jogoEliminatoria(v[0].nacao, v[3].nacao, data), jogoEliminatoria(v[1].nacao, v[2].nacao, data),
                ))
            }
            2 -> {
                val g = fase.jogos.map { it.vencedor()!! }
                c.fases += FaseSelecoes("Final", jogos = mutableListOf(jogoEliminatoria(g[0], g[1], CalendarioSelecoes.domingoJunho(c.epoca))))
            }
            else -> terminar(c, fase.jogos.first())
        }
    }

    private fun avancarQualificacao(c: CompeticaoSelecoes, fase: FaseSelecoes) {
        val torneio = if (c.tipo == TipoCompeticaoSelecoes.QUALIFICACAO_EURO) TipoCompeticaoSelecoes.EURO else TipoCompeticaoSelecoes.MUNDIAL
        if (c.fases.size >= 2) {
            c.qualificadas += fase.jogos.map { it.vencedor()!! }
            c.terminada = true
            return
        }
        val data = CalendarioSelecoes.quinta(c.epoca, 3)
        val playoff = mutableListOf<JogoSelecao>()
        for (zona in zonasQualificacao(torneio)) {
            val grupos = fase.grupos.filter { it.nome.startsWith("${zona.nome} - ") }
            val restantes = mutableListOf<Pair<Int, LinhaTabelaSelecao>>()
            for (g in grupos) {
                tabela(fase, g).forEachIndexed { pos, linha ->
                    if (pos < zona.diretos) c.qualificadas += linha.nacao else restantes += pos to linha
                }
            }
            val ligas = zona.vagas - grupos.size * zona.diretos
            if (ligas <= 0) continue
            val ordenados = restantes.sortedWith(compareBy<Pair<Int, LinhaTabelaSelecao>> { it.first }.thenComparing { a, b -> comparadorLinhas().compare(a.second, b.second) })
                .take(ligas * 2).map { it.second.nacao }
            for (i in 0 until ligas) {
                // O mais bem classificado joga em casa.
                playoff += jogoEliminatoria(ordenados[i], ordenados[ordenados.size - 1 - i], data, neutro = false, grupo = zona.nome)
            }
        }
        if (playoff.isEmpty()) {
            c.terminada = true
        } else {
            c.fases += FaseSelecoes("Play-off", jogos = playoff)
        }
    }

    private fun avancarTorneio(c: CompeticaoSelecoes, fase: FaseSelecoes) {
        val inicio = CalendarioSelecoes.inicioTorneio(c.epoca)
        val pares: List<Pair<String, String>> = if (fase.eliminatoria) {
            if (fase.nome == "Final") {
                terminar(c, fase.jogos.first())
                return
            }
            fase.jogos.map { it.vencedor()!! }.chunked(2).map { it[0] to it[1] }
        } else {
            val tabs = fase.grupos.map { tabela(fase, it) }
            if (c.tipo == TipoCompeticaoSelecoes.MUNDIAL) {
                // 1A-2B, 1C-2D, 1E-2F, 1G-2H, 1B-2A, 1D-2C, 1F-2E, 1H-2G (como no Mundial a sério).
                listOf(0 to 1, 2 to 3, 4 to 5, 6 to 7, 1 to 0, 3 to 2, 5 to 4, 7 to 6).map { (a, b) -> tabs[a][0].nacao to tabs[b][1].nacao }
            } else {
                // Os 6 primeiros, os 6 segundos e os 4 melhores terceiros entram por ordem de classificação.
                val cmp = comparadorLinhas()
                val sementes = (tabs.map { it[0] }.sortedWith(cmp) + tabs.map { it[1] }.sortedWith(cmp) + tabs.map { it[2] }.sortedWith(cmp).take(4)).map { it.nacao }
                val ordem = listOf(1, 16, 8, 9, 4, 13, 5, 12, 3, 14, 6, 11, 7, 10, 2, 15)
                ordem.map { sementes[it - 1] }.chunked(2).map { it[0] to it[1] }
            }
        }
        val (nome, dias) = when (pares.size) {
            8 -> "Oitavos de final" to 8L
            4 -> "Quartos de final" to 11L
            2 -> "Meias-finais" to 14L
            else -> "Final" to 17L
        }
        c.fases += FaseSelecoes(nome, jogos = pares.map { (a, b) -> jogoEliminatoria(a, b, inicio.plusDays(dias)) }.toMutableList())
    }

    // ---------------------------------------------------------------- plantéis fictícios

    private fun geradorDe(nacao: Nacao): GeradorJogadores {
        val lista = nomesExtra[nacao.nomes] ?: bd.nomes[nacao.nomes] ?: ListaNomes()
        // Um só grupo de nomes: o gerador não mistura nacionalidades.
        return GeradorJogadores(mapOf(nacao.nomes to lista), rng)
    }

    private fun novoJogador(nacao: Nacao, posicao: String, titular: Boolean): Jogador {
        val nivel = 50.0 + 0.37 * nacao.reputacao
        val j = if (titular) {
            geradorDe(nacao).criar(nacao.nomes, posicao, nivel + rng.gauss(0.0, 2.5), rng.nextInt(21, 34))
        } else {
            geradorDe(nacao).criar(nacao.nomes, posicao, nivel - 5.0 + rng.gauss(0.0, 3.0), rng.nextInt(19, 35))
        }
        j.nacionalidade = nacao.id
        j.equipa = ""
        j.id = proximoId--
        return j
    }

    private fun criarEsquadrao(nacao: Nacao): MutableList<Jogador> {
        val plantel = mutableListOf<Jogador>()
        val ger = geradorDe(nacao)
        for ((pos, quantos) in QUOTAS) {
            repeat(quantos) { i ->
                val titular = i < GeradorJogadores.TITULARES.getValue(pos)
                val nivel = 50.0 + 0.37 * nacao.reputacao
                val j = ger.criar(nacao.nomes, pos, if (titular) nivel + rng.gauss(0.0, 2.5) else nivel - 5.0 + rng.gauss(0.0, 3.0), rng.nextInt(21, 34))
                j.nacionalidade = nacao.id
                j.equipa = ""
                j.id = proximoId--
                plantel += j
            }
        }
        return plantel
    }

    // ---------------------------------------------------------------- gravar / carregar

    fun snapshot(): SnapshotSelecoes = SnapshotSelecoes(
        epoca = epoca, semente = semente, jogosJogados = jogosJogados, nacoes = nacoes.values.toList(),
        esquadroes = esquadroes.mapValues { it.value.toList() },
        lesoes = esquadroes.values.flatten().filter { it.lesao > 0 }.associate { it.id to it.lesao },
        ratings = ratings.toMap(), competicoes = competicoes.toList(), historico = historico.toList(),
        internacionalizacoes = caps.toMap(), golosSelecao = golos.toMap(), suspensoes = suspensoes.toMap(),
        convocatorias = convocatorias.toMap(), janelaAtual = janelaAtual, ultimaData = ultimaData,
        proximoIdFicticio = proximoId, nomesExtra = nomesExtra,
    )

    fun paraJson(): String = BaseDados.JSON.encodeToString(SnapshotSelecoes.serializer(), snapshot())

    companion object {
        /** Jogadores por posição numa convocatória (soma 23). */
        val QUOTAS = linkedMapOf("GR" to 3, "DD" to 2, "DC" to 4, "DE" to 2, "MDC" to 2, "MC" to 3, "MOC" to 2, "ED" to 2, "EE" to 2, "PL" to 1)
        const val TAMANHO_CONVOCATORIA = 23
        private const val MINIMO_CONVOCADOS = 18
        /** Jogadores reais (e guarda-redes entre eles) que uma nação precisa para formar o núcleo da seleção. */
        const val MINIMO_REAIS = 16
        const val MINIMO_GR_REAIS = 2
        /** Dias seguidos sem jogos a partir dos quais começa outra janela. */
        private const val JANELA_DIAS = 10

        /** Cria o estado para o início da época "epoca" (a que começa em 1 de julho desse ano). */
        fun criar(bd: BaseDados, catalogo: CatalogoSelecoes, epoca: Int, semente: Long = 1L): EstadoSelecoes {
            val s = EstadoSelecoes(bd)
            s.epoca = epoca
            s.semente = semente
            s.rng = Random(semente)
            s.nomesExtra = catalogo.nomes
            for (n in catalogo.nacoes) {
                n.baseDados = n.id in bd.paises
                s.nacoes[n.id] = n
                s.ratings[n.id] = 1300.0 + 8.0 * n.reputacao
                if (!n.baseDados) s.esquadroes[n.id] = s.criarEsquadrao(n)
            }
            s.criarCompeticoesDaEpoca(epoca)
            return s
        }

        /** Refaz o estado gravado. Os jogadores reais voltam a vir da BaseDados (por id). */
        fun reconstruir(bd: BaseDados, snap: SnapshotSelecoes): EstadoSelecoes {
            val s = EstadoSelecoes(bd)
            s.epoca = snap.epoca
            s.semente = snap.semente
            s.jogosJogados = snap.jogosJogados
            s.rng = Random(snap.semente + snap.jogosJogados * 7919L)
            s.proximoId = snap.proximoIdFicticio
            s.nomesExtra = snap.nomesExtra
            for (n in snap.nacoes) s.nacoes[n.id] = n
            for ((id, lista) in snap.esquadroes) {
                for (j in lista) j.lesao = snap.lesoes[j.id] ?: 0
                s.esquadroes[id] = lista.toMutableList()
            }
            s.ratings += snap.ratings
            s.competicoes += snap.competicoes
            s.historico += snap.historico
            s.caps += snap.internacionalizacoes
            s.golos += snap.golosSelecao
            s.suspensoes += snap.suspensoes
            s.convocatorias += snap.convocatorias
            s.janelaAtual = snap.janelaAtual
            s.ultimaData = snap.ultimaData
            return s
        }

        fun deJson(bd: BaseDados, texto: String): EstadoSelecoes =
            reconstruir(bd, BaseDados.JSON.decodeFromString(SnapshotSelecoes.serializer(), texto))
    }
}
