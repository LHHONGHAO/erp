@echo off
chcp 65001 >nul
title jshERP 本地启动（数据连远程库）
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-local.ps1" %*
pause
