package com.digitaleye.backend.dto;

/** Metadata for one screenshot. The image itself is fetched separately from /api/admin/screenshots/{id}/image. */
public record ScreenshotInfo(Long id, Long systemId, String takenAt, String application) {
}
