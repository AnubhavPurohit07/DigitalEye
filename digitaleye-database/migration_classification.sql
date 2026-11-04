-- DigitalEye: migration for the activity classification upgrade.
--
-- Run this ONCE, from the folder that contains this file:
--     mysql -u root -p
--     mysql> source migration_classification.sql;
--
-- What it does:
--   1. Makes activity_logs.category a plain text column (VARCHAR) so it can hold the new
--      categories: PRODUCTIVE, NON_PRODUCTIVE, TROUBLESHOOTING, CREATIVE, OTHER and IDLE.
--      (If the column was an ENUM before, this is what lets the new names be saved.)
--   2. Renames the old category ENTERTAINMENT to NON_PRODUCTIVE in the existing rows.
--
-- Nothing is deleted. It is safe to run again - the second run changes nothing.
-- After this, use "Re-apply rules" on the dashboard's Activity Logs page to re-classify
-- your OLD activity with the new, much larger rule set.

USE digitaleye;

ALTER TABLE activity_logs
    MODIFY COLUMN category VARCHAR(30) NULL DEFAULT 'OTHER';

UPDATE activity_logs SET category = 'NON_PRODUCTIVE' WHERE category = 'ENTERTAINMENT';
UPDATE activity_logs SET category = 'OTHER' WHERE category IS NULL OR category = '';

SELECT category, COUNT(*) AS rows_in_category FROM activity_logs GROUP BY category ORDER BY rows_in_category DESC;
