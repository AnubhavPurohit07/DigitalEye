package com.digitaleye.agent.network;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to the Spring Boot backend over plain HTTP using the JDK's built-in
 * HttpClient (java.net.http) - no extra Maven dependency needed. Responses
 * are tiny, backend-controlled JSON objects, so rather than pulling in a
 * full JSON library, extractString/extractNumber below just regex out the
 * one field we need from a known shape. That's fine for this scope; a
 * production version would use a real JSON parser.
 */
public class BackendClient {

    /** Returned by sendActivity/uploadScreenshot when the request never got an HTTP answer. */
    public static final int UNREACHABLE = -1;
    /** Returned when the agent hasn't registered its system yet, so nothing was sent. */
    public static final int NOT_REGISTERED = -2;

    private final String baseUrl;
    private final HttpClient httpClient;
    private String jwtToken;
    private Long systemId;

    public BackendClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean login(String username, String password) {
        try {
            String body = "{\"username\":\"" + escape(username) + "\",\"password\":\"" + escape(password) + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/auth/login"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Login failed: HTTP " + response.statusCode() + " - " + response.body());
                return false;
            }

            jwtToken = extractString(response.body(), "token");
            return jwtToken != null;
        } catch (Exception e) {
            System.err.println("Login request failed: " + e.getMessage());
            return false;
        }
    }

    public boolean registerSystem(String systemName) {
        try {
            String body = "{\"systemName\":\"" + escape(systemName) + "\"}";
            HttpResponse<String> response = authorizedPost("/api/agent/systems/register", body);

            if (response.statusCode() != 200) {
                System.err.println("System registration failed: HTTP " + response.statusCode() + " - " + response.body());
                return false;
            }

            String idStr = extractNumber(response.body(), "systemId");
            if (idStr == null) {
                return false;
            }

            systemId = Long.parseLong(idStr);
            return true;
        } catch (Exception e) {
            System.err.println("System registration request failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Reports the current application and ACTIVE/IDLE state. Returns the HTTP status code
     * (200 = accepted), or UNREACHABLE / NOT_REGISTERED, so the caller can tell a
     * temporary hiccup from "my login has expired, reconnect".
     */
    public int sendActivity(String applicationName, String status) {
        if (systemId == null) {
            return NOT_REGISTERED;
        }
        try {
            String body = "{\"systemId\":" + systemId
                    + ",\"applicationName\":\"" + escape(applicationName) + "\""
                    + ",\"status\":\"" + status + "\"}";

            return authorizedPost("/api/agent/activity", body).statusCode();
        } catch (Exception e) {
            System.err.println("Sending activity failed: " + e.getMessage());
            return UNREACHABLE;
        }
    }

    /** Uploads one JPEG screenshot, tagged with the application that was in front when it was taken. */
    public int uploadScreenshot(byte[] jpegBytes, String applicationName) {
        if (systemId == null) {
            return NOT_REGISTERED;
        }
        try {
            String url = baseUrl + "/api/agent/screenshots?systemId=" + systemId
                    + "&application=" + URLEncoder.encode(applicationName == null ? "" : applicationName, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "image/jpeg")
                    .header("Authorization", "Bearer " + jwtToken)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(jpegBytes))
                    .build();

            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception e) {
            System.err.println("Uploading screenshot failed: " + e.getMessage());
            return UNREACHABLE;
        }
    }

    /** The answer to "has the administrator asked for anything?": the HTTP status, and what was asked. */
    public record CommandResult(int status, boolean screenshot) {
    }

    /**
     * Asks the backend whether the administrator has clicked "Take Screenshot" for this system.
     * The backend hands each request out only once, so a true answer means "take exactly one now".
     */
    public CommandResult checkCommands() {
        if (systemId == null) {
            return new CommandResult(NOT_REGISTERED, false);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/agent/commands?systemId=" + systemId))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + jwtToken)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new CommandResult(response.statusCode(), false);
            }
            return new CommandResult(200, parseScreenshotFlag(response.body()));
        } catch (Exception e) {
            System.err.println("Checking for commands failed: " + e.getMessage());
            return new CommandResult(UNREACHABLE, false);
        }
    }

    /** Reads {"screenshot":true} / {"screenshot":false}; anything unexpected counts as false. */
    static boolean parseScreenshotFlag(String json) {
        return json != null && Pattern.compile("\"screenshot\"\\s*:\\s*true").matcher(json).find();
    }

    /** Tells the backend this agent is shutting down cleanly, so the dashboard shows OFFLINE right away. */
    public void sendGoodbye() {
        if (systemId == null || jwtToken == null) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/agent/goodbye"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + jwtToken)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"systemId\":" + systemId + "}"))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // Best effort only - if it fails, the backend notices the missing heartbeats and marks us offline anyway.
        }
    }

    private HttpResponse<String> authorizedPost(String path, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + jwtToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Escapes text for use inside a JSON string, including tabs/newlines that can appear in window titles. */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static String extractString(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String extractNumber(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }
}
