@echo off
cd /d "%~dp0.."
echo Building database, backend and agent.
echo The first run downloads libraries and can take a few minutes.
echo.
call mvn clean install -DskipTests
if errorlevel 1 goto fail
echo.
echo Build finished.
pause
exit /b 0
:fail
echo.
echo The build failed. Read the message above. Make sure Java 17 or newer and Maven are installed.
pause
exit /b 1
