package com.digitaleye.backend.dto;

import java.util.List;

/** One page of the Activity Logs list. page is 0-based. */
public record LogPage(List<LogRow> items, long total, int page, int size) {

    public record LogRow(Long id, Long systemId, String systemName, String application,
                         String status, String category, String start, String end,
                         long durationSeconds, boolean open) {
    }
}
