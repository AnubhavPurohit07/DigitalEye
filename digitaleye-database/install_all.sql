-- Creates a brand-new DigitalEye database in one go.
-- Run it from this folder (scripts\create-database.cmd does exactly that):
--     mysql -u root -p -e "source install_all.sql"
--
-- It runs the three files in the right order:
--   1. schema.sql                    - the database and the base tables
--   2. migration_milestone_1_3.sql   - alert rules, alerts and screenshot tables
--   3. migration_classification.sql  - the six activity categories
--
-- Run it ONCE on a new machine. To start again from nothing, first run:  DROP DATABASE digitaleye;

source schema.sql;
source migration_milestone_1_3.sql;
source migration_classification.sql;
