@echo off
cd /d "%~dp0..\digitaleye-dashboard"
echo Dashboard: http://localhost:5500/login.html   (press Ctrl+C to stop)
echo.
python -m http.server 5500
if errorlevel 1 (
  echo.
  echo Could not start Python. Install Python 3 from python.org and tick "Add python.exe to PATH",
  echo or open login.html with the VS Code "Live Server" extension instead.
  pause
)
