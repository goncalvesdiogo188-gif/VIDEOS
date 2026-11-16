package pt.futebolcarreira.aovivo

import pt.futebolcarreira.nucleo.aovivo.Comando
import pt.futebolcarreira.nucleo.aovivo.PartidaAoVivo

/** Como se jogam os jogos da carreira (opção em Mais → Opções). */
enum class ModoJogos(val nome: String) {
    SIMULAR("Simular"), VER_3D("Ver em 3D"), JOGAR_3D("Jogar em 3D");

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
) {
    @Volatile
    var pausado = false

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
        val comando = if (controlar) controlo.consumir(sentido()) else SEM_COMANDO
        partida.passo(dt.coerceIn(0f, MAX_PASSO), comando)
    }

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
