"""Linha de comandos da FÁBRICA.

Exemplos:
  fabrica novo "compras por impulso"
  fabrica novo --guiao guioes/exemplo_compras_impulso.json
  fabrica semana --n 7
  fabrica verificar
"""
import argparse
import sys


def _parser():
    p = argparse.ArgumentParser(prog="fabrica", description="FÁBRICA: cria Reels prontos a publicar.")
    sub = p.add_subparsers(dest="comando")

    novo = sub.add_parser("novo", help="cria um pacote (vídeo, capa, carrossel, legenda)")
    novo.add_argument("tema", nargs="?", help="o tema do vídeo, entre aspas")
    novo.add_argument("--guiao", help="usa um guião que já existe (ficheiro .json)")

    sem = sub.add_parser("semana", help="cria vários pacotes de uma vez")
    sem.add_argument("--n", type=int, default=7, help="quantos vídeos (por defeito 7)")

    sub.add_parser("verificar", help="verifica se o computador tem tudo o que é preciso")

    # FASE 4: aqui será acrescentado o comando "diario" (publicação/registo diário).
    return p


def main(argv=None):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    p = _parser()
    a = p.parse_args(argv)

    if a.comando == "verificar":
        from . import verificar
        return verificar.main()

    if a.comando not in ("novo", "semana"):
        p.print_help()
        return 0

    from . import guionista, pacote
    try:
        if a.comando == "novo":
            if not a.tema and not a.guiao:
                print("Diz-me o tema, por exemplo:  fabrica novo \"compras por impulso\"")
                return 1
            pasta = pacote.criar_pacote(a.tema, guiao_ficheiro=a.guiao)
            print(f"\nPasta: {pasta}")
            print("Pronto para publicar!")
        else:
            pastas = pacote.semana(a.n)
            if not pastas:
                print("\nNenhum pacote foi criado.")
                return 1
            print("\nPronto para publicar!")
    except (pacote.PacoteErro, guionista.GuionistaErro) as e:
        print(f"\nNão foi possível terminar: {e}")
        return 1
    except KeyboardInterrupt:
        print("\nInterrompido.")
        return 1
    return 0


if __name__ == "__main__":  # necessário no Windows (o vídeo usa vários processos)
    sys.exit(main())
