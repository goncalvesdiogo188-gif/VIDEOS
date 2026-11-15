class_name GeradorJogadores
extends RefCounted
## Cria jogadores fictícios (para a base de dados inicial e para os jovens
## que aparecem todas as épocas).

## Plantel-tipo de 24 jogadores: posição -> quantidade.
const PLANTEL_TIPO := {"GR": 3, "DD": 2, "DC": 4, "DE": 2, "MDC": 2, "MC": 3, "MOC": 2, "ED": 2, "EE": 2, "PL": 2}
## Quantos de cada posição são titulares (os restantes são suplentes).
const TITULARES := {"GR": 1, "DD": 1, "DC": 2, "DE": 1, "MDC": 1, "MC": 2, "MOC": 1, "ED": 1, "EE": 1, "PL": 1}

var nomes := {}   # país -> {"nomes": [...], "apelidos": [...]}
var rng: RandomNumberGenerator
var _nomes_usados := {}


func _init(lista_nomes: Dictionary, gerador_aleatorio: RandomNumberGenerator) -> void:
	nomes = lista_nomes
	rng = gerador_aleatorio


## Nível médio (geral) esperado para um titular de uma equipa com esta reputação.
static func nivel_da_reputacao(reputacao: int) -> float:
	return 30.0 + 0.58 * float(reputacao)


## Cria o plantel completo de uma equipa.
func criar_plantel(equipa: Equipa) -> Array[Jogador]:
	var plantel: Array[Jogador] = []
	var nivel := nivel_da_reputacao(equipa.reputacao)
	for posicao in PLANTEL_TIPO:
		for i in PLANTEL_TIPO[posicao]:
			var j: Jogador
			if i < TITULARES[posicao]:
				j = criar(equipa.pais, posicao, nivel + rng.randfn(1.0, 2.5), rng.randi_range(22, 32))
			elif rng.randf() < 0.5:
				j = criar(equipa.pais, posicao, nivel - 8.0 + rng.randfn(0.0, 3.0), rng.randi_range(17, 21))
			else:
				j = criar(equipa.pais, posicao, nivel - 5.0 + rng.randfn(0.0, 3.0), rng.randi_range(22, 34))
			j.equipa = equipa.id
			plantel.append(j)
	return plantel


## Cria um jovem para substituir um jogador que se reformou.
func criar_jovem(equipa: Equipa, posicao: String) -> Jogador:
	var nivel := nivel_da_reputacao(equipa.reputacao) - 11.0 + rng.randfn(0.0, 4.0)
	var j := criar(equipa.pais, posicao, nivel, rng.randi_range(17, 19))
	j.equipa = equipa.id
	return j


## Cria um jogador com um certo nível (geral) e idade.
func criar(pais_clube: String, posicao: String, nivel: float, idade: int) -> Jogador:
	var j := Jogador.new()
	j.posicao = posicao
	j.idade = idade
	# A maioria é do país do clube; alguns são estrangeiros.
	j.nacionalidade = pais_clube
	if rng.randf() < 0.22 and nomes.size() > 1:
		var outros := nomes.keys()
		j.nacionalidade = outros[rng.randi() % outros.size()]
	j.nome = _criar_nome(j.nacionalidade)
	j.pe = _escolher_pe(posicao)

	var alvo := clampi(roundi(nivel), 25, 95)
	var pesos: Dictionary = Jogador.PESOS[posicao]
	for atributo in Jogador.ATRIBUTOS:
		var valor: float
		if pesos.has(atributo):
			valor = alvo + rng.randfn(0.0, 4.0)
		elif atributo == "guarda_redes":
			valor = rng.randi_range(5, 15)
		else:
			valor = alvo - 22.0 + rng.randfn(0.0, 6.0)
		j.atributos[atributo] = clampi(roundi(valor), 1, 99)
	# Acerta os atributos principais para o geral bater com o nível pedido.
	for tentativa in 3:
		var diferenca := alvo - j.geral()
		if diferenca == 0:
			break
		j.ajustar_atributos(diferenca)

	var margem := 0
	if idade <= 21:
		margem = rng.randi_range(4, 16)
	elif idade <= 25:
		margem = rng.randi_range(1, 7)
	elif idade <= 28:
		margem = rng.randi_range(0, 2)
	j.potencial = mini(97, j.geral() + margem)
	return j


func _criar_nome(pais: String) -> String:
	var listas: Dictionary = nomes.get(pais, {})
	var proprios: Array = listas.get("nomes", ["Jogador"])
	var apelidos: Array = listas.get("apelidos", ["Anónimo"])
	var nome := ""
	# Tenta evitar nomes repetidos.
	for tentativa in 12:
		nome = "%s %s" % [proprios[rng.randi() % proprios.size()], apelidos[rng.randi() % apelidos.size()]]
		if not _nomes_usados.has(nome):
			break
	_nomes_usados[nome] = true
	return nome


func _escolher_pe(posicao: String) -> String:
	var sorte := rng.randf()
	if posicao == "DE" or posicao == "EE":
		return "esquerdo" if sorte < 0.7 else ("direito" if sorte < 0.95 else "ambos")
	return "direito" if sorte < 0.75 else ("esquerdo" if sorte < 0.95 else "ambos")
