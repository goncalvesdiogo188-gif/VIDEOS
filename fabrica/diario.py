"""Pacote do dia e agendamento automático.

  fabrica diario                  cria o pacote de hoje (só um por dia)
  fabrica diario --forcar         cria outro mesmo que hoje já exista
  fabrica agendar --hora 09:00    Windows: cria a tarefa no Agendador de Tarefas
  fabrica desagendar              Windows: apaga essa tarefa

Tudo o que acontece fica escrito em out/logs/diario_AAAA-MM-DD.log, porque quando
corre agendado ninguém está a ver o ecrã.
"""
import datetime
import json
import platform
import re
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
NOME_TAREFA = "FABRICA diario"
TENTATIVAS_TEMA = 2


def _registo(base):
    return Path(base) / "diario.json"


def ler_registo(base) -> dict:
    try:
        return json.loads(_registo(base).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def _gravar_registo(base, reg):
    _registo(base).write_text(json.dumps(reg, ensure_ascii=False, indent=2), encoding="utf-8")


def _logger(base, hoje):
    pasta = Path(base) / "logs"
    pasta.mkdir(parents=True, exist_ok=True)
    ficheiro = pasta / f"diario_{hoje.isoformat()}.log"

    def log(msg=""):
        print(msg, flush=True)
        with open(ficheiro, "a", encoding="utf-8") as f:
            f.write(f"[{datetime.datetime.now():%H:%M:%S}] {msg}\n")
    return log, ficheiro


def diario(base=Path("out"), forcar=False, hoje=None, log=None):
    """Cria o pacote do dia. Devolve a pasta, ou None se hoje já estava feito."""
    from . import guionista, pacote

    base = Path(base)
    base.mkdir(parents=True, exist_ok=True)
    hoje = hoje or datetime.date.today()
    if log is None:
        log, ficheiro = _logger(base, hoje)
        log(f"===== FÁBRICA diário {hoje:%d/%m/%Y} (registo: {ficheiro}) =====")

    reg = ler_registo(base)
    feito = reg.get(hoje.isoformat())
    if feito and not forcar:
        log(f"O pacote de hoje já existe: {feito}. Nada a fazer.")
        return None

    evitar = pacote.historico_para_evitar(base)
    ultimo_erro = None
    for k in range(1, TENTATIVAS_TEMA + 1):
        try:
            tema = guionista.escolher_temas(1, evitar=evitar)[0]
        except guionista.GuionistaErro as e:
            ultimo_erro = e
            log(f"Não consegui escolher um tema: {e}")
            break
        log(f"Tema de hoje (tentativa {k}/{TENTATIVAS_TEMA}): {tema}")
        try:
            pasta = pacote.criar_pacote(tema, base=base, data=hoje, log=log)
        except Exception as e:  # tenta outro tema
            ultimo_erro = e
            evitar.append(tema)
            log(f"Falhou com este tema: {e}")
            continue
        reg[hoje.isoformat()] = str(pasta)
        _gravar_registo(base, reg)
        log(f"Pronto para publicar: {pasta}")
        return pasta
    raise pacote.PacoteErro(f"Não foi possível criar o pacote de hoje. Último erro: {ultimo_erro}")


# ------------------------------------------------------------------ agendamento
def _validar_hora(hora):
    m = re.fullmatch(r"(\d{1,2}):(\d{2})", hora or "")
    if not m or int(m.group(1)) > 23 or int(m.group(2)) > 59:
        raise ValueError(f"Hora inválida: '{hora}'. Usa o formato 09:00.")
    return f"{int(m.group(1)):02d}:{m.group(2)}"


def _ps(texto):
    """Texto entre plicas para PowerShell."""
    return "'" + str(texto).replace("'", "''") + "'"


def comando_powershell_agendar(hora, raiz=RAIZ, python=None):
    """A tarefa chama o Python diretamente (não o .bat), porque o Controlo Inteligente
    de Aplicações do Windows 11 pode bloquear ficheiros .bat vindos da internet."""
    hora = _validar_hora(hora)
    python = python or sys.executable
    return "; ".join([
        f"$a = New-ScheduledTaskAction -Execute {_ps(python)} -Argument '-m fabrica diario' -WorkingDirectory {_ps(raiz)}",
        f"$t = New-ScheduledTaskTrigger -Daily -At {_ps(hora)}",
        # StartWhenAvailable: se o computador estava desligado à hora marcada, corre quando ligares
        "$s = New-ScheduledTaskSettingsSet -StartWhenAvailable -WakeToRun -AllowStartIfOnBatteries "
        "-DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Hours 2)",
        f"Register-ScheduledTask -TaskName {_ps(NOME_TAREFA)} -Action $a -Trigger $t -Settings $s "
        "-Description 'Cria o Reel do dia (FABRICA)' -Force | Out-Null",
    ])


def comando_powershell_desagendar():
    return f"Unregister-ScheduledTask -TaskName {_ps(NOME_TAREFA)} -Confirm:$false"


def linha_cron(hora, raiz=RAIZ):
    h, m = _validar_hora(hora).split(":")
    return f"{int(m)} {int(h)} * * * cd {raiz} && ./fabrica.sh diario >/dev/null 2>&1"


def _powershell(cmd):
    return subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", cmd],
                          capture_output=True, text=True)


def agendar(hora="09:00", log=print):
    hora = _validar_hora(hora)
    if platform.system() != "Windows":
        log("Isto não é Windows. Para agendar, corre 'crontab -e' e acrescenta esta linha:")
        log("  " + linha_cron(hora))
        return False
    r = _powershell(comando_powershell_agendar(hora))
    if r.returncode != 0:
        log("Não consegui criar a tarefa no Agendador de Tarefas:")
        log(r.stderr.strip() or r.stdout.strip())
        return False
    log(f"Feito! A tarefa '{NOME_TAREFA}' vai correr todos os dias às {hora}.")
    log("Podes vê-la em: Iniciar > Agendador de Tarefas > Biblioteca do Agendador de Tarefas.")
    return True


def desagendar(log=print):
    if platform.system() != "Windows":
        log("Isto não é Windows. Corre 'crontab -e' e apaga a linha que tem 'fabrica.sh diario'.")
        return False
    r = _powershell(comando_powershell_desagendar())
    if r.returncode != 0:
        log("Não consegui apagar a tarefa (talvez não exista): " + (r.stderr.strip() or r.stdout.strip()))
        return False
    log(f"A tarefa '{NOME_TAREFA}' foi apagada. A fábrica deixa de correr sozinha.")
    return True
