#!/usr/bin/env bash
# Atalho (Linux/Mac):  ./fabrica.sh novo "tema"
# (não pode chamar-se "fabrica" porque já existe a pasta fabrica/)
cd "$(dirname "$0")" || exit 1
exec python3 -m fabrica "$@"
