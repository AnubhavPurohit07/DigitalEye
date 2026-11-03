// Shared helpers for every dashboard page (loaded after config.js and auth.js).
// Depends on: API_BASE_URL (config.js) and getSession / requireAdminSession / logout (auth.js).

// ---------------------------------------------------------------- escaping

// Window titles and app names come from the monitored computer, so they are
// untrusted text. Anything that goes into innerHTML MUST pass through this.
function escapeHtml(str) {
    const div = document.createElement("div");
    div.textContent = str == null ? "" : String(str);
    return div.innerHTML;
}

// ------------------------------------------------------------- API calls

// The JWT lives in the saved login session (see auth.js).
function authToken() {
    const session = getSession();
    return session && session.token ? session.token : "";
}

async function apiCall(method, path, body) {
    const headers = { "Authorization": "Bearer " + authToken() };
    if (body !== undefined) {
        headers["Content-Type"] = "application/json";
    }

    let response;
    try {
        response = await fetch(API_BASE_URL + path, {
            method: method,
            headers: headers,
            body: body !== undefined ? JSON.stringify(body) : undefined
        });
    } catch (networkError) {
        throw new Error("Cannot reach the backend at " + API_BASE_URL + ". Is it running?");
    }

    if (response.status === 401 || response.status === 403) {
        logout(); // token missing, expired, or not an admin - back to the login page
        throw new Error("Your session has ended. Please sign in again.");
    }

    if (!response.ok) {
        let message = "Request failed (HTTP " + response.status + ")";
        try {
            const data = await response.json();
            if (data && data.message) {
                message = data.message;
            }
        } catch (ignored) { /* body wasn't JSON - keep the generic message */ }
        throw new Error(message);
    }

    return response.json();
}

function apiGet(path) {
    return apiCall("GET", path);
}

function apiSend(method, path, body) {
    return apiCall(method, path, body === undefined ? {} : body);
}

// Screenshots are protected by the JWT, and an <img src> can't send an
// Authorization header - so each image is fetched with fetch(), turned into a
// temporary blob: URL, and cached (newest ~80 kept) so re-opening is instant.
const _imageCache = new Map();
const IMAGE_CACHE_LIMIT = 80;

async function fetchImageUrl(screenshotId) {
    if (_imageCache.has(screenshotId)) {
        const cached = _imageCache.get(screenshotId);
        _imageCache.delete(screenshotId); // re-insert to mark as recently used
        _imageCache.set(screenshotId, cached);
        return cached;
    }

    const response = await fetch(API_BASE_URL + "/api/admin/screenshots/" + screenshotId + "/image", {
        headers: { "Authorization": "Bearer " + authToken() }
    });
    if (!response.ok) {
        throw new Error("Image not available (HTTP " + response.status + ")");
    }
    const url = URL.createObjectURL(await response.blob());

    _imageCache.set(screenshotId, url);
    if (_imageCache.size > IMAGE_CACHE_LIMIT) {
        const oldestKey = _imageCache.keys().next().value;
        URL.revokeObjectURL(_imageCache.get(oldestKey));
        _imageCache.delete(oldestKey);
    }
    return url;
}

// ------------------------------------------------- on-demand screenshots

// How long to wait for the agent to answer, and how often to look. (An object, so a test can shorten it.)
const SCREENSHOT_TIMING = { waitMs: 30000, pollMs: 1500 };

async function newestScreenshotId(systemId) {
    const list = await apiGet("/api/admin/screenshots?systemId=" + encodeURIComponent(systemId) + "&limit=1");
    return list.length ? Number(list[0].id) : 0;
}

// Asks the agent on that computer for a screenshot and waits until it has arrived.
// The agent checks for requests every few seconds, so this normally takes 3-8 seconds.
// Resolves with the new screenshot's id; rejects with a message that is fine to show the admin.
async function takeScreenshotNow(systemId) {
    const before = await newestScreenshotId(systemId);
    await apiSend("POST", "/api/admin/systems/" + encodeURIComponent(systemId) + "/screenshot-request");

    const deadline = Date.now() + SCREENSHOT_TIMING.waitMs;
    while (Date.now() < deadline) {
        await new Promise(resolve => setTimeout(resolve, SCREENSHOT_TIMING.pollMs));
        try {
            const newest = await newestScreenshotId(systemId);
            if (newest > before) return newest;
        } catch (ignored) { /* a hiccup while waiting - keep trying until the time is up */ }
    }
    throw new Error("The agent didn't answer within " + Math.round(SCREENSHOT_TIMING.waitMs / 1000) +
        " seconds. Check that it is running on that computer.");
}

// Makes a "Take Screenshot" button work. getSystemId() says which computer to ask;
// onDone() runs once the new screenshot has arrived (to refresh the page).
// While waiting, the button is disabled and button.dataset.busy is set.
function wireScreenshotButton(button, getSystemId, onDone) {
    const idleLabel = button.innerHTML;
    button.addEventListener("click", async function () {
        const systemId = getSystemId();
        if (!systemId || button.dataset.busy) return;

        button.dataset.busy = "1";
        button.disabled = true;
        button.innerHTML = "&#9203; Waiting for the agent...";
        try {
            await takeScreenshotNow(systemId);
            toast("Screenshot received.");
            if (onDone) await onDone();
        } catch (err) {
            toast(err.message);
        } finally {
            delete button.dataset.busy;
            button.disabled = false;
            button.innerHTML = idleLabel;
        }
    });
}

// --------------------------------------------------------------- formatting

function pad2(n) {
    return String(n).padStart(2, "0");
}

// Today's date as YYYY-MM-DD in the browser's own time zone.
function todayISO() {
    const d = new Date();
    return d.getFullYear() + "-" + pad2(d.getMonth() + 1) + "-" + pad2(d.getDate());
}

function formatToday() {
    const d = new Date();
    return d.toLocaleDateString(undefined, { weekday: "short", day: "2-digit", month: "short", year: "numeric" }) +
        ", " + d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
}

function fmtDuration(totalSeconds) {
    const s = Math.max(0, Math.floor(totalSeconds || 0));
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = s % 60;
    if (h > 0) return h + "h " + pad2(m) + "m";
    if (m > 0) return m + "m " + pad2(sec) + "s";
    return sec + "s";
}

function fmtClock(iso) {
    if (!iso) return "—";
    return new Date(iso).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

function fmtDateTime(iso) {
    if (!iso) return "—";
    const d = new Date(iso);
    return d.toLocaleDateString(undefined, { day: "2-digit", month: "short" }) + ", " +
        d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

function fmtDay(iso) {
    if (!iso) return "—";
    return new Date(iso).toLocaleDateString(undefined, { day: "2-digit", month: "short", year: "numeric" });
}

function hourLabel(h) {
    if (h === 0) return "12a";
    if (h < 12) return h + "a";
    if (h === 12) return "12p";
    return (h - 12) + "p";
}

function hourLabelLong(h) {
    const suffix = h < 12 ? "AM" : "PM";
    const twelve = h % 12 === 0 ? 12 : h % 12;
    return twelve + ":00 " + suffix;
}

// ----------------------------------------------------------- categories & chips

// The six categories, in the order they are listed everywhere (legend, filters, tooltips).
const CATEGORY_ORDER = ["PRODUCTIVE", "NON_PRODUCTIVE", "TROUBLESHOOTING", "CREATIVE", "OTHER", "IDLE"];

const CATEGORY_META = {
    PRODUCTIVE:      { label: "Productive",      color: "#22c55e", hint: "Work tools, work websites, learning" },
    NON_PRODUCTIVE:  { label: "Non-Productive",  color: "#ef4444", hint: "Games, streaming, social media, shopping" },
    TROUBLESHOOTING: { label: "Troubleshooting", color: "#3b82f6", hint: "Settings, Task Manager, Device Manager, support" },
    CREATIVE:        { label: "Creative",        color: "#a855f7", hint: "Design, photo, video and audio editing" },
    OTHER:           { label: "Uncategorized",   color: "#fbbf24", hint: "No rule matched this activity" },
    IDLE:            { label: "Idle",            color: "#cbd5e1", hint: "No keyboard or mouse activity" }
};
// Rows saved before the classification upgrade can still say ENTERTAINMENT.
CATEGORY_META.ENTERTAINMENT = CATEGORY_META.NON_PRODUCTIVE;

// Fills every <div data-category-legend> with one coloured dot per category.
function renderCategoryLegends() {
    document.querySelectorAll("[data-category-legend]").forEach(function (el) {
        el.innerHTML = CATEGORY_ORDER.map(function (key) {
            const meta = CATEGORY_META[key];
            return '<span title="' + escapeHtml(meta.hint) + '"><span class="dot" style="background:' + meta.color + '"></span>' +
                escapeHtml(meta.label) + "</span>";
        }).join("");
    });
}

function categoryTag(category) {
    const meta = CATEGORY_META[category] || CATEGORY_META.OTHER;
    return '<span class="cat-tag"><span class="dot" style="background:' + meta.color + '"></span>' +
        escapeHtml(meta.label) + '</span>';
}

function statusChip(status) {
    return status === "IDLE"
        ? '<span class="status-chip idle">Idle</span>'
        : '<span class="status-chip active">Active</span>';
}

function scorePill(score) {
    if (score == null) {
        return '<span class="score-pill none" title="Needs about a minute of tracked activity">—</span>';
    }
    const cls = score >= 70 ? "good" : score >= 40 ? "mid" : "low";
    return '<span class="score-pill ' + cls + '">' + score + '</span>';
}

function severityClass(severity) {
    return severity === "HIGH" ? "high" : "medium";
}

function severityTag(severity) {
    const label = severity === "HIGH" ? "High" : severity === "LOW" ? "Low" : "Medium";
    const cls = severity === "HIGH" ? "high" : severity === "LOW" ? "low" : "medium";
    return '<span class="sev-tag ' + cls + '">' + label + '</span>';
}

// ----------------------------------------------------------------- charts

// Stacked hourly bars. Each bar's height is how much of that hour was tracked (a full
// hour = full height); its colored slices are the categories. Shows 8AM-6PM by default
// and stretches to cover any activity outside those hours.
function renderHourlyChart(chartEl, labelsEl, hourly) {
    const secondsOf = (b, key) => (b.seconds && b.seconds[key]) || 0;
    const total = b => CATEGORY_ORDER.reduce((sum, key) => sum + secondsOf(b, key), 0);

    let first = -1;
    let last = -1;
    hourly.forEach((b, i) => {
        if (total(b) > 0) {
            if (first === -1) first = i;
            last = i;
        }
    });

    let from = 8;
    let to = 18;
    if (first !== -1) {
        from = Math.min(from, first);
        to = Math.max(to, last);
    }

    const cols = [];
    const labels = [];
    for (let h = from; h <= to; h++) {
        const b = hourly[h];
        const t = total(b);
        // First in the list = top of the bar, so the order is the legend order read bottom-up.
        const segments = CATEGORY_ORDER.slice().reverse()
            .map(key => [key, secondsOf(b, key)])
            .filter(s => s[1] > 0);

        const stackPct = t === 0 ? 0 : Math.max(4, Math.min(100, t / 3600 * 100));
        const segHtml = segments.map(s =>
            '<div class="hchart-seg ' + s[0] + '" style="height:' + (s[1] / t * 100).toFixed(2) +
            '%;background:' + CATEGORY_META[s[0]].color + '"></div>'
        ).join("");

        const tip = hourLabelLong(h) + (segments.length === 0 ? ": no activity" : " — " +
            segments.slice().reverse().map(s => CATEGORY_META[s[0]].label + " " + fmtDuration(s[1])).join(", "));

        cols.push('<div class="hchart-col" title="' + escapeHtml(tip) + '">' +
            '<div class="hchart-stack" style="height:' + stackPct.toFixed(2) + '%">' + segHtml + '</div></div>');
        labels.push("<span>" + hourLabel(h) + "</span>");
    }

    chartEl.innerHTML = first === -1
        ? '<div class="hchart-empty">No activity tracked on this day yet</div>'
        : cols.join("");
    labelsEl.innerHTML = labels.join("");
}

// A donut drawn with SVG circles: each slice is a stroke-dasharray segment of one circle's outline.
function renderDonut(svgEl, legendEl, slices, centerValue) {
    const radius = 50;
    const circumference = 2 * Math.PI * radius;
    const total = slices.reduce((sum, s) => sum + s.seconds, 0);

    let svg = '<circle cx="70" cy="70" r="50" fill="none" stroke="#e2e8f0" stroke-width="18"></circle>';
    if (total > 0) {
        let offset = 0;
        slices.forEach(s => {
            if (s.seconds <= 0) return;
            const meta = CATEGORY_META[s.category] || CATEGORY_META.OTHER;
            const length = s.seconds / total * circumference;
            svg += '<circle cx="70" cy="70" r="50" fill="none" stroke="' + meta.color + '" stroke-width="18"' +
                ' stroke-dasharray="' + length.toFixed(2) + " " + (circumference - length).toFixed(2) + '"' +
                ' stroke-dashoffset="' + (-offset).toFixed(2) + '" transform="rotate(-90 70 70)"></circle>';
            offset += length;
        });
    }
    svg += '<text x="70" y="68" text-anchor="middle" font-size="24" font-weight="800" fill="#0f172a">' +
        escapeHtml(centerValue) + '</text>' +
        '<text x="70" y="85" text-anchor="middle" font-size="10" fill="#64748b">activity score</text>';
    svgEl.innerHTML = svg;

    legendEl.innerHTML = slices.map(s => {
        const meta = CATEGORY_META[s.category] || CATEGORY_META.OTHER;
        return '<div class="row"><span class="dot" style="background:' + meta.color + '"></span>' +
            escapeHtml(meta.label) + '<span class="pct">' + s.percent.toFixed(1) + '%</span>' +
            '<span class="muted">' + fmtDuration(s.seconds) + '</span></div>';
    }).join("");
}

// ------------------------------------------------------------------- toast

function toast(message) {
    let el = document.getElementById("toast");
    if (!el) {
        el = document.createElement("div");
        el.id = "toast";
        el.className = "toast";
        document.body.appendChild(el);
    }
    el.textContent = message;
    el.classList.add("show");
    clearTimeout(el._timer);
    el._timer = setTimeout(() => el.classList.remove("show"), 3200);
}

// ----------------------------------------------------------- page chrome

const NAV_ITEMS = [
    { key: "dashboard",   icon: "&#8962;",   label: "Dashboard",        href: "dashboard.html" },
    { key: "systems",     icon: "&#128421;", label: "Client Systems",   href: "dashboard.html#systems" },
    { key: "logs",        icon: "&#128203;", label: "Activity Logs",    href: "activity-logs.html" },
    { key: "screenshots", icon: "&#128247;", label: "Screenshots",      href: "screenshots.html" },
    { key: "alerts",      icon: "&#128276;", label: "Alerts &amp; Rules", href: "alerts.html" },
    { key: "reports",     icon: "&#128202;", label: "Reports",          soon: true },
    { key: "users",       icon: "&#128101;", label: "User Management",  soon: true },
    { key: "settings",    icon: "&#9881;",   label: "System Settings",  soon: true }
];

function renderSidebar(activeKey, session) {
    const sidebar = document.getElementById("sidebar");
    if (!sidebar) return;

    const items = NAV_ITEMS.map(item => {
        const cls = "nav-item" + (item.key === activeKey ? " active" : "") + (item.soon ? " disabled" : "");
        const attrs = item.soon
            ? ' title="Coming in a later milestone" data-soon="1"'
            : ' data-href="' + item.href + '"';
        return '<div class="' + cls + '"' + attrs + '><span class="nav-icon">' + item.icon + "</span> " + item.label + "</div>";
    }).join("");

    sidebar.innerHTML =
        '<div class="sidebar-brand"><div class="logo">&#128065;</div><div>' +
        '<div class="name">DigitalEye</div><div class="tag">Monitor &middot; Analyze &middot; Empower</div></div></div>' +
        '<nav class="nav-group">' + items + '</nav>' +
        '<div class="sidebar-footer"><div class="sidebar-user">' +
        '<div class="avatar">' + escapeHtml(session.username.charAt(0).toUpperCase()) + '</div>' +
        '<div><div class="who-name">' + escapeHtml(session.username) + '</div><div class="who-role">Administrator</div></div></div>' +
        '<div class="logout-link" id="logoutBtn"><span class="nav-icon">&#8618;</span> Logout</div>' +
        '<div class="sidebar-version">DigitalEye v1.0<br>A Smarter Way to Monitor Digital Workspaces</div></div>';

    sidebar.addEventListener("click", function (event) {
        const item = event.target.closest(".nav-item");
        if (!item) return;
        if (item.dataset.soon) {
            toast("That page arrives in a later milestone.");
        } else if (item.dataset.href) {
            if (item.dataset.href === "dashboard.html#systems" && location.pathname.endsWith("dashboard.html")) {
                const target = document.getElementById("systems");
                if (target) target.scrollIntoView({ behavior: "smooth" });
            } else {
                location.href = item.dataset.href;
            }
        }
    });
    document.getElementById("logoutBtn").addEventListener("click", logout);
}

async function refreshAlertBadge() {
    const badge = document.getElementById("alertBadgeCount");
    if (!badge) return;
    try {
        const summary = await apiGet("/api/admin/alerts/summary");
        badge.textContent = summary.unacknowledged;
    } catch (ignored) { /* the page's own loader reports connection problems */ }
}

// Every page calls this first. Returns the session, or null if the user was sent to the login page.
function initPage(activeKey) {
    const session = requireAdminSession();
    if (!session) return null;

    renderSidebar(activeKey, session);

    const pill = document.getElementById("todayPill");
    if (pill) pill.textContent = "📅 " + formatToday();

    renderCategoryLegends();
    refreshAlertBadge();
    setInterval(refreshAlertBadge, 15000);
    return session;
}
