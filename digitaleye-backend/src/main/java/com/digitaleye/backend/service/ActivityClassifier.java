package com.digitaleye.backend.service;

import com.digitaleye.backend.service.ClassificationCatalog.Ai;
import com.digitaleye.backend.service.ClassificationCatalog.AiKind;
import com.digitaleye.backend.service.ClassificationCatalog.Mixed;
import com.digitaleye.backend.service.ClassificationCatalog.Rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides which category a window title belongs to: Productive, Non-Productive, Troubleshooting,
 * Creative, Uncategorized (OTHER) or Idle.
 *
 * The agent only reports the active window's title, e.g. "Main.java - DigitalEye - Visual Studio Code"
 * or "Java Tutorial - YouTube - Google Chrome". The title is looked at in this order:
 *
 *   1. admin keywords (classification.blocked-keywords, classification.company-keywords)
 *   2. a browser window  -> the page title is judged: mixed platforms (YouTube, Reddit ...), AI tools,
 *                           known work / creative / support / non-work sites, then general keywords
 *   3. any other window  -> known desktop applications (IDEs, Office, system tools, editors, games ...);
 *                           if none matches, the title is tried as a web app / by file type
 *   4. nothing matched   -> Uncategorized (or whatever classification.unknown-app / unknown-page says)
 *
 * It is a rule engine, not a mind reader: it can only see the title. See the "reason" of each
 * Result to find out why something was filed where it was.
 */
public final class ActivityClassifier {

    /** What was decided and why. label is a short type ("Code editor / IDE"), reason names the matching rule. */
    public record Result(ActivityCategory category, String label, String reason) {
    }

    /** Settings an admin can change in application.properties. */
    public static final class Config {
        final List<String> companyKeywords;
        final List<String> blockedKeywords;
        final ActivityCategory unknownApp;
        final ActivityCategory unknownPage;
        final ActivityCategory aiUnclear;

        public Config(List<String> companyKeywords, List<String> blockedKeywords, ActivityCategory unknownApp,
                      ActivityCategory unknownPage, ActivityCategory aiUnclear) {
            this.companyKeywords = companyKeywords;
            this.blockedKeywords = blockedKeywords;
            this.unknownApp = unknownApp;
            this.unknownPage = unknownPage;
            this.aiUnclear = aiUnclear;
        }

        public static Config defaults() {
            return new Config(List.of(), List.of(), ActivityCategory.OTHER, ActivityCategory.OTHER, ActivityCategory.PRODUCTIVE);
        }

        /** Builds a Config from the raw property strings; bad values fall back to the defaults. */
        public static Config parse(String company, String blocked, String unknownApp, String unknownPage, String aiUnclear) {
            return new Config(
                    keywords(company),
                    keywords(blocked),
                    categoryOr(unknownApp, ActivityCategory.OTHER),
                    categoryOr(unknownPage, ActivityCategory.OTHER),
                    categoryOr(aiUnclear, ActivityCategory.PRODUCTIVE));
        }

        private static List<String> keywords(String csv) {
            List<String> list = new ArrayList<>();
            if (csv != null) {
                for (String part : csv.split(",")) {
                    String k = TextMatch.normalize(part);
                    if (!k.isEmpty()) {
                        list.add(k);
                    }
                }
            }
            return list;
        }

        private static ActivityCategory categoryOr(String value, ActivityCategory fallback) {
            ActivityCategory parsed = ActivityCategory.parseOrNull(value);
            // IDLE makes no sense as the answer to "what is this window"
            return parsed == null || parsed == ActivityCategory.IDLE ? fallback : parsed;
        }
    }

    private static final Pattern FILE_EXTENSION = Pattern.compile("[a-z0-9_)\\]-]\\.([a-z0-9]{1,10})(?![a-z0-9])");

    private final Config config;

    public ActivityClassifier(Config config) {
        this.config = config == null ? Config.defaults() : config;
    }

    // ------------------------------------------------------------------ entry point

    public Result classify(String windowTitle, String status) {
        if ("IDLE".equalsIgnoreCase(status)) {
            return new Result(ActivityCategory.IDLE, "Idle", "The agent reported the user as idle");
        }

        String norm = TextMatch.normalize(windowTitle);
        if (norm.isEmpty()) {
            return new Result(ActivityCategory.OTHER, "Unknown window", "The window has no title");
        }

        // 1. admin overrides
        String blocked = firstConfigHit(norm, config.blockedKeywords);
        if (blocked != null) {
            return new Result(ActivityCategory.NON_PRODUCTIVE, "Blocked by admin keyword", "Matches blocked keyword \"" + blocked + "\"");
        }
        String company = firstConfigHit(norm, config.companyKeywords);
        if (company != null) {
            return new Result(ActivityCategory.PRODUCTIVE, "Company keyword", "Matches company keyword \"" + company + "\"");
        }

        List<String> segs = TextMatch.segments(norm);

        // 2. browser window
        int browserAt = browserPartIndex(segs);
        if (browserAt >= 0) {
            List<String> pageParts = new ArrayList<>(segs.subList(0, browserAt));
            String pageText = String.join(" - ", pageParts);
            Result web = classifyWeb(pageText, pageParts);
            if (web == null) {
                web = byFileType(pageText);
            }
            if (web != null) {
                return web;
            }
            if (pageText.isEmpty()) {
                return new Result(ActivityCategory.OTHER, "Browser", "Browser window without a page title");
            }
            return new Result(config.unknownPage, "Uncategorized web page", "No known site or keyword in the page title");
        }

        // 3. desktop application
        String appPart = TextMatch.stripDecoration(segs.get(segs.size() - 1));
        Result app = classifyNative(appPart, norm, segs.size() > 1);
        if (app != null) {
            return app;
        }

        // an installed web app (PWA), or a window named after a site, e.g. "WhatsApp" or "Netflix"
        Result web = classifyWeb(norm, segs);
        if (web != null) {
            return web;
        }
        Result byType = byFileType(norm);
        if (byType != null) {
            return byType;
        }
        return new Result(config.unknownApp, "Uncategorized application", "No known application or keyword in the window title");
    }

    // ------------------------------------------------------------------ browsers

    /** Index of the segment that names the browser, or -1 if this is not a browser window. */
    private int browserPartIndex(List<String> segs) {
        int last = segs.size() - 1;
        String lastPart = TextMatch.stripDecoration(segs.get(last));
        if (isBrowserName(lastPart, segs.size() > 1)) {
            return last;
        }
        // Chrome adds the profile name after itself: "Page - Google Chrome - John"
        if (last >= 1) {
            String before = TextMatch.stripDecoration(segs.get(last - 1));
            if (TextMatch.equalsAny(before, ClassificationCatalog.BROWSER_NAMES_MULTIWORD)) {
                return last - 1;
            }
        }
        return -1;
    }

    /** A bare word like "arc" or "edge" only counts as a browser when it follows a page title, never on its own. */
    private boolean isBrowserName(String part, boolean hasPageBefore) {
        if (!TextMatch.equalsAny(part, ClassificationCatalog.BROWSER_NAMES)) {
            return false;
        }
        if (hasPageBefore) {
            return true;
        }
        return TextMatch.equalsAny(part, ClassificationCatalog.BROWSER_NAMES_MULTIWORD)
                || part.equals("google chrome") || part.equals("chrome") || part.equals("msedge") || part.equals("firefox");
    }

    // ------------------------------------------------------------------ desktop applications

    private Result classifyNative(String appPart, String wholeTitle, boolean multiPart) {
        // pass 1: the application name is normally the last part of the title
        for (Rule rule : ClassificationCatalog.NATIVE_RULES) {
            if (TextMatch.equalsAny(appPart, rule.exact)) {
                return new Result(rule.category, rule.label, "Application \"" + appPart + "\"");
            }
            String hit = TextMatch.firstHit(appPart, rule.phrases);
            if (hit != null) {
                return new Result(rule.category, rule.label, "Application matches \"" + hit + "\"");
            }
        }
        // pass 2: some applications put their name first ("Adobe Photoshop 2024 - [Untitled-1]")
        if (multiPart) {
            for (Rule rule : ClassificationCatalog.NATIVE_RULES) {
                String hit = TextMatch.firstHit(wholeTitle, rule.phrases);
                if (hit != null) {
                    return new Result(rule.category, rule.label, "Title matches \"" + hit + "\"");
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ web pages / web apps

    /** Judges the text of a page (or a web-app window). Returns null when nothing in it is recognised. */
    private Result classifyWeb(String text, List<String> parts) {
        if (text.isEmpty()) {
            return null;
        }

        for (Rule rule : ClassificationCatalog.PRE_WEB_RULES) {
            String hit = TextMatch.firstHit(text, rule.phrases);
            if (hit != null) {
                return new Result(rule.category, rule.label, "Page matches \"" + hit + "\"");
            }
        }

        // platforms whose category depends on the content
        for (Mixed mixed : ClassificationCatalog.MIXED) {
            String hit = TextMatch.firstHit(text, mixed.phrases);
            if (hit != null) {
                return resolveMixed(mixed, hit, text);
            }
        }

        // AI tools
        for (Ai ai : ClassificationCatalog.AI_TOOLS) {
            String hit = TextMatch.firstHit(text, ai.phrases);
            if (hit == null) {
                hit = exactPartHit(parts, ai.exactPart);
            }
            if (hit != null) {
                return resolveAi(ai, hit, text);
            }
        }

        // known sites, most reliable first
        Result r = firstRule(ClassificationCatalog.WEB_PRODUCTIVE, text, parts, false);
        if (r != null) {
            return r;
        }
        r = firstRule(ClassificationCatalog.WEB_CREATIVE, text, parts, false);
        if (r != null) {
            return r;
        }
        r = firstRule(ClassificationCatalog.WEB_TROUBLESHOOTING, text, parts, false);
        if (r != null) {
            return r;
        }
        r = firstRule(ClassificationCatalog.WEB_NON_PRODUCTIVE, text, parts, true);
        if (r != null) {
            return r;
        }
        r = firstRule(ClassificationCatalog.WEB_NEUTRAL, text, parts, false);
        if (r != null) {
            return r;
        }

        // general keywords in the page title
        String hit = TextMatch.firstHit(text, ClassificationCatalog.GENERIC_PRODUCTIVE);
        if (hit != null) {
            return new Result(ActivityCategory.PRODUCTIVE, "Technical / educational content", "Page mentions \"" + hit + "\"");
        }
        hit = TextMatch.firstHit(text, ClassificationCatalog.GENERIC_TROUBLESHOOTING);
        if (hit != null) {
            return new Result(ActivityCategory.TROUBLESHOOTING, "Troubleshooting content", "Page mentions \"" + hit + "\"");
        }
        hit = TextMatch.firstHit(text, ClassificationCatalog.GENERIC_NON_PRODUCTIVE);
        if (hit != null) {
            return new Result(ActivityCategory.NON_PRODUCTIVE, "Entertainment / personal content", "Page mentions \"" + hit + "\"");
        }
        return null;
    }

    private Result firstRule(List<Rule> rules, String text, List<String> parts, boolean allowSoftSkip) {
        for (Rule rule : rules) {
            if (rule.soft && allowSoftSkip && workSignal(text) != null) {
                continue;
            }
            for (String part : parts) {
                if (TextMatch.equalsAny(TextMatch.stripDecoration(part), rule.exact)) {
                    return new Result(rule.category, rule.label, "Page \"" + part + "\"");
                }
            }
            String hit = TextMatch.firstHit(text, rule.phrases);
            if (hit != null) {
                return new Result(rule.category, rule.label, "Page matches \"" + hit + "\"");
            }
        }
        return null;
    }

    private String exactPartHit(List<String> parts, String[] names) {
        for (String part : parts) {
            String p = TextMatch.stripDecoration(part);
            if (TextMatch.equalsAny(p, names)) {
                return p;
            }
        }
        return null;
    }

    /** Something in the text that points to real work: a technical keyword or a known work platform. */
    private String workSignal(String text) {
        String hit = TextMatch.firstHit(text, ClassificationCatalog.GENERIC_PRODUCTIVE);
        if (hit != null) {
            return hit;
        }
        for (Rule rule : ClassificationCatalog.WEB_PRODUCTIVE) {
            hit = TextMatch.firstHit(text, rule.phrases);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private Result resolveMixed(Mixed mixed, String platformHit, String text) {
        String work = workSignal(text);
        if (work != null) {
            return new Result(mixed.workCategory, mixed.workLabel,
                    mixed.name + " page about \"" + work + "\"");
        }
        if (mixed.search) {
            String personal = TextMatch.firstHit(text, ClassificationCatalog.GENERIC_NON_PRODUCTIVE);
            if (personal != null) {
                return new Result(ActivityCategory.NON_PRODUCTIVE, "Personal search", "Search mentions \"" + personal + "\"");
            }
        }
        return new Result(mixed.otherwiseCategory, mixed.otherwiseLabel,
                mixed.name + " page with no work-related keyword");
    }

    private Result resolveAi(Ai ai, String toolHit, String text) {
        switch (ai.kind) {
            case CODING:
                return new Result(ActivityCategory.PRODUCTIVE, "AI coding tool", "AI tool \"" + toolHit + "\" is a coding assistant");
            case CREATIVE:
                return new Result(ActivityCategory.CREATIVE, "AI creative tool", "AI tool \"" + toolHit + "\" generates images / video / audio");
            case COMPANION:
                return new Result(ActivityCategory.NON_PRODUCTIVE, "AI companion / entertainment chatbot",
                        "AI tool \"" + toolHit + "\" is a companion chatbot");
            default:
                break;
        }

        String work = workSignal(text);
        if (work == null) {
            work = TextMatch.firstHit(text, ClassificationCatalog.AI_WORK_EXTRA);
        }
        if (work != null) {
            return new Result(ActivityCategory.PRODUCTIVE, "AI tool - work use", ai.name + " chat about \"" + work + "\"");
        }
        String personal = TextMatch.firstHit(text, ClassificationCatalog.AI_PERSONAL);
        if (personal != null) {
            return new Result(ActivityCategory.NON_PRODUCTIVE, "AI tool - personal use", ai.name + " chat about \"" + personal + "\"");
        }
        return new Result(config.aiUnclear, "AI tool - purpose unclear",
                ai.name + " in use, but the title does not say what for (counted as "
                        + config.aiUnclear.label().toLowerCase(Locale.ROOT) + ")");
    }

    // ------------------------------------------------------------------ file types

    private Result byFileType(String text) {
        Matcher m = FILE_EXTENSION.matcher(text);
        while (m.find()) {
            String ext = m.group(1);
            for (Rule rule : ClassificationCatalog.EXTENSION_RULES) {
                if (TextMatch.equalsAny(ext, rule.phrases)) {
                    return new Result(rule.category, rule.label, "Title contains a ." + ext + " file");
                }
            }
        }
        return null;
    }

    private static String firstConfigHit(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (TextMatch.has(text, keyword)) {
                return keyword;
            }
        }
        return null;
    }

    /** Used by tests / the tester endpoint to show which kind of AI rule fired. */
    static AiKind kindOf(Ai ai) {
        return ai.kind;
    }
}
