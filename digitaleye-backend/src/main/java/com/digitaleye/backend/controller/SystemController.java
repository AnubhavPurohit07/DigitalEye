package com.digitaleye.backend.controller;

import com.digitaleye.backend.dto.SystemSummaryResponse;
import com.digitaleye.backend.service.SystemSummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;

/**
 * Admin-only endpoint backing the dashboard's "Client Systems" table. Only reachable with a
 * valid JWT for a user whose role is ADMIN (enforced by SecurityConfig's "/api/admin/**"
 * rule). Each row carries the system's status, what it is doing right now, and today's
 * Activity Score.
 */
@RestController
@RequestMapping("/api/admin")
public class SystemController {

    private final SystemSummaryService summaryService;

    public SystemController(SystemSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/systems")
    public ResponseEntity<List<SystemSummaryResponse>> listSystems() {
        try {
            return ResponseEntity.ok(summaryService.summarizeAll());
        } catch (SQLException e) {
            throw new RuntimeException("Database error while fetching systems", e);
        }
    }
}
