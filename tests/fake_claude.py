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
if "FALHA" in p:
    sys.stderr.write("erro de teste\n")
    sys.exit(1)
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
