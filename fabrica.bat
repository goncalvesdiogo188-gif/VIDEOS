@echo off
rem FABRICA - atalho para o Windows: fabrica novo "tema", fabrica diario, ...
chcp 65001 >nul
cd /d "%~dp0"
set "PY="
py -3 --version >nul 2>nul && set "PY=py -3"
if not defined PY python --version >nul 2>nul && set "PY=python"
if not defined PY goto sem_python
%PY% -m fabrica %*
exit /b %errorlevel%

:sem_python
echo Nao encontrei o Python. Corre primeiro o instalar.bat (clica duas vezes nele).
exit /b 1
