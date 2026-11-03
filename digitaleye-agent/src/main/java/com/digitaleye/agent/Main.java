package com.digitaleye.agent;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.digitaleye.agent.monitor.ActiveWindowDetector;
import com.digitaleye.agent.monitor.IdleTimeDetector;
import com.digitaleye.agent.monitor.ScreenshotCapturer;
import com.digitaleye.agent.network.AgentConfig;
import com.digitaleye.agent.network.BackendClient;


public class Main {

    private static final long POLL_INTERVAL_SECONDS = 5;
    private static final long IDLE_THRESHOLD_MILLIS = 60_000; // 60 seconds
    private static final long RECONNECT_INTERVAL_SECONDS = 30;

    private static final ActiveWindowDetector windowDetector = new ActiveWindowDetector();
    private static final IdleTimeDetector idleDetector = new IdleTimeDetector();
    private static final ScreenshotCapturer screenshotCapturer = new ScreenshotCapturer();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final BackendClient backendClient = new BackendClient(AgentConfig.backendUrl());

    private static String currentApp = null;
    private static LocalDateTime sessionStart = null;
    private static boolean wasIdle = false;
    private static volatile boolean connectedToBackend = false;

    public static void main(String[] args) {
        System.out.println("DigitalEye Agent starting... polling every " + POLL_INTERVAL_SECONDS + "s");
        if (AgentConfig.screenshotsEnabled()) {
            System.out.println("NOTICE: your administrator can request a screenshot of this screen from the dashboard. "
                    + "A screenshot is only taken when they ask for one, and a message is printed here each time.");
            if (AgentConfig.screenshotIntervalSeconds() > 0) {
                System.out.println("NOTICE: automatic screenshots are also on - one every "
                        + AgentConfig.screenshotIntervalSeconds() + "s.");
            }
        }

        connectToBackend();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(Main::poll, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);

        scheduler.scheduleWithFixedDelay(() -> {
            if (!connectedToBackend) {
                connectToBackend();
            }
        }, RECONNECT_INTERVAL_SECONDS, RECONNECT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        scheduler.scheduleWithFixedDelay(Main::heartbeat,
                AgentConfig.heartbeatSeconds(), AgentConfig.heartbeatSeconds(), TimeUnit.SECONDS);

        if (AgentConfig.screenshotsEnabled()) {
            scheduler.scheduleWithFixedDelay(Main::pollCommands,
                    AgentConfig.commandPollSeconds(), AgentConfig.commandPollSeconds(), TimeUnit.SECONDS);

            int interval = AgentConfig.screenshotIntervalSeconds();
            if (interval > 0) {
                scheduler.scheduleWithFixedDelay(() -> captureScreenshot(false), Math.min(interval, 15), interval, TimeUnit.SECONDS);
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            closeCurrentSession();
            if (connectedToBackend) {
                backendClient.sendGoodbye();
            }
            System.out.println("DigitalEye Agent stopped.");
        }));
    }

    private static void connectToBackend() {
        log("Connecting to backend at " + AgentConfig.backendUrl() + "...");

        if (!backendClient.login(AgentConfig.username(), AgentConfig.password())) {
            log("Could not log in to backend - will keep working locally and retry in " + RECONNECT_INTERVAL_SECONDS + "s.");
            connectedToBackend = false;
            return;
        }

        String systemName = getHostName();
        if (!backendClient.registerSystem(systemName)) {
            log("Could not register this system with the backend - will retry in " + RECONNECT_INTERVAL_SECONDS + "s.");
            connectedToBackend = false;
            return;
        }

        connectedToBackend = true;
        log("Connected to backend as system \"" + systemName + "\".");

        heartbeat();
    }

    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "UnknownSystem";
        }
    }

    private static void poll() {
        long idleMillis = idleDetector.getIdleTimeMillis();
        boolean isIdle = idleMillis >= IDLE_THRESHOLD_MILLIS;

        if (isIdle) {
            if (!wasIdle) {
                closeCurrentSession();
                log("User went idle");
                wasIdle = true;
                reportToBackend("Idle", "IDLE");
            }
            return;
        }

        if (wasIdle) {
            log("User is active again");
            wasIdle = false;
        }

        String activeApp = windowDetector.getActiveWindowTitle();

        if (!activeApp.equals(currentApp)) {
            closeCurrentSession();
            currentApp = activeApp;
            sessionStart = LocalDateTime.now();
            log("Active app changed -> " + currentApp);
            reportToBackend(currentApp, "ACTIVE");
        }
    }

    private static void heartbeat() {
        if (!connectedToBackend) {
            return;
        }
        if (wasIdle) {
            reportToBackend("Idle", "IDLE");
        } else if (currentApp != null) {
            reportToBackend(currentApp, "ACTIVE");
        }
    }

    private static void pollCommands() {
        if (!connectedToBackend) {
            return;
        }
        BackendClient.CommandResult result = backendClient.checkCommands();
        if (result.status() != 200) {
            handleFailure(result.status());
            return;
        }
        if (result.screenshot()) {
            log("Your administrator requested a screenshot - capturing now.");
            captureScreenshot(true);
        }
    }

    /**
     * @param requested true when an administrator asked for this one. A requested screenshot is
     *                  taken even while the user is idle; an automatic one skips idle time.
     */
    private static void captureScreenshot(boolean requested) {
        if (!connectedToBackend || (!requested && wasIdle)) {
            return;
        }
        try {
            byte[] jpeg = screenshotCapturer.captureJpeg(AgentConfig.screenshotMaxWidth(), AgentConfig.screenshotQuality());
            int code = backendClient.uploadScreenshot(jpeg, currentApp == null ? "" : currentApp);
            if (code == 200) {
                log("Screenshot sent (" + Math.max(1, jpeg.length / 1024) + " KB)");
            } else {
                log("Screenshot upload failed (" + describe(code) + ")");
                handleFailure(code);
            }
        } catch (Exception e) {
            log("Could not capture screenshot: " + e.getMessage());
        }
    }

    private static void reportToBackend(String application, String status) {
        if (!connectedToBackend) {
            return;
        }
        int code = backendClient.sendActivity(application, status);
        if (code != 200) {
            handleFailure(code);
        }
    }

    private static void handleFailure(int code) {
        if (connectedToBackend) {
            log("Lost connection to backend (" + describe(code) + ") - will reconnect in "
                    + RECONNECT_INTERVAL_SECONDS + "s.");
            connectedToBackend = false;
        }
    }

    private static String describe(int code) {
        if (code == BackendClient.UNREACHABLE) {
            return "backend unreachable";
        }
        if (code == BackendClient.NOT_REGISTERED) {
            return "not registered yet";
        }
        return "HTTP " + code;
    }

    private static void closeCurrentSession() {
        if (currentApp != null && sessionStart != null) {
            long seconds = Duration.between(sessionStart, LocalDateTime.now()).getSeconds();
            log(String.format("Session ended: \"%s\" (%d sec)", currentApp, seconds));
        }
        currentApp = null;
        sessionStart = null;
    }

    private static void log(String message) {
        System.out.println("[" + LocalDateTime.now().format(TIME_FMT) + "] " + message);
    }
}
