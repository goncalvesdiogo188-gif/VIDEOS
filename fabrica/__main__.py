"""Linha de comandos da FÁBRICA.

Exemplos:
  fabrica novo "compras por impulso"
  fabrica novo --guiao guioes/exemplo_compras_impulso.json
  fabrica guiao "compras por impulso"      (só o guião em texto, sem vídeo)
  fabrica semana --n 7
  fabrica refazer out/2026-11-15_procrastinacao
  fabrica diario
  fabrica agendar --hora 09:00
  fabrica desagendar
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

    gu = sub.add_parser("guiao", help="escreve só o guião (texto, sem vídeo) e grava-o em guioes/")
    gu.add_argument("tema", help="o tema do vídeo, entre aspas")

    sem = sub.add_parser("semana", help="cria vários pacotes de uma vez")
    sem.add_argument("--n", type=int, default=7, help="quantos vídeos (por defeito 7)")

    ref = sub.add_parser("refazer", help="volta a gerar um pacote a partir do seu guiao.json")
    ref.add_argument("pastas", nargs="+", help="pasta(s) do pacote, ex.: out/2026-11-15_procrastinacao")

    ver = sub.add_parser("verificar", help="verifica se o computador tem tudo o que é preciso")
    ver.add_argument("--rapido", action="store_true", help="não testa o login do Claude")

    dia = sub.add_parser("diario", help="cria o pacote do dia (pensado para correr agendado)")
    dia.add_argument("--forcar", action="store_true", help="cria outro mesmo que o de hoje já exista")

    ag = sub.add_parser("agendar", help="Windows: corre 'fabrica diario' todos os dias sozinho")
    ag.add_argument("--hora", default="09:00", help="a que horas (por defeito 09:00)")

    sub.add_parser("desagendar", help="Windows: deixa de correr sozinho")
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
        return verificar.main(rapido=a.rapido)

    if a.comando in ("agendar", "desagendar"):
        from . import diario
        try:
            ok = diario.agendar(a.hora) if a.comando == "agendar" else diario.desagendar()
        except ValueError as e:
            print(e)
            return 1
        return 0 if ok else 1

    if a.comando not in ("novo", "guiao", "semana", "refazer", "diario"):
        p.print_help()
        return 0

    from . import guionista, pacote, voz
    try:
        if a.comando == "novo":
            if not a.tema and not a.guiao:
                print("Diz-me o tema, por exemplo:  fabrica novo \"compras por impulso\"")
                return 1
            pasta = pacote.criar_pacote(a.tema, guiao_ficheiro=a.guiao)
            print(f"\nPasta: {pasta}")
            print("Pronto para publicar!")
        elif a.comando == "guiao":
            f = pacote.escrever_so_guiao(a.tema)
            print(f"\nGostaste? Para fazer o vídeo:  fabrica novo --guiao {f}")
        elif a.comando == "diario":
            from . import diario
            diario.diario(forcar=a.forcar)
        elif a.comando == "refazer":
            for pasta in a.pastas:
                pacote.refazer_pacote(pasta)
            print("\nPronto para publicar!")
        else:
            pastas = pacote.semana(a.n)
            if not pastas:
                print("\nNenhum pacote foi criado.")
                return 1
            print("\nPronto para publicar!")
    except (pacote.PacoteErro, guionista.GuionistaErro, voz.VozErro) as e:
        print(f"\nNão foi possível terminar: {e}")
        return 1
    except KeyboardInterrupt:
        print("\nInterrompido.")
        return 1
    return 0


if __name__ == "__main__":  # necessário no Windows (o vídeo usa vários processos)
    sys.exit(main())
