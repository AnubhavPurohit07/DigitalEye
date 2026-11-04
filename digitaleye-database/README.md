# DigitalEye Database Module

This is the `database` branch's content: schema + JDBC data access layer.

## Setup
1. Install MySQL if you don't have it, and make sure it's running.
2. Run the schema:
   ```
   mysql -u root -p < schema.sql
   ```
   This creates the `digitaleye` database and every table.
3. Open `src/main/resources/db.properties` and set your own MySQL
   username/password (and URL, if not running on localhost:3306).
4. Open this folder in VS Code / IntelliJ as a Maven project - it'll pull
   in the MySQL driver automatically.
5. Run `DbConnectionTest.java` (it has a `main` method). If it prints
   "Database connection and DAO layer are working." at the end, you're
   good.

## What's included so far
- `schema.sql` - every table for the whole system (users, systems,
  activity_logs, screenshots, alerts, alert_rules, app_classification,
  monitoring_policies, input_activity_summary, reports)
- `DBConnection` - shared JDBC connection helper, reads db.properties
- Models: `User`, `MonitoredSystem`, `ActivityLog`
- DAOs (CRUD + the queries the dashboard needs): `UserDao`,
  `MonitoredSystemDao`, `ActivityLogDao`

## Still to add
DAOs for the remaining tables: `ScreenshotDao`, `AlertDao`, `AlertRuleDao`,
`MonitoringPolicyDao`, `AppClassificationDao`, `InputActivitySummaryDao`,
`ReportDao`. Same pattern as the three already here - copy one of them
and swap the table/fields.

## How this connects to the rest of the project
The Backend (Spring Boot) module will use these DAOs directly rather than
writing its own JDBC code - either by adding this module as a dependency,
or by copying these `model`/`dao` packages into the backend project. This
gets finalized once the backend module exists.
