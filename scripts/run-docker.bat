@echo off
setlocal
cd /d "%~dp0.."

set IMAGE=lab-login-app:latest
set CONTAINER=lab-login-app
set HOST_PORT=8081

echo === Building Docker image %IMAGE% ===
docker build -t %IMAGE% . || goto :error

echo === Removing old container (if any) ===
docker rm -f %CONTAINER% >nul 2>&1

echo === Starting container %CONTAINER% on http://localhost:%HOST_PORT% ===
docker run -d --name %CONTAINER% --restart unless-stopped -p %HOST_PORT%:8080 ^
  -e APP_USER=admin -e APP_PASSWORD=admin123 %IMAGE% || goto :error

echo === Waiting for health check ===
set /a tries=0
:waitloop
timeout /t 2 /nobreak >nul
curl -fsS http://localhost:%HOST_PORT%/healthz >nul 2>&1 && goto :healthy
set /a tries+=1
if %tries% lss 20 goto waitloop
echo Container failed to become healthy
docker logs %CONTAINER%
exit /b 1

:healthy
echo.
echo Container is healthy: http://localhost:%HOST_PORT%
docker ps --filter name=%CONTAINER% --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
start http://localhost:%HOST_PORT%
goto :eof

:error
echo Build/deploy failed.
exit /b 1
