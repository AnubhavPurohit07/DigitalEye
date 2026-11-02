package com.digitaleye.backend.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Remembers "the administrator asked system X for a screenshot" until the agent on X picks
 * the request up.
 *
 * The backend can't call an agent directly (the agent is the one that connects out to the
 * backend), so the flow is: the dashboard button files a request here, and the agent - which
 * asks "anything for me?" every few seconds - collects it with consume() and then uploads
 * the screenshot like any other.
 *
 * Requests are kept in memory only. A request nobody collects within REQUEST_LIFETIME is
 * dropped, so a long-forgotten click can never make a screenshot appear later, and a backend
 * restart simply clears anything pending (the admin just clicks the button again).
 */
@Service
public class ScreenshotRequestService {

    /** How long an uncollected request stays valid. */
    static final Duration REQUEST_LIFETIME = Duration.ofSeconds(60);

    private final ConcurrentMap<Long, Instant> pending = new ConcurrentHashMap<>();

    /** Files (or renews) a request for this system. */
    public void request(Long systemId) {
        request(systemId, Instant.now());
    }

    /**
     * Collects the request, if there is a live one. Returns true at most once per request, so
     * the agent takes exactly one screenshot per click.
     */
    public boolean consume(Long systemId) {
        return consume(systemId, Instant.now());
    }

    /** True if a live request is waiting (does not collect it). */
    public boolean isPending(Long systemId) {
        Instant filedAt = pending.get(systemId);
        return filedAt != null && !isExpired(filedAt, Instant.now());
    }

    // Package-private versions that take the time as a parameter, so tests don't have to wait.

    void request(Long systemId, Instant now) {
        pending.put(systemId, now);
    }

    boolean consume(Long systemId, Instant now) {
        Instant filedAt = pending.remove(systemId);
        return filedAt != null && !isExpired(filedAt, now);
    }

    private static boolean isExpired(Instant filedAt, Instant now) {
        return Duration.between(filedAt, now).compareTo(REQUEST_LIFETIME) > 0;
    }
}
