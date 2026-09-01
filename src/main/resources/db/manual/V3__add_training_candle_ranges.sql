-- Apply once to existing MySQL databases before deploying the matching application.
-- This project uses Hibernate ddl-auto=update rather than Flyway, so this migration
-- is intentionally manual and includes the required legacy-data backfill.

ALTER TABLE training_session_chart
    ADD COLUMN analysis_bars INT NULL,
    ADD COLUMN training_bars INT NULL;

UPDATE training_session_chart
SET analysis_bars = LEAST(60, bars),
    training_bars = bars - LEAST(60, bars)
WHERE analysis_bars IS NULL
   OR training_bars IS NULL;

ALTER TABLE training_session_chart
    MODIFY analysis_bars INT NOT NULL,
    MODIFY training_bars INT NOT NULL;
