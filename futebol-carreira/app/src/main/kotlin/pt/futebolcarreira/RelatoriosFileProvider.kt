package pt.futebolcarreira

import androidx.core.content.FileProvider

/**
 * Partilha dos relatórios. Existe só para ter um nome próprio: a biblioteca do Godot declara
 * outro FileProvider com o nome da classe base e os dois chocavam ao juntar os manifestos.
 */
class RelatoriosFileProvider : FileProvider()
