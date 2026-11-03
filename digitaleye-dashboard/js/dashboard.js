// Dashboard home. Everything here is live: systems (with each one's current
// application and today's Activity Score), the stat cards, the recent alerts and
// the hourly activity chart all come from the backend and refresh on a timer.

let allSystems = [];
const AUTO_REFRESH_SECONDS = 7;

document.addEventListener("DOMContentLoaded", async function () {
    const session = initPage("dashboard");
    if (!session) return;

    document.getElementById("welcomeName").textContent = session.username;
    document.getElementById("systemSearch").addEventListener("input", applyFilters);
    document.getElementById("statusFilter").addEventListener("change", applyFilters);

    document.getElementById("systemsTableBody").addEventListener("click", function (event) {
        const button = event.target.closest("[data-system-id]");
        if (button) {
            window.location.href = "client-detail.html?systemId=" + encodeURIComponent(button.dataset.systemId);
        }
    });

    await refreshAll();
    setInterval(refreshAll, AUTO_REFRESH_SECONDS * 1000);
});

function refreshAll() {
    // Each loader handles its own errors, so one failing panel doesn't blank the others.
    return Promise.all([loadSystems(), loadOverview(), loadAlerts()]);
}

async function loadSystems() {
    try {
        allSystems = await apiGet("/api/admin/systems");
    } catch (err) {
        document.getElementById("systemsTableBody").innerHTML =
            '<tr><td colspan="7" class="empty-state">Could not load systems: ' + escapeHtml(err.message) + '</td></tr>';
        return;
    }
    applyFilters();
}

async function loadOverview() {
    try {
        const overview = await apiGet("/api/admin/overview?date=" + todayISO());
        renderStatCards(overview);
        renderHourlyChart(document.getElementById("barChart"), document.getElementById("barLabels"), overview.hourly);
    } catch (err) {
        document.getElementById("statGrid").innerHTML =
            '<div class="empty-note">Could not load the overview: ' + escapeHtml(err.message) + '</div>';
    }
}

async function loadAlerts() {
    const list = document.getElementById("alertsList");
    try {
        const page = await apiGet("/api/admin/alerts?size=5");
        if (page.items.length === 0) {
            list.innerHTML = '<div class="empty-note">No alerts yet &mdash; everything looks normal.</div>';
            return;
        }
        list.innerHTML = page.items.map(a => `
            <div class="alert-row" style="${a.acknowledged ? "opacity:.55" : ""}">
                <div class="alert-dot ${severityClass(a.severity)}">${a.severity === "HIGH" ? "&#9888;" : "&#8505;"}</div>
                <div>
                    <div class="alert-title">${escapeHtml(a.message)}</div>
                    <div class="alert-meta">${escapeHtml(a.systemName)} &middot; ${escapeHtml(fmtDateTime(a.createdAt))}</div>
                </div>
                ${severityTag(a.severity)}
            </div>
        `).join("");
    } catch (err) {
        list.innerHTML = '<div class="empty-note">Could not load alerts: ' + escapeHtml(err.message) + '</div>';
    }
}

function renderStatCards(o) {
    const cards = [
        { icon: "blue",   symbol: "👥", value: o.totalSystems, label: "Total Systems", delta: "Registered agents", deltaClass: "flat" },
        { icon: "green",  symbol: "💻", value: o.onlineSystems + " / " + o.totalSystems, label: "Online Agents", delta: "Heard from in the last 90s", deltaClass: "flat" },
        { icon: "amber",  symbol: "⏱", value: o.avgActivityScore == null ? "—" : o.avgActivityScore, label: "Avg Activity Score", delta: "Today, across systems with data", deltaClass: "flat" },
        { icon: "purple", symbol: "📄", value: o.logsToday, label: "Activity Logs Today", delta: "App and idle sessions", deltaClass: "flat" },
        { icon: "red",    symbol: "🔔", value: o.alertsUnacknowledged, label: "Activity Alerts", delta: o.alertsHigh + " high severity open", deltaClass: o.alertsHigh > 0 ? "down" : "flat" }
    ];

    document.getElementById("statGrid").innerHTML = cards.map(c => `
        <div class="stat-card">
            <div class="stat-icon ${c.icon}">${c.symbol}</div>
            <div>
                <div class="stat-value">${c.value}</div>
                <div class="stat-label">${c.label}</div>
                <div class="stat-delta ${c.deltaClass}">${c.delta}</div>
            </div>
        </div>
    `).join("");

    const badge = document.getElementById("alertBadgeCount");
    if (badge) badge.textContent = o.alertsUnacknowledged;
}

function renderSystemsTable(systems) {
    const tbody = document.getElementById("systemsTableBody");

    if (systems.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="empty-state">' +
            (allSystems.length === 0
                ? "No systems registered yet. Run the agent on a monitored machine to see it appear here."
                : "No systems match the current filter.") +
            '</td></tr>';
        document.getElementById("systemsCountLabel").textContent = "Showing 0 of " + allSystems.length + " systems";
        return;
    }

    tbody.innerHTML = systems.map((s, i) => {
        const online = s.status === "ONLINE";
        let currentApp = '<span class="muted">—</span>';
        let running = '<span class="muted">—</span>';

        if (online && s.currentApplication) {
            if (s.activityStatus === "IDLE") {
                currentApp = '<span class="muted">💤 Idle</span>';
            } else {
                currentApp = '<div class="cell-ellipsis" title="' + escapeHtml(s.currentApplication) + '">' +
                    escapeHtml(s.currentApplication) + '</div>';
            }
            running = s.sessionStartTime ? formatRunningTime(s.sessionStartTime) : running;
        }

        return `
        <tr>
            <td>${i + 1}</td>
            <td>${escapeHtml(s.systemName)}</td>
            <td><span class="status-dot ${online ? "online" : "offline"}">${online ? "Online" : "Offline"}</span></td>
            <td>${currentApp}</td>
            <td class="nowrap">${running}</td>
            <td>${scorePill(s.activityScore)}</td>
            <td><button class="btn small" data-system-id="${s.id}">View</button></td>
        </tr>`;
    }).join("");

    document.getElementById("systemsCountLabel").textContent =
        "Showing " + systems.length + " of " + allSystems.length + " systems";
}

function formatRunningTime(startIso) {
    const startMs = new Date(startIso).getTime();
    if (isNaN(startMs)) return "—";

    const diffSeconds = Math.max(0, Math.floor((Date.now() - startMs) / 1000));
    const hours = Math.floor(diffSeconds / 3600);
    const minutes = Math.floor((diffSeconds % 3600) / 60);

    if (hours > 0) return hours + "h " + minutes + "m";
    if (minutes > 0) return minutes + "m";
    return diffSeconds + "s";
}

function applyFilters() {
    const query = document.getElementById("systemSearch").value.trim().toLowerCase();
    const statusFilter = document.getElementById("statusFilter").value;

    const filtered = allSystems.filter(s => {
        const matchesQuery = !query || s.systemName.toLowerCase().includes(query);
        const matchesStatus = statusFilter === "ALL" || s.status === statusFilter;
        return matchesQuery && matchesStatus;
    });

    renderSystemsTable(filtered);
}
