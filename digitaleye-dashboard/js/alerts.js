// Alerts & Rules page. Two tabs:
//   Alerts - what has been triggered (filter, page, acknowledge)
//   Rules  - the admin-editable rules that trigger them (add, edit, enable/disable, delete)

const ALERT_PAGE_SIZE = 20;
let alertPage = 0;
let rules = [];
let editingRuleId = null;

document.addEventListener("DOMContentLoaded", async function () {
    const session = initPage("alerts");
    if (!session) return;

    // ----- tabs
    document.querySelectorAll(".tab").forEach(tab => {
        tab.addEventListener("click", () => showTab(tab.dataset.tab));
    });
    if (window.location.hash === "#rules") showTab("rules");

    // ----- alerts tab
    ["aState", "aSeverity", "aSystem"].forEach(id => {
        document.getElementById(id).addEventListener("change", () => { alertPage = 0; loadAlerts(); });
    });
    document.getElementById("alertPrev").addEventListener("click", () => { alertPage = Math.max(0, alertPage - 1); loadAlerts(); });
    document.getElementById("alertNext").addEventListener("click", () => { alertPage += 1; loadAlerts(); });
    document.getElementById("ackAllBtn").addEventListener("click", acknowledgeAll);
    document.getElementById("alertsBody").addEventListener("click", function (event) {
        const button = event.target.closest("[data-ack-id]");
        if (button) acknowledgeOne(button.dataset.ackId);
    });

    // ----- rules tab
    document.getElementById("newRuleBtn").addEventListener("click", () => openRuleForm(null));
    document.getElementById("ruleCancelBtn").addEventListener("click", closeRuleForm);
    document.getElementById("rType").addEventListener("change", syncRuleFields);
    document.getElementById("ruleForm").addEventListener("submit", saveRule);
    document.getElementById("rulesBody").addEventListener("click", onRulesClick);
    document.getElementById("rulesBody").addEventListener("change", onRulesToggle);

    await loadSystemOptions();
    await Promise.all([loadAlerts(), loadRules()]);
    setInterval(() => {
        // Keep the alerts list fresh, but don't interrupt someone filling in the rule form.
        if (document.getElementById("tab-alerts").style.display !== "none") loadAlerts();
    }, 10000);
});

function showTab(name) {
    document.querySelectorAll(".tab").forEach(t => t.classList.toggle("active", t.dataset.tab === name));
    document.getElementById("tab-alerts").style.display = name === "alerts" ? "" : "none";
    document.getElementById("tab-rules").style.display = name === "rules" ? "" : "none";
}

// =============================================================== alerts tab

async function loadSystemOptions() {
    try {
        const systems = await apiGet("/api/admin/systems");
        const select = document.getElementById("aSystem");
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

function alertQuery() {
    const params = new URLSearchParams();
    const state = document.getElementById("aState").value;
    if (state === "open") params.set("acknowledged", "false");
    if (state === "acked") params.set("acknowledged", "true");
    const severity = document.getElementById("aSeverity").value;
    if (severity) params.set("severity", severity);
    const system = document.getElementById("aSystem").value;
    if (system) params.set("systemId", system);
    params.set("page", alertPage);
    params.set("size", ALERT_PAGE_SIZE);
    return params.toString();
}

async function loadAlerts() {
    const body = document.getElementById("alertsBody");
    try {
        const result = await apiGet("/api/admin/alerts?" + alertQuery());

        if (result.items.length === 0 && result.total > 0 && alertPage > 0) {
            alertPage = Math.max(0, Math.ceil(result.total / ALERT_PAGE_SIZE) - 1);
            return loadAlerts();
        }

        if (result.items.length === 0) {
            body.innerHTML = '<tr><td colspan="5" class="empty-state">No alerts here. &#127881;</td></tr>';
        } else {
            body.innerHTML = result.items.map(a => `
                <tr class="${a.acknowledged ? "acked" : ""}">
                    <td>${severityTag(a.severity)}</td>
                    <td>
                        <div>${escapeHtml(a.message)}</div>
                        ${a.application ? '<div class="muted cell-ellipsis" style="max-width:420px" title="' + escapeHtml(a.application) + '">' + escapeHtml(a.application) + "</div>" : ""}
                    </td>
                    <td>${escapeHtml(a.systemName)}</td>
                    <td class="nowrap">${escapeHtml(fmtDateTime(a.createdAt))}</td>
                    <td class="nowrap">${a.acknowledged
                        ? '<span class="muted">&#10003; Acknowledged</span>'
                        : '<button class="btn small" data-ack-id="' + a.id + '">Acknowledge</button>'}</td>
                </tr>
            `).join("");
        }

        const first = result.total === 0 ? 0 : result.page * result.size + 1;
        const last = result.page * result.size + result.items.length;
        document.getElementById("alertPagerInfo").textContent = "Showing " + first + "–" + last + " of " + result.total + " alerts";
        document.getElementById("alertPrev").disabled = result.page === 0;
        document.getElementById("alertNext").disabled = (result.page + 1) * result.size >= result.total;
    } catch (err) {
        body.innerHTML = '<tr><td colspan="5" class="empty-state">Could not load alerts: ' + escapeHtml(err.message) + "</td></tr>";
    }
}

async function acknowledgeOne(id) {
    try {
        await apiSend("POST", "/api/admin/alerts/" + encodeURIComponent(id) + "/acknowledge");
        await loadAlerts();
        refreshAlertBadge();
    } catch (err) {
        toast("Could not acknowledge: " + err.message);
    }
}

async function acknowledgeAll() {
    try {
        const result = await apiSend("POST", "/api/admin/alerts/acknowledge-all");
        toast(result.acknowledged + " alert(s) acknowledged.");
        await loadAlerts();
        refreshAlertBadge();
    } catch (err) {
        toast("Could not acknowledge: " + err.message);
    }
}

// ================================================================ rules tab

async function loadRules() {
    const body = document.getElementById("rulesBody");
    try {
        rules = await apiGet("/api/admin/alert-rules");
        if (rules.length === 0) {
            body.innerHTML = '<tr><td colspan="6" class="empty-state">No rules yet - click "Add rule".</td></tr>';
            return;
        }
        body.innerHTML = rules.map(r => `
            <tr>
                <td>${escapeHtml(r.name)}</td>
                <td class="nowrap">${escapeHtml(ruleTypeLabel(r.ruleType))}</td>
                <td>${escapeHtml(ruleCondition(r))}</td>
                <td>${severityTag(r.severity)}</td>
                <td><label class="switch"><input type="checkbox" data-toggle-id="${r.id}" ${r.enabled ? "checked" : ""}> ${r.enabled ? "On" : "Off"}</label></td>
                <td class="nowrap">
                    <button class="btn small" data-edit-id="${r.id}">Edit</button>
                    <button class="btn small danger" data-delete-id="${r.id}">Delete</button>
                </td>
            </tr>
        `).join("");
    } catch (err) {
        body.innerHTML = '<tr><td colspan="6" class="empty-state">Could not load rules: ' + escapeHtml(err.message) + "</td></tr>";
    }
}

function ruleTypeLabel(type) {
    return { IDLE_THRESHOLD: "Idle time", UNAUTHORIZED_APP: "Unauthorized app", OFF_HOURS: "Off-hours activity" }[type] || type;
}

function ruleCondition(r) {
    if (r.ruleType === "IDLE_THRESHOLD") return "Idle for " + r.thresholdMinutes + "+ minutes";
    if (r.ruleType === "UNAUTHORIZED_APP") return 'Application name contains "' + r.keyword + '"';
    if (r.ruleType === "OFF_HOURS") return "Outside " + String(r.startHour).padStart(2, "0") + ":00–" + String(r.endHour).padStart(2, "0") + ":00";
    return "";
}

function onRulesClick(event) {
    const edit = event.target.closest("[data-edit-id]");
    if (edit) {
        openRuleForm(rules.find(r => String(r.id) === edit.dataset.editId));
        return;
    }
    const del = event.target.closest("[data-delete-id]");
    if (del) deleteRule(del.dataset.deleteId);
}

async function onRulesToggle(event) {
    const box = event.target.closest("[data-toggle-id]");
    if (!box) return;
    const rule = rules.find(r => String(r.id) === box.dataset.toggleId);
    if (!rule) return;
    try {
        await apiSend("PUT", "/api/admin/alert-rules/" + rule.id, Object.assign({}, rule, { enabled: box.checked }));
        await loadRules();
    } catch (err) {
        toast("Could not update the rule: " + err.message);
        await loadRules();
    }
}

async function deleteRule(id) {
    const rule = rules.find(r => String(r.id) === String(id));
    if (!rule || !confirm('Delete the rule "' + rule.name + '"? Alerts it already raised are kept.')) return;
    try {
        await apiSend("DELETE", "/api/admin/alert-rules/" + encodeURIComponent(id));
        await loadRules();
    } catch (err) {
        toast("Could not delete: " + err.message);
    }
}

function openRuleForm(rule) {
    editingRuleId = rule ? rule.id : null;
    document.getElementById("ruleFormTitle").textContent = rule ? "Edit rule" : "New rule";
    document.getElementById("rName").value = rule ? rule.name : "";
    document.getElementById("rType").value = rule ? rule.ruleType : "UNAUTHORIZED_APP";
    document.getElementById("rSeverity").value = rule ? rule.severity : "MEDIUM";
    document.getElementById("rKeyword").value = rule && rule.keyword ? rule.keyword : "";
    document.getElementById("rMinutes").value = rule && rule.thresholdMinutes ? rule.thresholdMinutes : 20;
    document.getElementById("rStart").value = rule && rule.startHour != null ? rule.startHour : 9;
    document.getElementById("rEnd").value = rule && rule.endHour != null ? rule.endHour : 18;
    document.getElementById("rEnabled").checked = rule ? rule.enabled : true;
    document.getElementById("ruleError").style.display = "none";

    syncRuleFields();
    document.getElementById("ruleFormPanel").style.display = "";
    document.getElementById("ruleFormPanel").scrollIntoView({ behavior: "smooth" });
}

function closeRuleForm() {
    document.getElementById("ruleFormPanel").style.display = "none";
    editingRuleId = null;
}

// Only show the inputs that matter for the chosen rule type.
function syncRuleFields() {
    const type = document.getElementById("rType").value;
    document.querySelectorAll("#ruleForm [data-for]").forEach(field => {
        field.style.display = field.dataset.for === type ? "" : "none";
    });
}

async function saveRule(event) {
    event.preventDefault();
    const errorBox = document.getElementById("ruleError");
    errorBox.style.display = "none";

    const type = document.getElementById("rType").value;
    const rule = {
        name: document.getElementById("rName").value.trim(),
        ruleType: type,
        severity: document.getElementById("rSeverity").value,
        enabled: document.getElementById("rEnabled").checked,
        keyword: type === "UNAUTHORIZED_APP" ? document.getElementById("rKeyword").value.trim() : null,
        thresholdMinutes: type === "IDLE_THRESHOLD" ? parseInt(document.getElementById("rMinutes").value, 10) : null,
        startHour: type === "OFF_HOURS" ? parseInt(document.getElementById("rStart").value, 10) : null,
        endHour: type === "OFF_HOURS" ? parseInt(document.getElementById("rEnd").value, 10) : null
    };

    const saveBtn = document.getElementById("ruleSaveBtn");
    saveBtn.disabled = true;
    try {
        if (editingRuleId == null) {
            await apiSend("POST", "/api/admin/alert-rules", rule);
        } else {
            await apiSend("PUT", "/api/admin/alert-rules/" + editingRuleId, rule);
        }
        closeRuleForm();
        toast("Rule saved.");
        await loadRules();
    } catch (err) {
        errorBox.textContent = err.message;
        errorBox.style.display = "block";
    } finally {
        saveBtn.disabled = false;
    }
}
