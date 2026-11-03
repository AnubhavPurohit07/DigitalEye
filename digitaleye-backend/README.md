# DigitalEye Backend

Spring Boot 3.5 REST API on **http://localhost:9090**. Handles login (JWT, BCrypt), receives data from the agent, runs the analytics, alert and classification logic, and serves the dashboard.

## Run

1. Build the database module first: `.\scripts\build-all.cmd` from the project root. The backend depends on `com.digitaleye:digitaleye-database`, which only exists in your local Maven cache after that build.
2. Start it: `.\scripts\start-backend.cmd`, or inside this folder `mvn spring-boot:run`.

Database settings: `src/main/resources/db.properties` (created by `.\scripts\setup.cmd`, git-ignored). Other settings: `src/main/resources/application.properties`.

## Packages

- `controller`: `AuthController`, `AgentController`, `SystemController`, `AnalyticsController`, `LogController`, `AlertController`, `ScreenshotController`, `ClassificationController`.
- `service`: `ActivityAnalytics` (time per category, score), `AlertEngine` and `MonitoringScheduler` (idle, off-hours, offline alerts), `ScreenshotStorage` and `ScreenshotRequestService`, and the classification engine (`AppClassifier`, `ActivityClassifier`, `ClassificationCatalog`, `TextMatch`, `ActivityCategory`, `ClassificationSettings`).
- `security`: `SecurityConfig`, `JwtAuthFilter`, `JwtUtil`, `CustomUserDetailsService`. `/api/admin/**` needs the ADMIN role, `/api/agent/**` a logged-in user.
- `dto`: request and response objects.

The endpoint list is in the root README.

## Classification engine in short

`AppClassifier.classify(windowTitle, status)` returns a category name. The category is stored in `activity_logs.category` when a session starts. Rules are written in `ClassificationCatalog` and matched at word boundaries. The admin can test a title and re-classify old rows from the dashboard's Activity Logs page.
