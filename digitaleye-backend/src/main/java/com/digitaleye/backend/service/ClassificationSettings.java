package com.digitaleye.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads the optional classification.* settings from application.properties once at start-up and
 * hands them to the classifier. Every setting has a default, so adding none of them is fine.
 *
 *   classification.company-keywords   comma separated words that mean "our own site / tool" -> Productive
 *   classification.blocked-keywords   comma separated words that are never allowed          -> Non-Productive
 *   classification.unknown-app        category for desktop apps nothing matched (default OTHER)
 *   classification.unknown-page       category for web pages nothing matched    (default OTHER)
 *   classification.ai-unclear         category for an AI tool whose title says nothing about the task (default PRODUCTIVE)
 */
@Component
public class ClassificationSettings {

    public ClassificationSettings(
            @Value("${classification.company-keywords:}") String companyKeywords,
            @Value("${classification.blocked-keywords:}") String blockedKeywords,
            @Value("${classification.unknown-app:OTHER}") String unknownApp,
            @Value("${classification.unknown-page:OTHER}") String unknownPage,
            @Value("${classification.ai-unclear:PRODUCTIVE}") String aiUnclear) {
        AppClassifier.configure(ActivityClassifier.Config.parse(
                companyKeywords, blockedKeywords, unknownApp, unknownPage, aiUnclear));
    }
}
