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

    const val CHAVE_ESTILO = "estilo_graficos"

    /** Estilo gráfico do jogo 3D (PlayStation 2 por defeito). */
    fun estilo(p: SharedPreferences): EstiloGraficos = EstiloGraficos.deNome(p.getString(CHAVE_ESTILO, null))

    fun definirEstilo(p: SharedPreferences, e: EstiloGraficos) = p.edit().putString(CHAVE_ESTILO, e.name).apply()

    fun disposicao(p: SharedPreferences): DisposicaoBotoes = DisposicaoBotoes.ler(p.getString(CHAVE_BOTOES, null))

    fun guardarDisposicao(p: SharedPreferences, d: DisposicaoBotoes) = p.edit().putString(CHAVE_BOTOES, d.serializar()).apply()

    fun repor(p: SharedPreferences) = p.edit().remove(CHAVE_BOTOES).apply()
}

/** Aspeto do jogo 3D: «Móvel vivo» (cores fortes, figuras «chibi», resolução total com suavização de arestas), PS2 (imagem de 480 linhas, suave, com texturas), PS1 (240 linhas, pixelizado) ou Normal (resolução total). */
enum class EstiloGraficos(val nome: String) {
    DLS("Móvel vivo"), PS2("PlayStation 2"), PS1("PlayStation 1"), NORMAL("Normal");

    companion object {
        fun deNome(t: String?): EstiloGraficos = entries.firstOrNull { it.name == t } ?: DLS
    }
}
