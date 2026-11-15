"""Testes do validador de guiões."""
import copy
import json
import unittest
from pathlib import Path

from fabrica import validar as V

RAIZ = Path(__file__).resolve().parent.parent
EXEMPLO = RAIZ / "guioes" / "exemplo_compras_impulso.json"


def exemplo():
    with open(EXEMPLO, encoding="utf-8") as f:
        return json.load(f)


class TestValidar(unittest.TestCase):
    def erros(self, g):
        return V.validar(g)

    def assertErro(self, g, trecho):
        es = self.erros(g)
        self.assertTrue(any(trecho in x for x in es), f"'{trecho}' não encontrado em {es}")

    def test_exemplo_valido(self):
        self.assertEqual(self.erros(exemplo()), [])

    def test_duracao_estimada(self):
        d = V.duracao_estimada(exemplo())
        self.assertTrue(V.DURACAO_MIN <= d <= V.DURACAO_MAX)

    def test_campo_em_falta(self):
        for campo in ("titulo", "falas", "legenda", "hashtags", "carrossel"):
            g = exemplo()
            del g[campo]
            self.assertErro(g, f'"{campo}"')

    def test_titulo_e_legenda(self):
        g = exemplo(); g["titulo"] = "x" * 61
        self.assertErro(g, "61 caracteres")
        g = exemplo(); g["titulo"] = "  "
        self.assertErro(g, "titulo")
        g = exemplo(); g["legenda"] = "Olá. " * 100
        self.assertErro(g, "legenda tem")

    def test_numero_de_falas(self):
        g = exemplo(); g["falas"] = g["falas"][:3]
        self.assertErro(g, "3 falas")
        g = exemplo(); g["falas"] = g["falas"] + g["falas"][:3]
        self.assertErro(g, "9 falas")

    def test_fala_invalida(self):
        g = exemplo(); g["falas"][1]["quem"] = "joao"
        self.assertErro(g, "Fala 2")
        g = exemplo(); g["falas"][2]["texto"] = ""
        self.assertErro(g, "Fala 3")
        g = exemplo(); g["falas"][0]["expressao"] = "zangado"
        self.assertErro(g, "expressão")
        g = exemplo(); g["falas"][0]["pose"] = "dança"
        self.assertErro(g, "pose")

    def test_palavras_max(self):
        g = exemplo(); g["falas"][0]["texto"] = "Uma duas três quatro cinco seis sete oito nove dez onze doze treze."
        self.assertErro(g, "13 palavras")

    def test_ambos_falam_e_ultima_ze(self):
        g = exemplo()
        for f in g["falas"]:
            f["quem"] = "neurao"
        self.assertErro(g, '"ze" não fala')
        g = exemplo(); g["falas"][-1]["quem"] = "neurao"
        self.assertErro(g, "última fala")

    def test_cenario(self):
        g = exemplo(); g["cenario"] = "praia"
        self.assertErro(g, "cenário")
        for ok in ("comando", "memorias", "dopamina", "sono", "sala", "quarto", "loja"):
            g = exemplo(); g["cenario"] = ok
            self.assertEqual(self.erros(g), [], ok)

    def test_duracao(self):
        g = exemplo(); g["falas"] = g["falas"][:5]
        for f in g["falas"]:
            f["texto"] = "Ok."
        g["falas"][-1]["quem"] = "ze"
        g["falas"][0]["quem"] = "neurao"
        for i, f in enumerate(g["falas"]):
            f["texto"] = f"Sim {'a' * i}."
        self.assertErro(g, "mínimo é 15")
        g = exemplo()
        for f in g["falas"]:
            f["texto"] = "Palavra esquisita extraordinariamente interminável, imensamente complicadíssima, sempre."
        self.assertErro(g, "máximo é 30")

    def test_hashtags(self):
        g = exemplo(); g["hashtags"] = g["hashtags"][:4]
        self.assertErro(g, "4 hashtags")
        g = exemplo(); g["hashtags"][0] = "#Cérebro"
        self.assertErro(g, "inválida")
        g = exemplo(); g["hashtags"][1] = "psicologia"
        self.assertErro(g, "inválida")
        g = exemplo(); g["hashtags"][1] = "#CEREBRO"
        self.assertTrue(self.erros(g))
        g = exemplo(); g["hashtags"][1] = "#cerebro"
        self.assertErro(g, "repetida")

    def test_carrossel(self):
        g = exemplo(); g["carrossel"] = g["carrossel"][:2]
        self.assertErro(g, "2 slides")
        g = exemplo(); g["carrossel"][0]["texto"] = ""
        self.assertErro(g, "Slide 1")
        g = exemplo(); g["carrossel"][2]["texto"] = "a" * 121 + "."
        self.assertErro(g, "Slide 3")
        g = exemplo(); g["carrossel"][1] = "texto"
        self.assertErro(g, "Slide 2")

    def test_proibidos(self):
        casos = [
            ("Vais ficar rico com isto.", "ficar rico"),
            ("O Bitcoin sobe sempre.", "Bitcoin".lower()),
            ("Isto é um diagnóstico.", "diagnóstico"),
            ("Que ecrã tão giro.", "giro"),
            ("A gente compra tudo.", "a gente"),
            ("Larga o TELEMOVEL agora.", "telemóvel"),
        ]
        for texto, termo in casos:
            g = exemplo(); g["falas"][2]["texto"] = texto
            self.assertErro(g, termo)
        g = exemplo(); g["legenda"] = "Dinheiro fácil para todos."
        self.assertErro(g, "dinheiro fácil")
        g = exemplo(); g["carrossel"][0]["titulo"] = "Investe em ouro"
        self.assertErro(g, "investe em")
        g = exemplo(); g["titulo"] = "Truque legal"
        self.assertErro(g, "legal")

    def test_palavra_inteira(self):
        # "carrinho" e "legalmente" não são "cara"/"legal"
        g = exemplo(); g["falas"][2]["texto"] = "O carrinho paga o sono dele."
        self.assertEqual(self.erros(g), [])

    def test_higiene(self):
        g = exemplo(); g["falas"][0]["texto"] = "Sabes  porque compraste?"
        self.assertErro(g, "espaços duplos")
        g = exemplo(); g["falas"][0]["texto"] = "Sabes porque compraste ?"
        self.assertErro(g, "antes de pontuação")
        g = exemplo(); g["falas"][0]["texto"] = "Sabes o o que compraste?"
        self.assertErro(g, "repetida")
        g = exemplo(); g["falas"][0]["texto"] = "Sabes porque compraste"
        self.assertErro(g, "pontuação final")
        g = exemplo(); g["falas"][0]["texto"] = "ISTO É MUITO IMPORTANTE SABER!"
        self.assertErro(g, "MAIÚSCULAS")
        g = exemplo(); g["falas"][0]["texto"] = "Sabes (porque compraste?"
        self.assertErro(g, "parênteses")
        g = exemplo(); g["falas"][0]["texto"] = 'Sabes "porque compraste?'
        self.assertErro(g, "aspas")
        g = exemplo(); g["falas"][1]["texto"] = g["falas"][0]["texto"]
        self.assertErro(g, "repete o texto da fala 1")

    def test_final_com_aspas_ok(self):
        g = exemplo(); g["falas"][0]["texto"] = 'Diz "olá!"'
        self.assertEqual(self.erros(g), [])

    def test_nunca_rebenta(self):
        base = exemplo()
        maus = [None, {}, [], "texto", 5, {"falas": None}, {"falas": "x"}, {"falas": [None, 3, {}]},
                {"titulo": 5, "falas": [], "legenda": [], "hashtags": "x", "carrossel": {}},
                {"falas": [{"quem": "ze"}], "cenario": 7}]
        g = copy.deepcopy(base); g["falas"] = [{"quem": "ze"}] * 5
        maus.append(g)
        g = copy.deepcopy(base); g["hashtags"] = [1, None, [], {}, "#a"]
        maus.append(g)
        g = copy.deepcopy(base); g["carrossel"] = [None, 1, {"titulo": 3, "texto": None}]
        maus.append(g)
        g = copy.deepcopy(base); g["falas"][0]["texto"] = 42
        maus.append(g)
        for m in maus:
            es = V.validar(m)
            self.assertIsInstance(es, list)
            self.assertTrue(es, f"devia haver erros para {m!r}")
            V.normalizar(m)

    def test_normalizar(self):
        g = {"titulo": "  Olá ", "falas": [{"quem": "ze", "texto": " Oi. "}]}
        n = V.normalizar(g)
        self.assertEqual(n["titulo"], "Olá")
        self.assertEqual(n["falas"][0]["texto"], "Oi.")
        self.assertEqual(n["falas"][0]["expressao"], "neutro")
        self.assertEqual(n["falas"][0]["pose"], "normal")
        self.assertIn("cenario", n)
        self.assertNotIn("expressao", g["falas"][0])  # não altera o original
        # não corrige valores inválidos
        g = {"falas": [{"quem": "ze", "texto": "x", "expressao": "zangado"}]}
        self.assertEqual(V.normalizar(g)["falas"][0]["expressao"], "zangado")


class TestCLI(unittest.TestCase):
    def test_cli(self):
        self.assertEqual(V.main([str(EXEMPLO)]), 0)
        self.assertEqual(V.main(["não_existe.json"]), 1)


if __name__ == "__main__":
    unittest.main()
