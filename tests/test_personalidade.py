"""Testes do ritmo da voz, das reações, da vida dos personagens e dos efeitos sonoros."""
import unittest

from fabrica import sfx, video, voz
from fabrica.personagens import EXPRESSOES, desenha


def plano(*falas):
    return [dict(dict(quem=q, inicio=a, fim=b, expressao=ex, pose="normal", texto=tx,
                      palavras=[(w, a + i * 0.3, a + i * 0.3 + 0.25) for i, w in enumerate(tx.split())]), **extra)
            for q, a, b, ex, tx, extra in falas]


class TestVoz(unittest.TestCase):
    def test_prosodia_cobre_todas_as_expressoes(self):
        for e in EXPRESSOES:
            self.assertIn(e, voz.PROSODIA)

    def test_ritmo_calmo(self):
        self.assertLessEqual(voz.VOZES["ze"]["velocidade"], 0)
        self.assertLess(voz.VOZES["neurao"]["velocidade"], 0)
        self.assertGreaterEqual(voz.PAUSA, 0.35)


class TestReacao(unittest.TestCase):
    def f(self, texto, ex):
        return dict(texto=texto, expressao=ex)

    def test_reacoes(self):
        self.assertEqual(video.reacao(self.f("Que susto!", "triste"), "ze"), "surpreendido")
        self.assertEqual(video.reacao(self.f("Sabes porquê?", "neutro"), "ze"), "confuso")
        self.assertEqual(video.reacao(self.f("Sabes porquê?", "neutro"), "neurao"), "pensativo")
        self.assertEqual(video.reacao(self.f("Eu sei tudo.", "convencido"), "ze"), "confuso")
        self.assertEqual(video.reacao(self.f("Viva!", "alegre"), "ze"), "alegre")


class TestVida(unittest.TestCase):
    def test_quem_fala_mexe_mais(self):
        p = plano(("ze", 1.0, 3.0, "alegre", "Olá amigo grande", {}))
        f = p[0]
        fala = video.vida(p, "ze", 1.2, f, True, 0.8)
        ouve = video.vida(p, "neurao", 1.2, f, False, 0.0)
        self.assertEqual(fala["gesto"], 1.0)
        self.assertGreater(fala["sobr"], 5)
        self.assertEqual(ouve["gesto"], 0.0)
        self.assertEqual(ouve["sobr"], 0.0)

    def test_ze_nunca_para(self):
        p = plano(("neurao", 1.0, 3.0, "neutro", "Bom dia", {}))
        self.assertNotEqual(video.vida(p, "ze", 0.4, None, False, 0.0)["salto"], 0.0)

    def test_desenha_com_vida(self):
        svg = desenha("neurao", 0, 0, 1, "alegre", "normal", inclinar=5, esmagar=0.06, sobr=10, gesto=1)
        self.assertIn("rotate(5.00)", svg)


class TestSfx(unittest.TestCase):
    def test_eventos(self):
        p = plano(("ze", 1.0, 2.0, "surpreendido", "a", dict(acao="salta", adereco="lampada")),
                  ("neurao", 3.0, 4.0, "neutro", "b", dict(acao="aproxima", pose="mao_testa")))
        nomes = [n for _, n in sfx.eventos(p)]
        for esperado in ("boing", "ding", "uh", "whoosh", "bonk"):
            self.assertIn(esperado, nomes)

    def test_misturar(self):
        mix = [0.0] * (3 * sfx.TAXA)
        sfx.misturar_em(mix, plano(("ze", 0.5, 1.5, "neutro", "a", dict(acao="salta"))))
        self.assertGreater(max(abs(v) for v in mix), 0.1)
        self.assertLessEqual(max(abs(v) for v in mix), 1.0)


if __name__ == "__main__":
    unittest.main()
