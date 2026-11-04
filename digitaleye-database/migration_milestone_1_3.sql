-- DigitalEye: migration for milestones 1-3 (activity analytics, alerts + client health, screenshots).
--
-- Run this ONCE, after schema.sql, from the folder that contains this file:
--     mysql -u root -p
--     mysql> source migration_milestone_1_3.sql;
--
-- What it does: re-creates three tables that nothing used until now
-- (alert_rules, alerts, screenshots) with the exact columns the new code
-- needs, and seeds a few default alert rules. activity_logs, systems and
-- users are NOT touched, so your existing data stays as it is.
--
-- Safe to run again: it drops and re-creates only those three tables, so
-- rerunning resets alerts, alert rules and screenshot records (the image
-- files themselves stay on disk in the backend's screenshots folder).

USE digitaleye;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS alerts;
DROP TABLE IF EXISTS alert_rules;
DROP TABLE IF EXISTS screenshots;
SET FOREIGN_KEY_CHECKS = 1;

-- Admin-editable rules the backend checks activity against.
--   rule_type = IDLE_THRESHOLD   -> uses threshold_minutes
--   rule_type = UNAUTHORIZED_APP -> uses keyword (case-insensitive match on the window title)
--   rule_type = OFF_HOURS        -> uses start_hour / end_hour (the allowed working window, 0-23)
CREATE TABLE alert_rules (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    rule_type         VARCHAR(30)  NOT NULL,
    threshold_minutes INT          NULL,
    keyword           VARCHAR(100) NULL,
    start_hour        TINYINT      NULL,
    end_hour          TINYINT      NULL,
    severity          VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    enabled           TINYINT(1)   NOT NULL DEFAULT 1,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- One row per triggered alert. rule_id is NULL for built-in alerts (AGENT_OFFLINE).
-- The unique key stops the idle checker from raising the same alert for the
-- same idle session over and over.
CREATE TABLE alerts (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    system_id        BIGINT       NOT NULL,
    rule_id          BIGINT       NULL,
    activity_log_id  BIGINT       NULL,
    alert_type       VARCHAR(30)  NOT NULL,
    severity         VARCHAR(10)  NOT NULL,
    message          VARCHAR(255) NOT NULL,
    application_name VARCHAR(255) NULL,
    acknowledged     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_alert_rule_log (rule_id, activity_log_id),
    KEY idx_alerts_created (created_at),
    KEY idx_alerts_system_created (system_id, created_at)
);

-- Metadata for each captured screenshot. The image itself is a file on the
-- backend's disk; file_path is relative to the backend's screenshot folder.
CREATE TABLE screenshots (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    system_id        BIGINT       NOT NULL,
    taken_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    application_name VARCHAR(255) NULL,
    file_path        VARCHAR(500) NOT NULL,
    file_size        INT          NOT NULL DEFAULT 0,
    KEY idx_shots_system_taken (system_id, taken_at)
);

-- Default rules. Edit, disable or add more from the dashboard's "Alerts & Rules" page.
INSERT INTO alert_rules (name, rule_type, threshold_minutes, keyword, start_hour, end_hour, severity, enabled) VALUES
    ('Idle time exceeded',           'IDLE_THRESHOLD',   20,   NULL,      NULL, NULL, 'MEDIUM', 1),
    ('Game application detected',    'UNAUTHORIZED_APP', NULL, 'game',    NULL, NULL, 'HIGH',   1),
    ('Unauthorized site: YouTube',   'UNAUTHORIZED_APP', NULL, 'youtube', NULL, NULL, 'MEDIUM', 1),
    ('Activity outside work hours',  'OFF_HOURS',        NULL, NULL,      9,    18,   'MEDIUM', 1);

-- Fast range queries for the analytics endpoints. (If MySQL says this index
-- already exists, that is fine - ignore the error.)
CREATE INDEX idx_activity_system_start ON activity_logs (system_id, start_time);
