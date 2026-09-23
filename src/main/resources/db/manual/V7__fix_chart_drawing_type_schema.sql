-- Existing MySQL ENUM columns are not expanded by Hibernate ddl-auto=update.
-- Keep every v1 value and add the v2 drawing types without rewriting existing rows.
ALTER TABLE chart_drawing
    MODIFY COLUMN type ENUM(
        'HORIZONTAL_LINE',
        'VERTICAL_LINE',
        'TREND_LINE',
        'RAY',
        'ZONE',
        'PARALLEL_CHANNEL',
        'FIBONACCI_RETRACEMENT',
        'TEXT'
    ) NOT NULL,
    MODIFY COLUMN start_price DECIMAL(19,4) NULL;
