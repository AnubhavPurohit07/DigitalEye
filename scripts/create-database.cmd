@echo off
cd /d "%~dp0..\digitaleye-database"
echo Creating the digitaleye database (tables, default alert rules, categories).
echo Enter your MySQL root password when asked.
echo.
mysql -u root -p -e "source install_all.sql"
if errorlevel 1 goto fail
echo.
echo Database ready.
pause
exit /b 0
:fail
echo.
echo The database step failed. Read the message above.
echo - "mysql is not recognized": add MySQL's bin folder to PATH, or run the three .sql files in MySQL Workbench in this order:
echo   schema.sql, migration_milestone_1_3.sql, migration_classification.sql
echo - "Table already exists": the database was already created earlier, so you can skip this step.
pause
exit /b 1
