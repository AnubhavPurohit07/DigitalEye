package com.digitaleye.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The two background checks that can't be triggered by an incoming request, because
 * what they look for is the ABSENCE of requests: an agent that stopped talking, and a
 * user who has been idle for a long time.
 */
@Component
public class MonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitoringScheduler.class);

    private final AlertEngine alertEngine;
    private final int offlineAfterSeconds;

    public MonitoringScheduler(AlertEngine alertEngine,
                               @Value("${monitoring.offline-after-seconds:90}") int offlineAfterSeconds) {
        this.alertEngine = alertEngine;
        this.offlineAfterSeconds = offlineAfterSeconds;
    }

    // The 60 s initial delay gives agents time to re-sync after a backend restart,
    // so a restart doesn't make every system look like it just went offline.
    @Scheduled(initialDelay = 60_000, fixedDelay = 15_000)
    public void checkClientHealth() {
        try {
            int flipped = alertEngine.checkOfflineSystems(offlineAfterSeconds);
            if (flipped > 0) {
                log.info("Marked {} system(s) OFFLINE (no heartbeat for {}s)", flipped, offlineAfterSeconds);
            }
        } catch (Exception e) {
            log.error("Client health check failed", e);
        }
    }

    @Scheduled(initialDelay = 20_000, fixedDelay = 30_000)
    public void checkIdleAlerts() {
        try {
            alertEngine.checkIdleSessions();
        } catch (Exception e) {
            log.error("Idle alert check failed", e);
        }
    }
}
