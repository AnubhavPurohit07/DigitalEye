// Client detail page: one system, one day. All of it comes from
//   GET /api/admin/systems/{id}/analytics?date=YYYY-MM-DD
// Mouse-click and keystroke counts are the only things still missing - they need the
// agent to track input, which is planned for a later milestone.

let systemId = null;
let selectedDate = todayISO();
let lastShotKey = null;

document.addEventListener("DOMContentLoaded", async function () {
    const session = initPage("systems");
    if (!session) return;

    const params = new URLSearchParams(window.location.search);
    systemId = params.get("systemId");
    if (!systemId) {
        showError("No system selected. Go back to the Client Systems list and click View.");
        return;
    }

    selectedDate = params.get("date") || todayISO();
    const picker = document.getElementById("datePicker");
    picker.value = selectedDate;
    picker.max = todayISO();
    picker.addEventListener("change", function () {
        selectedDate = picker.value || todayISO();
        history.replaceState(null, "", "?systemId=" + encodeURIComponent(systemId) + "&date=" + selectedDate);
        lastShotKey = null;
        load();
    });

    document.getElementById("reportBtn").addEventListener("click", function () {
        toast("Report generation arrives in the next milestone.");
    });

    wireScreenshotButton(document.getElementById("takeShotBtn"), () => systemId, async function () {
        // The new screenshot belongs to today, so make sure today is what is shown.
        if (selectedDate !== todayISO()) {
            selectedDate = todayISO();
            picker.value = selectedDate;
            history.replaceState(null, "", "?systemId=" + encodeURIComponent(systemId) + "&date=" + selectedDate);
        }
        lastShotKey = null;
        await load();
    });

    await load();
    // Only today is "live" - an older day can't change, so it isn't refreshed.
    setInterval(function () {
        if (selectedDate === todayISO()) load();
    }, 10000);
});

async function load() {
    try {
        const data = await apiGet("/api/admin/systems/" + encodeURIComponent(systemId) + "/analytics?date=" + selectedDate);
        document.getElementById("pageError").style.display = "none";
        render(data);
    } catch (err) {
        showError(err.message.includes("404")
            ? "That system doesn't exist (it may have been removed)."
            : "Could not load this system: " + err.message);
    }
}

function showError(message) {
    const box = document.getElementById("pageError");
    box.textContent = message;
    box.style.display = "block";
}

function render(data) {
    renderHeader(data.system);
    renderKpis(data.summary, data.deltas);
    renderHourlyChart(document.getElementById("timelineChart"), document.getElementById("timelineLabels"), data.hourly);
    renderDonut(document.getElementById("donutSvg"), document.getElementById("donutLegend"),
        data.categories, data.summary.activityScore == null ? "—" : data.summary.activityScore);
    renderSummary(data.summary);
    renderUsage(data.appUsage);
    renderLogs(data.recentLogs);
    renderScreenshots(data.recentScreenshots, data.date);

    document.getElementById("screensLink").href = "screenshots.html?systemId=" + systemId + "&date=" + data.date;
    document.getElementById("allShotsLink").href = "screenshots.html?systemId=" + systemId + "&date=" + data.date;
    document.getElementById("allLogsLink").href = "activity-logs.html?systemId=" + systemId;
}

function renderHeader(system) {
    document.title = "DigitalEye — " + system.systemName;
    document.getElementById("breadcrumbName").textContent = system.systemName;
    document.getElementById("detailName").textContent = system.systemName;
    document.getElementById("detailAvatar").textContent = system.systemName.charAt(0).toUpperCase();

    const online = system.status === "ONLINE";
    document.getElementById("detailStatus").innerHTML =
        '<span class="status-dot ' + (online ? "online" : "offline") + '">' + (online ? "Online" : "Offline") + "</span>";

    // A screenshot can only be taken while the agent is running; leave the button alone while one is in progress.
    const shotButton = document.getElementById("takeShotBtn");
    if (!shotButton.dataset.busy) {
        shotButton.disabled = !online;
        shotButton.title = online ? "Ask the agent on this computer for a screenshot now"
                                  : "This computer's agent is offline, so it can't take a screenshot";
    }

    let lastActive = "Last active: " + fmtDateTime(system.lastHeartbeat);
    if (online && system.currentApplication) {
        lastActive = system.activityStatus === "IDLE" ? "Currently idle" : "Now: " + system.currentApplication;
    }
    document.getElementById("detailLastActive").textContent = lastActive;
    document.getElementById("detailMonitoringSince").textContent = "Monitoring since: " + fmtDay(system.monitoringSince);
}

// goodWhenUp: true if an increase is good news (active time, score); false if bad (idle time, alerts); null = neutral.
function deltaHtml(value, goodWhenUp) {
    if (value == null) {
        return '<div class="kpi-delta">no data for the previous day</div>';
    }
    if (value === 0) {
        return '<div class="kpi-delta">no change vs previous day</div>';
    }
    const up = value > 0;
    let cls = "";
    if (goodWhenUp !== null) {
        cls = (up === goodWhenUp) ? "up" : "down";
    }
    return '<div class="kpi-delta ' + cls + '">' + (up ? "▲ " : "▼ ") + Math.abs(value).toFixed(1) + "% vs previous day</div>";
}

function renderKpis(s, d) {
    const kpis = [
        { label: "Active Time",       value: fmtDuration(s.activeSeconds),            delta: deltaHtml(d.activeSeconds, true) },
        { label: "Idle Time",         value: fmtDuration(s.idleSeconds),              delta: deltaHtml(d.idleSeconds, false) },
        { label: "Activity Score",    value: s.activityScore == null ? "—" : s.activityScore, delta: deltaHtml(d.activityScore, true) },
        { label: "Applications Used", value: s.applicationsUsed,                      delta: deltaHtml(d.applicationsUsed, null) },
        { label: "Alerts Triggered",  value: s.alertsTriggered,                       delta: deltaHtml(d.alertsTriggered, false) }
    ];
    document.getElementById("kpiGrid").innerHTML = kpis.map(k => `
        <div class="kpi">
            <div class="kpi-label">${k.label}</div>
            <div class="kpi-value">${k.value}</div>
            ${k.delta}
        </div>
    `).join("");
}

function renderSummary(s) {
    const items = [
        { label: "Mouse Clicks",       value: "—", sub: "needs agent input tracking" },
        { label: "Keystrokes",         value: "—", sub: "counts only, planned" },
        { label: "App Sessions",       value: s.sessionCount, sub: "separate stretches of use" },
        { label: "Applications Used",  value: s.applicationsUsed, sub: "distinct applications" },
        { label: "Idle Periods",       value: s.idlePeriods, sub: "times the user went idle" },
        { label: "Longest Idle",       value: fmtDuration(s.longestIdleSeconds), sub: "single stretch" }
    ];
    document.getElementById("summaryGrid").innerHTML = items.map(i => `
        <div class="summary-item">
            <div class="kpi-label">${i.label}</div>
            <div class="kpi-value">${i.value}</div>
            <div class="kpi-sub">${i.sub}</div>
        </div>
    `).join("");
}

function renderUsage(usage) {
    const body = document.getElementById("usageTableBody");
    if (usage.length === 0) {
        body.innerHTML = '<tr><td colspan="3" class="empty-state">No application activity on this day.</td></tr>';
        return;
    }
    body.innerHTML = usage.map(u => `
        <tr>
            <td><div class="cell-ellipsis" title="${escapeHtml(u.application)}">${escapeHtml(u.application)}</div>${categoryTag(u.category)}</td>
            <td class="nowrap">${fmtDuration(u.seconds)}</td>
            <td class="nowrap">${u.percent.toFixed(1)}%</td>
        </tr>
    `).join("");
}

function renderLogs(logs) {
    const body = document.getElementById("logsTableBody");
    if (logs.length === 0) {
        body.innerHTML = '<tr><td colspan="5" class="empty-state">No activity recorded on this day.</td></tr>';
        return;
    }
    body.innerHTML = logs.map(l => `
        <tr>
            <td class="nowrap">${escapeHtml(fmtClock(l.start))}</td>
            <td><div class="cell-ellipsis" title="${escapeHtml(l.application)}">${escapeHtml(l.application)}</div></td>
            <td>${statusChip(l.status)}</td>
            <td>${categoryTag(l.status === "IDLE" ? "IDLE" : l.category)}</td>
            <td class="nowrap">${l.end ? fmtDuration(l.durationSeconds) : "<em>now</em>"}</td>
        </tr>
    `).join("");
}

function renderScreenshots(shots, date) {
    const grid = document.getElementById("shotGrid");

    // The page refreshes every 10s; only rebuild the strip (and re-fetch images) when something changed.
    const key = shots.map(s => s.id).join(",");
    if (key === lastShotKey) return;
    lastShotKey = key;

    if (shots.length === 0) {
        grid.innerHTML = '<div class="empty-note">No screenshots for this day yet. Click "Take Screenshot" above to capture one from this computer.</div>';
        return;
    }

    grid.innerHTML = shots.map(s => `
        <a class="thumb" href="screenshots.html?systemId=${systemId}&date=${date}" style="text-decoration:none;color:inherit;">
            <img class="thumb-img" data-shot-id="${s.id}" alt="Screenshot">
            <div class="thumb-meta">
                <div class="t">${escapeHtml(fmtClock(s.takenAt))}</div>
                <div class="a" title="${escapeHtml(s.application || "")}">${escapeHtml(s.application || "—")}</div>
            </div>
        </a>
    `).join("");

    grid.querySelectorAll("img[data-shot-id]").forEach(img => {
        fetchImageUrl(Number(img.dataset.shotId))
            .then(url => { img.src = url; })
            .catch(() => { img.alt = "Image unavailable"; });
    });
}
