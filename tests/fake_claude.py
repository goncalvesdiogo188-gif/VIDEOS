"""Falso Claude para testes: lê o stdin e responde com texto pronto."""
import json
import sys

p = sys.stdin.read()
GUIAO = {
    "titulo": "Porque adias tudo?",
    "cenario": "comando",
    "falas": [
        {"quem": "neurao", "texto": "Porque adias o que tens de fazer?", "expressao": "convencido", "pose": "aponta"},
        {"quem": "ze", "texto": "Eu? Só estou a pensar muito.", "expressao": "confuso", "pose": "encolhe"},
        {"quem": "neurao", "texto": "O cérebro foge do esforço e prefere recompensas rápidas.", "expressao": "convencido", "pose": "normal"},
        {"quem": "ze", "texto": "Começa por dois minutos. O resto vem atrás!", "expressao": "alegre", "pose": "aponta"},
    ],
    "carrossel": [{"titulo": "A", "texto": "b"}] * 3,
    "legenda": "Adias tudo? E tu, o que adias? Segue para mais!",
    "hashtags": ["#cerebro", "#psicologia", "#procrastinacao", "#habitos", "#vidaadulta"],
}
FICHA = {"dono": "Marta", "situacao": "A Marta tem um relatório.", "reviravolta": "o alarme toca", "piada_ze": "fotos por cor",
         "licao": "uma frase feia", "callback": "ideia minha", "zona": "comando",
         "cenas": [{"hora": "18:00", "icone": "portatil", "texto": "Relatório aberto.", "acontece": "a"},
                   {"hora": "19:00", "icone": "frigorifico", "texto": "Frigorífico limpo.", "acontece": "b"},
                   {"hora": "23:50", "icone": "despertador", "texto": "Pânico.", "acontece": "c"}]}


def roteiro(versao):
    n = json.loads(json.dumps(GUIAO))
    n["tema"] = "procrastinação"
    n["dono"] = "Marta"
    n["falas"][0]["cena"] = {"icone": "portatil", "hora": "18:00", "texto": "Relatório aberto."}
    n["titulo"] = "Porque adias tudo? v" + str(versao)
    return n


if "FALHA" in p:
    sys.stderr.write("erro de teste\n")
    sys.exit(1)
elif "Analisa este vídeo JÁ FEITO" in p:
    print(json.dumps({"nota": 7, "pontos_fortes": ["gancho direto"], "fraquezas": ["lição vaga"],
                      "regras": ["Evita lições vagas.", "Faz a primeira fala com um objeto concreto."], "resumo": "Bom, lição vaga."}, ensure_ascii=False))
elif "lições aprendidas pela fábrica" in p:
    print(json.dumps(["Evita lições vagas.", "Faz o gancho com um objeto concreto."], ensure_ascii=False))
elif "Escolhe, da lista, o conceito" in p:
    print(json.dumps({"id": "adiar_e_emocao"}))
elif "Cria a FICHA DA HISTÓRIA" in p:
    print("```json\n" + json.dumps(FICHA, ensure_ascii=False) + "\n```")
elif "Avalia este guião de Reel" in p:
    ruim = "v1" in p and "CRITICA_BOA" not in p
    notas = {k: (3 if ruim else 5) for k in ("coerencia", "historia", "humor", "ciencia", "licao", "ritmo", "imagem")}
    print(json.dumps({"notas": notas, "problemas": ["Fala 2: liga melhor à anterior."] if ruim else []}))
elif "O editor avaliou o teu guião" in p:
    print(json.dumps(roteiro(2), ensure_ascii=False))
elif "FICHA DA HISTÓRIA (segue-a" in p:
    print("```json\n" + json.dumps(roteiro(1), ensure_ascii=False) + "\n```")
elif "revisão de texto" in p:
    g = json.loads(p[p.index("GUIÃO:") + 6:p.index("Responde APENAS")])
    if "SEMMUDANCAS" in p:
        print(json.dumps({"guiao": g, "alteracoes": []}))
    else:
        g["titulo"] = "Porque adias tudo?"
        print("Aqui está:\n```json\n" + json.dumps({"guiao": g, "alteracoes": ["adiaz -> adias"]}, ensure_ascii=False) + "\n```")
elif "Sugere" in p:
    print('Claro!\n["Adiar tarefas", "adiar tarefas", "Sono e telefone", "Dinheiro no fim do mês"]')
elif "Corrige-o" in p:
    print("```json\n" + json.dumps(GUIAO, ensure_ascii=False) + "\n```")
else:
    print("Com certeza! Aqui vai:\n\n```json\n" + json.dumps(GUIAO, ensure_ascii=False) + "\n```\n\nEspero que ajude.")
