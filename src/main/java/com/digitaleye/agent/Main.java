package com.digitaleye.agent;

import com.digitaleye.agent.monitor.ActiveWindowDetector;
import com.digitaleye.agent.monitor.IdleTimeDetector;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Milestone 1 of the DigitalEye agent: application usage tracking + idle
 * detection, logged to the console. No screenshots, no networking, no data
 * storage yet - those come once this core loop is solid.
 */
public class Main {

    private static final long POLL_INTERVAL_SECONDS = 5;
    private static final long IDLE_THRESHOLD_MILLIS = 60_000; // 60 seconds

    private static final ActiveWindowDetector windowDetector = new ActiveWindowDetector();
    private static final IdleTimeDetector idleDetector = new IdleTimeDetector();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Tracks the currently "open" session so its duration can be logged once it ends
    private static String currentApp = null;
    private static LocalDateTime sessionStart = null;
    private static boolean wasIdle = false;

    public static void main(String[] args) {
        System.out.println("DigitalEye Agent starting... polling every " + POLL_INTERVAL_SECONDS + "s");

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(Main::poll, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            closeCurrentSession();
            System.out.println("DigitalEye Agent stopped.");
        }));
    }

    private static void poll() {
        long idleMillis = idleDetector.getIdleTimeMillis();
        boolean isIdle = idleMillis >= IDLE_THRESHOLD_MILLIS;

        if (isIdle) {
            if (!wasIdle) {
                closeCurrentSession();
                log("User went idle");
                wasIdle = true;
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
        }
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
