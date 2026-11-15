class_name Equipa
extends RefCounted
## Um clube: dados gerais e plantel.

## Tática base usada para escolher o melhor onze (4-3-3).
const FORMACAO: Array[String] = ["GR", "DD", "DC", "DC", "DE", "MDC", "MC", "MC", "ED", "EE", "PL"]

## Posições parecidas: um jogador pode tapar um buraco com pequena penalização.
const POSICOES_PARECIDAS := {
	"DD": ["DE", "DC"], "DE": ["DD", "DC"], "DC": ["MDC", "DD", "DE"],
	"MDC": ["MC", "DC"], "MC": ["MOC", "MDC"], "MOC": ["MC", "ED", "EE"],
	"ED": ["EE", "MOC"], "EE": ["ED", "MOC"], "PL": ["ED", "EE", "MOC"], "GR": [],
}

## Quanto cada lugar do onze conta para o ataque e para a defesa.
const PESO_ATAQUE := {"GR": 0.0, "DD": 0.5, "DE": 0.5, "DC": 0.0, "MDC": 0.5, "MC": 1.5, "ED": 2.0, "EE": 2.0, "PL": 3.0}
const PESO_DEFESA := {"GR": 3.0, "DD": 1.5, "DE": 1.5, "DC": 2.0, "MDC": 1.5, "MC": 0.5, "ED": 0.0, "EE": 0.0, "PL": 0.0}

var id: String = ""
var nome: String = ""
var abreviatura: String = ""
var pais: String = ""
var liga: String = ""       # id da liga onde joga esta época
var cidade: String = ""
var estadio: String = ""
var capacidade: int = 5000
var reputacao: int = 50     # 1 a 99
var orcamento: int = 1000000
var cores: Array = ["#ffffff", "#000000"]
var plantel: Array[Jogador] = []
var titulos: int = 0        # campeonatos ganhos durante o jogo

var _forca_cache := {}


## Deve ser chamado sempre que o plantel ou os atributos mudam.
func invalidar_forca() -> void:
	_forca_cache = {}


## Escolhe os 11 melhores jogadores para a formação.
## Devolve uma lista de {"lugar": "PL", "jogador": Jogador, "nota": 78}.
func melhor_onze() -> Array:
	var usados := {}
	var onze := []
	for lugar in FORMACAO:
		var melhor: Jogador = null
		var melhor_nota := -1
		for j in plantel:
			if usados.has(j.id):
				continue
			var nota := _nota_no_lugar(j, lugar)
			if nota > melhor_nota:
				melhor_nota = nota
				melhor = j
		if melhor != null:
			usados[melhor.id] = true
			onze.append({"lugar": lugar, "jogador": melhor, "nota": melhor_nota})
		else:
			# Plantel com menos de 11 jogadores: o lugar fica muito fraco.
			onze.append({"lugar": lugar, "jogador": null, "nota": 20})
	return onze


## Nota de um jogador num certo lugar (penaliza quem joga fora de posição).
func _nota_no_lugar(j: Jogador, lugar: String) -> int:
	if j.posicao == lugar:
		return j.geral()
	if lugar == "GR" or j.posicao == "GR":
		return maxi(1, j.geral() - 40)
	if POSICOES_PARECIDAS.get(lugar, []).has(j.posicao):
		return maxi(1, j.geral() - 5)
	return maxi(1, j.geral() - 12)


## Força da equipa: {"ataque": x, "defesa": y, "geral": z} (escala 1 a 99).
func forca() -> Dictionary:
	if not _forca_cache.is_empty():
		return _forca_cache
	var soma_atq := 0.0
	var peso_atq := 0.0
	var soma_def := 0.0
	var peso_def := 0.0
	var soma := 0.0
	for entrada in melhor_onze():
		var nota := float(entrada["nota"])
		var pa: float = PESO_ATAQUE[entrada["lugar"]]
		var pd: float = PESO_DEFESA[entrada["lugar"]]
		soma_atq += nota * pa
		peso_atq += pa
		soma_def += nota * pd
		peso_def += pd
		soma += nota
	_forca_cache = {
		"ataque": soma_atq / peso_atq,
		"defesa": soma_def / peso_def,
		"geral": soma / float(FORMACAO.size()),
	}
	return _forca_cache


static func de_dicionario(d: Dictionary) -> Equipa:
	var e := Equipa.new()
	e.id = str(d.get("id", ""))
	e.nome = str(d.get("nome", e.id))
	e.abreviatura = str(d.get("abreviatura", e.nome.substr(0, 3).to_upper()))
	e.pais = str(d.get("pais", ""))
	e.liga = str(d.get("liga", ""))
	e.cidade = str(d.get("cidade", ""))
	e.estadio = str(d.get("estadio", ""))
	e.capacidade = int(d.get("capacidade", 5000))
	e.reputacao = clampi(int(d.get("reputacao", 50)), 1, 99)
	e.orcamento = int(d.get("orcamento", 1000000))
	e.cores = d.get("cores", ["#ffffff", "#000000"])
	return e


func para_dicionario() -> Dictionary:
	return {
		"id": id,
		"nome": nome,
		"abreviatura": abreviatura,
		"pais": pais,
		"liga": liga,
		"cidade": cidade,
		"estadio": estadio,
		"capacidade": capacidade,
		"reputacao": reputacao,
		"orcamento": orcamento,
		"cores": cores,
	}
