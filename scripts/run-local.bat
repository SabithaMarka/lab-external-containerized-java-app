@echo off
setlocal
cd /d "%~dp0.."

echo === Compiling Java application ===
if exist out rmdir /s /q out
mkdir out
javac -encoding UTF-8 -d out src\main\java\com\lab\app\LoginApp.java || goto :error

echo === Copying web resources ===
if not exist out\public mkdir out\public
copy /Y src\main\resources\public\index.html out\public\index.html >nul || goto :error

echo === Starting application on http://localhost:8090 ===
set PORT=8090
java -cp out com.lab.app.LoginApp
goto :eof

:error
echo Build failed.
exit /b 1
