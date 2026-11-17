@echo off
rem FABRICA - instalador para o Windows. Clica duas vezes neste ficheiro.
rem Pode ser corrido varias vezes: so instala o que faltar.
chcp 65001 >nul
cd /d "%~dp0"
title Instalar a FABRICA
set "REABRIR="
echo.
echo ==================== Instalar a FABRICA ====================
echo.

rem ---------------------------------------------------------- 1. Python
echo [1/5] Python...
set "PY="
py -3 --version >nul 2>nul && set "PY=py -3"
if not defined PY python --version >nul 2>nul && set "PY=python"
if defined PY goto python_versao
echo    Python nao encontrado. A instalar o Python 3.12 com o winget...
winget install -e --id Python.Python.3.12 --scope user --accept-source-agreements --accept-package-agreements
if errorlevel 1 goto sem_winget
echo.
echo    Python instalado. FECHA esta janela e clica outra vez no instalar.bat.
pause
exit /b 0

:python_versao
%PY% -c "import sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)"
if not errorlevel 1 goto python_ok
echo    O teu Python e antigo: a fabrica precisa do 3.10 ou mais recente.
echo    A instalar o Python 3.12 com o winget...
winget install -e --id Python.Python.3.12 --scope user --accept-source-agreements --accept-package-agreements
echo    FECHA esta janela e clica outra vez no instalar.bat.
pause
exit /b 0

:python_ok
echo    OK

rem ---------------------------------------------------------- 2. ffmpeg
echo [2/5] ffmpeg (monta os videos)...
where ffmpeg >nul 2>nul
if not errorlevel 1 goto ffmpeg_ok
echo    A instalar o ffmpeg com o winget...
winget install -e --id Gyan.FFmpeg --accept-source-agreements --accept-package-agreements
if errorlevel 1 goto sem_winget
set "REABRIR=1"
goto bibliotecas
:ffmpeg_ok
echo    OK

rem ---------------------------------------------------------- 3. bibliotecas
:bibliotecas
echo [3/5] Bibliotecas do Python (desenho e vozes). Pode demorar alguns minutos...
%PY% -m pip install --upgrade pip --quiet
%PY% -m pip install -r requirements.txt
if errorlevel 1 goto erro_pip
echo    OK

rem ---------------------------------------------------------- 4. Claude Code
echo [4/5] Claude Code (escreve os guioes)...
where claude >nul 2>nul
if not errorlevel 1 goto claude_ok
if exist "%USERPROFILE%\.local\bin\claude.exe" goto claude_path
echo    A instalar o Claude Code (instalador oficial)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "irm https://claude.ai/install.ps1 | iex"
call :juntar_path
set "REABRIR=1"
echo.
echo    IMPORTANTE (so uma vez): abre uma janela nova do PowerShell, escreve  claude
echo    e faz login com a tua conta Claude no navegador. Depois escreve /exit
goto fim_instalacao
:claude_path
echo    O Claude Code esta instalado mas fora do PATH. A corrigir...
call :juntar_path
set "REABRIR=1"
goto fim_instalacao
:claude_ok
echo    OK

:fim_instalacao
if not defined REABRIR goto verificar
echo.
echo Foram instaladas coisas novas. FECHA esta janela e clica outra vez no instalar.bat
echo para confirmar que esta tudo bem.
pause
exit /b 0

rem ---------------------------------------------------------- 5. verificar
:verificar
echo [5/5] Verificacao final...
echo.
%PY% -m fabrica verificar
if errorlevel 1 goto falta_algo
echo.
set "AG="
set /p AG=Queres que a fabrica crie um video sozinha todos os dias? Escreve S ou N e Enter: 
if /i not "%AG%"=="S" goto fim
set "HORA=09:00"
set /p HORA=A que horas? Por exemplo 09:00 e Enter: 
call "%~dp0fabrica.bat" agendar --hora %HORA%
goto fim

:falta_algo
echo.
echo Ainda falta alguma coisa (ver [FALTA] acima). Resolve e corre o instalar.bat outra vez.
goto fim

:sem_winget
echo.
echo Nao consegui usar o winget (instalador do Windows).
echo Instala a mao: Python em https://www.python.org/downloads/ (marca "Add Python to PATH")
echo e o ffmpeg com as instrucoes do README. Depois corre o instalar.bat outra vez.
goto fim

:erro_pip
echo.
echo Erro a instalar as bibliotecas do Python. Confirma que tens internet e tenta outra vez.
goto fim

:juntar_path
rem junta %USERPROFILE%\.local\bin ao PATH do utilizador (so se ainda nao estiver)
powershell -NoProfile -ExecutionPolicy Bypass -Command "$p=[Environment]::GetEnvironmentVariable('Path','User'); if (-not $p) { $p = '' }; $d=Join-Path $env:USERPROFILE '.local\bin'; if (-not (($p -split ';') -contains $d)) { [Environment]::SetEnvironmentVariable('Path', ($p.TrimEnd(';') + ';' + $d), 'User'); Write-Host '   Pasta do Claude juntada ao PATH.' }"
exit /b 0

:fim
echo.
echo Para criar um video: abre o PowerShell nesta pasta e escreve:  .\fabrica novo "o teu tema"
pause
