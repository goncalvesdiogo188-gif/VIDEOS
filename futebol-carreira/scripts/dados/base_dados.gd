class_name BaseDados
extends RefCounted
## Carrega e guarda toda a base de dados do jogo a partir de ficheiros JSON.
##
## Ficheiros (dentro da pasta "dados"):
##   paises.json            lista de países
##   competicoes.json       ligas
##   nomes.json             nomes próprios e apelidos por país (para gerar jogadores)
##   equipas/<PAIS>.json    clubes de cada país
##   jogadores/<PAIS>.json  jogadores dos clubes de cada país

var paises := {}        # id -> Dictionary {"id", "nome"}
var competicoes := {}   # id -> Competicao
var equipas := {}       # id -> Equipa
var jogadores := {}     # id (int) -> Jogador
var nomes := {}         # país -> {"nomes": [...], "apelidos": [...]}
var erros: Array[String] = []   # problemas encontrados ao carregar

var _proximo_id := 1


## Lê todos os ficheiros. Devolve true se correu tudo bem.
func carregar(pasta: String = "res://dados") -> bool:
	paises.clear()
	competicoes.clear()
	equipas.clear()
	jogadores.clear()
	erros.clear()
	_proximo_id = 1

	for d in _ler_lista(pasta + "/paises.json"):
		paises[str(d.get("id", ""))] = d
	for d in _ler_lista(pasta + "/competicoes.json"):
		var c := Competicao.de_dicionario(d)
		competicoes[c.id] = c
	var n = ler_json(pasta + "/nomes.json", erros)
	if n is Dictionary:
		nomes = n

	for pais in paises:
		for d in _ler_lista(pasta + "/equipas/%s.json" % pais):
			var e := Equipa.de_dicionario(d)
			if e.pais == "":
				e.pais = pais
			if equipas.has(e.id):
				erros.append("Equipa repetida: %s" % e.id)
				continue
			if not competicoes.has(e.liga):
				erros.append("A equipa %s está numa liga que não existe: '%s'" % [e.id, e.liga])
			equipas[e.id] = e
	for pais in paises:
		for d in _ler_lista(pasta + "/jogadores/%s.json" % pais, false):
			var j := Jogador.de_dicionario(d)
			if j.id <= 0 or jogadores.has(j.id):
				j.id = _proximo_id  # id em falta ou repetido: atribui um novo
			adicionar_jogador(j)
	return erros.is_empty()


## Regista um jogador e coloca-o no plantel da sua equipa.
func adicionar_jogador(j: Jogador) -> void:
	if j.id <= 0:
		j.id = _proximo_id
	_proximo_id = maxi(_proximo_id, j.id + 1)
	jogadores[j.id] = j
	if equipas.has(j.equipa):
		var e: Equipa = equipas[j.equipa]
		e.plantel.append(j)
		e.invalidar_forca()
	elif j.equipa != "":
		erros.append("O jogador %s (%d) está numa equipa que não existe: '%s'" % [j.nome, j.id, j.equipa])
		j.equipa = ""


## Remove um jogador do jogo (por exemplo, quando se reforma).
func remover_jogador(j: Jogador) -> void:
	jogadores.erase(j.id)
	if equipas.has(j.equipa):
		var e: Equipa = equipas[j.equipa]
		e.plantel.erase(j)
		e.invalidar_forca()


## Equipas que jogam numa liga.
func equipas_da_liga(liga_id: String) -> Array[Equipa]:
	var lista: Array[Equipa] = []
	for e: Equipa in equipas.values():
		if e.liga == liga_id:
			lista.append(e)
	return lista


## Ligas de um país, ordenadas por nível (1.ª divisão primeiro).
func ligas_do_pais(pais: String) -> Array[Competicao]:
	var lista: Array[Competicao] = []
	for c: Competicao in competicoes.values():
		if c.pais == pais and c.tipo == "liga":
			lista.append(c)
	lista.sort_custom(func(a: Competicao, b: Competicao) -> bool: return a.nivel < b.nivel)
	return lista


## Grava equipas e jogadores em JSON (usado pelo gerador e, mais tarde, pelo editor).
func guardar(pasta: String) -> void:
	DirAccess.make_dir_recursive_absolute(pasta + "/equipas")
	DirAccess.make_dir_recursive_absolute(pasta + "/jogadores")
	for pais in paises:
		var lista_equipas := []
		var lista_jogadores := []
		for e: Equipa in equipas.values():
			if e.pais != pais:
				continue
			lista_equipas.append(e.para_dicionario())
			for j in e.plantel:
				lista_jogadores.append(j.para_dicionario())
		_escrever(pasta + "/equipas/%s.json" % pais, JSON.stringify(lista_equipas, "\t", false))
		# Um jogador por linha: fica fácil de ler e de editar.
		var linhas := PackedStringArray()
		for d in lista_jogadores:
			linhas.append("\t" + JSON.stringify(d, "", false))
		_escrever(pasta + "/jogadores/%s.json" % pais, "[\n" + ",\n".join(linhas) + "\n]")


func _escrever(caminho: String, texto: String) -> void:
	var f := FileAccess.open(caminho, FileAccess.WRITE)
	if f == null:
		push_error("Não foi possível escrever em " + caminho)
		return
	f.store_string(texto + "\n")
	f.close()


func _ler_lista(caminho: String, obrigatorio: bool = true) -> Array:
	if not obrigatorio and not FileAccess.file_exists(caminho):
		return []
	var dados = ler_json(caminho, erros)
	if dados is Array:
		return dados
	if dados != null:
		erros.append("%s devia conter uma lista [ ... ]" % caminho)
	return []


## Lê um ficheiro JSON. Em caso de erro devolve null e explica o problema em "erros".
static func ler_json(caminho: String, lista_erros: Array[String]) -> Variant:
	if not FileAccess.file_exists(caminho):
		lista_erros.append("Ficheiro em falta: %s" % caminho)
		return null
	var json := JSON.new()
	if json.parse(FileAccess.get_file_as_string(caminho)) != OK:
		lista_erros.append("Erro de JSON em %s, linha %d: %s" % [caminho, json.get_error_line(), json.get_error_message()])
		return null
	return json.data
