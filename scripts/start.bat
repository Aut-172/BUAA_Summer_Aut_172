@echo off
chcp 65001 >nul
title Life Assistant Platform - Startup
setlocal EnableExtensions

set "ROOT_DIR=%~dp0.."
for %%I in ("%ROOT_DIR%") do set "ROOT_DIR=%%~fI"
set "BACKEND_DIR=%ROOT_DIR%\backend"
set "FRONTEND_DIR=%ROOT_DIR%\frontend"
set "BACKEND_JAR=%BACKEND_DIR%\build\libs\demo-0.0.1-SNAPSHOT.jar"

echo ========================================
echo     Life Assistant Platform Startup
echo ========================================
echo.

echo [1/4] Starting MySQL...
net start MySQL80 >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK] MySQL started
) else (
    echo [OK] MySQL already running or unavailable to this script
)

echo [2/4] Initializing database...
powershell -ExecutionPolicy Bypass -File "%ROOT_DIR%\scripts\init-db.ps1"
if errorlevel 1 (
    echo [ERROR] Database initialization failed
    goto END
)
echo [OK] Database initialized

echo [3/4] Starting backend service...
if not exist "%BACKEND_JAR%" (
    echo [INFO] Backend jar not found, building first...
    pushd "%BACKEND_DIR%"
    call gradlew.bat bootJar
    if errorlevel 1 (
        echo [ERROR] Backend build failed
        popd
        goto END
    )
    popd
)
start "Backend" cmd /c "cd /d %BACKEND_DIR% && java -jar \"%BACKEND_JAR%\" --server.port=8081"

echo Waiting for backend health check...
set WAIT_COUNT=0
:WAIT_BACKEND
timeout /t 2 /nobreak >nul
set /a WAIT_COUNT+=1
curl -s http://localhost:8081/api/health >nul 2>&1
if %errorlevel% equ 0 (
    echo [OK] Backend started on http://localhost:8081
    goto BACKEND_READY
)
if %WAIT_COUNT% lss 15 goto WAIT_BACKEND
echo [WARN] Backend did not become healthy within 30 seconds
:BACKEND_READY

echo [4/4] Starting frontend service...
if not exist "%FRONTEND_DIR%\node_modules" (
    echo [INFO] Frontend dependencies not found, installing...
    pushd "%FRONTEND_DIR%"
    call npm install
    if errorlevel 1 (
        echo [ERROR] Frontend dependency installation failed
        popd
        goto END
    )
    popd
)
start "Frontend" cmd /c "cd /d %FRONTEND_DIR% && npm run dev"
timeout /t 3 /nobreak >nul

echo.
echo ========================================
echo     Startup finished
echo.
echo     Frontend: http://localhost:5173
echo     Backend : http://localhost:8081
echo.
echo     If backend fails, verify DB_URL / DB_USERNAME / DB_PASSWORD
echo     or check backend\src\main\resources\application.yml
echo ========================================
echo.
pause

:END
endlocal
