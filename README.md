# DigitalEye: Digital Activity Monitoring System

An advanced-Java project. A small **agent** on a Windows computer reports which application or web page is in use and whether the user is active or idle. A **Spring Boot backend** stores it in **MySQL**, classifies it, raises alerts, and serves a browser **dashboard** for the administrator.

```
 Agent (Windows PC)  --HTTP/JSON-->  Backend (Spring Boot, port 9090)  --JDBC-->  MySQL
                                              ^
 Dashboard (browser, port 5500)  --HTTP/JSON--+
```

| Folder | What it is | Technology |
|---|---|---|
| `digitaleye-agent/` | Runs on the monitored PC. Sends the active window title and ACTIVE/IDLE. Takes a screenshot only when the admin clicks the button. | Java 17, JNA |
| `digitaleye-database/` | Schema, SQL migrations, models and DAOs (plain JDBC). | MySQL 8, Java 17 |
| `digitaleye-backend/` | REST API, JWT login, analytics, alerts, classification engine, screenshot storage. | Spring Boot 3.5, Spring Security |
| `digitaleye-dashboard/` | The admin web pages. No build step. | HTML, CSS, JavaScript |
| `scripts/` | One-click Windows scripts for setup and start-up. | `.cmd`, PowerShell |

## What it does

- **Live client health**: every monitored system shows ONLINE/OFFLINE (heartbeat every 20 s).
- **Activity analytics**: hourly timeline, category split, application usage, a Digital Activity Score, idle periods.
- **Activity classification**: every window is filed as Productive, Non-Productive, Troubleshooting, Creative, Uncategorized or Idle (details below).
- **Alerts and rules**: idle too long, unauthorized application, activity outside working hours, agent offline. Rules are editable on the Alerts page.
- **Screenshots on demand**: click **Take Screenshot** on a system's page; the agent captures one image. Nothing is captured automatically. Images are viewable only by a logged-in admin.
- **Activity logs**: searchable, filterable by category, with a classification tester.

## Requirements

Install these once (versions are minimums):

| Tool | Check with |
|---|---|
| JDK 17 or newer | `java -version` |
| Apache Maven 3.9+ | `mvn -v` |
| MySQL 8 (running) | `mysql --version` |
| Python 3 (only to serve the dashboard) | `python --version` |
| VS Code with **Extension Pack for Java** (suggested automatically on first open) | |

The agent reads the active window through Windows (`user32.dll`), so **the agent runs on Windows only**. The backend, database and dashboard run anywhere.

## First-time setup (about 10 minutes)

1. Unzip to a short folder **without spaces**, for example `C:\DigitalEye`. In VS Code choose **File > Open Folder** and pick it.
2. Open a terminal (**Ctrl+`**) and run, one after another:

   ```powershell
   .\scripts\setup.cmd
   .\scripts\create-database.cmd
   .\scripts\build-all.cmd
   ```
   - `setup` asks for your MySQL password and the agent's login, and writes the private settings files (git ignores them).
   - `create-database` builds the `digitaleye` database. Run it once.
   - `build-all` compiles the database module, backend and agent. The first run downloads libraries.

3. Open a **second** terminal and start the backend:

   ```powershell
   .\scripts\start-backend.cmd
   ```
   Wait for `Started DigitalEyeBackendApplication`. Leave it running.

4. Back in the first terminal, create the two accounts:

   ```powershell
   .\scripts\create-users.cmd
   ```
   Choose an admin username and password (you log in to the dashboard with it). The agent account uses the login you gave in `setup`.

5. Open a **third** terminal and start the dashboard:

   ```powershell
   .\scripts\start-dashboard.cmd
   ```
   Open <http://localhost:5500/login.html> and log in as the admin.

6. Open a **fourth** terminal and start the agent:

   ```powershell
   .\scripts\start-agent.cmd
   ```
   Within a few seconds your computer shows as ONLINE on the dashboard.

You can also use **Terminal > Run Task...** in VS Code; the same steps are listed there in order.

### Every other day

Start only the backend (step 3), the dashboard (step 5) and the agent (step 6).

## Things to try

On **Activity Logs**, type a window title into the classification tester and press **Check**:

| Window title | Result |
|---|---|
| `Main.java - DigitalEye - Visual Studio Code` | Productive |
| `Task Manager` | Troubleshooting |
| `Untitled - Paint` | Creative |
| `Java Collections - GeeksforGeeks - Google Chrome` | Productive |
| `Netflix - Google Chrome` | Non-Productive |
| `Spring Boot Tutorial for Beginners - YouTube - Google Chrome` | Productive |
| `Funny Cat Videos - YouTube - Google Chrome` | Non-Productive |
| `Debug Spring Boot JWT filter - Claude - Google Chrome` | Productive |
| `Movie recommendations for weekend - ChatGPT - Google Chrome` | Non-Productive |
| `Random Blog Post - Google Chrome` | Uncategorized |

Live test with the agent running: open VS Code, Task Manager, Paint, a Java YouTube video, then Netflix. Open the system on the dashboard and watch the timeline, donut chart and Application Usage fill in. Then click **Take Screenshot**.

## Activity classification

The agent only sends the window title, so rules read the title.

| Category | Colour | Score weight | Examples |
|---|---|---|---|
| Productive | green | 100% | IDEs, Microsoft Office, terminals, Postman, GitHub, Stack Overflow, AWS, Azure, Docker, MongoDB, ServiceNow, GeeksforGeeks, Coursera, LinkedIn, AI tools used for work |
| Non-Productive | red | 0% | Games, Netflix, Prime Video, Spotify, social media, shopping, torrents, betting, AI tools used for personal chat |
| Troubleshooting | blue | 75% | Settings, Task Manager, Device Manager, Event Viewer, Services, TeamViewer |
| Creative | purple | 75% | Canva, Figma, Paint, Photoshop, Lightroom, Premiere, Blender |
| Uncategorized | yellow | 50% | Anything no rule recognises |
| Idle | grey | 0% | No keyboard or mouse activity |

- **Browsers** are judged by the page title, not by the browser.
- **YouTube, Reddit, Quora, Medium and web search** depend on what is being viewed.
- **AI tools** (ChatGPT, Claude, Gemini, Perplexity, Lovable, Copilot ...) are Productive when the title shows work (code, bug, report, SQL) and Non-Productive when it shows personal use (movie, song, horoscope, trip).
- **Activity Score** = 30% active-time share + 70% weighted category quality. It stays empty until at least 60 seconds are tracked.
- After the rules change, open **Activity Logs** and click **Re-apply rules to old activity**.

Optional tuning in `digitaleye-backend/src/main/resources/application.properties` (remove the `#` to enable): `classification.company-keywords`, `classification.blocked-keywords`, `classification.unknown-app`, `classification.unknown-page`, `classification.ai-unclear`. Restart the backend afterwards.

### Honest limits

- The agent sees the **window title, not the web address**. A site is recognised only when its name or topic appears in the title.
- "Authorized vs unauthorized" needs an approved-sites list. That is the planned Policy management milestone. Until then unknown pages are Uncategorized. Set `classification.unknown-page=NON_PRODUCTIVE` together with `classification.company-keywords` for a strict allow-list.
- The AI-tool work/personal decision is a keyword guess on the title, not a reading of the conversation.
- Score weights are a design choice and can be changed in `ActivityCategory.java`.
- Rules are built in; making them editable from the dashboard comes with Policy management.

## API overview

Login first (`POST /api/auth/login`), then send `Authorization: Bearer <token>`.

| Group | Endpoints |
|---|---|
| Auth | `POST /api/auth/login`, `POST /api/auth/register` |
| Agent (USER role) | `POST /api/agent/systems/register`, `/activity`, `/goodbye`, `/screenshots`; `GET /api/agent/commands` |
| Systems and analytics (ADMIN) | `GET /api/admin/systems`, `/overview`, `/systems/{id}/analytics`, `/logs` |
| Alerts (ADMIN) | `GET /api/admin/alerts`, `/alerts/summary`; `POST /alerts/{id}/acknowledge`, `/alerts/acknowledge-all`; `GET/POST /alert-rules`, `PUT/DELETE /alert-rules/{id}` |
| Screenshots (ADMIN) | `POST /api/admin/systems/{id}/screenshot-request`; `GET /screenshots`, `/screenshots/{id}/image` |
| Classification (ADMIN) | `GET /api/admin/classification/test?title=...`; `POST /api/admin/classification/reapply` |

## Troubleshooting

| Message or symptom | Fix |
|---|---|
| `No plugin found for prefix 'springboot'` | The command is `mvn spring-boot:run` (with a hyphen). `start-backend.cmd` does it for you. |
| `Could not resolve ... digitaleye-database` or `cannot find symbol findPageAfterId` | The backend uses the database jar from your local Maven cache. Run `.\scripts\build-all.cmd`. Repeat it after any change in `digitaleye-database`. |
| `Access denied for user 'root'` | Wrong MySQL password. Run `.\scripts\setup.cmd` again. |
| `Unknown database 'digitaleye'` | Run `.\scripts\create-database.cmd`. |
| `Table ... already exists` while creating the database | It was already created. To start from nothing: `mysql -u root -p -e "DROP DATABASE digitaleye"`, then create it again. |
| `Port 9090 was already in use` | Change `server.port` in `application.properties`, `backend.url` in `agent.properties` and `API_BASE_URL` in `digitaleye-dashboard/js/config.js`. |
| Dashboard says "Failed to fetch" | The backend is not running, or `API_BASE_URL` does not match its port. |
| Login works but you are sent back to the login page | The account is not an ADMIN account. |
| Agent says login failed | Run `.\scripts\create-users.cmd`, or check the agent username and password in `agent.properties`. |
| Do not run `DbConnectionTest` | It is a manual test from the early database milestone and inserts a dummy admin row that blocks registering `admin`. |

## Security notes

- **Passwords live in only three files**, all ignored by git: `digitaleye-database/src/main/resources/db.properties`, `digitaleye-backend/src/main/resources/db.properties`, `digitaleye-agent/src/main/resources/agent.properties`. Only the `*.example` templates are committed. Check before pushing: `git ls-files | Select-String "(db|agent)\.properties$"` must print nothing.
- The JWT secret has a development default. For anything real, set the `JWT_SECRET` environment variable (32+ characters).
- `/api/auth/register` is open so you can create the first accounts. Anyone could register as ADMIN, so keep this on a local network only until it is locked behind an admin login.
- Traffic is plain HTTP. Use HTTPS before any real deployment.
- The agent sends only the window title and ACTIVE/IDLE. It never reads keystroke content or page content.
- Screenshots are saved in `digitaleye-backend/screenshots/` (git-ignored) and can be opened only through the admin API.

## Publishing to GitHub

```powershell
git init -b main
git add .
git status
git ls-files | Select-String "(db|agent)\.properties$|screenshots/"
git commit -m "Add DigitalEye project"
git remote add origin https://github.com/<your-name>/<new-repo>.git
git push -u origin main
```
Read the `git status` output first: no `db.properties`, `agent.properties`, `screenshots` or `target` entries may be listed.

## Roadmap

Done: activity analytics, alerts and client health, screenshots on demand, activity classification (six categories).
Planned: daily and weekly reports, policy management (editable rules, approved-sites list, intervals), agent offline queue and input counts (counts only, never content), admin-only registration with a user-management page, HTTPS.
