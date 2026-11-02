// Thin wrapper around fetch() that adds the JWT Authorization header and
// sends the user back to the login page if the backend says the token is
// no longer valid (401/403).

async function apiRequest(path, options = {}) {
    const token = getToken();
    const headers = Object.assign(
        { "Content-Type": "application/json" },
        options.headers || {}
    );
    if (token) {
        headers["Authorization"] = "Bearer " + token;
    }

    const response = await fetch(API_BASE_URL + path, Object.assign({}, options, { headers }));

    if (response.status === 401 || response.status === 403) {
        clearSession();
        window.location.href = "login.html";
        throw new Error("Session expired or not authorized.");
    }

    if (!response.ok) {
        const text = await response.text().catch(() => "");
        throw new Error("Request failed (" + response.status + "): " + text);
    }

    const contentType = response.headers.get("content-type") || "";
    if (contentType.includes("application/json")) {
        return response.json();
    }
    return null;
}

// Login does NOT go through apiRequest - there's no token yet, and a
// failed login (wrong password) shouldn't redirect anywhere, just show
// an error on the login form itself.
async function loginRequest(username, password) {
    const response = await fetch(API_BASE_URL + "/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password })
    });

    if (!response.ok) {
        throw new Error(response.status === 401 || response.status === 403
            ? "Incorrect username or password."
            : "Login failed (" + response.status + ").");
    }

    return response.json();
}

function fetchSystems() {
    return apiRequest("/api/admin/systems");
}
