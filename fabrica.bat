@echo off
cd /d "%~dp0"
where python >nul 2>nul
if %errorlevel%==0 (
    python -m fabrica %*
) else (
    py -m fabrica %*
)
exit /b %errorlevel%
