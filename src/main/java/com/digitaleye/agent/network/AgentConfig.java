package com.digitaleye.agent.network;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads agent.properties the same way the database/backend modules load their own
 * .properties files - read once at class-load time, exposed as simple static getters.
 * Optional settings fall back to a sensible default when they are missing from the file.
 */
public class AgentConfig {

    private static final Properties props = new Properties();

    static {
        try (InputStream in = AgentConfig.class.getClassLoader().getResourceAsStream("agent.properties")) {
            if (in == null) {
                throw new RuntimeException("agent.properties not found on the classpath");
            }
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load agent.properties", e);
        }
    }

    public static String backendUrl() {
        return props.getProperty("backend.url");
    }

    public static String username() {
        return props.getProperty("agent.username");
    }

    public static String password() {
        return props.getProperty("agent.password");
    }

    /** How often the agent re-sends its current state so the backend knows it is still alive. */
    public static int heartbeatSeconds() {
        return intProperty("agent.heartbeat.seconds", 20);
    }

    /** Master switch: if false, this agent never takes a screenshot, not even when asked. */
    public static boolean screenshotsEnabled() {
        return Boolean.parseBoolean(props.getProperty("screenshot.enabled", "true").trim());
    }

    /**
     * Seconds between AUTOMATIC screenshots. 0 (the default) means there are none: screenshots
     * are only taken when an administrator clicks "Take Screenshot" on the dashboard. Any
     * value above 0 turns the automatic capture back on (minimum 5 seconds).
     */
    public static int screenshotIntervalSeconds() {
        int seconds = intProperty("screenshot.interval.seconds", 0);
        return seconds <= 0 ? 0 : Math.max(5, seconds);
    }

    /** How often the agent asks the backend whether an administrator has requested a screenshot. */
    public static int commandPollSeconds() {
        return Math.max(1, intProperty("agent.command.poll.seconds", 3));
    }

    /** Screenshots wider than this are scaled down before upload, to keep each one small. */
    public static int screenshotMaxWidth() {
        return Math.max(320, intProperty("screenshot.max.width", 1280));
    }

    /** JPEG quality from 0.1 (smallest file) to 1.0 (best quality). */
    public static float screenshotQuality() {
        try {
            float value = Float.parseFloat(props.getProperty("screenshot.quality", "0.6").trim());
            return Math.min(1.0f, Math.max(0.1f, value));
        } catch (NumberFormatException e) {
            return 0.6f;
        }
    }

    private static int intProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
