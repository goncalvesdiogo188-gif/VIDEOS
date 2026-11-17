"""Cada zona do cérebro tem cores, desenho, música, está no guionista e passa no validador."""
import json
import unittest
from pathlib import Path

from fabrica import cenarios, guionista, musica, validar
from fabrica.render import svg_para_png

RAIZ = Path(__file__).resolve().parent.parent


class TestZonas(unittest.TestCase):
    def test_zonas_completas(self):
        g = json.loads((RAIZ / "guioes" / "exemplo_compras_impulso.json").read_text(encoding="utf-8"))
        for z in cenarios.ZONAS:
            with self.subTest(zona=z):
                self.assertIn(z, cenarios.TEMAS)
                self.assertIn(z, musica.ESTILOS)
                self.assertIn(z, guionista.CENARIOS)
                g["cenario"] = z
                self.assertEqual(validar.validar(g), [])

    def test_desenha_sem_erros(self):
        for z in cenarios.ZONAS:
            for t in (0.0, 3.7):
                svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="270" height="480" viewBox="0 0 1080 1920">{cenarios.cenario(z, t=t)}</svg>'
                self.assertTrue(svg_para_png(svg).startswith(b"\x89PNG"))


if __name__ == "__main__":
    unittest.main()
