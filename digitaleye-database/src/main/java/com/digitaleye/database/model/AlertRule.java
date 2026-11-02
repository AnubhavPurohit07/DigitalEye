package com.digitaleye.database.model;

/**
 * An admin-editable alert rule. Which fields matter depends on ruleType:
 * IDLE_THRESHOLD uses thresholdMinutes, UNAUTHORIZED_APP uses keyword,
 * OFF_HOURS uses startHour/endHour (the allowed working window).
 */
public class AlertRule {

    public static final String IDLE_THRESHOLD = "IDLE_THRESHOLD";
    public static final String UNAUTHORIZED_APP = "UNAUTHORIZED_APP";
    public static final String OFF_HOURS = "OFF_HOURS";

    private Long id;
    private String name;
    private String ruleType;
    private Integer thresholdMinutes;
    private String keyword;
    private Integer startHour;
    private Integer endHour;
    private String severity;
    private boolean enabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public Integer getThresholdMinutes() { return thresholdMinutes; }
    public void setThresholdMinutes(Integer thresholdMinutes) { this.thresholdMinutes = thresholdMinutes; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public Integer getStartHour() { return startHour; }
    public void setStartHour(Integer startHour) { this.startHour = startHour; }

    public Integer getEndHour() { return endHour; }
    public void setEndHour(Integer endHour) { this.endHour = endHour; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
