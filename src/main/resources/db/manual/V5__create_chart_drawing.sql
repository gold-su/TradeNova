CREATE TABLE chart_drawing (
  id BIGINT NOT NULL AUTO_INCREMENT,
  chart_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL,
  start_date DATE NULL,
  start_price DECIMAL(19,4) NOT NULL,
  end_date DATE NULL,
  end_price DECIMAL(19,4) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_chart_drawing_chart FOREIGN KEY (chart_id) REFERENCES training_session_chart(id),
  INDEX idx_chart_drawing_chart (chart_id)
);
