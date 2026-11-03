package com.digitaleye.backend.service;

/**
 * The one entry point the rest of the backend uses to classify an activity. It keeps the
 * original static method (AgentController calls it when a new activity session opens) and
 * delegates to {@link ActivityClassifier}, which holds the rules.
 *
 * The settings (company keywords, blocked keywords, what to do with unknown apps ...) are
 * applied once at start-up by {@link ClassificationSettings}.
 */
public final class AppClassifier {

    private static volatile ActivityClassifier engine = new ActivityClassifier(ActivityClassifier.Config.defaults());

    private AppClassifier() {
    }

    /** Category name stored in activity_logs.category: PRODUCTIVE, NON_PRODUCTIVE, TROUBLESHOOTING, CREATIVE, OTHER or IDLE. */
    public static String classify(String applicationName, String status) {
        return engine.classify(applicationName, status).category().name();
    }

    /** Same decision with the human label and the reason - used by the classifier tester on the dashboard. */
    public static ActivityClassifier.Result classifyDetailed(String applicationName, String status) {
        return engine.classify(applicationName, status);
    }

    static void configure(ActivityClassifier.Config config) {
        engine = new ActivityClassifier(config);
    }
}
