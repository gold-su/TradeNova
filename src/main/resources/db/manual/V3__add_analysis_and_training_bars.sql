ALTER TABLE training_session_chart
    ADD COLUMN analysis_bars INT NULL AFTER bars,
    ADD COLUMN training_bars INT NULL AFTER analysis_bars;

UPDATE training_session_chart
SET analysis_bars = LEAST(bars, 60),
    training_bars = GREATEST(bars - LEAST(bars, 60), 0)
WHERE analysis_bars IS NULL OR training_bars IS NULL;

ALTER TABLE training_session_chart
    MODIFY analysis_bars INT NOT NULL,
    MODIFY training_bars INT NOT NULL;
