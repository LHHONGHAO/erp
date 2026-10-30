@echo off
setlocal enabledelayedexpansion
chcp 936 >nul
title 华夏ERP 一键部署（本地）

rem ============================================================
rem  用法：
rem    deploy.bat          完整部署（拉代码 -> 构建前端 -> 打包后端 -> 重启服务）
rem    deploy.bat web      只构建前端（后端无需重启，刷新浏览器即可）
rem    deploy.bat boot     只打包后端并重启服务
rem    deploy.bat start    只启动服务（不构建）
rem ============================================================

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "BOOT=%ROOT%\jshERP-boot"
set "WEB=%ROOT%\jshERP-web"
set "JAR=%BOOT%\target\jshERP.jar"
set "LOG=%BOOT%\deploy-backend.log"
set "ERRLOG=%BOOT%\deploy-backend.err.log"
set "PORT=9999"

rem ---------- 定位 JDK ----------
set "JAVA_HOME=C:\Users\Administrator\.jdks\jdk8u492-b09"
if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [错误] 未找到 JDK 8，请修改本脚本中的 JAVA_HOME 变量。
    pause
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "JAVA=%JAVA_HOME%\bin\java.exe"

rem ---------- 定位 Maven ----------
set "MVN=C:\Program Files\apache-maven-3.9.16\bin\mvn.cmd"
if not exist "%MVN%" set "MVN=mvn"

set "MODE=%~1"
if "%MODE%"=="" set "MODE=all"

if /i "%MODE%"=="start" goto START_ONLY
if /i "%MODE%"=="web"   goto ONLY_WEB
if /i "%MODE%"=="boot"  goto ONLY_BOOT

rem ================= 完整部署 =================
call :STEP_STOP
call :STEP_PULL
call :STEP_WEB
if errorlevel 1 goto FAIL
call :STEP_BOOT
if errorlevel 1 goto FAIL
goto START_ONLY

:ONLY_WEB
call :STEP_WEB
if errorlevel 1 goto FAIL
echo.
echo [完成] 前端已构建到 jsherp-web\dist，后端会直接读取该目录。
echo        浏览器按 Ctrl+F5 强制刷新即可看到最新界面。
goto END

:ONLY_BOOT
call :STEP_STOP
call :STEP_BOOT
if errorlevel 1 goto FAIL
goto START_ONLY

:START_ONLY
call :STEP_START
goto END

:FAIL
echo.
echo [部署失败] 请根据上方提示排查。
if not "%NO_PAUSE%"=="1" pause
exit /b 1

:END
echo.
if not "%NO_PAUSE%"=="1" pause
exit /b 0


rem ============================================================
rem  以下是子步骤
rem ============================================================

:STEP_STOP
echo [1/5] 停止旧服务 ...
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%PORT%" ^| findstr "LISTENING"') do (
    taskkill /f /pid %%p >nul 2>&1
)
timeout /t 1 >nul
echo       完成
exit /b 0

:STEP_PULL
echo [2/5] 拉取最新代码 ...
pushd "%ROOT%"
git pull --ff-only
if errorlevel 1 (
    echo       [警告] 拉取失败（网络不通或有本地改动），继续使用当前本地代码
)
popd
exit /b 0

:STEP_WEB
echo [3/5] 构建前端 ...
pushd "%WEB%"
if not exist "node_modules" (
    echo       首次部署，正在安装依赖 ...
    call npm install
    if errorlevel 1 (
        popd
        echo       [错误] 依赖安装失败
        exit /b 1
    )
)
set "NODE_OPTIONS=--max-old-space-size=4096"
call npm run build
if errorlevel 1 (
    popd
    echo       [错误] 前端构建失败
    exit /b 1
)
popd
echo       完成，产物：%WEB%\dist
exit /b 0

:STEP_BOOT
echo [4/5] 打包后端 ...
pushd "%BOOT%"
call "%MVN%" -o -DskipTests clean package
if errorlevel 1 (
    echo       [提示] 离线打包失败，尝试联网打包 ...
    call "%MVN%" -DskipTests clean package
)
popd
if not exist "%JAR%" (
    echo       [错误] 未生成 %JAR%
    exit /b 1
)
echo       完成，产物：%JAR%
exit /b 0

:STEP_START
echo [5/5] 启动服务 ...
if not exist "%JAR%" (
    echo       [错误] 找不到 %JAR%，请先执行 deploy.bat boot
    exit /b 1
)
powershell -NoProfile -Command "Start-Process -FilePath '%JAVA%' -ArgumentList '-Xms256m','-Xmx1024m','-Dspring.profiles.active=dev','-Dfile.encoding=UTF-8','-jar','%JAR%' -WorkingDirectory '%BOOT%' -WindowStyle Minimized -RedirectStandardOutput '%LOG%' -RedirectStandardError '%ERRLOG%'"

set /a WAITED=0
:WAIT_PORT
timeout /t 2 >nul
netstat -ano | findstr ":%PORT%" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 goto PORT_READY
set /a WAITED+=2
if !WAITED! lss 60 goto WAIT_PORT
echo       [警告] 60 秒内未监听到 %PORT% 端口，请查看日志：%LOG%
exit /b 0

:PORT_READY
set "LANIP="
for /f %%i in ('powershell -NoProfile -Command "(Get-NetIPAddress -AddressFamily IPv4 ^| Where-Object {$_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*'} ^| Select-Object -First 1).IPAddress"') do set "LANIP=%%i"
echo.
echo ============================================================
echo   部署完成，服务已启动
echo ------------------------------------------------------------
echo   本机访问：http://localhost:%PORT%/jshERP-boot/
if not "!LANIP!"=="" echo   局域网访问：http://!LANIP!:%PORT%/jshERP-boot/
echo   后端日志：%LOG%
echo ============================================================
exit /b 0
