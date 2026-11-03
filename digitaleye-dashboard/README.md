# DigitalEye Dashboard

Plain HTML, CSS and JavaScript. No framework, no build step, no CDN.

## Run

Start it with `.\scripts\start-dashboard.cmd` and open <http://localhost:5500/login.html>. Do not double-click the HTML files: browsers block `fetch()` on `file://` pages. Instead of Python you can use the VS Code **Live Server** extension on `login.html`.

The backend address is in `js/config.js` (`API_BASE_URL`, default `http://localhost:9090`).

## Pages

| Page | What it shows |
|---|---|
| `login.html` | Admin login. The JWT is kept in `localStorage`. |
| `dashboard.html` | Overview: stat cards, client systems with ONLINE/OFFLINE, alerts, hourly activity chart. |
| `client-detail.html` | One system: timeline, category donut, application usage, activity log, screenshots, **Take Screenshot**. |
| `activity-logs.html` | All logs with filters (including category), the classification tester and **Re-apply rules to old activity**. |
| `alerts.html` | Alerts with acknowledge, and the editable alert rules. |
| `screenshots.html` | Screenshot gallery per system and day, with **Take Screenshot**. |

Text that comes from monitored computers (window titles) is always HTML-escaped before it is shown.
