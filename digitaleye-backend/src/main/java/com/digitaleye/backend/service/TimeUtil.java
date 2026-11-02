package com.digitaleye.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * "Today" always means today in the server's own time zone. The dashboard sends
 * plain dates (YYYY-MM-DD); everything here turns them into exact instants.
 */
public final class TimeUtil {

    public static final ZoneId ZONE = ZoneId.systemDefault();

    private TimeUtil() {
    }

    /** Parses "2026-10-22", or returns today's date when the parameter is missing/blank. */
    public static LocalDate parseDateOrToday(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now(ZONE);
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dates must look like 2026-10-22");
        }
    }

    public static Instant dayStart(LocalDate day) {
        return day.atStartOfDay(ZONE).toInstant();
    }

    /** Exclusive end of the day = start of the next one. */
    public static Instant dayEnd(LocalDate day) {
        return day.plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    public static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    public static String iso(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    /** Cuts text to fit a VARCHAR column instead of letting the INSERT fail. */
    public static String abbreviate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 3) + "...";
    }
}
