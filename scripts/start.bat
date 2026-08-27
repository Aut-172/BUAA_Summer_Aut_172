@echo off
chcp 65001 >nul
title Life Assistant Microservices - Startup
setlocal EnableExtensions

set "ROOT_DIR=%~dp0.."
for %%I in ("%ROOT_DIR%") do set "ROOT_DIR=%%~fI"

echo ========================================
echo   Life Assistant Microservices Startup
echo ========================================
echo.

where docker >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker was not found. Install Docker Desktop and retry.
    goto END
)

echo [1/2] Building and starting microservice stack...
pushd "%ROOT_DIR%"
set "COMPOSE_PARALLEL_LIMIT=1"
docker compose up --build -d
if errorlevel 1 (
    echo [ERROR] docker compose startup failed
    popd
    goto END
)
popd

echo [2/2] Startup command finished.
echo.
echo Gateway health:  http://localhost:8080/actuator/health
echo Frontend:        http://localhost:5173
echo Nacos console:   http://localhost:8848/nacos
echo.
echo Use "docker compose logs -f api-gateway" or "docker compose ps" for runtime status.
echo.
pause

:END
endlocal
