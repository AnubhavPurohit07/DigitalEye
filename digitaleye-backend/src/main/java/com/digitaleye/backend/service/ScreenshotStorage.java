package com.digitaleye.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Keeps screenshot images as plain files on the backend's disk, laid out as
 *   <storage-dir>/<systemId>/<yyyy-MM-dd>/<HHmmss>-<random>.jpg
 * The database only stores the relative path. Nothing here is served directly:
 * images only leave through the admin-only /api/admin/screenshots/{id}/image endpoint.
 */
@Service
public class ScreenshotStorage {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HHmmss");

    private final Path root;

    public ScreenshotStorage(@Value("${screenshot.storage-dir:screenshots}") String storageDir) {
        this.root = Paths.get(storageDir).toAbsolutePath().normalize();
    }

    /** Writes the image and returns its path relative to the storage folder (forward slashes). */
    public String save(Long systemId, Instant takenAt, byte[] jpegBytes) throws IOException {
        ZonedDateTime when = takenAt.atZone(TimeUtil.ZONE);
        String fileName = CLOCK.format(when) + "-" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
        Path relative = Paths.get(String.valueOf(systemId), when.toLocalDate().toString(), fileName);

        Path full = root.resolve(relative).normalize();
        Files.createDirectories(full.getParent());
        Files.write(full, jpegBytes);

        return relative.toString().replace('\\', '/');
    }

    public byte[] read(String relativePath) throws IOException {
        Path full = root.resolve(relativePath).normalize();
        if (!full.startsWith(root)) {
            throw new IOException("Screenshot path escapes the storage folder");
        }
        return Files.readAllBytes(full);
    }

    public Path getRoot() {
        return root;
    }
}
