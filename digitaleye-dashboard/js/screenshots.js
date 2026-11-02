// Screenshots page: browse one system's screenshots for one day, open any of them
// full size, or replay the whole day in chronological order (play/pause, speed, scrub).

const THUMBS_PER_PAGE = 24;

let shots = [];          // oldest first - the order a replay plays them in
let thumbOrder = [];     // indexes into shots, newest first - the order the grid shows them in
let shownThumbs = 0;

let viewerIndex = 0;
let playing = false;
let playTimer = null;

document.addEventListener("DOMContentLoaded", async function () {
    const session = initPage("screenshots");
    if (!session) return;

    const params = new URLSearchParams(window.location.search);
    const dateInput = document.getElementById("sDate");
    dateInput.value = params.get("date") || todayISO();
    dateInput.max = todayISO();

    await loadSystemOptions(params.get("systemId"));

    document.getElementById("sSystem").addEventListener("change", loadShots);
    dateInput.addEventListener("change", loadShots);
    document.getElementById("sRefresh").addEventListener("click", loadShots);
    wireScreenshotButton(document.getElementById("sTake"), () => document.getElementById("sSystem").value, async function () {
        // The new screenshot belongs to today, so show today.
        dateInput.value = todayISO();
        await loadShots();
    });
    document.getElementById("sReplay").addEventListener("click", () => openViewer(0, true));
    document.getElementById("moreBtn").addEventListener("click", renderMoreThumbs);
    document.getElementById("thumbGrid").addEventListener("click", function (event) {
        const card = event.target.closest("[data-index]");
        if (card) openViewer(Number(card.dataset.index), false);
    });

    wireViewer();
    await loadShots();
});

async function loadSystemOptions(preselect) {
    const select = document.getElementById("sSystem");
    try {
        const systems = await apiGet("/api/admin/systems");
        if (systems.length === 0) {
            select.innerHTML = '<option value="">No systems registered yet</option>';
            return;
        }
        select.innerHTML = systems.map(s => '<option value="' + s.id + '">' + escapeHtml(s.systemName) + "</option>").join("");
        if (preselect && systems.some(s => String(s.id) === preselect)) {
            select.value = preselect;
        }
    } catch (err) {
        select.innerHTML = '<option value="">Could not load systems</option>';
        toast("Could not load the systems list: " + err.message);
    }
}

async function loadShots() {
    const systemId = document.getElementById("sSystem").value;
    const date = document.getElementById("sDate").value || todayISO();
    const info = document.getElementById("sInfo");
    const grid = document.getElementById("thumbGrid");

    shots = [];
    thumbOrder = [];
    shownThumbs = 0;
    grid.innerHTML = "";
    document.getElementById("moreBtn").style.display = "none";
    document.getElementById("sReplay").disabled = true;
    const takeButton = document.getElementById("sTake");
    if (!takeButton.dataset.busy) {
        takeButton.disabled = !systemId;
    }

    if (!systemId) {
        info.textContent = "Pick a system to see its screenshots.";
        return;
    }

    info.textContent = "Loading...";
    try {
        shots = await apiGet("/api/admin/screenshots?systemId=" + encodeURIComponent(systemId) +
            "&date=" + encodeURIComponent(date) + "&limit=2000&order=asc");
    } catch (err) {
        info.textContent = "Could not load screenshots: " + err.message;
        return;
    }

    if (shots.length === 0) {
        info.textContent = "No screenshots for this system on this day.";
        grid.innerHTML = '<div class="empty-note">Nothing captured on this day. Click "Take Screenshot" to capture one ' +
            "from the selected computer (its agent must be running).</div>";
        return;
    }

    info.textContent = shots.length + " screenshot" + (shots.length === 1 ? "" : "s") + " · first " +
        fmtClock(shots[0].takenAt) + " · last " + fmtClock(shots[shots.length - 1].takenAt);
    document.getElementById("sReplay").disabled = false;

    thumbOrder = shots.map((s, i) => i).reverse();
    renderMoreThumbs();
}

function renderMoreThumbs() {
    const grid = document.getElementById("thumbGrid");
    const batch = thumbOrder.slice(shownThumbs, shownThumbs + THUMBS_PER_PAGE);
    shownThumbs += batch.length;

    const wrapper = document.createElement("div");
    wrapper.innerHTML = batch.map(index => {
        const s = shots[index];
        return `
        <div class="thumb" data-index="${index}">
            <img class="thumb-img" data-shot-id="${s.id}" alt="Screenshot at ${escapeHtml(fmtClock(s.takenAt))}">
            <div class="thumb-meta">
                <div class="t">${escapeHtml(fmtClock(s.takenAt))}</div>
                <div class="a" title="${escapeHtml(s.application || "")}">${escapeHtml(s.application || "—")}</div>
            </div>
        </div>`;
    }).join("");

    // wrapper.children skips the whitespace text nodes between the cards.
    Array.from(wrapper.children).forEach(card => {
        grid.appendChild(card);
        const img = card.querySelector("img[data-shot-id]");
        if (img) {
            fetchImageUrl(Number(img.dataset.shotId))
                .then(url => { img.src = url; })
                .catch(() => { img.alt = "Image unavailable"; });
        }
    });

    document.getElementById("moreBtn").style.display = shownThumbs < thumbOrder.length ? "" : "none";
    document.getElementById("moreBtn").textContent =
        "Show more (" + (thumbOrder.length - shownThumbs) + " left)";
}

// ============================================================ viewer / replay

function wireViewer() {
    const backdrop = document.getElementById("viewerBackdrop");
    document.getElementById("viewerClose").addEventListener("click", closeViewer);
    backdrop.addEventListener("click", function (event) {
        if (event.target === backdrop) closeViewer();
    });

    document.getElementById("vPrev").addEventListener("click", () => step(-1));
    document.getElementById("vNext").addEventListener("click", () => step(1));
    document.getElementById("vPlay").addEventListener("click", togglePlay);
    document.getElementById("vSpeed").addEventListener("change", function () {
        if (playing) {
            stopTimer();
            startTimer();
        }
    });
    document.getElementById("vSlider").addEventListener("input", function (event) {
        showFrame(Number(event.target.value));
    });

    document.addEventListener("keydown", function (event) {
        if (!backdrop.classList.contains("open")) return;
        if (event.key === "Escape") closeViewer();
        else if (event.key === "ArrowRight") step(1);
        else if (event.key === "ArrowLeft") step(-1);
        else if (event.key === " ") {
            event.preventDefault();
            togglePlay();
        }
    });
}

function openViewer(index, autoplay) {
    if (shots.length === 0) return;
    document.getElementById("viewerBackdrop").classList.add("open");
    document.getElementById("vSlider").max = shots.length - 1;
    showFrame(index);
    if (autoplay) play();
}

function closeViewer() {
    pause();
    document.getElementById("viewerBackdrop").classList.remove("open");
}

function showFrame(index) {
    index = Math.min(shots.length - 1, Math.max(0, index));
    viewerIndex = index;
    const shot = shots[index];

    document.getElementById("viewerCaption").innerHTML =
        '<span class="t">' + escapeHtml(fmtDateTime(shot.takenAt)) + "</span> &nbsp;·&nbsp; " +
        escapeHtml(shot.application || "—");
    document.getElementById("vCounter").textContent = (index + 1) + " / " + shots.length;
    document.getElementById("vSlider").value = index;

    const img = document.getElementById("viewerImg");
    const loading = document.getElementById("viewerLoading");
    if (!img.src) {
        loading.style.display = "";
        loading.textContent = "Loading...";
    }

    fetchImageUrl(shot.id).then(url => {
        if (viewerIndex !== index) return; // the user already moved on
        img.src = url;
        img.style.display = "";
        loading.style.display = "none";
    }).catch(err => {
        if (viewerIndex !== index) return;
        img.style.display = "none";
        loading.style.display = "";
        loading.textContent = "Image unavailable (" + err.message + ")";
    });

    // Warm the cache for the next frames so playback doesn't stall.
    [1, 2, 3].forEach(offset => {
        const next = shots[index + offset];
        if (next) fetchImageUrl(next.id).catch(() => { /* shown if/when it is reached */ });
    });
}

function step(delta) {
    showFrame(viewerIndex + delta);
}

function togglePlay() {
    if (playing) pause();
    else play();
}

function play() {
    if (viewerIndex >= shots.length - 1) {
        showFrame(0); // pressing play at the end starts over
    }
    playing = true;
    document.getElementById("vPlay").innerHTML = "&#9208; Pause";
    startTimer();
}

function pause() {
    playing = false;
    document.getElementById("vPlay").innerHTML = "&#9654; Play";
    stopTimer();
}

function startTimer() {
    const speed = Number(document.getElementById("vSpeed").value) || 1;
    playTimer = setInterval(function () {
        if (viewerIndex >= shots.length - 1) {
            pause();
        } else {
            showFrame(viewerIndex + 1);
        }
    }, 1000 / speed);
}

function stopTimer() {
    if (playTimer !== null) {
        clearInterval(playTimer);
        playTimer = null;
    }
}
