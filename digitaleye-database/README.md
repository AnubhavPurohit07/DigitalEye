# DigitalEye Database Module

MySQL 8 schema plus the JDBC data-access layer. The backend uses this module as a Maven dependency.

## Create the database

From the project root: `.\scripts\create-database.cmd`. It runs `install_all.sql`, which runs these in order:

1. `schema.sql` creates the `digitaleye` database and the base tables.
2. `migration_milestone_1_3.sql` creates `alert_rules`, `alerts` and `screenshots` and adds three default alert rules.
3. `migration_classification.sql` lets `activity_logs.category` hold the six categories and renames the old ENTERTAINMENT rows to NON_PRODUCTIVE.

Run it once on a new machine. To start over: `DROP DATABASE digitaleye;`.

## Connection settings

`src/main/resources/db.properties` (created by `.\scripts\setup.cmd` from `db.properties.example`) holds the URL, user and password. It is ignored by git.

## Build

`mvn clean install` (or `.\scripts\build-all.cmd` from the root) installs the jar into your local Maven cache. Do this again after every change here, because the backend reads that jar.

## Contents

- `DBConnection` opens the connection from `db.properties`.
- Models: `User`, `MonitoredSystem`, `ActivityLog`, `ActivityLogView`, `ActivityLogFilter`, `Alert`, `AlertRule`, `Screenshot`.
- DAOs: `UserDao`, `MonitoredSystemDao`, `ActivityLogDao`, `AlertDao`, `AlertRuleDao`, `ScreenshotDao`.

Do not run `DbConnectionTest`. It inserts a dummy admin user.
