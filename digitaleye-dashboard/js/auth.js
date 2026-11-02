// Everything to do with the logged-in session: storing the JWT, reading it
// back, guarding pages that require login, and logging out.

const AUTH_STORAGE_KEY = "digitaleye_auth";

function saveSession(authResponse) {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(authResponse));
}

function getSession() {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) return null;
    try {
        return JSON.parse(raw);
    } catch (e) {
        return null;
    }
}

function clearSession() {
    localStorage.removeItem(AUTH_STORAGE_KEY);
}

function getToken() {
    const session = getSession();
    return session ? session.token : null;
}

// Call this at the top of every page that needs a logged-in admin.
// Redirects to login.html if there's no session at all, or if the
// logged-in user isn't an admin (this milestone only has an admin UI).
function requireAdminSession() {
    const session = getSession();
    if (!session || !session.token) {
        window.location.href = "login.html";
        return null;
    }
    if (session.role !== "ADMIN") {
        alert("This dashboard is for administrators only.");
        clearSession();
        window.location.href = "login.html";
        return null;
    }
    return session;
}

function logout() {
    clearSession();
    window.location.href = "login.html";
}
