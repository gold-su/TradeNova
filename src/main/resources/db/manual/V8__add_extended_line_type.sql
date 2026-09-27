-- Extend the existing MySQL ENUM without rewriting or deleting persisted drawings.
ALTER TABLE chart_drawing
    MODIFY COLUMN type ENUM(
        'HORIZONTAL_LINE',
        'VERTICAL_LINE',
        'TREND_LINE',
        'RAY',
        'EXTENDED_LINE',
        'ZONE',
        'PARALLEL_CHANNEL',
        'FIBONACCI_RETRACEMENT',
        'TEXT'
    ) NOT NULL;
