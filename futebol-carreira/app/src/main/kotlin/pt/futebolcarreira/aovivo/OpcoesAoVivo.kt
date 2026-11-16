package pt.futebolcarreira.aovivo

import android.content.Context
import android.content.SharedPreferences
import pt.futebolcarreira.nucleo.aovivo.DuracaoJogo
import pt.futebolcarreira.nucleo.aovivo.NivelMira

/** As opções do jogo ao vivo, guardadas nas SharedPreferences "opcoes". */
object OpcoesAoVivo {
    const val FICHEIRO = "opcoes"
    const val CHAVE_DURACAO = "duracao_jogo_min"
    const val CHAVE_MIRA = "ajuda_mira"          // "AUTO" (segue a dificuldade) ou um NivelMira
    const val CHAVE_BOTOES = "botoes_disposicao"

    const val MIRA_AUTO = "AUTO"

    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences(FICHEIRO, Context.MODE_PRIVATE)

    fun duracaoMinutos(p: SharedPreferences): Int = DuracaoJogo.valida(p.getInt(CHAVE_DURACAO, DuracaoJogo.PADRAO_MINUTOS))

    fun definirDuracao(p: SharedPreferences, minutos: Int) = p.edit().putInt(CHAVE_DURACAO, DuracaoJogo.valida(minutos)).apply()

    /** O nível pedido nas opções, ou null se for "AUTO". */
    fun miraEscolhida(p: SharedPreferences): NivelMira? = NivelMira.deNome(p.getString(CHAVE_MIRA, MIRA_AUTO))

    fun definirMira(p: SharedPreferences, nivel: NivelMira?) = p.edit().putString(CHAVE_MIRA, nivel?.name ?: MIRA_AUTO).apply()

    /** O nível a usar: o escolhido, ou o da dificuldade (texto como "NORMAL"; null = Normal). */
    fun nivelMira(p: SharedPreferences, dificuldade: String? = null): NivelMira = miraEscolhida(p) ?: NivelMira.daDificuldade(dificuldade)

    const val CHAVE_PS1 = "graficos_ps1"

    /** Gráficos ao estilo PlayStation 1 (ligados por defeito). */
    fun estiloPs1(p: SharedPreferences): Boolean = p.getBoolean(CHAVE_PS1, true)

    fun definirEstiloPs1(p: SharedPreferences, ligado: Boolean) = p.edit().putBoolean(CHAVE_PS1, ligado).apply()

    fun disposicao(p: SharedPreferences): DisposicaoBotoes = DisposicaoBotoes.ler(p.getString(CHAVE_BOTOES, null))

    fun guardarDisposicao(p: SharedPreferences, d: DisposicaoBotoes) = p.edit().putString(CHAVE_BOTOES, d.serializar()).apply()

    fun repor(p: SharedPreferences) = p.edit().remove(CHAVE_BOTOES).apply()
}
