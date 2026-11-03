@echo off
cd /d "%~dp0..\digitaleye-backend"
if not exist "src\main\resources\db.properties" (
  echo db.properties is missing. Run scripts\setup.cmd first.
  pause
  exit /b 1
)
echo Starting the backend on http://localhost:9090  (press Ctrl+C to stop)
echo Wait for the line "Started DigitalEyeBackendApplication".
echo.
call mvn spring-boot:run
if errorlevel 1 (
  echo.
  echo The backend stopped with an error. Read the message above.
  echo If it cannot find com.digitaleye:digitaleye-database, run scripts\build-all.cmd first.
  pause
)
