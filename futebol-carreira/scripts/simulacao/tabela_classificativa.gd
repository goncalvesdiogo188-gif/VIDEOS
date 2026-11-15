class_name TabelaClassificativa
extends RefCounted
## Tabela classificativa de uma liga.

var pontos_vitoria := 3
var pontos_empate := 1
var linhas := {}   # id da equipa -> {"id","j","v","e","d","gm","gs","pts"}


func _init(ids_equipas: Array, p_vitoria: int = 3, p_empate: int = 1) -> void:
	pontos_vitoria = p_vitoria
	pontos_empate = p_empate
	for id in ids_equipas:
		linhas[id] = {"id": id, "j": 0, "v": 0, "e": 0, "d": 0, "gm": 0, "gs": 0, "pts": 0}


## Regista o resultado de um jogo.
func registar(casa: String, fora: String, golos_casa: int, golos_fora: int) -> void:
	_somar(linhas[casa], golos_casa, golos_fora)
	_somar(linhas[fora], golos_fora, golos_casa)


func _somar(linha: Dictionary, marcados: int, sofridos: int) -> void:
	linha["j"] += 1
	linha["gm"] += marcados
	linha["gs"] += sofridos
	if marcados > sofridos:
		linha["v"] += 1
		linha["pts"] += pontos_vitoria
	elif marcados == sofridos:
		linha["e"] += 1
		linha["pts"] += pontos_empate
	else:
		linha["d"] += 1


## Linhas ordenadas: pontos, diferença de golos, golos marcados e, por fim, o id.
## (O desempate por confronto direto fica para uma fase posterior.)
func ordenada() -> Array:
	var lista := linhas.values()
	lista.sort_custom(func(a: Dictionary, b: Dictionary) -> bool:
		if a["pts"] != b["pts"]:
			return a["pts"] > b["pts"]
		var dg_a: int = a["gm"] - a["gs"]
		var dg_b: int = b["gm"] - b["gs"]
		if dg_a != dg_b:
			return dg_a > dg_b
		if a["gm"] != b["gm"]:
			return a["gm"] > b["gm"]
		return str(a["id"]) < str(b["id"]))
	return lista
