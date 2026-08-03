@echo off
chcp 65001 >nul
REM ============================================================
REM  DeckForge · PPT 工作台 - 一键启动（Windows）
REM  启动后端(8090) + 前端(5173)
REM ============================================================
echo.
echo  ======== DeckForge · PPT 工作台  一键启动 ========
echo.

SET ROOT=%~dp0.
SET BACKEND=%ROOT%\backend
SET FRONTEND=%ROOT%\frontend

REM ---------------- 后端 ----------------
where java >nul 2>nul
if %errorlevel% neq 0 (
  echo [错误] 未检测到 JDK，请先安装 JDK 1.8+
  pause
  exit /b 1
)
echo [1/2] 启动后端 Spring Boot (端口 8090) ...
start "DeckForge-Backend" cmd /k "cd /d %BACKEND% && java -jar target\deckforge-app.jar --server.port=8090"
echo       后端已启动，等待初始化...
timeout /t 12 /nobreak >nul

REM ---------------- 前端 ----------------
where node >nul 2>nul
if %errorlevel% neq 0 (
  echo [错误] 未检测到 Node.js，请先安装 Node 18+
  pause
  exit /b 1
)
if not exist "%FRONTEND%\node_modules" (
  echo [提示] 首次运行，正在安装前端依赖...
  cd /d %FRONTEND% && call npm install
)
echo [2/2] 启动前端 Vite (端口 5173) ...
start "DeckForge-Frontend" cmd /k "cd /d %FRONTEND% && npm run dev"

echo.
echo  ============================================================
echo   DeckerForge 已启动！
echo   浏览器打开:  http://localhost:5173
echo   （若 5173 被占用，看前端窗口里的实际地址）
echo  ============================================================
echo.
pause
