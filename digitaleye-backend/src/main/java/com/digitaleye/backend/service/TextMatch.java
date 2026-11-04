package com.digitaleye.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Small text helpers for the classifier. The important one is {@link #has}: it finds a phrase
 * only at word boundaries, so "word" matches "Report - Word" but not "Wordle" or "Crossword",
 * and "excel" does not match "Excellent". A phrase ending in * is a prefix match
 * ("tutorial*" matches "tutorials" and "tutorial").
 */
final class TextMatch {

    /** " - ", " \u2013 ", " \u2014 ", " | ", " \u00b7 " between the parts of a window title. */
    private static final Pattern SEPARATOR = Pattern.compile("\\s+[-\u2013\u2014|\u00b7\u2022]\\s+");
    private static final Pattern INVISIBLE = Pattern.compile("[\u200b-\u200f\u202a-\u202e\u2060\ufeff]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern TRAILING_DECORATION = Pattern.compile("\\s*[(\\[][^)\\]]*[)\\]]\\s*$");

    private TextMatch() {
    }

    /** Lower-cases, removes invisible characters (Edge puts one inside "Microsoft Edge") and tidies whitespace. */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = INVISIBLE.matcher(raw).replaceAll("");
        s = s.replace('\u00a0', ' ').replace('\u2019', '\'').replace('\u2018', '\'');
        s = SPACES.matcher(s).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
        if (s.endsWith(".exe")) {
            s = s.substring(0, s.length() - 4);
        }
        return s;
    }

    /** Splits a normalised title into its parts: "a - b - c" -> [a, b, c]. */
    static List<String> segments(String normalized) {
        List<String> parts = new ArrayList<>();
        if (normalized.isEmpty()) {
            return parts;
        }
        for (String part : SEPARATOR.split(normalized)) {
            String p = part.trim();
            if (!p.isEmpty()) {
                parts.add(p);
            }
        }
        if (parts.isEmpty()) {
            parts.add(normalized);
        }
        return parts;
    }

    /** "excel (product activation failed)" -> "excel"; "outlook [new]" -> "outlook". */
    static String stripDecoration(String segment) {
        String s = segment;
        for (int i = 0; i < 3; i++) {
            String next = TRAILING_DECORATION.matcher(s).replaceFirst("").trim();
            if (next.isEmpty() || next.equals(s)) {
                break;
            }
            s = next;
        }
        return s;
    }

    /** True if the phrase occurs in the text at word boundaries (see class comment). */
    static boolean has(String text, String phrase) {
        if (text == null || phrase == null || phrase.isEmpty()) {
            return false;
        }
        boolean prefixOnly = phrase.endsWith("*");
        String p = prefixOnly ? phrase.substring(0, phrase.length() - 1) : phrase;
        if (p.isEmpty()) {
            return false;
        }
        boolean checkLeft = Character.isLetterOrDigit(p.charAt(0));
        boolean checkRight = !prefixOnly && Character.isLetterOrDigit(p.charAt(p.length() - 1));

        int from = 0;
        while (true) {
            int idx = text.indexOf(p, from);
            if (idx < 0) {
                return false;
            }
            int end = idx + p.length();
            boolean leftOk = !checkLeft || idx == 0 || !Character.isLetterOrDigit(text.charAt(idx - 1));
            boolean rightOk = !checkRight || end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            from = idx + 1;
        }
    }

    /** The first phrase from the list found in the text, or null. */
    static String firstHit(String text, String[] phrases) {
        if (phrases == null) {
            return null;
        }
        for (String phrase : phrases) {
            if (has(text, phrase)) {
                return phrase.endsWith("*") ? phrase.substring(0, phrase.length() - 1) : phrase;
            }
        }
        return null;
    }

    static boolean equalsAny(String text, String[] values) {
        if (values == null) {
            return false;
        }
        for (String v : values) {
            if (v.equals(text)) {
                return true;
            }
        }
        return false;
    }
}
