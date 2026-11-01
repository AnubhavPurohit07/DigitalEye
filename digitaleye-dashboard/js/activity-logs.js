// Activity Logs page: filterable, paged list from GET /api/admin/logs.

const PAGE_SIZE = 25;
let currentPage = 0;

document.addEventListener("DOMContentLoaded", async function () {
    const session = initPage("logs");
    if (!session) return;

    await loadSystemOptions();

    const wantedSystem = new URLSearchParams(window.location.search).get("systemId");
    if (wantedSystem) {
        document.getElementById("fSystem").value = wantedSystem;
    }

    document.getElementById("filterForm").addEventListener("submit", function (event) {
        event.preventDefault();
        currentPage = 0;
        loadLogs();
    });
    document.getElementById("resetBtn").addEventListener("click", function () {
        document.getElementById("filterForm").reset();
        currentPage = 0;
        loadLogs();
    });
    document.getElementById("refreshBtn").addEventListener("click", loadLogs);
    document.getElementById("cTest").addEventListener("click", testTitle);
    document.getElementById("cTitle").addEventListener("keydown", function (event) {
        if (event.key === "Enter") {
            event.preventDefault();
            testTitle();
        }
    });
    document.getElementById("cReapply").addEventListener("click", reapplyRules);
    document.getElementById("prevBtn").addEventListener("click", function () {
        currentPage = Math.max(0, currentPage - 1);
        loadLogs();
    });
    document.getElementById("nextBtn").addEventListener("click", function () {
        currentPage += 1;
        loadLogs();
    });

    await loadLogs();
});

async function loadSystemOptions() {
    try {
        const systems = await apiGet("/api/admin/systems");
        const select = document.getElementById("fSystem");
        systems.forEach(s => {
            const option = document.createElement("option");
            option.value = s.id;
            option.textContent = s.systemName;
            select.appendChild(option);
        });
    } catch (err) {
        toast("Could not load the systems list: " + err.message);
    }
}

function buildQuery() {
    const params = new URLSearchParams();
    const fields = {
        systemId: "fSystem", category: "fCategory", status: "fStatus",
        q: "fQuery", from: "fFrom", to: "fTo"
    };
    Object.keys(fields).forEach(name => {
        const value = document.getElementById(fields[name]).value.trim();
        if (value) params.set(name, value);
    });
    params.set("page", currentPage);
    params.set("size", PAGE_SIZE);
    return params.toString();
}

async function loadLogs() {
    const body = document.getElementById("logsBody");
    try {
        const result = await apiGet("/api/admin/logs?" + buildQuery());

        // Filtering can leave fewer pages than before - step back if we are past the end.
        if (result.items.length === 0 && result.total > 0 && currentPage > 0) {
            currentPage = Math.max(0, Math.ceil(result.total / PAGE_SIZE) - 1);
            return loadLogs();
        }

        if (result.items.length === 0) {
            body.innerHTML = '<tr><td colspan="6" class="empty-state">No activity matches these filters.</td></tr>';
        } else {
            body.innerHTML = result.items.map(row => `
                <tr>
                    <td class="nowrap">${escapeHtml(fmtDateTime(row.start))}</td>
                    <td>${escapeHtml(row.systemName)}</td>
                    <td><div class="cell-ellipsis" style="max-width:340px" title="${escapeHtml(row.application)}">${escapeHtml(row.application)}</div></td>
                    <td>${statusChip(row.status)}</td>
                    <td>${categoryTag(row.status === "IDLE" ? "IDLE" : row.category)}</td>
                    <td class="nowrap">${row.open ? "<em>in progress</em>" : fmtDuration(row.durationSeconds)}</td>
                </tr>
            `).join("");
        }
        updatePager(result);
    } catch (err) {
        body.innerHTML = '<tr><td colspan="6" class="empty-state">Could not load logs: ' + escapeHtml(err.message) + '</td></tr>';
    }
}

function updatePager(result) {
    const first = result.total === 0 ? 0 : result.page * result.size + 1;
    const last = result.page * result.size + result.items.length;
    document.getElementById("pagerInfo").textContent =
        "Showing " + first + "–" + last + " of " + result.total + " records";
    document.getElementById("prevBtn").disabled = result.page === 0;
    document.getElementById("nextBtn").disabled = (result.page + 1) * result.size >= result.total;
}

// ------------------------------------------------------------ classification tools

async function testTitle() {
    const title = document.getElementById("cTitle").value.trim();
    const box = document.getElementById("cResult");
    if (!title) {
        toast("Type or paste a window title first.");
        return;
    }
    try {
        const r = await apiGet("/api/admin/classification/test?title=" + encodeURIComponent(title));
        box.innerHTML = categoryTag(r.category) + ' <strong>' + escapeHtml(r.label) + '</strong>' +
            '<div class="classify-reason">' + escapeHtml(r.reason) + '</div>';
        box.style.display = "block";
    } catch (err) {
        box.innerHTML = "Could not check this title: " + escapeHtml(err.message);
        box.style.display = "block";
    }
}

async function reapplyRules() {
    const ok = window.confirm("Re-classify ALL stored activity with the current rules?\n\n" +
        "Only the category of each record changes. Nothing is deleted. This can take a little while on a large history.");
    if (!ok) return;

    const button = document.getElementById("cReapply");
    button.disabled = true;
    const original = button.innerHTML;
    button.textContent = "Re-applying...";
    try {
        const r = await apiSend("POST", "/api/admin/classification/reapply");
        toast("Checked " + r.rowsChecked + " records, updated " + r.rowsUpdated + ".");
        currentPage = 0;
        await loadLogs();
    } catch (err) {
        toast("Could not re-apply the rules: " + err.message);
    } finally {
        button.disabled = false;
        button.innerHTML = original;
    }
}
