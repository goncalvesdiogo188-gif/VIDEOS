package pt.futebolcarreira.aovivo

import pt.futebolcarreira.nucleo.aovivo.Comando
import pt.futebolcarreira.nucleo.aovivo.ComandoExtra
import pt.futebolcarreira.nucleo.aovivo.ControloTatico
import pt.futebolcarreira.nucleo.aovivo.DetetorMomentos
import pt.futebolcarreira.nucleo.aovivo.EstadoPartida
import pt.futebolcarreira.nucleo.aovivo.FaseJogo
import pt.futebolcarreira.nucleo.aovivo.IaPeloUtilizador
import pt.futebolcarreira.nucleo.aovivo.Mira
import pt.futebolcarreira.nucleo.aovivo.NivelMira
import pt.futebolcarreira.nucleo.aovivo.Palestra
import pt.futebolcarreira.nucleo.aovivo.PartidaAoVivo
import pt.futebolcarreira.nucleo.aovivo.RitmoRapido
import pt.futebolcarreira.nucleo.aovivo.TipoPasse
import pt.futebolcarreira.nucleo.aovivo.TipoRemate
import pt.futebolcarreira.nucleo.aovivo.controloTatico
import pt.futebolcarreira.nucleo.aovivo.passoComExtra
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Como se jogam os jogos da carreira (opção em Mais → Opções). */
enum class ModoJogos(val nome: String) {
    SIMULAR("Simular"), VER_3D("Ver em 3D"), JOGAR_3D("Jogar em 3D"), JOGAR_MOMENTOS("Só os momentos-chave");

    /** O modo do núcleo que corresponde a esta opção. */
    val modoPartida: pt.futebolcarreira.nucleo.aovivo.ModoPartida
        get() = when (this) {
            SIMULAR -> pt.futebolcarreira.nucleo.aovivo.ModoPartida.SIMULAR
            VER_3D -> pt.futebolcarreira.nucleo.aovivo.ModoPartida.VER
            JOGAR_3D -> pt.futebolcarreira.nucleo.aovivo.ModoPartida.COMPLETO
            JOGAR_MOMENTOS -> pt.futebolcarreira.nucleo.aovivo.ModoPartida.MOMENTOS_CHAVE
        }

    companion object {
        fun deNome(texto: String?) = entries.firstOrNull { it.name == texto } ?: SIMULAR
    }
}

/**
 * Um jogo 3D aberto: a partida, o que o utilizador toca e o estado de pausa.
 * O fio do OpenGL chama [avancar] a cada imagem; o ecrã (Compose) só lê e escreve os controlos.
 */
class SessaoAoVivo(
    val partida: PartidaAoVivo,
    /** O utilizador comanda a equipa da casa? */
    val utilizadorCasa: Boolean,
    /** false = só a ver (os controlos não fazem nada). */
    val controlar: Boolean = true,
    val controlo: ControloTactil = ControloTactil(),
    /** Só os momentos-chave: o resto do jogo avança depressa. */
    val momentosChave: Boolean = false,
    /** Ajuda de mira (por omissão a de uma dificuldade Normal). */
    val nivelMira: NivelMira = NivelMira.MEDIA,
    /** Substituições, formação, mentalidade e palestra (por omissão, o que a própria partida oferece). */
    val tatica: ControloTatico = partida.controloTatico(),
) {
    @Volatile
    var pausado = false

    /** true enquanto o jogo avança depressa (momentos sem interesse, no modo «só os momentos-chave»). */
    @Volatile
    var avancandoDepressa = false
        private set

    /** O jogo está parado à espera de o utilizador escolher a palestra do intervalo. */
    @Volatile
    var aguardaPalestra = false
        private set
    private var palestraFeita = false

    /** Há jogadores muito cansados e substituições por usar (atualizado no fio do jogo, de 2 em 2 segundos). */
    @Volatile
    var avisoCansaco = false
        private set
    private var tempoCansaco = 0f

    private val detetor = DetetorMomentos()

    /** Mede as imagens por segundo do desenho 3D (o renderer regista; o ecrã mostra se a opção estiver ligada). */
    val medidor = MedidorDesempenho()

    /** Comando vazio, partilhado: o modo "ver" não cria um novo a cada imagem. */
    private val SEM_COMANDO = Comando()

    /** +1 se a equipa do utilizador ataca para +x (e a câmara mostra o +x à direita); -1 se ataca para -x. */
    fun sentido(): Int {
        val segunda = partida.estado.segundaParte
        return if (utilizadorCasa xor segunda) 1 else -1
    }

    /** Avança "dt" segundos com o que o utilizador está a tocar. */
    fun avancar(dt: Float) {
        if (pausado || partida.terminou) return
        if (precisaPalestra()) return
        val passo = dt.coerceIn(0f, MAX_PASSO)
        verCansaco(passo)
        if (controlar && momentosChave) {
            val agora = !detetor.atualizar(partida.estado, passo)
            if (agora != avancandoDepressa) {
                avancandoDepressa = agora
                (partida as? IaPeloUtilizador)?.iaControlaUtilizador = agora
                controlo.limpar()
            }
            if (agora) { avancarDepressa(); return }
        }
        if (!controlar) { partida.passo(passo, SEM_COMANDO); return }
        val cc = controlo.consumirCompleto(sentido())
        val extra = refinar(cc.comando, cc.extra, partida.estado)
        partida.passoComExtra(passo, cc.comando, extra, sentido())
    }

    /** Dá vários passos pequenos (dentro de um orçamento de tempo) com a IA a jogar pelas duas equipas. */
    private fun avancarDepressa() {
        val inicio = System.nanoTime()
        var n = 0
        while (n < RitmoRapido.PASSOS_POR_IMAGEM && !partida.terminou) {
            partida.passo(RitmoRapido.PASSO, SEM_COMANDO)
            n++
            if (DetetorMomentos.ehMomento(partida.estado)) break
            if (System.nanoTime() - inicio > RitmoRapido.ORCAMENTO_NS) break
        }
    }

    private fun verCansaco(dt: Float) {
        if (!controlar || !tatica.suportado) return
        tempoCansaco += dt
        if (tempoCansaco < 2f) return
        tempoCansaco = 0f
        avisoCansaco = tatica.substituicoesRestantes(utilizadorCasa) > 0 && tatica.onzeEmCampo(utilizadorCasa).any { it.muitoCansado }
    }

    private fun precisaPalestra(): Boolean {
        if (aguardaPalestra) return true
        if (!controlar || palestraFeita || !tatica.suportado) return false
        if (partida.estado.fase == FaseJogo.INTERVALO) {
            aguardaPalestra = true
            controlo.limpar()
            return true
        }
        return false
    }

    /** O utilizador escolheu a palestra (ou nenhuma): o jogo retoma. Devolve a variação de moral. */
    fun resolverPalestra(p: Palestra?): Int {
        val d = if (p != null) tatica.aplicarPalestra(utilizadorCasa, p) else 0
        palestraFeita = true
        aguardaPalestra = false
        return d
    }

    /**
     * Junta ao comando os ângulos que dependem da posição dos jogadores: ajuda de mira nos passes, direção do passe em
     * profundidade e do cruzamento, e o canto do remate colocado. Devolve o mesmo "extra" se não houver nada a fazer.
     */
    internal fun refinar(c: Comando, e: ComandoExtra, estado: EstadoPartida): ComandoExtra {
        if (!c.passe && !c.remate) return e
        val eu = estado.jogadores.firstOrNull { it.controlado } ?: return e
        val s = sentido()
        val golX = s * 52.5f
        var r = e
        if (c.passe) {
            val joystick = hypot(c.movX, c.movY) > 0.25f
            when (e.tipoPasse) {
                TipoPasse.NORMAL -> if (joystick && nivelMira != NivelMira.NENHUMA) {
                    val base = atan2(c.movY, c.movX)
                    val aj = Mira.ajustarPasse(base, alvosDeColegas(estado, eu), nivelMira)
                    if (abs(aj - base) > 1e-4f) r = r.copy(tipoPasse = TipoPasse.DIRIGIDO, anguloPasse = aj)
                }
                TipoPasse.DIRIGIDO -> e.anguloPasse?.let { r = r.copy(anguloPasse = Mira.ajustarPasse(it, alvosDeColegas(estado, eu), nivelMira)) }
                TipoPasse.PROFUNDIDADE -> {
                    val base = Mira.anguloProfundidade(s, c.movY)
                    r = r.copy(anguloPasse = Mira.ajustarPasse(base, alvosDeColegas(estado, eu).filter { it.dx * s > 0f }, nivelMira))
                }
                TipoPasse.CRUZAMENTO -> r = r.copy(anguloPasse = Mira.anguloCruzamento(eu.x, eu.y, golX))
            }
        }
        if (c.remate && e.tipoRemate != TipoRemate.NORMAL) {
            val gr = estado.jogadores.firstOrNull { it.posicao == "GR" && it.casa != utilizadorCasa }
            val lado = if (abs(c.movY) > 0.3f) (if (c.movY > 0f) 1 else -1) else 0
            r = r.copy(anguloRemate = Mira.anguloCanto(eu.x, eu.y, golX, gr?.y, lado))
        }
        return r
    }

    private fun alvosDeColegas(estado: EstadoPartida, eu: pt.futebolcarreira.nucleo.aovivo.JogadorEmCampo): List<Mira.Alvo> =
        estado.jogadores.filter { it.casa == eu.casa && it.id != eu.id && it.posicao != "GR" }.map { Mira.Alvo(it.x - eu.x, it.y - eu.y) }

    /** Joga o que falta sem ninguém a comandar (pausa o resto). Devolve quantos passos deu. */
    fun simularResto(): Int {
        pausado = true
        controlo.limpar()
        var n = 0
        while (!partida.terminou && n < LIMITE_PASSOS) {
            partida.passo(PASSO_RAPIDO, Comando())
            n++
        }
        return n
    }

    companion object {
        const val MAX_PASSO = 0.05f
        const val PASSO_RAPIDO = 0.1f
        const val LIMITE_PASSOS = 100_000
    }
}
