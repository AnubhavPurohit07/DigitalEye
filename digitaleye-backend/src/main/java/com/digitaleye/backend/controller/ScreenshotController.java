package com.digitaleye.backend.controller;

import com.digitaleye.backend.dto.ScreenshotInfo;
import com.digitaleye.backend.service.ScreenshotRequestService;
import com.digitaleye.backend.service.ScreenshotStorage;
import com.digitaleye.backend.service.TimeUtil;
import com.digitaleye.database.dao.MonitoredSystemDao;
import com.digitaleye.database.dao.ScreenshotDao;
import com.digitaleye.database.model.MonitoredSystem;
import com.digitaleye.database.model.Screenshot;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Admin-only access to captured screenshots. The image files are never exposed as static
 * files - every image goes through here, behind the same JWT + ADMIN check as the rest of
 * /api/admin/**.
 */
@RestController
@RequestMapping("/api/admin")
public class ScreenshotController {

    private static final int MAX_LIST = 2000;

    private final ScreenshotDao screenshotDao = new ScreenshotDao();
    private final MonitoredSystemDao systemDao = new MonitoredSystemDao();
    private final ScreenshotStorage storage;
    private final ScreenshotRequestService requests;

    public ScreenshotController(ScreenshotStorage storage, ScreenshotRequestService requests) {
        this.storage = storage;
        this.requests = requests;
    }

    /**
     * The dashboard's "Take Screenshot" button. This does not capture anything itself - it
     * files a request, which the agent on that computer collects within a few seconds
     * (see AgentController.commands) and answers by uploading a screenshot.
     */
    @PostMapping("/systems/{id}/screenshot-request")
    public ResponseEntity<Map<String, Object>> requestScreenshot(@PathVariable("id") Long id) {
        try {
            MonitoredSystem system = systemDao.findById(id);
            if (system == null) {
                return reply(HttpStatus.NOT_FOUND, "No such system");
            }
            if (!"ONLINE".equalsIgnoreCase(system.getStatus())) {
                return reply(HttpStatus.CONFLICT,
                        "This computer's agent is offline, so it can't take a screenshot right now.");
            }
            requests.request(system.getId());
            return ResponseEntity.ok(Map.of("requested", true));
        } catch (SQLException e) {
            throw new RuntimeException("Database error while requesting a screenshot", e);
        }
    }

    private ResponseEntity<Map<String, Object>> reply(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    /**
     * With a date: that day's screenshots (oldest first by default, which is replay order).
     * Without a date: the newest screenshots of any day.
     */
    @GetMapping("/screenshots")
    public List<ScreenshotInfo> list(
            @RequestParam("systemId") Long systemId,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "limit", defaultValue = "500") int limit,
            @RequestParam(name = "order", defaultValue = "asc") String order) {
        try {
            int safeLimit = Math.min(MAX_LIST, Math.max(1, limit));
            boolean oldestFirst = !"desc".equalsIgnoreCase(order);

            List<Screenshot> shots;
            if (date == null || date.isBlank()) {
                shots = screenshotDao.findLatestBySystem(systemId, safeLimit);
            } else {
                LocalDate day = TimeUtil.parseDateOrToday(date);
                Instant start = TimeUtil.dayStart(day);
                Instant end = TimeUtil.dayEnd(day);
                shots = screenshotDao.findBySystemBetween(
                        systemId, TimeUtil.ts(start), TimeUtil.ts(end), safeLimit, oldestFirst);
            }

            List<ScreenshotInfo> result = new ArrayList<>();
            for (Screenshot shot : shots) {
                result.add(new ScreenshotInfo(shot.getId(), shot.getSystemId(),
                        TimeUtil.iso(shot.getTakenAt()), shot.getApplicationName()));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Database error while listing screenshots", e);
        }
    }

    @GetMapping("/screenshots/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable("id") Long id) {
        try {
            Screenshot shot = screenshotDao.findById(id);
            if (shot == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such screenshot");
            }

            byte[] bytes;
            try {
                bytes = storage.read(shot.getFilePath());
            } catch (NoSuchFileException missing) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The image file is missing on the server");
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                    .body(bytes);
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Could not read screenshot", e);
        }
    }
}
