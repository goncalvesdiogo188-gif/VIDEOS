class_name Competicao
extends RefCounted
## Uma competição. Na Fase 1 só existem ligas (todos contra todos).

var id: String = ""
var nome: String = ""
var pais: String = ""
var nivel: int = 1            # 1 = primeira divisão, 2 = segunda...
var tipo: String = "liga"
var voltas: int = 2           # 2 = casa e fora
var pontos_vitoria: int = 3
var pontos_empate: int = 1
var descidas: int = 0         # quantas equipas descem (e sobem da liga abaixo)
var desce_para: String = ""   # id da liga para onde descem ("" = ninguém desce)


static func de_dicionario(d: Dictionary) -> Competicao:
	var c := Competicao.new()
	c.id = str(d.get("id", ""))
	c.nome = str(d.get("nome", c.id))
	c.pais = str(d.get("pais", ""))
	c.nivel = int(d.get("nivel", 1))
	c.tipo = str(d.get("tipo", "liga"))
	c.voltas = maxi(1, int(d.get("voltas", 2)))
	c.pontos_vitoria = int(d.get("pontos_vitoria", 3))
	c.pontos_empate = int(d.get("pontos_empate", 1))
	c.descidas = maxi(0, int(d.get("descidas", 0)))
	c.desce_para = str(d.get("desce_para", ""))
	return c
