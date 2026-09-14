# DigitalEye Agent — Milestone 1

Core loop for the monitoring agent: active-application tracking + idle-time
detection, logged to the console.

## What's in this milestone
- `ActiveWindowDetector` — gets the title of the currently focused window
- `IdleTimeDetector` — gets milliseconds since the last keyboard/mouse input
- `Main` — polls both every 5 seconds, logs when the active app changes
  (with session duration) and when the user goes idle / becomes active again

## What's NOT in this milestone (on purpose)
- Screen capture
- Sending data to the backend (no networking yet)
- Local storage / offline buffering
- Encryption

Keeping this milestone console-only means you can test and debug the
detection logic in isolation before wiring in anything else.

## How to run
1. Open the `digitaleye-agent` folder in IntelliJ (File > Open, select this
   folder). IntelliJ will detect the `pom.xml` and download the JNA
   dependency automatically.
2. Run `Main.java` (right-click the file > Run 'Main.main()').
3. Switch between a couple of windows and watch the console log the app
   changes. Leave the mouse/keyboard untouched for 60+ seconds to see the
   idle detection trigger.

Command line alternative (from this folder, with Maven installed):
```
mvn clean package
java -jar target/digitaleye-agent.jar
```

## Next milestone
Screen capture (`java.awt.Robot`) and writing sessions to a local file
instead of just the console.
