ALTER TABLE chart_drawing ADD COLUMN anchor3_date DATE NULL;
ALTER TABLE chart_drawing ADD COLUMN anchor3_price DECIMAL(19,4) NULL;
ALTER TABLE chart_drawing ADD COLUMN text_content VARCHAR(500) NULL;
ALTER TABLE chart_drawing ADD COLUMN options_json VARCHAR(2000) NULL;
