package com.digitaleye.database.model;

import java.sql.Timestamp;

/** Metadata for one captured screenshot; the image bytes live in a file on the backend's disk. */
public class Screenshot {

    private Long id;
    private Long systemId;
    private Timestamp takenAt;
    private String applicationName;
    private String filePath;
    private int fileSize;

    public Screenshot() {
    }

    public Screenshot(Long systemId, Timestamp takenAt, String applicationName, String filePath, int fileSize) {
        this.systemId = systemId;
        this.takenAt = takenAt;
        this.applicationName = applicationName;
        this.filePath = filePath;
        this.fileSize = fileSize;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSystemId() { return systemId; }
    public void setSystemId(Long systemId) { this.systemId = systemId; }

    public Timestamp getTakenAt() { return takenAt; }
    public void setTakenAt(Timestamp takenAt) { this.takenAt = takenAt; }

    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public int getFileSize() { return fileSize; }
    public void setFileSize(int fileSize) { this.fileSize = fileSize; }
}
