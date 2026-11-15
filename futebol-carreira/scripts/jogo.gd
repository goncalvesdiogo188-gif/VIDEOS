extends Node
## Ponto central do jogo ("autoload": existe sempre, em todas as cenas).
## Guarda a base de dados e o gestor de épocas para as cenas usarem.

var bd: BaseDados
var gestor: GestorEpocas


func _ready() -> void:
	novo_jogo()


## Carrega a base de dados de raiz e começa na primeira época.
func novo_jogo() -> void:
	bd = BaseDados.new()
	bd.carregar("res://dados")
	for erro in bd.erros:
		push_warning(erro)
	gestor = GestorEpocas.new(bd)
