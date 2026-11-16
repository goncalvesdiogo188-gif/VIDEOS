package pt.futebolcarreira.aovivo

import pt.futebolcarreira.nucleo.aovivo.Animacao
import pt.futebolcarreira.nucleo.aovivo.Bola
import pt.futebolcarreira.nucleo.aovivo.Comando
import pt.futebolcarreira.nucleo.aovivo.ComandoExtra
import pt.futebolcarreira.nucleo.aovivo.ControloTatico
import pt.futebolcarreira.nucleo.aovivo.EstadoPartida
import pt.futebolcarreira.nucleo.aovivo.FaseJogo
import pt.futebolcarreira.nucleo.aovivo.GestorSubstituicoes
import pt.futebolcarreira.nucleo.aovivo.IaPeloUtilizador
import pt.futebolcarreira.nucleo.aovivo.JogadorEmCampo
import pt.futebolcarreira.nucleo.aovivo.JogadorTatico
import pt.futebolcarreira.nucleo.aovivo.Palestra
import pt.futebolcarreira.nucleo.aovivo.PartidaComAcoes
import pt.futebolcarreira.nucleo.aovivo.ResultadoSubstituicao
import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.Mentalidade

/** Partida de teste com a bola, a fase e as táticas à mão. Guarda o que lhe pedem. */
class PartidaTaticaFalsa(val casaUtilizador: Boolean = true) : PartidaComAcoes, ControloTatico, IaPeloUtilizador {
    var fase = FaseJogo.A_JOGAR
    var bx = 0f
    var by = 0f
    var minuto = 10
    var passos = 0
    var passosNoIntervalo = 0
    var ultimoComando: Comando? = null
    var ultimoExtra: ComandoExtra? = null
    override var iaControlaUtilizador = false
    override var terminou = false

    /** Colegas do utilizador (e um GR adversário) em posições à escolha. */
    val jogadores = mutableListOf(
        JogadorEmCampo(1, true, 10, "MC", "Eu", 0f, 0f, 0f, Animacao.PARADO, true),
        JogadorEmCampo(2, true, 7, "ED", "Colega", 20f, 6f, 0f, Animacao.PARADO, false),
        JogadorEmCampo(3, true, 1, "GR", "Guarda", -49f, 0f, 0f, Animacao.PARADO, false),
        JogadorEmCampo(101, false, 1, "GR", "Rival GR", 49f, 1.5f, 3.14f, Animacao.PARADO, false),
    )

    override val estado: EstadoPartida
        get() = EstadoPartida(
            minuto, 0f, 0, 0, fase, false, jogadores, Bola(bx, by, 0f), 0xFFFF0000L to 0xFFFFFFFFL, 0xFF0000FFL to 0xFFFFFFFFL, "AAA", "BBB", null,
        )

    override fun passo(dt: Float, comando: Comando) {
        passos++
        ultimoComando = comando
        if (fase == FaseJogo.INTERVALO) { passosNoIntervalo++; if (passosNoIntervalo >= 3) fase = FaseJogo.A_JOGAR }
    }

    override fun passo(dt: Float, comando: Comando, extra: ComandoExtra) {
        ultimoExtra = extra
        passo(dt, comando)
    }

    // --- táticas
    override val suportado = true
    val onze = mutableListOf(
        JogadorTatico(1, "Eu", "MC", "MC", 10, 90), JogadorTatico(2, "Cansado", "ED", "ED", 7, 20), JogadorTatico(3, "Guarda", "GR", "GR", 1, 100),
    )
    val bancoLista = mutableListOf(JogadorTatico(20, "Suplente", "ED", "", 14, 100, geral = 70), JogadorTatico(21, "Guarda B", "GR", "", 13, 100, geral = 60))
    var feitas = 0
    var formacao = Formacao.F433
    var mentalidade = Mentalidade.EQUILIBRADA
    val palestras = mutableListOf<Palestra>()

    override fun onzeEmCampo(casa: Boolean) = onze.toList()
    override fun banco(casa: Boolean) = bancoLista.toList()
    override fun substituicoesFeitas(casa: Boolean) = feitas
    override fun substituir(casa: Boolean, saiId: Int, entraId: Int): ResultadoSubstituicao {
        val sai = onze.firstOrNull { it.id == saiId }
        val entra = bancoLista.firstOrNull { it.id == entraId }
        val r = GestorSubstituicoes.validar(feitas, sai?.lugar, entra?.posicao)
        if (r != ResultadoSubstituicao.FEITA || sai == null || entra == null) return r
        onze[onze.indexOf(sai)] = entra.copy(lugar = sai.lugar, energia = 100)
        bancoLista.remove(entra)
        feitas++
        return r
    }
    override fun formacaoAtual(casa: Boolean) = formacao
    override fun mudarFormacao(casa: Boolean, formacao: Formacao): Boolean { this.formacao = formacao; return true }
    override fun mentalidadeAtual(casa: Boolean) = mentalidade
    override fun mudarMentalidade(casa: Boolean, mentalidade: Mentalidade): Boolean { this.mentalidade = mentalidade; return true }
    override fun aplicarPalestra(casa: Boolean, palestra: Palestra): Int { palestras += palestra; return palestra.efeitoMoral(0, 0) }
}
