class_name Jogador
extends RefCounted
## Um jogador de futebol: identidade, posição e atributos.
## Este ficheiro só guarda DADOS e pequenas contas sobre o próprio jogador.

## Posições possíveis (siglas portuguesas).
## GR guarda-redes, DD/DE defesas laterais, DC defesa central, MDC médio defensivo,
## MC médio centro, MOC médio ofensivo, ED/EE extremos, PL ponta de lança.
const POSICOES: Array[String] = ["GR", "DD", "DC", "DE", "MDC", "MC", "MOC", "ED", "EE", "PL"]

## Atributos de cada jogador (valores de 1 a 99).
const ATRIBUTOS: Array[String] = ["ritmo", "remate", "passe", "drible", "defesa", "fisico", "guarda_redes"]

## Peso de cada atributo no valor "geral", conforme a posição (cada linha soma 1).
const PESOS := {
	"GR": {"guarda_redes": 0.80, "fisico": 0.12, "passe": 0.08},
	"DC": {"defesa": 0.55, "fisico": 0.25, "ritmo": 0.10, "passe": 0.10},
	"DD": {"defesa": 0.35, "ritmo": 0.25, "passe": 0.15, "fisico": 0.15, "drible": 0.10},
	"DE": {"defesa": 0.35, "ritmo": 0.25, "passe": 0.15, "fisico": 0.15, "drible": 0.10},
	"MDC": {"defesa": 0.35, "passe": 0.30, "fisico": 0.20, "drible": 0.10, "ritmo": 0.05},
	"MC": {"passe": 0.40, "drible": 0.20, "defesa": 0.15, "fisico": 0.10, "remate": 0.10, "ritmo": 0.05},
	"MOC": {"passe": 0.30, "drible": 0.30, "remate": 0.20, "ritmo": 0.15, "fisico": 0.05},
	"ED": {"ritmo": 0.30, "drible": 0.30, "remate": 0.20, "passe": 0.15, "fisico": 0.05},
	"EE": {"ritmo": 0.30, "drible": 0.30, "remate": 0.20, "passe": 0.15, "fisico": 0.05},
	"PL": {"remate": 0.45, "ritmo": 0.20, "drible": 0.15, "fisico": 0.15, "passe": 0.05},
}

var id: int = 0
var nome: String = ""
var nacionalidade: String = ""
var idade: int = 20
var posicao: String = "MC"
var pe: String = "direito"  # "direito", "esquerdo" ou "ambos"
var equipa: String = ""     # id da equipa ("" = sem clube)
var potencial: int = 50     # geral máximo que pode atingir
var atributos := {}         # nome do atributo -> valor (1 a 99)


## Valor global do jogador na sua posição (1 a 99).
func geral() -> int:
	var pesos: Dictionary = PESOS.get(posicao, PESOS["MC"])
	var soma := 0.0
	for atributo in pesos:
		soma += float(atributos.get(atributo, 1)) * float(pesos[atributo])
	return clampi(roundi(soma), 1, 99)


## Soma "delta" aos atributos que contam para a posição (usado na evolução).
func ajustar_atributos(delta: int) -> void:
	var pesos: Dictionary = PESOS.get(posicao, PESOS["MC"])
	for atributo in pesos:
		atributos[atributo] = clampi(int(atributos.get(atributo, 1)) + delta, 1, 99)


## Valor de mercado estimado, em euros (provisório até à Fase 5).
func valor_mercado() -> int:
	var base := pow(1.18, geral() - 50) * 250000.0
	var fator_idade := 1.0
	if idade <= 23:
		fator_idade = 1.0 + float(potencial - geral()) * 0.04
	elif idade >= 30:
		fator_idade = maxf(0.15, 1.0 - float(idade - 29) * 0.15)
	return int(round(base * fator_idade / 10000.0)) * 10000


## Constrói um jogador a partir de um dicionário lido do JSON.
static func de_dicionario(d: Dictionary) -> Jogador:
	var j := Jogador.new()
	j.id = int(d.get("id", 0))
	j.nome = str(d.get("nome", "Sem Nome"))
	j.nacionalidade = str(d.get("nacionalidade", ""))
	j.idade = int(d.get("idade", 20))
	j.posicao = str(d.get("posicao", "MC"))
	if not POSICOES.has(j.posicao):
		j.posicao = "MC"
	j.pe = str(d.get("pe", "direito"))
	j.equipa = str(d.get("equipa", ""))
	var origem: Dictionary = d.get("atributos", {})
	for atributo in ATRIBUTOS:
		j.atributos[atributo] = clampi(int(origem.get(atributo, 30)), 1, 99)
	j.potencial = clampi(int(d.get("potencial", j.geral())), 1, 99)
	return j


## Converte o jogador num dicionário pronto a gravar em JSON.
func para_dicionario() -> Dictionary:
	return {
		"id": id,
		"nome": nome,
		"nacionalidade": nacionalidade,
		"idade": idade,
		"posicao": posicao,
		"pe": pe,
		"equipa": equipa,
		"potencial": potencial,
		"atributos": atributos.duplicate(),
	}
