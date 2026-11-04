-- =====================================================================
-- DigitalEye: Digital Activity Monitoring System
-- Database schema (MySQL 8+)
--
-- Run this once to create the database and all tables:
--   mysql -u root -p < schema.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS digitaleye
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE digitaleye;

-- ---------------------------------------------------------------------
-- Users: both monitored users and admins live here, split by role.
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    role          ENUM('ADMIN', 'USER') NOT NULL DEFAULT 'USER',
    job_title     VARCHAR(100),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Systems: one row per monitored machine. A user can have more than
-- one machine registered against them.
-- ---------------------------------------------------------------------
CREATE TABLE systems (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    system_name      VARCHAR(100) NOT NULL,
    status           ENUM('ONLINE', 'OFFLINE') NOT NULL DEFAULT 'OFFLINE',
    last_heartbeat   TIMESTAMP NULL,
    monitoring_since TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Activity logs: one row per app session OR idle period.
-- Idle time is just a row with application_name = 'Idle' and
-- status = 'IDLE', so the timeline/usage queries only ever touch
-- this one table.
-- ---------------------------------------------------------------------
CREATE TABLE activity_logs (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_id        BIGINT NOT NULL,
    application_name VARCHAR(150) NOT NULL,
    status           ENUM('ACTIVE', 'IDLE') NOT NULL DEFAULT 'ACTIVE',
    category         ENUM('PRODUCTIVE', 'ENTERTAINMENT', 'NON_PRODUCTIVE', 'IDLE', 'OTHER') NOT NULL DEFAULT 'OTHER',
    start_time       TIMESTAMP NOT NULL,
    end_time         TIMESTAMP NULL,
    FOREIGN KEY (system_id) REFERENCES systems(id) ON DELETE CASCADE,
    INDEX idx_activity_system_time (system_id, start_time)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Screenshots: metadata + file path only. The actual image bytes live
-- on disk (or object storage later); the DB just tracks where.
-- ---------------------------------------------------------------------
CREATE TABLE screenshots (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_id        BIGINT NOT NULL,
    application_name VARCHAR(150),
    file_path        VARCHAR(500) NOT NULL,
    captured_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (system_id) REFERENCES systems(id) ON DELETE CASCADE,
    INDEX idx_screenshot_system_time (system_id, captured_at)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- App classification: lets the admin categorize applications
-- (e.g. "Eclipse" -> PRODUCTIVE) instead of hardcoding it in Java.
-- ---------------------------------------------------------------------
CREATE TABLE app_classification (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_name VARCHAR(150) NOT NULL UNIQUE,
    category         ENUM('PRODUCTIVE', 'ENTERTAINMENT', 'NON_PRODUCTIVE', 'OTHER') NOT NULL DEFAULT 'OTHER'
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Alert rules: admin-configurable conditions that generate alerts.
-- ---------------------------------------------------------------------
CREATE TABLE alert_rules (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_type         ENUM('IDLE_THRESHOLD', 'UNAUTHORIZED_APP', 'OFF_HOURS') NOT NULL,
    threshold_seconds INT,          -- used by IDLE_THRESHOLD / OFF_HOURS
    target_app        VARCHAR(150), -- used by UNAUTHORIZED_APP
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_by        BIGINT,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Alerts: an actual triggered event, tied back to the rule that fired it.
-- ---------------------------------------------------------------------
CREATE TABLE alerts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_id       BIGINT NOT NULL,
    rule_id         BIGINT,
    severity        ENUM('HIGH', 'MEDIUM', 'LOW') NOT NULL DEFAULT 'MEDIUM',
    message         VARCHAR(255) NOT NULL,
    triggered_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (system_id) REFERENCES systems(id) ON DELETE CASCADE,
    FOREIGN KEY (rule_id) REFERENCES alert_rules(id) ON DELETE SET NULL,
    INDEX idx_alert_system_time (system_id, triggered_at)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Monitoring policies: per-system config (screenshot interval, idle
-- threshold). system_id = NULL means "global default".
-- ---------------------------------------------------------------------
CREATE TABLE monitoring_policies (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_id                BIGINT NULL,
    screenshot_interval_secs INT NOT NULL DEFAULT 300,
    idle_threshold_secs      INT NOT NULL DEFAULT 60,
    updated_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (system_id) REFERENCES systems(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Daily input-activity summary: counts only (mouse clicks, keystrokes,
-- websites visited) - never keystroke content.
-- ---------------------------------------------------------------------
CREATE TABLE input_activity_summary (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_id        BIGINT NOT NULL,
    summary_date     DATE NOT NULL,
    mouse_clicks     INT NOT NULL DEFAULT 0,
    keystrokes       INT NOT NULL DEFAULT 0,
    websites_visited INT NOT NULL DEFAULT 0,
    FOREIGN KEY (system_id) REFERENCES systems(id) ON DELETE CASCADE,
    UNIQUE KEY uq_system_date (system_id, summary_date)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Generated reports: a persisted record each time "Generate Report" is
-- used, so past reports can be looked up again later.
-- ---------------------------------------------------------------------
CREATE TABLE reports (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_id    BIGINT NOT NULL,
    report_type  ENUM('DAILY', 'WEEKLY') NOT NULL,
    period_start DATE NOT NULL,
    period_end   DATE NOT NULL,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    generated_by BIGINT,
    FOREIGN KEY (system_id) REFERENCES systems(id) ON DELETE CASCADE,
    FOREIGN KEY (generated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Seed data: a default global policy so the system has sane defaults
-- from the start. NOTE: the admin user's real password hash should be
-- generated by the backend's registration/signup flow (BCrypt) once
-- that exists - don't rely on inserting a hash by hand here.
-- ---------------------------------------------------------------------
INSERT INTO monitoring_policies (system_id, screenshot_interval_secs, idle_threshold_secs)
VALUES (NULL, 300, 60);
