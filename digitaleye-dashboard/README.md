# DigitalEye Dashboard (Frontend)

This is the `dashboard` branch's content: a plain HTML/CSS/JavaScript
admin dashboard (no frameworks, no build step, no CDN dependencies) that
talks to the Spring Boot backend over `fetch()`.

## Pages
- `login.html` &mdash; admin login, stores the JWT in `localStorage`
- `dashboard.html` &mdash; the main dashboard (sidebar, stat cards, client
  systems table, alerts panel, hourly activity chart)
- `client-detail.html` &mdash; per-system detail page (timeline,
  productivity donut, usage table, activity log, screenshots)

## One-time setup: add CORS support to the backend

The dashboard runs in a browser from its own local address (whatever port
you serve it on). The backend doesn't yet allow cross-origin requests, so
the browser will block every `fetch()` call with a CORS error until you
add this.

1. In `digitaleye-backend`, replace
   `src/main/java/com/digitaleye/backend/security/SecurityConfig.java`
   with the updated `SecurityConfig.java` from this delivery (it adds a
   `CorsConfigurationSource` bean and wires it into the filter chain).
2. Re-run `DigitalEyeBackendApplication.java`. You should see it start
   the same way as before (`Started DigitalEyeBackendApplication`).

## Running everything together

1. Make sure MySQL is running and the `digitaleye` schema exists
   (already done, per the database module).
2. Start the backend (`DigitalEyeBackendApplication.java`). Confirm
   which port it's listening on &mdash; this machine currently uses
   **9090** (changed from 8080 because a Windows service was already
   using that port). If that's different on your machine, open
   `js/config.js` in this folder and update `API_BASE_URL` to match.
3. Serve this `digitaleye-dashboard` folder as a local website &mdash;
   don't just double-click `login.html` to open it as a `file://` page,
   since some browsers block `fetch()` from file pages. Easiest options:
   - **VS Code**: install the "Live Server" extension, right-click
     `login.html` &rarr; "Open with Live Server"
   - **Any machine with Python**: open a terminal in this folder and run
     `python -m http.server 5500`, then visit `http://localhost:5500/login.html`
4. Log in with the admin account you registered earlier (e.g.
   `admin` / `admin123`, or whatever you used with `/api/auth/register`).
5. You should land on `dashboard.html` and see your real system(s) in
   the "Client Systems" table, pulled live from MySQL through the
   backend.

## What's live vs sample right now

Both `dashboard.html` and `client-detail.html` show a blue banner at the
top stating this explicitly, and sample values are also labeled inline
(e.g. "SAMPLE" under a stat card, a muted dash in the table with a
tooltip). Nothing fake is presented as if it were real data without a
label next to it.

**Live (from the backend/database):**
- Login / JWT authentication
- System name, online/offline status, and total system count

**Sample (hardcoded in `dashboard.js` / `client-detail.js`) because the
backend doesn't have these endpoints yet:**
- Activity score, activity logs count, alerts count/list
- The hourly activity bar chart and per-user timeline
- Application usage table, input-activity summary (clicks/keystrokes/websites)
- Recent activity logs table
- Screenshots

## Next milestone: making the rest live

To replace each sample section with real data, the **database** module
already has the tables (`activity_logs`, `screenshots`, `alerts`,
`alert_rules`, `input_activity_summary` &mdash; see its `schema.sql`), but
the **backend** needs matching DAOs + REST endpoints for them (following
the same pattern as `MonitoredSystemDao` + `SystemController`), and the
**agent** needs to actually send this data over the network instead of
just logging it locally. Roughly, in order:

1. Agent: add the networking layer (send activity sessions to the backend
   over HTTPS instead of just `System.out.println`)
2. Backend: endpoints to receive and store that data (`POST
   /api/agent/activity`, `POST /api/agent/screenshot`, etc.), and
   endpoints for the dashboard to read it back (`GET
   /api/admin/systems/{id}/activity`, `GET /api/admin/alerts`, etc.)
3. Dashboard: swap each `renderSample...()` function in `dashboard.js` /
   `client-detail.js` for a real `apiRequest(...)` call, the same way
   `fetchSystems()` already works

## Troubleshooting

- **"Failed to fetch" / network error on the login page** &mdash; the
  backend isn't running, or `API_BASE_URL` in `js/config.js` doesn't
  match the port it's actually running on.
- **CORS error in the browser console** &mdash; you haven't applied the
  `SecurityConfig.java` update above, or didn't restart the backend
  after applying it.
- **Login succeeds but immediately bounces back to the login page** &mdash;
  the account you logged in with isn't an `ADMIN` role account (this
  milestone's dashboard is admin-only).
