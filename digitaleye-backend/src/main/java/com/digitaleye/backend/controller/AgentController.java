package com.digitaleye.backend.controller;

import com.digitaleye.backend.dto.AgentActivityRequest;
import com.digitaleye.backend.dto.AgentCommands;
import com.digitaleye.backend.dto.AgentGoodbyeRequest;
import com.digitaleye.backend.dto.AgentSystemRegisterRequest;
import com.digitaleye.backend.dto.AgentSystemRegisterResponse;
import com.digitaleye.backend.service.AlertEngine;
import com.digitaleye.backend.service.AppClassifier;
import com.digitaleye.backend.service.ScreenshotRequestService;
import com.digitaleye.backend.service.ScreenshotStorage;
import com.digitaleye.backend.service.TimeUtil;
import com.digitaleye.database.dao.ActivityLogDao;
import com.digitaleye.database.dao.MonitoredSystemDao;
import com.digitaleye.database.dao.ScreenshotDao;
import com.digitaleye.database.dao.UserDao;
import com.digitaleye.database.model.ActivityLog;
import com.digitaleye.database.model.MonitoredSystem;
import com.digitaleye.database.model.Screenshot;
import com.digitaleye.database.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

/**
 * What the Java monitoring agent talks to. Reachable by any logged-in account (ADMIN or
 * USER both satisfy SecurityConfig's anyRequest().authenticated() fallback) - there is no
 * separate "agent role": the agent logs in as a normal account via /api/auth/login, then
 * calls these endpoints with that token.
 *
 * Every call that names a systemId checks that the system belongs to the account making
 * the call, so one agent can't write activity or screenshots into another agent's system.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private static final int MAX_TEXT = 255;
    private static final int MAX_SCREENSHOT_BYTES = 5 * 1024 * 1024;

    private final MonitoredSystemDao systemDao = new MonitoredSystemDao();
    private final ActivityLogDao activityLogDao = new ActivityLogDao();
    private final ScreenshotDao screenshotDao = new ScreenshotDao();
    private final UserDao userDao = new UserDao();

    private final AlertEngine alertEngine;
    private final ScreenshotStorage screenshotStorage;
    private final ScreenshotRequestService screenshotRequests;

    public AgentController(AlertEngine alertEngine, ScreenshotStorage screenshotStorage,
                           ScreenshotRequestService screenshotRequests) {
        this.alertEngine = alertEngine;
        this.screenshotStorage = screenshotStorage;
        this.screenshotRequests = screenshotRequests;
    }

    /**
     * Called whenever the agent (re)connects. Finds this system under the logged-in account
     * if it's already been registered before (so restarting the agent doesn't create a
     * duplicate "computer"), otherwise creates it.
     */
    @PostMapping("/systems/register")
    public ResponseEntity<AgentSystemRegisterResponse> registerSystem(
            @RequestBody AgentSystemRegisterRequest request,
            Authentication authentication) {
        try {
            User user = userDao.findByUsername(authentication.getName());
            if (user == null) {
                return ResponseEntity.status(401).build();
            }

            String systemName = TimeUtil.abbreviate(request.getSystemName(), MAX_TEXT);
            MonitoredSystem existing = systemDao.findByUserIdAndName(user.getId(), systemName);
            Timestamp now = new Timestamp(System.currentTimeMillis());

            Long systemId;
            if (existing != null) {
                systemId = existing.getId();
            } else {
                MonitoredSystem newSystem = new MonitoredSystem(user.getId(), systemName, "ONLINE");
                systemId = systemDao.insert(newSystem);
            }

            systemDao.updateStatus(systemId, "ONLINE", now);
            return ResponseEntity.ok(new AgentSystemRegisterResponse(systemId, "ONLINE"));
        } catch (SQLException e) {
            throw new RuntimeException("Database error while registering system", e);
        }
    }

    /**
     * Called whenever the active application changes (or the user goes idle / becomes active
     * again) AND every ~20 seconds as a heartbeat carrying the agent's current state.
     *
     * If the reported state matches the open session, only the heartbeat is refreshed. If it
     * differs - including when no session is open, e.g. right after the system was marked
     * offline - the old session is closed and a new one opened, so "the one open row with
     * end_time IS NULL" always IS the system's current activity.
     */
    @PostMapping("/activity")
    public ResponseEntity<Void> reportActivity(@RequestBody AgentActivityRequest request,
                                               Authentication authentication) {
        try {
            MonitoredSystem system = ownedSystem(request.getSystemId(), authentication);
            if (system == null) {
                return ResponseEntity.status(403).build();
            }

            String status = request.getStatus() == null ? "" : request.getStatus().trim().toUpperCase();
            if (!status.equals("ACTIVE") && !status.equals("IDLE")) {
                return ResponseEntity.badRequest().build();
            }
            String applicationName = request.getApplicationName() == null || request.getApplicationName().isBlank()
                    ? "Unknown" : TimeUtil.abbreviate(request.getApplicationName(), MAX_TEXT);

            Timestamp now = new Timestamp(System.currentTimeMillis());
            ActivityLog open = activityLogDao.findOpenSession(system.getId());

            boolean isSameAsAlreadyOpen = open != null
                    && Objects.equals(open.getApplicationName(), applicationName)
                    && status.equalsIgnoreCase(open.getStatus());

            if (!isSameAsAlreadyOpen) {
                if (open != null) {
                    activityLogDao.closeSession(open.getId(), now);
                }
                String category = AppClassifier.classify(applicationName, status);
                ActivityLog newLog = new ActivityLog(system.getId(), applicationName, status, category, now);
                newLog.setId(activityLogDao.insert(newLog));

                try {
                    alertEngine.onNewSession(system, newLog);
                } catch (Exception e) {
                    // A failing alert rule must never stop activity from being recorded.
                    log.error("Alert evaluation failed for system {}", system.getId(), e);
                }
            }

            systemDao.updateStatus(system.getId(), "ONLINE", now);
            return ResponseEntity.ok().build();
        } catch (SQLException e) {
            throw new RuntimeException("Database error while recording activity", e);
        }
    }

    /** Sent by the agent when it shuts down cleanly, so the dashboard shows OFFLINE immediately. */
    @PostMapping("/goodbye")
    public ResponseEntity<Void> goodbye(@RequestBody AgentGoodbyeRequest request, Authentication authentication) {
        try {
            MonitoredSystem system = ownedSystem(request.systemId(), authentication);
            if (system == null) {
                return ResponseEntity.status(403).build();
            }
            alertEngine.markOffline(system, Instant.now());
            return ResponseEntity.ok().build();
        } catch (SQLException e) {
            throw new RuntimeException("Database error while marking system offline", e);
        }
    }

    /**
     * The agent asks this every few seconds: "has the administrator asked for anything?".
     * A screenshot request is handed out exactly once - the first poll after the admin clicks
     * the dashboard button gets {"screenshot":true}, every other poll gets false.
     */
    @GetMapping("/commands")
    public ResponseEntity<AgentCommands> commands(@RequestParam("systemId") Long systemId,
                                                  Authentication authentication) {
        try {
            MonitoredSystem system = ownedSystem(systemId, authentication);
            if (system == null) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.ok(new AgentCommands(screenshotRequests.consume(system.getId())));
        } catch (SQLException e) {
            throw new RuntimeException("Database error while checking commands", e);
        }
    }

    /**
     * The agent POSTs the raw JPEG bytes as the request body (Content-Type: image/jpeg) with
     * the system id and active application name as query parameters.
     */
    @PostMapping("/screenshots")
    public ResponseEntity<Void> uploadScreenshot(
            @RequestParam("systemId") Long systemId,
            @RequestParam(name = "application", required = false) String application,
            @RequestBody byte[] body,
            Authentication authentication) {
        try {
            MonitoredSystem system = ownedSystem(systemId, authentication);
            if (system == null) {
                return ResponseEntity.status(403).build();
            }
            if (body == null || body.length == 0 || body.length > MAX_SCREENSHOT_BYTES || !looksLikeJpeg(body)) {
                return ResponseEntity.badRequest().build();
            }

            Instant takenAt = Instant.now();
            String relativePath = screenshotStorage.save(system.getId(), takenAt, body);
            screenshotDao.insert(new Screenshot(system.getId(), Timestamp.from(takenAt),
                    TimeUtil.abbreviate(application, MAX_TEXT), relativePath, body.length));

            systemDao.updateStatus(system.getId(), "ONLINE", Timestamp.from(takenAt));
            return ResponseEntity.ok().build();
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Could not store screenshot", e);
        }
    }

    /** The system, but only if it exists AND belongs to the account making the request. */
    private MonitoredSystem ownedSystem(Long systemId, Authentication authentication) throws SQLException {
        if (systemId == null) {
            return null;
        }
        MonitoredSystem system = systemDao.findById(systemId);
        if (system == null) {
            return null;
        }
        User user = userDao.findByUsername(authentication.getName());
        if (user == null || !Objects.equals(user.getId(), system.getUserId())) {
            return null;
        }
        return system;
    }

    /** JPEG files always start with the bytes FF D8 FF. */
    private boolean looksLikeJpeg(byte[] data) {
        return data.length > 3
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF;
    }
}
