"""Testes das poses novas, objetos, ações e entrada dos personagens."""
import json
import unittest
from pathlib import Path

from fabrica import video, validar
from fabrica.personagens import ADERECOS, POSES, desenha

RAIZ = Path(__file__).resolve().parent.parent


def plano(*falas):
    return [dict(quem=q, inicio=a, fim=b, expressao="neutro", pose="normal", **extra) for q, a, b, extra in falas]


class TestMovimento(unittest.TestCase):
    def test_entrada(self):
        p = plano(("neurao", 1.0, 2.0, {}))
        self.assertAlmostEqual(video.movimento(p, "neurao", 0.0)[0], -380)
        self.assertAlmostEqual(video.movimento(p, "ze", 0.0)[0], 380)
        self.assertEqual(video.movimento(p, "neurao", 0.8), (0.0, 0.0, None))

    def test_aproxima_e_volta(self):
        p = plano(("neurao", 1.0, 2.0, {"acao": "aproxima"}), ("neurao", 3.0, 4.0, {}))
        self.assertAlmostEqual(video.movimento(p, "neurao", 1.8)[0], video.APROXIMA)
        self.assertIsNotNone(video.movimento(p, "neurao", 1.2)[2])  # a andar
        self.assertAlmostEqual(video.movimento(p, "neurao", 3.8)[0], 0.0)

    def test_salta_e_treme(self):
        p = plano(("ze", 1.0, 2.0, {"acao": "salta"}), ("neurao", 1.0, 2.0, {"acao": "treme"}))
        self.assertLess(video.movimento(p, "ze", 1.25)[1], -100)
        self.assertNotEqual(video.movimento(p, "neurao", 1.31)[0], 0.0)

    def test_adereco_aparece_e_desaparece(self):
        p = plano(("ze", 1.0, 2.0, {"adereco": "telefone"}))
        self.assertEqual(video.adereco_em(p, "ze", 1.5), ("telefone", 1.0))
        self.assertEqual(video.adereco_em(p, "ze", 3.0)[0], None)
        self.assertEqual(video.adereco_em(p, "neurao", 1.5)[0], None)


class TestDesenho(unittest.TestCase):
    def test_todas_as_poses_e_objetos(self):
        for po in POSES:
            self.assertIn("<g", desenha("neurao", 0, 0, 1, "neutro", po, t=1.0, andar=0.5))
        for ad in ADERECOS:
            self.assertIn("<g", desenha("ze", 0, 0, 1, "alegre", "segura", adereco=ad))


class TestValidar(unittest.TestCase):
    def setUp(self):
        self.g = json.loads((RAIZ / "guioes" / "exemplo_compras_impulso.json").read_text(encoding="utf-8"))

    def test_campos_novos_validos(self):
        self.g["falas"][0].update(adereco="carrinho", acao="aproxima", pose="segura")
        self.assertEqual(validar.validar(self.g), [])

    def test_campos_novos_invalidos(self):
        self.g["falas"][0].update(adereco="foguetao", acao="voa")
        erros = " ".join(validar.validar(self.g))
        self.assertIn("adereco", erros)
        self.assertIn("acao", erros)


if __name__ == "__main__":
    unittest.main()
