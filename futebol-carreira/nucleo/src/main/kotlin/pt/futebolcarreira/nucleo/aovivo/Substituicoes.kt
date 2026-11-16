package pt.futebolcarreira.nucleo.aovivo

import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.Mentalidade

// Táticas ao vivo (fase E4): substituições (máx. 5), formação, mentalidade e palestra ao intervalo.
// O contrato é "ControloTatico", com implementação por omissão que não faz nada ("NENHUM"). A ligação ao
// MotorAoVivo atual está em TaticaMotor.kt; quando o motor implementar a interface, passa a valer a dele.

/** Um jogador, como o painel de táticas o vê. "energia" 0..100 (cansaço); "lugar" = onde joga agora ("" no banco). */
data class JogadorTatico(
    val id: Int, val nome: String, val posicao: String, val lugar: String, val numero: Int,
    val energia: Int = 100, val lesionado: Boolean = false, val amarelos: Int = 0, val geral: Int = 0,
) {
    val cansado: Boolean get() = energia < GestorSubstituicoes.LIMITE_CANSADO
    val muitoCansado: Boolean get() = energia < GestorSubstituicoes.LIMITE_EXAUSTO
}

enum class ResultadoSubstituicao(val texto: String) {
    FEITA("Substituição feita."),
    SEM_SUBSTITUICOES("Já usaste as ${GestorSubstituicoes.MAXIMO} substituições."),
    JOGADOR_INVALIDO("Esse jogador não pode ser trocado."),
    GR_INCOMPATIVEL("Um guarda-redes só pode ser trocado por outro guarda-redes."),
    NAO_SUPORTADO("As substituições não estão disponíveis neste jogo."),
}

/** Palestra ao intervalo: uma escolha simples, com efeito pequeno na moral dos jogadores em campo. */
enum class Palestra(val nome: String, val descricao: String) {
    MOTIVAR("Motivar", "«Ainda dá para virar isto!» Ajuda mais quando se está a perder."),
    ELOGIAR("Elogiar", "«Estão a fazer um grande jogo.» Ajuda mais quando se está a ganhar."),
    ACALMAR("Acalmar", "«Calma e cabeça fria.» Um pouco de confiança em qualquer resultado."),
    PRESSIONAR("Pressionar", "«Isto não chega!» Arrisca: ajuda se se perde, magoa se se ganha."),
    NADA("Não dizer nada", "Deixar a equipa a pensar no jogo.");

    /** Variação da moral (pontos de 0 a 100) de cada jogador em campo. Sempre pequena: de -2 a +4. */
    fun efeitoMoral(golosPro: Int, golosContra: Int): Int {
        val dif = golosPro - golosContra
        return when (this) {
            MOTIVAR -> if (dif < 0) 3 else 2
            ELOGIAR -> if (dif > 0) 3 else if (dif == 0) 2 else 1
            ACALMAR -> if (dif >= 0) 2 else 1
            PRESSIONAR -> if (dif < 0) 4 else if (dif == 0) 1 else -2
            NADA -> 0
        }
    }

    companion object {
        fun deNome(texto: String?) = entries.firstOrNull { it.name == texto }
    }
}

/**
 * Contrato das táticas ao vivo. "casa" = a equipa da casa. Tudo tem implementação por omissão que diz
 * "não suportado", para uma partida (ou um motor falso) poder ignorar o painel.
 */
interface ControloTatico {
    /** false = o painel mostra "indisponível". */
    val suportado: Boolean get() = false

    fun onzeEmCampo(casa: Boolean): List<JogadorTatico> = emptyList()
    fun banco(casa: Boolean): List<JogadorTatico> = emptyList()

    /** Substituições já feitas (incluindo as forçadas por lesão). */
    fun substituicoesFeitas(casa: Boolean): Int = 0
    fun substituicoesRestantes(casa: Boolean): Int = (GestorSubstituicoes.MAXIMO - substituicoesFeitas(casa)).coerceAtLeast(0)

    fun substituir(casa: Boolean, saiId: Int, entraId: Int): ResultadoSubstituicao = ResultadoSubstituicao.NAO_SUPORTADO

    fun formacaoAtual(casa: Boolean): Formacao? = null
    fun mudarFormacao(casa: Boolean, formacao: Formacao): Boolean = false

    fun mentalidadeAtual(casa: Boolean): Mentalidade? = null
    fun mudarMentalidade(casa: Boolean, mentalidade: Mentalidade): Boolean = false

    /** Aplica a palestra à moral da equipa e devolve a variação (0 se não suportado). */
    fun aplicarPalestra(casa: Boolean, palestra: Palestra): Int = 0

    companion object {
        /** Não faz nada. */
        val NENHUM: ControloTatico = object : ControloTatico {}
    }
}

/** O controlo tático de uma partida: o dela própria se o implementa, senão o do motor real, senão NENHUM. */
fun PartidaAoVivo.controloTatico(): ControloTatico = when (this) {
    is ControloTatico -> this
    is MotorAoVivo -> TaticaDoMotor(this)
    else -> ControloTatico.NENHUM
}

/** Regras das substituições e sugestões por cansaço, sem depender do motor (testável). */
object GestorSubstituicoes {
    const val MAXIMO = 5
    const val LIMITE_CANSADO = 55
    const val LIMITE_EXAUSTO = 30

    /** Valida uma substituição. "saiLesionado" não conta no limite de cansaço, só no resto. */
    fun validar(feitas: Int, saiPosicaoLugar: String?, entraPosicao: String?, entraDisponivel: Boolean = true): ResultadoSubstituicao = when {
        saiPosicaoLugar == null || entraPosicao == null || !entraDisponivel -> ResultadoSubstituicao.JOGADOR_INVALIDO
        feitas >= MAXIMO -> ResultadoSubstituicao.SEM_SUBSTITUICOES
        (saiPosicaoLugar == "GR") != (entraPosicao == "GR") -> ResultadoSubstituicao.GR_INCOMPATIVEL
        else -> ResultadoSubstituicao.FEITA
    }

    /** Quem merece sair: lesionados primeiro, depois os mais cansados (abaixo do limite), pelo menos energia primeiro. */
    fun candidatosASair(onze: List<JogadorTatico>): List<JogadorTatico> =
        onze.filter { it.lesionado || it.cansado }.sortedWith(compareByDescending<JogadorTatico> { it.lesionado }.thenBy { it.energia })

    /** O melhor do banco para um lugar: o que joga ali (ou o maior "geral" ), sem lesionados; guarda-redes só por guarda-redes. */
    fun melhorEntrada(saiLugar: String, banco: List<JogadorTatico>): JogadorTatico? {
        val aptos = banco.filter { !it.lesionado && ((saiLugar == "GR") == (it.posicao == "GR")) }
        return aptos.sortedWith(compareByDescending<JogadorTatico> { it.posicao == saiLugar }.thenByDescending { it.geral }).firstOrNull()
    }

    /** Resumo de uma linha para o painel (ex.: "Substituições: 2/5"). */
    fun resumo(feitas: Int) = "Substituições: ${feitas.coerceAtMost(MAXIMO)}/$MAXIMO"
}
