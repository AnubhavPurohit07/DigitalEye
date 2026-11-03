package com.digitaleye.backend.controller;

import com.digitaleye.backend.service.ActivityCategory;
import com.digitaleye.backend.service.ActivityClassifier;
import com.digitaleye.backend.service.AppClassifier;
import com.digitaleye.database.dao.ActivityLogDao;
import com.digitaleye.database.model.ActivityLog;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tools for the activity classification rules:
 *   - a tester: type any window title and see which category the rules give it and why
 *   - "re-apply": walk through every stored activity row and re-classify it with the current rules
 *     (new activity is classified once, when it arrives, so old rows keep their old category until this runs)
 */
@RestController
@RequestMapping("/api/admin/classification")
public class ClassificationController {

    private static final int PAGE_SIZE = 2000;
    private static final int UPDATE_BATCH = 1000;
    private static final int MAX_TITLE_LENGTH = 500;

    public record TestResult(String title, String category, String label, String reason) {
    }

    public record ReapplyResult(long rowsChecked, long rowsUpdated, Map<String, Long> updatedInto, long milliseconds) {
    }

    private final ActivityLogDao activityLogDao = new ActivityLogDao();
    private final AtomicBoolean reapplyRunning = new AtomicBoolean(false);

    /** What would the rules say about this window title? Nothing is stored. */
    @GetMapping("/test")
    public TestResult test(@RequestParam(name = "title") String title,
                           @RequestParam(name = "status", defaultValue = "ACTIVE") String status) {
        String clipped = title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
        ActivityClassifier.Result result = AppClassifier.classifyDetailed(clipped, status);
        return new TestResult(clipped, result.category().name(), result.label(), result.reason());
    }

    /** Re-classifies every stored activity row with the current rules. Safe to run more than once. */
    @PostMapping("/reapply")
    public ReapplyResult reapply() {
        if (!reapplyRunning.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Re-classification is already running");
        }
        long started = System.currentTimeMillis();
        try {
            long checked = 0;
            long updated = 0;
            Map<String, Long> updatedInto = new LinkedHashMap<>();
            Map<String, String> cache = new HashMap<>();
            Map<Long, String> pending = new LinkedHashMap<>();

            long lastId = 0;
            while (true) {
                List<ActivityLog> page = activityLogDao.findPageAfterId(lastId, PAGE_SIZE);
                if (page.isEmpty()) {
                    break;
                }
                for (ActivityLog row : page) {
                    lastId = Math.max(lastId, row.getId());
                    checked++;

                    String wanted = categoryFor(row, cache);
                    if (!wanted.equals(row.getCategory())) {
                        pending.put(row.getId(), wanted);
                        updatedInto.merge(wanted, 1L, Long::sum);
                        if (pending.size() >= UPDATE_BATCH) {
                            updated += activityLogDao.updateCategories(pending);
                            pending.clear();
                        }
                    }
                }
            }
            updated += activityLogDao.updateCategories(pending);

            return new ReapplyResult(checked, updated, updatedInto, System.currentTimeMillis() - started);
        } catch (SQLException e) {
            throw new RuntimeException("Database error while re-applying the classification rules", e);
        } finally {
            reapplyRunning.set(false);
        }
    }

    private String categoryFor(ActivityLog row, Map<String, String> cache) {
        if ("IDLE".equalsIgnoreCase(row.getStatus())) {
            return ActivityCategory.IDLE.name();
        }
        String title = row.getApplicationName() == null ? "" : row.getApplicationName();
        String category = cache.get(title);
        if (category == null) {
            category = AppClassifier.classify(title, row.getStatus());
            if (cache.size() > 50_000) {
                cache.clear();
            }
            cache.put(title, category);
        }
        return category;
    }
}
