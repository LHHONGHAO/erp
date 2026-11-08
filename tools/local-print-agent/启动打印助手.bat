@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo 正在启动本地打印助手...
python print_agent.py
pause
