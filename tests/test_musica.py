"""Testes da música de fundo."""
import os
import unittest

from fabrica import musica


class TestMusica(unittest.TestCase):
    def test_duracao_e_limites(self):
        for z in list(musica.ESTILOS) + ["sala", "zona_que_nao_existe"]:
            m = musica.gerar(z, 3.0)
            self.assertEqual(len(m), 3 * musica.TAXA)
            self.assertLessEqual(max(abs(v) for v in m), 1.0 + 1e-9)

    def test_acaba_em_silencio(self):
        m = musica.gerar("comando", 5.0)
        self.assertLess(abs(m[-1]), 0.01)

    def test_baixa_quando_alguem_fala(self):
        plano = [{"inicio": 1.0, "fim": 2.0}]
        g = musica.ganho_ducking(plano, 4.0)
        self.assertAlmostEqual(g[int(1.5 * musica.TAXA)], musica.DUCK)
        self.assertEqual(g[int(3.5 * musica.TAXA)], 1.0)

    def test_desligar(self):
        self.assertTrue(musica.ativa({}))
        self.assertFalse(musica.ativa({"musica": False}))
        os.environ["FABRICA_SEM_MUSICA"] = "1"
        try:
            self.assertFalse(musica.ativa({}))
        finally:
            del os.environ["FABRICA_SEM_MUSICA"]


if __name__ == "__main__":
    unittest.main()
