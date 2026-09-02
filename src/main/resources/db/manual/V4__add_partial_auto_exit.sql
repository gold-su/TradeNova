-- Existing risk rules represented full exits. Add nullable/defaulted columns first,
-- backfill legacy rows, then enforce the durable contract.
ALTER TABLE training_risk_rule
    ADD COLUMN stop_loss_exit_percent INT NULL DEFAULT 100,
    ADD COLUMN take_profit_exit_percent INT NULL DEFAULT 100,
    ADD COLUMN stop_loss_consumed BOOLEAN NULL DEFAULT FALSE,
    ADD COLUMN take_profit_consumed BOOLEAN NULL DEFAULT FALSE;

UPDATE training_risk_rule
SET stop_loss_exit_percent = COALESCE(stop_loss_exit_percent, 100),
    take_profit_exit_percent = COALESCE(take_profit_exit_percent, 100),
    stop_loss_consumed = COALESCE(stop_loss_consumed, FALSE),
    take_profit_consumed = COALESCE(take_profit_consumed, FALSE);

ALTER TABLE training_risk_rule
    MODIFY stop_loss_exit_percent INT NOT NULL DEFAULT 100,
    MODIFY take_profit_exit_percent INT NOT NULL DEFAULT 100,
    MODIFY stop_loss_consumed BOOLEAN NOT NULL DEFAULT FALSE,
    MODIFY take_profit_consumed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE training_risk_rule_history
    ADD COLUMN stop_loss_exit_percent INT NULL DEFAULT 100,
    ADD COLUMN take_profit_exit_percent INT NULL DEFAULT 100;

UPDATE training_risk_rule_history
SET stop_loss_exit_percent = COALESCE(stop_loss_exit_percent, 100),
    take_profit_exit_percent = COALESCE(take_profit_exit_percent, 100);

ALTER TABLE training_risk_rule_history
    MODIFY stop_loss_exit_percent INT NOT NULL DEFAULT 100,
    MODIFY take_profit_exit_percent INT NOT NULL DEFAULT 100;
