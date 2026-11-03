# DigitalEye Agent

Runs on the monitored Windows computer.

- Every 5 seconds it reads the **active window title** (`ActiveWindowDetector`) and the **idle time** (`IdleTimeDetector`) through JNA and `user32.dll`.
- It logs in to the backend, registers the computer, and reports each change of window or ACTIVE/IDLE state (`BackendClient`).
- A heartbeat every 20 seconds keeps the system ONLINE on the dashboard.
- Every 3 seconds it asks the backend whether the admin clicked **Take Screenshot**. If so it captures one image (`ScreenshotCapturer`) and uploads it. Automatic screenshots are off unless `screenshot.interval.seconds` is set above 0.
- It sends nothing except the window title and ACTIVE/IDLE state, plus a screenshot when requested.

## Run

From the project root, with the backend running:

```powershell
.\scripts\start-agent.cmd
```

Or in VS Code: open `Main.java` and click **Run** above `main`.

Settings are in `src/main/resources/agent.properties`, created by `.\scripts\setup.cmd` from `agent.properties.example`. That file holds a password and is never committed.
