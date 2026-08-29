-- Apply once to existing MySQL databases before deploying the matching application.
-- This project currently uses Hibernate ddl-auto=update rather than Flyway; the
-- directory is intentionally named "manual" so this is not mistaken for an
-- automatically executed migration.

ALTER TABLE training_session_chart
    DROP INDEX uk_chart_session_index,
    ADD COLUMN active_chart_index INT NULL;

UPDATE training_session_chart
SET active_chart_index = CASE WHEN active = TRUE THEN chart_index ELSE NULL END;

ALTER TABLE training_session_chart
    ADD CONSTRAINT uk_session_chart_active_slot
        UNIQUE (session_id, active_chart_index);
