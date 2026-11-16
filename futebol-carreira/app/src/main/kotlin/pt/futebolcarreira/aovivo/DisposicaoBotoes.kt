package pt.futebolcarreira.aovivo

import java.util.Locale

/** Os botões do jogo 3D. "tamanho" é o diâmetro base em dp; "rotulo" o texto no botão. */
enum class BotaoId(val rotulo: String, val nome: String, val tamanho: Int, val descricao: String) {
    REMATE("REMATE", "Remate", 94, "Mantém premido para dar força."),
    PASSE("PASSE", "Passe", 82, "Toque curto: passe curto; mantido: passe longo. Arrasta para dar a direção (seta)."),
    SPRINT("SPRINT", "Sprint", 70, "Corre mais depressa enquanto estiver premido."),
    TROCAR("TROCAR", "Trocar jogador", 62, "A defender: passas a comandar o jogador mais perto da bola."),
    PROFUNDIDADE("PROF.", "Passe em profundidade", 58, "Passe nas costas da defesa, para a frente."),
    CRUZAMENTO("CRUZAR", "Cruzamento", 58, "Bola alta para a área."),
    COLOCADO("COLOC.", "Remate colocado", 54, "Remate ao canto, longe do guarda-redes."),
    POR_CIMA("ALTO", "Remate por cima", 54, "Remate forte e alto, de longe."),
    FINTA("FINTA", "Finta", 54, "Toque de lado para fintar o adversário."),
    PROTEGER("PROTEGER", "Proteger a bola", 58, "Mantém premido para resguardar a bola com o corpo.");

    companion object {
        fun deNome(t: String?) = entries.firstOrNull { it.name == t }
    }
}

/** Onde está um botão: "x" e "y" são a posição do centro, de 0 a 1, em fração do ecrã (de cima para baixo). */
data class ConfigBotao(val id: BotaoId, val x: Float, val y: Float, val escala: Float = 1f, val ativo: Boolean = true) {
    val diametroDp: Float get() = id.tamanho * escala
}

/** A disposição de todos os botões. Guarda-se como texto nas SharedPreferences "opcoes". */
class DisposicaoBotoes(botoes: List<ConfigBotao> = padrao()) {
    /** Sempre um por cada BotaoId, pela ordem do enum. */
    val botoes: List<ConfigBotao> = BotaoId.entries.map { id -> botoes.firstOrNull { it.id == id }?.limpo() ?: padraoDe(id) }

    operator fun get(id: BotaoId): ConfigBotao = botoes.first { it.id == id }

    fun com(novo: ConfigBotao) = DisposicaoBotoes(botoes.map { if (it.id == novo.id) novo else it })

    fun ativos() = botoes.filter { it.ativo }

    fun serializar(): String = botoes.joinToString(";") {
        String.format(Locale.ROOT, "%s:%.3f:%.3f:%.2f:%d", it.id.name, it.x, it.y, it.escala, if (it.ativo) 1 else 0)
    }

    override fun equals(other: Any?) = other is DisposicaoBotoes && other.botoes == botoes
    override fun hashCode() = botoes.hashCode()

    companion object {
        const val ESCALA_MIN = 0.6f
        const val ESCALA_MAX = 1.5f

        private fun ConfigBotao.limpo() = copy(x = x.coerceIn(0.04f, 0.96f), y = y.coerceIn(0.08f, 0.94f), escala = escala.coerceIn(ESCALA_MIN, ESCALA_MAX))

        fun padraoDe(id: BotaoId): ConfigBotao = when (id) {
            BotaoId.REMATE -> ConfigBotao(id, 0.915f, 0.78f)
            BotaoId.PASSE -> ConfigBotao(id, 0.790f, 0.80f)
            BotaoId.PROFUNDIDADE -> ConfigBotao(id, 0.675f, 0.83f)
            BotaoId.SPRINT -> ConfigBotao(id, 0.935f, 0.50f)
            BotaoId.TROCAR -> ConfigBotao(id, 0.825f, 0.55f)
            BotaoId.CRUZAMENTO -> ConfigBotao(id, 0.715f, 0.60f)
            BotaoId.COLOCADO -> ConfigBotao(id, 0.945f, 0.28f)
            BotaoId.POR_CIMA -> ConfigBotao(id, 0.855f, 0.28f)
            BotaoId.FINTA -> ConfigBotao(id, 0.765f, 0.28f)
            BotaoId.PROTEGER -> ConfigBotao(id, 0.665f, 0.36f)
        }

        fun padrao(): List<ConfigBotao> = BotaoId.entries.map(::padraoDe)

        /** Lê o texto guardado; o que faltar ou estiver estragado fica no padrão. */
        fun ler(texto: String?): DisposicaoBotoes {
            if (texto.isNullOrBlank()) return DisposicaoBotoes()
            val lidos = texto.split(';').mapNotNull { parte ->
                val p = parte.split(':')
                if (p.size < 5) return@mapNotNull null
                val id = BotaoId.deNome(p[0]) ?: return@mapNotNull null
                val x = p[1].toFloatOrNull() ?: return@mapNotNull null
                val y = p[2].toFloatOrNull() ?: return@mapNotNull null
                val e = p[3].toFloatOrNull() ?: return@mapNotNull null
                if (!x.isFinite() || !y.isFinite() || !e.isFinite()) return@mapNotNull null
                ConfigBotao(id, x, y, e, p[4] != "0")
            }
            return DisposicaoBotoes(lidos)
        }
    }
}
