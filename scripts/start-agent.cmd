@echo off
cd /d "%~dp0..\digitaleye-agent"
if not exist "src\main\resources\agent.properties" (
  echo agent.properties is missing. Run scripts\setup.cmd first.
  pause
  exit /b 1
)
echo Building the agent...
call mvn -q compile
if errorlevel 1 goto fail
call mvn -q dependency:build-classpath -Dmdep.outputFile=target\agent-classpath.txt
if errorlevel 1 goto fail
set /p AGENT_CP=<target\agent-classpath.txt
echo Starting the agent (press Ctrl+C to stop). It monitors THIS computer's active window.
echo.
java -cp "target\classes;%AGENT_CP%" com.digitaleye.agent.Main
exit /b 0
:fail
echo.
echo The agent could not be built. Read the message above.
pause
exit /b 1
