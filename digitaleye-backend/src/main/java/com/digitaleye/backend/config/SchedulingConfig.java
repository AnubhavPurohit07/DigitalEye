package com.digitaleye.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Switches on @Scheduled methods (the client-health and idle-alert checks). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
