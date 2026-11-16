package pt.futebolcarreira.nucleo.carreira

import kotlinx.serialization.Serializable
import pt.futebolcarreira.nucleo.modelos.AjustesTreino
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.FocoTreino
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.simulacao.LadoRelatorio
import kotlin.math.pow

/**
 * Fase 13 (G5): treino detalhado. O plano semanal diz o que se treina em cada dia (e quanta carga isso dá), o treino
 * individual dá um foco só a alguns jogadores, a mudança de posição ensina um jogador a jogar noutro lugar e a gestão
 * da carga acompanha a fadiga de cada um (jogos seguidos e treinos fortes cansam; descanso e fisioterapia recuperam).
 */
enum class Sessao(val nome: String, val carga: Int, val atributos: Map<String, Double>, val descricao: String) {
    DESCANSO("Descanso", 0, emptyMap(), "Dia livre: recupera a fadiga."),
    RECUPERACAO("Recuperação", 0, emptyMap(), "Treino leve de regeneração."),
    TECNICA("Técnica", 2, mapOf("passe" to 1.0, "drible" to 1.0), "Passe e drible."),
    FINALIZACAO("Finalização", 2, mapOf("remate" to 1.5), "Remate e finalização."),
    TATICA("Tática e defesa", 2, mapOf("defesa" to 1.0, "passe" to 0.5), "Posicionamento e defesa."),
    FISICA("Físico", 3, mapOf("ritmo" to 1.0, "fisico" to 1.0), "Ritmo e força: dá muita carga."),
    GUARDA_REDES("Guarda-redes", 2, mapOf("guarda_redes" to 2.0), "Treino específico dos guarda-redes."),
}

val DIAS_DA_SEMANA = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")

/** Uma semana de treino: uma sessão por dia, de segunda a domingo. */
@Serializable
class PlanoSemanal(val dias: MutableList<Sessao> = PLANOS.getValue("Equilibrado").toMutableList()) {
    fun volume(): Int = dias.sumOf { it.carga }
    fun descansos(): Int = dias.count { it.carga == 0 }

    companion object {
        /** Planos prontos a escolher. */
        val PLANOS: Map<String, List<Sessao>> = linkedMapOf(
            "Equilibrado" to listOf(Sessao.TECNICA, Sessao.FISICA, Sessao.TATICA, Sessao.DESCANSO, Sessao.TECNICA, Sessao.RECUPERACAO, Sessao.DESCANSO),
            "Ofensivo" to listOf(Sessao.FINALIZACAO, Sessao.TECNICA, Sessao.FISICA, Sessao.DESCANSO, Sessao.FINALIZACAO, Sessao.RECUPERACAO, Sessao.DESCANSO),
            "Defensivo" to listOf(Sessao.TATICA, Sessao.FISICA, Sessao.TATICA, Sessao.DESCANSO, Sessao.GUARDA_REDES, Sessao.RECUPERACAO, Sessao.DESCANSO),
            "Físico" to listOf(Sessao.FISICA, Sessao.FISICA, Sessao.TECNICA, Sessao.DESCANSO, Sessao.FISICA, Sessao.RECUPERACAO, Sessao.DESCANSO),
            "Leve" to listOf(Sessao.TECNICA, Sessao.RECUPERACAO, Sessao.TATICA, Sessao.DESCANSO, Sessao.DESCANSO, Sessao.RECUPERACAO, Sessao.DESCANSO),
            "Intenso" to listOf(Sessao.FISICA, Sessao.TECNICA, Sessao.FISICA, Sessao.TATICA, Sessao.FINALIZACAO, Sessao.DESCANSO, Sessao.DESCANSO),
        )
    }
}

/** Um jogador a aprender uma posição nova: demora uns meses e, no fim, passa a jogar nela. */
@Serializable
class Reconversao(val jogadorId: Int, val de: String, val para: String, var meses: Int, val total: Int)

@Serializable
class EstadoTreino(
    /** null = o treino habitual de sempre (plano equilibrado sem efeitos extra). */
    var plano: PlanoSemanal? = null,
    /** Fadiga de cada jogador (0 a 100; ausente = 0). */
    val fadiga: MutableMap<Int, Int> = mutableMapOf(),
    val individuais: MutableMap<Int, FocoTreino> = mutableMapOf(),
    val reconversoes: MutableList<Reconversao> = mutableListOf(),
    /** Rotação automática: os mais cansados ficam em desvantagem na escolha do onze. */
    var rotacao: Boolean = false,
    /** Jogadores a poupar no próximo jogo. */
    val poupados: MutableSet<Int> = mutableSetOf(),
)

/** O resumo do que um plano faz, para o ecrã. */
class EfeitosDoPlano(val volume: Int, val crescimento: Double, val risco: Double, val recuperacaoPorDia: Double, val cargaPorDia: Double)

class PlanosTreino(private val c: Carreira, private val g: GestaoClube) {
    private val e get() = g.estado.treino
    private val meu get() = c.equipa
    private val bd get() = c.bd

    /** O plano em vigor: o escolhido ou, se nunca se escolheu nenhum, o equilibrado. */
    fun plano(): PlanoSemanal = e.plano ?: PlanoSemanal()

    fun temPlano(): Boolean = e.plano != null

    fun usarPreset(nome: String): Resposta {
        val dias = PlanoSemanal.PLANOS[nome] ?: return Resposta.NAO_PERMITIDO
        if (!g.podeGerir) return Resposta.NAO_PERMITIDO
        e.plano = PlanoSemanal(dias.toMutableList())
        g.aplicarAjustes()
        return Resposta.ACEITE
    }

    fun definirSessao(dia: Int, sessao: Sessao): Resposta {
        if (!g.podeGerir || dia !in 0..6) return Resposta.NAO_PERMITIDO
        val p = e.plano ?: PlanoSemanal()
        p.dias[dia] = sessao
        e.plano = p
        g.aplicarAjustes()
        return Resposta.ACEITE
    }

    /** O que o plano faz ao treino: crescimento, risco de lesão e quanto se recupera e se cansa por dia. */
    fun efeitos(p: PlanoSemanal = plano()): EfeitosDoPlano {
        val volume = p.volume()
        val crescimento = (0.85 + 0.15 * volume / 9.0).coerceIn(0.8, 1.15)
        val risco = (volume / 9.0).pow(1.5).coerceIn(0.4, 2.5)
        val recup = RECUPERACAO_BASE + 0.5 * (p.descansos() - 3) + 0.25 * (g.equipaTecnica.nivel(Funcao.PREPARADOR) - 1) + 0.25 * (g.equipaTecnica.nivel(Funcao.FISIOTERAPEUTA) - 1)
        return EfeitosDoPlano(volume, crescimento, risco, recup, volume * 1.2 / 7.0)
    }

    /** Multiplicador de cada atributo conforme as sessões da semana (0,8 sem nenhuma sessão, até 1,6). */
    fun pesosDoPlano(p: PlanoSemanal = plano()): Map<String, Double> {
        val pontos = HashMap<String, Double>()
        for (s in p.dias) for ((a, w) in s.atributos) pontos[a] = (pontos[a] ?: 0.0) + w
        return Jogador.ATRIBUTOS.associateWith { (0.8 + 0.2 * (pontos[it] ?: 0.0)).coerceIn(0.8, 1.6) }
    }

    /** Soma os efeitos do plano (só se houver um escolhido) e da fadiga aos ajustes do clube. */
    fun aplicarEm(a: AjustesTreino) {
        if (e.plano != null) {
            val f = efeitos()
            a.crescimento *= f.crescimento
            a.riscoLesao *= f.risco
            a.pesoAtributo = pesosDoPlano()
        }
        val fadiga = e.fadiga
        a.riscoJogador = { j -> 1.0 + ((fadiga[j.id] ?: 0) - 55).coerceAtLeast(0) / 45.0 }
    }

    // ------------------------------------------------------------ fadiga e carga

    fun fadiga(j: Jogador): Int = e.fadiga[j.id] ?: 0

    fun rotacao(): Boolean = e.rotacao

    fun definirRotacao(ligada: Boolean) { e.rotacao = ligada; g.aplicarAjustes() }

    fun poupado(j: Jogador): Boolean = j.id in e.poupados

    /** Poupar um jogador no próximo jogo (ou deixar de o poupar): o treinador só o põe a jogar se não houver alternativa. */
    fun alternarPoupar(j: Jogador): Resposta {
        if (!g.podeGerir || j.equipa != meu.id) return Resposta.NAO_PERMITIDO
        if (!e.poupados.add(j.id)) e.poupados.remove(j.id)
        g.aplicarAjustes()
        return Resposta.ACEITE
    }

    /** Passam "dias" dias: recupera-se conforme o plano, o descanso e a equipa técnica. */
    fun passarDias(dias: Int) {
        if (dias <= 0) return
        val f = efeitos()
        val liquido = (f.recuperacaoPorDia - f.cargaPorDia) * dias
        for (j in meu.plantel) {
            val atual = e.fadiga[j.id] ?: 0
            val novo = (atual + (-liquido).toInt()).coerceIn(0, 100)
            if (novo == 0) e.fadiga.remove(j.id) else e.fadiga[j.id] = novo
        }
        e.fadiga.keys.retainAll { id -> bd.jogadores[id]?.equipa == meu.id }
    }

    /** Depois de um jogo do clube: quem jogou cansa-se (conforme os minutos); muita fadiga tira forma. */
    fun aposJogo(lado: LadoRelatorio, duracao: Int) {
        for (ficha in lado.fichas) {
            val fim = if (ficha.saiu > 0) ficha.saiu else duracao
            val minutos = (fim - ficha.entrou).coerceAtLeast(0)
            val j = ficha.jogador
            if (minutos > 0) {
                val novo = ((e.fadiga[j.id] ?: 0) + 14.0 * minutos / 90.0).toInt().coerceIn(0, 100)
                e.fadiga[j.id] = novo
                if (novo > 65) j.forma = (j.forma - (novo - 65) / 8).coerceAtLeast(0)
            }
        }
        e.poupados.clear()
    }

    /** Termina a época: o verão limpa a fadiga. */
    fun verao() {
        e.fadiga.clear()
        e.poupados.clear()
    }

    /** Põe (ou tira) nos jogadores a vantagem ou desvantagem que a rotação e os «poupar» dão na escolha do onze. */
    fun aplicarAjuda() {
        for (j in meu.plantel) {
            val extra = (if (e.rotacao) -(fadiga(j) - 70).coerceAtLeast(0) * 0.3 else 0.0) + (if (j.id in e.poupados) -30.0 else 0.0)
            if (extra != 0.0) meu.ajudaNaEscolha[j.id] = extra else meu.ajudaNaEscolha.remove(j.id)
        }
    }

    // ------------------------------------------------------------ treino individual

    fun vagasIndividuais(): Int = g.equipaTecnica.vagasIndividuais()

    fun individual(j: Jogador): FocoTreino = e.individuais[j.id] ?: FocoTreino.EQUILIBRADO

    fun definirIndividual(j: Jogador, foco: FocoTreino): Resposta {
        if (!g.podeGerir || j.equipa != meu.id) return Resposta.NAO_PERMITIDO
        if (foco == FocoTreino.EQUILIBRADO) e.individuais.remove(j.id)
        else {
            if (j.id !in e.individuais && e.individuais.size >= vagasIndividuais()) return Resposta.SEM_VAGAS_TREINO
            e.individuais[j.id] = foco
        }
        g.aplicarAjustes()
        return Resposta.ACEITE
    }

    /** Os treinos individuais em vigor (id -> foco) para o gestor de épocas. */
    fun individuais(): Map<Int, FocoTreino> = e.individuais.filterKeys { id -> bd.jogadores[id]?.equipa == meu.id }

    // ------------------------------------------------------------ mudança de posição

    fun reconversoes(): List<Reconversao> = e.reconversoes

    fun maxReconversoes(): Int = if (g.equipaTecnica.nivel(Funcao.ADJUNTO) >= 3) 3 else 2

    /** Quantos meses demora a aprender a posição (menos com um bom adjunto). */
    fun mesesPara(j: Jogador, nova: String): Int {
        val base = if (nova in Equipa.POSICOES_PARECIDAS.getValue(j.posicao)) 2 else 4
        return (base - if (g.equipaTecnica.nivel(Funcao.ADJUNTO) >= 4) 1 else 0).coerceAtLeast(1)
    }

    /** Posições para onde se pode treinar o jogador (os guarda-redes não mudam, nem se vai para guarda-redes). */
    fun posicoesPossiveis(j: Jogador): List<String> =
        if (j.posicao == "GR") emptyList() else Jogador.POSICOES.filter { it != "GR" && it != j.posicao }

    fun iniciarReconversao(j: Jogador, nova: String): Resposta {
        if (!g.podeGerir || j.equipa != meu.id || nova !in posicoesPossiveis(j)) return Resposta.NAO_PERMITIDO
        if (e.reconversoes.any { it.jogadorId == j.id }) return Resposta.NAO_PERMITIDO
        if (e.reconversoes.size >= maxReconversoes()) return Resposta.SEM_VAGAS_TREINO
        val meses = mesesPara(j, nova)
        e.reconversoes += Reconversao(j.id, j.posicao, nova, meses, meses)
        c.mensagem("Treino de posição: ${j.nome}", "${j.nome} começa a treinar para jogar a ${nova}. Fica pronto em $meses ${if (meses == 1) "mês" else "meses"}.")
        return Resposta.ACEITE
    }

    fun cancelarReconversao(jogadorId: Int): Resposta {
        if (!e.reconversoes.removeAll { it.jogadorId == jogadorId }) return Resposta.NAO_PERMITIDO
        return Resposta.ACEITE
    }

    /** Todos os meses o treino de posição avança; quando acaba, o jogador passa a jogar na posição nova. */
    fun processarMes() {
        val prontas = mutableListOf<Reconversao>()
        for (r in e.reconversoes) {
            r.meses--
            if (r.meses <= 0) prontas += r
        }
        for (r in prontas) {
            e.reconversoes.remove(r)
            val j = bd.jogadores[r.jogadorId] ?: continue
            if (j.equipa != meu.id || j.posicao != r.de) continue
            converter(j, r.para)
        }
    }

    /**
     * Muda a posição do jogador. Quem aprende uma posição nova chega lá a jogar quase tão bem como na antiga:
     * se o "geral" na posição nova fosse mais de 3 pontos mais baixo, os atributos dessa posição sobem um pouco.
     */
    internal fun converter(j: Jogador, nova: String) {
        val antes = j.geral()
        j.posicao = nova
        val depois = j.geral()
        val falta = antes - 3 - depois
        if (falta > 0) {
            val sobe = falta.coerceAtMost(12)
            for (a in (Jogador.PESOS[nova] ?: emptyMap()).keys) j.atributos[a] = (j.atributo(a) + sobe).coerceAtMost(99)
        }
        if (j.potencial < j.geral()) j.potencial = j.geral()
        meu.invalidarForca()
        c.baseDadosPorGravar = true
        c.mensagem("Nova posição: ${j.nome}", "${j.nome} aprendeu a jogar a ${nova} (geral ${j.geral()}).")
    }

    companion object {
        /** Quanto um jogador recupera de fadiga por dia (antes da carga do plano). */
        const val RECUPERACAO_BASE = 5.0
    }
}
