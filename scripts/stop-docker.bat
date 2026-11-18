@echo off
setlocal
docker rm -f lab-login-app >nul 2>&1
if errorlevel 1 (
  echo Container lab-login-app was not running.
) else (
  echo Container lab-login-app removed.
)
