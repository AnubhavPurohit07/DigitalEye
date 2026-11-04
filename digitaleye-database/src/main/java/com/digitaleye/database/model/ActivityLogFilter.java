package com.digitaleye.database.model;

import java.sql.Timestamp;

/** Optional filters for the Activity Logs page. Any field left null is simply not filtered on. */
public class ActivityLogFilter {

    private Long systemId;
    private String category;
    private String status;
    private String applicationQuery;
    private Timestamp from;  // inclusive
    private Timestamp to;    // exclusive

    public Long getSystemId() { return systemId; }
    public void setSystemId(Long systemId) { this.systemId = systemId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getApplicationQuery() { return applicationQuery; }
    public void setApplicationQuery(String applicationQuery) { this.applicationQuery = applicationQuery; }

    public Timestamp getFrom() { return from; }
    public void setFrom(Timestamp from) { this.from = from; }

    public Timestamp getTo() { return to; }
    public void setTo(Timestamp to) { this.to = to; }
}
