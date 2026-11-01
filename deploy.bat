@echo off
rem ============================================================
rem  Local deploy entry point.
rem  All logic lives in deploy-local.ps1 (reads deploy.config.json).
rem  Usage:
rem    deploy.bat           full deploy (pull + build frontend/backend + start)
rem    deploy.bat web       frontend only
rem    deploy.bat boot      backend only
rem    deploy.bat start     start services only (no build)
rem ============================================================
setlocal
set "ROOT=%~dp0"
set "PS1=%ROOT%deploy-local.ps1"
set "MODE=%~1"
set "ARGS="
if /i "%MODE%"=="web"   set "ARGS=-SkipBoot"
if /i "%MODE%"=="boot"  set "ARGS=-SkipWeb"
if /i "%MODE%"=="start" set "ARGS=-StartOnly"

powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%" %ARGS%
set "CODE=%ERRORLEVEL%"
if not "%NO_PAUSE%"=="1" pause
exit /b %CODE%
